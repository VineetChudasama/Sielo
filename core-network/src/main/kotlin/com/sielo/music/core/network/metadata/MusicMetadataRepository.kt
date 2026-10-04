package com.sielo.music.core.network.metadata

import android.util.Log
import com.sielo.music.core.network.cache.ArtistProfileCache
import com.sielo.music.core.network.innertube.ArtistMetadataResolver
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.innertube.TrackMatchValidator
import com.sielo.music.core.network.innertube.YouTubeArtistImageResolver
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicMetadataRepository @Inject constructor(
    private val musicBrainzClient: MusicBrainzClient,
    private val coverArtArchiveClient: CoverArtArchiveClient,
    private val artistProfileCache: ArtistProfileCache,
    private val innerTubeClient: InnerTubeClient
) {

    private val albumCache = ConcurrentHashMap<String, SieloAlbum>()
    private val albumTracksCache = ConcurrentHashMap<String, List<SieloTrack>>()

    /**
     * Fast synchronous memory cache lookup for artist details.
     */
    fun getCachedArtist(artistName: String): ArtistDetails? {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return null
        val cached = artistProfileCache.get(cleanName) ?: return null
        val hasContent = cached.originalAlbums.isNotEmpty() ||
                cached.featuredAlbums.isNotEmpty() ||
                cached.singles.isNotEmpty() ||
                cached.topSongs.isNotEmpty()
        return if (hasContent) cached else null
    }

    companion object {
        private const val TAG = "MusicMetadata"
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    /**
     * Resolves the MusicBrainz Artist ID (MBID) for a given artist name.
     */
    suspend fun resolveArtistMbid(artistName: String): String? = withContext(Dispatchers.IO) {
        val mbArtist = musicBrainzClient.searchArtist(artistName)
        mbArtist?.id
    }

    /**
     * Searches for artists matching query and maps to SieloArtist.
     */
    suspend fun searchArtist(query: String): List<SieloArtist> = withContext(Dispatchers.IO) {
        try {
            val matched = musicBrainzClient.searchArtist(query)
            if (matched != null) {
                val photo = YouTubeArtistImageResolver.getCachedArtistImageUrl(matched.name)
                    ?: ArtistMetadataResolver.fetchWikipediaBio(matched.name)?.photoUrl
                    ?: YouTubeArtistImageResolver.resolveArtistImageUrl(matched.name)
                listOf(MusicBrainzMapper.toSieloArtist(matched, photo))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchArtist error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Primary entry point for loading artist profile and discography.
     * Uses MusicBrainz for authoritative discography and Cover Art Archive for artwork,
     * while gracefully preserving existing fallback systems for bio, top tracks, and image.
     */
    suspend fun getArtistDetails(
        artistName: String,
        imageUrl: String? = null,
        artistId: String? = null,
        forceRefresh: Boolean = false
    ): ArtistDetails? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext null

        if (forceRefresh) {
            artistProfileCache.remove(cleanName)
            if (!artistId.isNullOrBlank()) {
                artistProfileCache.remove(artistId)
            }
        } else {
            // 1. Session Memory Cache Check
            val cached = getCachedArtist(cleanName)
            if (cached != null) {
                Log.d(TAG, "Cache hit for artist: $cleanName")
                return@withContext cached
            }
        }

        Log.d(TAG, "Resolving artist discography from MusicBrainz for: $cleanName (forceRefresh=$forceRefresh)")

        coroutineScope {
            // Parallel metadata jobs with robust timeouts to prevent slow external endpoints from blocking UI
            val mbArtistDeferred = async {
                try {
                    withTimeoutOrNull(2500L) {
                        musicBrainzClient.searchArtist(cleanName)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val wikiBioDeferred = async {
                try {
                    withTimeoutOrNull(2000L) {
                        ArtistMetadataResolver.fetchWikipediaBio(cleanName)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val portraitDeferred = async {
                YouTubeArtistImageResolver.resolveArtistImageUrl(cleanName)
            }
            val topTracksDeferred = async {
                try {
                    val searchResult = innerTubeClient.search(cleanName)
                    searchResult.filter { track ->
                        val trackArtist = track.artist.lowercase()
                        val qLower = cleanName.lowercase()
                        trackArtist.contains(qLower) || qLower.contains(trackArtist)
                    }.take(15)
                } catch (e: Exception) {
                    emptyList<SieloTrack>()
                }
            }
            val fallbackDetailsDeferred = async {
                try {
                    withTimeoutOrNull(2500L) {
                        innerTubeClient.getArtistDetails(cleanName, imageUrl, artistId)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            val mbArtist = mbArtistDeferred.await()
            val wikiBio = wikiBioDeferred.await()
            val resolvedPhoto = portraitDeferred.await()
            val fallbackDetails = fallbackDetailsDeferred.await()
            val rawTopTracks = topTracksDeferred.await()

            val deathYear = mbArtist?.lifeSpan?.end?.take(4)?.toIntOrNull()
            val isDeceased = mbArtist?.lifeSpan?.ended == true || deathYear != null
            val maxActiveYear = if (deathYear != null) deathYear + 3 else null

            // Prioritize verified human portraits over promotional single covers/avatars:
            // 1. Wikipedia verified encyclopedia photo (e.g. Mukesh_Indian_Singer.jpg)
            // 2. JioSaavn artist CDN verified studio portrait (e.g. Mukesh_500x500.jpg)
            // 3. YouTubeArtistImageResolver (Deezer/JioSaavn)
            // 4. Candidate image passed from caller (only if not a YouTube avatar for classic deceased artists)
            val isYouTubeAvatar = imageUrl?.let {
                it.contains("googleusercontent.com") || it.contains("ggpht.com") || it.contains("ytimg.com")
            } == true

            val verifiedWikiPhoto = wikiBio?.photoUrl?.takeIf { it.isNotBlank() }
            val verifiedSaavnPhoto = fallbackDetails?.imageUrl?.takeIf {
                !isPlaceholderImage(it) && !it.contains("default")
            }

            val authenticPhoto = verifiedWikiPhoto
                ?: verifiedSaavnPhoto
                ?: resolvedPhoto
                ?: if (!imageUrl.isNullOrBlank() && !isPlaceholderImage(imageUrl) && (!isDeceased || !isYouTubeAvatar)) imageUrl else null
                ?: if (!imageUrl.isNullOrBlank() && !isPlaceholderImage(imageUrl)) imageUrl else null

            // For classic artists, prefer authentic provider catalog top songs over raw YouTube search
            val topSongs = if (isDeceased && !fallbackDetails?.topSongs.isNullOrEmpty()) {
                fallbackDetails!!.topSongs.take(15)
            } else if (rawTopTracks.isNotEmpty()) {
                rawTopTracks
            } else {
                fallbackDetails?.topSongs.orEmpty().take(15)
            }

            // If MusicBrainz found the artist, build discography from MusicBrainz + CAA
            if (mbArtist != null) {
                val mbid = mbArtist.id
                Log.d(TAG, "MusicBrainz MBID resolved: $mbid for $cleanName (isDeceased=$isDeceased, maxActiveYear=$maxActiveYear)")

                // Fetch release-groups and direct releases in parallel
                val rgsDeferred = async { musicBrainzClient.getArtistReleaseGroups(mbid, maxLimit = 100) }
                val releasesDeferred = async { musicBrainzClient.getArtistReleases(mbid, limit = 100) }

                val releaseGroups = rgsDeferred.await()
                val releases = releasesDeferred.await()

                // Map release-group ID to the real track count from releases media
                val rgTrackCountMap = releases.groupBy { it.releaseGroup?.id }
                    .mapValues { (_, rels) ->
                        rels.maxOfOrNull { r -> r.media.sumOf { m -> m.trackCount } } ?: 0
                    }

                // Collect release groups from both endpoints, deduplicating by MBID
                val rgMap = LinkedHashMap<String, MbReleaseGroup>()
                for (rg in releaseGroups) {
                    rgMap[rg.id] = rg
                }
                for (rel in releases) {
                    val rg = rel.releaseGroup
                    if (rg != null && !rgMap.containsKey(rg.id)) {
                        rgMap[rg.id] = rg
                    }
                }

                Log.d(TAG, "Total unique release groups collected: ${rgMap.size}")

                // Step 1: Collect all known Album and Soundtrack titles & recording track titles
                val existingAlbumTitles = mutableSetOf<String>()
                val existingAlbumTrackTitles = mutableSetOf<String>()

                for (rel in releases) {
                    val rg = rel.releaseGroup
                    val pt = rg?.primaryType
                    val st = rg?.secondaryTypes ?: emptyList()
                    val relTitle = rel.title
                    val rgTitle = rg?.title ?: ""

                    val isAlbumOrSoundtrack = pt == "Album" ||
                            st.contains("Soundtrack") ||
                            extractMovieInfo(relTitle) != null ||
                            extractMovieInfo(rgTitle) != null

                    if (isAlbumOrSoundtrack) {
                        existingAlbumTitles.add(normalizeSongTitle(relTitle))
                        if (rgTitle.isNotBlank()) existingAlbumTitles.add(normalizeSongTitle(rgTitle))
                        for (m in rel.media) {
                            for (tr in m.tracks) {
                                if (tr.title.isNotBlank()) {
                                    existingAlbumTrackTitles.add(normalizeSongTitle(tr.title))
                                }
                            }
                        }
                    }
                }

                // Step 2: Categorize release groups into Studio Albums, Soundtracks, and Candidate Singles
                val studioRgList = mutableListOf<MbReleaseGroup>()
                val candidateSingleRgs = mutableListOf<MbReleaseGroup>()
                val soundtrackRgList = mutableListOf<MbReleaseGroup>()

                for (rg in rgMap.values) {
                    val rgYear = MusicBrainzMapper.extractYear(rg.firstReleaseDate)?.toIntOrNull()
                    if (maxActiveYear != null && rgYear != null && rgYear > maxActiveYear) {
                        // Skip modern posthumous repackages / digital dumps decades after artist's lifetime
                        continue
                    }

                    val movieInfo = extractMovieInfo(rg.title)
                    val category = MusicBrainzMapper.categorizeReleaseGroup(rg)

                    if (movieInfo != null || category == ReleaseType.SOUNDTRACK || rg.secondaryTypes.contains("Soundtrack")) {
                        soundtrackRgList.add(rg)
                        if (movieInfo != null) {
                            existingAlbumTitles.add(normalizeSongTitle(movieInfo.first))
                            existingAlbumTrackTitles.add(normalizeSongTitle(movieInfo.second))
                        } else {
                            existingAlbumTitles.add(normalizeSongTitle(cleanMovieName(rg.title)))
                        }
                    } else {
                        when (category) {
                            ReleaseType.ALBUM -> {
                                val artistDisplay = MusicBrainzMapper.formatArtistCredit(rg.artistCredit, cleanName)
                                val isPrimaryArtist = rg.artistCredit.isEmpty() ||
                                    rg.artistCredit.firstOrNull()?.let { isArtistMatch(it.name, cleanName) || it.artist?.id == mbid } == true
                                if (isPrimaryArtist &&
                                    rg.id != "03042be0-a47e-45c7-9138-86a9e689c067" &&
                                    !TrackMatchValidator.isCompilationAlbum(rg.title, cleanName) &&
                                    TrackMatchValidator.isAlbumMadeByArtist(rg.title, artistDisplay, cleanName)
                                ) {
                                    studioRgList.add(rg)
                                    existingAlbumTitles.add(normalizeSongTitle(rg.title))
                                }
                            }
                            ReleaseType.EP -> {
                                val artistDisplay = MusicBrainzMapper.formatArtistCredit(rg.artistCredit, cleanName)
                                val isPrimaryArtist = rg.artistCredit.isEmpty() ||
                                    rg.artistCredit.firstOrNull()?.let { isArtistMatch(it.name, cleanName) || it.artist?.id == mbid } == true
                                if (isPrimaryArtist &&
                                    rg.id != "03042be0-a47e-45c7-9138-86a9e689c067" &&
                                    !TrackMatchValidator.isCompilationAlbum(rg.title, cleanName) &&
                                    TrackMatchValidator.isAlbumMadeByArtist(rg.title, artistDisplay, cleanName)
                                ) {
                                    val tc = rgTrackCountMap[rg.id] ?: 0
                                    if (tc > 2) {
                                        // Multi-track authentic EP: showcase under studio albums
                                        studioRgList.add(rg)
                                        existingAlbumTitles.add(normalizeSongTitle(rg.title))
                                    } else {
                                        // 1-2 track EP candidate for singles
                                        candidateSingleRgs.add(rg)
                                    }
                                }
                            }
                            ReleaseType.SINGLE -> {
                                val isPrimaryArtist = rg.artistCredit.isEmpty() ||
                                    rg.artistCredit.firstOrNull()?.let { isArtistMatch(it.name, cleanName) || it.artist?.id == mbid } == true
                                if (isPrimaryArtist &&
                                    rg.id != "03042be0-a47e-45c7-9138-86a9e689c067" &&
                                    !TrackMatchValidator.isCompilationAlbum(rg.title, cleanName)
                                ) {
                                    candidateSingleRgs.add(rg)
                                }
                            }
                            ReleaseType.COMPILATION, ReleaseType.UNKNOWN, ReleaseType.SOUNDTRACK -> {
                                // Exclude pure compilations
                            }
                        }
                    }
                }

                // Step 3: Group soundtrack releases by movie name so all songs from the same movie form ONE album
                val movieSoundtrackMap = LinkedHashMap<String, MutableList<Pair<MbReleaseGroup, Pair<String, String>>>>()
                val standaloneSoundtrackRgs = mutableListOf<MbReleaseGroup>()

                for (rg in soundtrackRgList) {
                    val movieInfo = extractMovieInfo(rg.title)
                    if (movieInfo != null) {
                        val normKey = movieInfo.first.trim().lowercase()
                        movieSoundtrackMap.getOrPut(normKey) { mutableListOf() }.add(rg to movieInfo)
                        existingAlbumTitles.add(normalizeSongTitle(movieInfo.first))
                        existingAlbumTrackTitles.add(normalizeSongTitle(movieInfo.second))
                    } else {
                        standaloneSoundtrackRgs.add(rg)
                    }
                }

                val fallbackCover = authenticPhoto ?: topSongs.firstOrNull()?.thumbnailUrl
                val groupedSoundtracks = mutableListOf<SieloAlbum>()

                // Create combined Soundtrack albums for each movie
                for ((_, entries) in movieSoundtrackMap) {
                    val canonicalMovieName = entries.first().second.first
                    val primaryRg = entries.first().first
                    val latestYear = entries.mapNotNull { MusicBrainzMapper.extractYear(it.first.firstReleaseDate) }
                        .maxByOrNull { it.toIntOrNull() ?: 0 }
                    val primaryCover = coverArtArchiveClient.getDirectReleaseGroupUrls(primaryRg.id)

                    val movieTracks = entries.map { (rg, info) ->
                        val trackCover = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id).medium ?: fallbackCover
                        existingAlbumTrackTitles.add(normalizeSongTitle(info.second))
                        SieloTrack(
                            id = rg.id,
                            title = info.second,
                            artist = cleanName,
                            album = canonicalMovieName,
                            durationText = "3:30",
                            durationSeconds = 210,
                            thumbnailUrl = trackCover
                        )
                    }

                    val movieAlbum = SieloAlbum(
                        id = "soundtrack_" + canonicalMovieName.lowercase().replace(Regex("[^a-z0-9]"), "_"),
                        title = canonicalMovieName,
                        artist = cleanName,
                        year = latestYear,
                        thumbnailUrl = primaryCover.medium ?: fallbackCover,
                        tracks = movieTracks,
                        songCount = movieTracks.size,
                        type = "Soundtrack",
                        releaseDate = entries.mapNotNull { it.first.firstReleaseDate }.maxOrNull(),
                        musicBrainzId = primaryRg.id,
                        releaseType = "SOUNDTRACK"
                    )
                    groupedSoundtracks.add(movieAlbum)
                }

                // Add standalone soundtrack releases (e.g. official full OST albums)
                for (rg in standaloneSoundtrackRgs) {
                    val normTitle = cleanMovieName(rg.title).trim().lowercase()
                    if (movieSoundtrackMap.containsKey(normTitle)) continue

                    val realCount = rgTrackCountMap[rg.id] ?: 0
                    groupedSoundtracks.add(
                        MusicBrainzMapper.toSieloAlbum(
                            rg = rg,
                            coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                            fallbackArtistName = cleanName,
                            fallbackArtworkUrl = fallbackCover,
                            explicitTrackCount = if (realCount > 0) realCount else null
                        )
                    )
                }

                val fallbackDetails = fallbackDetailsDeferred.await()
                val verifiedSoundtracksFromProvider = fallbackDetails?.featuredAlbums.orEmpty().filter { album ->
                    val yr = album.year?.toIntOrNull()
                    if (maxActiveYear != null && yr != null && yr > maxActiveYear) {
                        false
                    } else {
                        !TrackMatchValidator.isCompilationAlbum(album.title, cleanName)
                    }
                }

                val allSoundtracks = (groupedSoundtracks + verifiedSoundtracksFromProvider)
                    .distinctBy { normalizeSongTitle(it.title) }

                val soundtracks = if (isDeceased) {
                    allSoundtracks.sortedBy { it.year?.toIntOrNull() ?: 9999 }
                } else {
                    allSoundtracks.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                }

                // Step 4: Strictly filter Singles:
                // - No tracks already present in albums/soundtracks
                // - No instrumental versions / karaoke / covers / tributes / lofi / slowed+reverb
                // - Keep strictly for songs produced by the artist (reject third party posters)
                // - Only if it was a single song
                val acceptedSingleRgs = mutableListOf<MbReleaseGroup>()

                for (rg in candidateSingleRgs) {
                    val title = rg.title
                    val dis = rg.disambiguation
                    val st = rg.secondaryTypes
                    val normTitle = normalizeSongTitle(title)

                    // 1. Movie / Soundtrack filter
                    if (extractMovieInfo(title) != null || st.contains("Soundtrack") || TrackMatchValidator.isSoundtrackRelease(title, rg.primaryType)) {
                        continue
                    }

                    // 2. Reject if already present in albums or soundtracks
                    if (normTitle in existingAlbumTitles || normTitle in existingAlbumTrackTitles) {
                        continue
                    }

                    // 3. Reject instrumentals, karaoke, covers, tributes, lofi, or slowed+reverb
                    if (isInstrumentalOrTributeOrCover(title, dis)) {
                        continue
                    }

                    // 4. Reject compilations or remixes
                    if (st.contains("Compilation") || st.contains("Remix")) {
                        continue
                    }

                    // 5. Check artist credit: strictly for songs produced/sung by the artist
                    if (rg.artistCredit.isNotEmpty()) {
                        val firstArtist = rg.artistCredit[0].name.orEmpty()
                        if (isThirdPartyArtistOrMixer(firstArtist)) {
                            continue
                        }
                        if (rg.artistCredit.size > 3 && !firstArtist.contains(cleanName, ignoreCase = true)) {
                            continue
                        }
                        val isTargetCredited = rg.artistCredit.any { credit ->
                            credit.artist?.id == mbid || isArtistMatch(credit.name, cleanName)
                        }
                        if (!isTargetCredited) {
                            continue
                        }
                    }

                    // 6. Must be a single song (not multi-track album/EP)
                    val tc = rgTrackCountMap[rg.id] ?: 1
                    if (tc > 2) {
                        if (!studioRgList.any { it.id == rg.id }) {
                            studioRgList.add(rg)
                        }
                        continue
                    }

                    acceptedSingleRgs.add(rg)
                }

                // Map to SieloAlbum objects with exact track counts
                val studioAlbumsFromMb = studioRgList.map { rg ->
                    val realCount = rgTrackCountMap[rg.id] ?: 0
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover,
                        explicitTrackCount = if (realCount > 0) realCount else null
                    )
                }.filter {
                    TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, cleanName) &&
                    !TrackMatchValidator.isCompilationAlbum(it.title, cleanName) &&
                    it.musicBrainzId != "03042be0-a47e-45c7-9138-86a9e689c067"
                }

                val verifiedStudioFromProvider = fallbackDetails?.originalAlbums.orEmpty().filter { album ->
                    val yr = album.year?.toIntOrNull()
                    if (maxActiveYear != null && yr != null && yr > maxActiveYear) {
                        false
                    } else {
                        !TrackMatchValidator.isCompilationAlbum(album.title, cleanName) &&
                        TrackMatchValidator.isAlbumMadeByArtist(album.title, album.artist, cleanName)
                    }
                }

                val allStudioAlbums = (studioAlbumsFromMb + verifiedStudioFromProvider)
                    .distinctBy { normalizeSongTitle(it.title) }

                val studioAlbums = if (isDeceased) {
                    allStudioAlbums.sortedBy { it.year?.toIntOrNull() ?: 9999 }
                } else {
                    allStudioAlbums.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                }

                val singles = acceptedSingleRgs.filter { rg ->
                    val yr = MusicBrainzMapper.extractYear(rg.firstReleaseDate)?.toIntOrNull()
                    maxActiveYear == null || yr == null || yr <= maxActiveYear
                }.map { rg ->
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover,
                        explicitTrackCount = 1
                    )
                }.let { list ->
                    if (isDeceased) list.sortedBy { it.year?.toIntOrNull() ?: 9999 }
                    else list.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                }

                val pastAlbums = studioAlbums
                val eligibleForLatest = (studioAlbums + singles).ifEmpty { soundtracks }
                val latestAlbum = if (isDeceased) {
                    eligibleForLatest.firstOrNull() ?: studioAlbums.firstOrNull()
                } else {
                    eligibleForLatest.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: studioAlbums.firstOrNull()
                }

                val bioText = wikiBio?.bio?.takeIf { it.isNotBlank() }
                    ?: mbArtist.disambiguation?.takeIf { it.isNotBlank() }
                    ?: "$cleanName is a celebrated musical artist featured on Sielo, renowned for their acclaimed compositions, iconic releases, and globally streamed catalog."

                val genresList = if (mbArtist.tags.isNotEmpty()) {
                    mbArtist.tags.sortedByDescending { it.count }.map { it.name }.take(5)
                } else emptyList()

                val cleanLower = cleanName.lowercase().trim()
                val rawSimilar = if (!fallbackDetails?.similarArtists.isNullOrEmpty()) {
                    fallbackDetails!!.similarArtists
                } else {
                    innerTubeClient.getSimilarArtistsForArtist(cleanName)
                }
                val resolvedSimilarArtists = rawSimilar.filter { artist ->
                    val aLower = artist.name.lowercase().trim()
                    aLower != cleanLower &&
                    !aLower.split(Regex("[^a-z0-9]+")).contains(cleanLower) &&
                    !cleanLower.split(Regex("[^a-z0-9]+")).contains(aLower)
                }.distinctBy { it.name.trim().lowercase() }.take(10)

                val details = ArtistDetails(
                    id = mbid,
                    name = cleanName,
                    imageUrl = authenticPhoto,
                    heroImageUrl = authenticPhoto ?: fallbackCover,
                    bio = bioText,
                    latestAlbum = latestAlbum,
                    topSongs = topSongs,
                    pastAlbums = pastAlbums,
                    originalAlbums = studioAlbums,
                    featuredAlbums = soundtracks,
                    singles = singles,
                    similarArtists = resolvedSimilarArtists,
                    isVerified = true,
                    wikiUrl = wikiBio?.wikiUrl,
                    genres = genresList,
                    origin = wikiBio?.origin ?: mbArtist.country,
                    activeYears = wikiBio?.activeYears,
                    recordLabel = null,
                    description = wikiBio?.description,
                    musicBrainzId = mbid
                )

                Log.d(TAG, "MusicMetadata resolved: Albums=${studioAlbums.size} Singles=${singles.size} Soundtracks=${soundtracks.size}")

                artistProfileCache.put(cleanName, details)
                artistProfileCache.put(mbid, details)
                return@coroutineScope details
            }

            // Fallback path: If MusicBrainz had no match, fallback to InnerTubeClient guaranteed profile
            Log.w(TAG, "MusicBrainz artist not found. Delegating to fallback resolver for: $cleanName")
            val profileFallback = fallbackDetails ?: innerTubeClient.getArtistDetails(cleanName, authenticPhoto, artistId)
            if (profileFallback != null) {
                val cleanLower = cleanName.lowercase().trim()
                val rawFallbackSimilar = if (!profileFallback.similarArtists.isNullOrEmpty()) {
                    profileFallback.similarArtists
                } else {
                    innerTubeClient.getSimilarArtistsForArtist(cleanName)
                }
                val fallbackSimilar = rawFallbackSimilar.filter { artist ->
                    val aLower = artist.name.lowercase().trim()
                    aLower != cleanLower &&
                    !aLower.split(Regex("[^a-z0-9]+")).contains(cleanLower) &&
                    !cleanLower.split(Regex("[^a-z0-9]+")).contains(aLower)
                }.distinctBy { it.name.trim().lowercase() }.take(10)
                val enhancedFallback = profileFallback.copy(
                    imageUrl = authenticPhoto ?: profileFallback.imageUrl,
                    heroImageUrl = authenticPhoto ?: profileFallback.heroImageUrl ?: profileFallback.imageUrl,
                    similarArtists = fallbackSimilar
                )
                artistProfileCache.put(cleanName, enhancedFallback)
                return@coroutineScope enhancedFallback
            }
            null
        }
    }

    /**
     * Retrieves tracklist for an album.
     * If the album has a MusicBrainz ID or UUID, retrieves official recordings from MusicBrainz.
     * Otherwise delegates to existing stream/album provider.
     */
    suspend fun getAlbumTracks(album: SieloAlbum): List<SieloTrack> = withContext(Dispatchers.IO) {
        if (album.tracks.isNotEmpty()) return@withContext album.tracks

        val cacheKey = album.musicBrainzId ?: album.id
        albumTracksCache[cacheKey]?.let { return@withContext it }

        val mbid = album.musicBrainzId ?: if (isUuid(album.id)) album.id else null

        if (mbid != null) {
            try {
                Log.d(TAG, "Fetching album tracks from MusicBrainz for RG: $mbid (${album.title})")
                val releases = musicBrainzClient.getReleaseGroupReleases(mbid)

                // Pick best release: prefer official status or largest track count
                val bestRelease = releases.maxByOrNull { rel ->
                    val tracksCount = rel.media.sumOf { it.tracks.size }
                    val statusBonus = if (rel.status.equals("Official", ignoreCase = true)) 1000 else 0
                    tracksCount + statusBonus
                }

                if (bestRelease != null) {
                    val rawTracks = bestRelease.media.flatMap { it.tracks }
                    if (rawTracks.isNotEmpty()) {
                        val mappedTracks = rawTracks.map { track ->
                            MusicBrainzMapper.toSieloTrack(
                                track = track,
                                albumTitle = album.title,
                                albumId = mbid,
                                albumArtworkUrl = album.thumbnailUrl
                            )
                        }
                        Log.d(TAG, "Successfully retrieved ${mappedTracks.size} tracks for album: ${album.title}")
                        albumTracksCache[cacheKey] = mappedTracks
                        return@withContext mappedTracks
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching MusicBrainz tracks for ${album.title}: ${e.message}")
            }
        }

        // Fallback to InnerTubeClient album lookup
        val fallbackTracks = innerTubeClient.getAlbumSongs(album.id, album.title, album.artist)
        if (fallbackTracks.isNotEmpty()) {
            albumTracksCache[cacheKey] = fallbackTracks
        }
        fallbackTracks
    }

    private fun isUuid(str: String): Boolean = UUID_REGEX.matches(str)

    private val FROM_MOVIE_REGEX = Regex(
        """[\(\[]\s*From\s+(?:the\s+(?:movie|film|soundtrack)\s+)?[\"\'\u201c\u201d]?([^\"\'\u201c\u201d\(\)\[\]]+)[\"\'\u201c\u201d]?\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private val OST_SUFFIX_REGEX = Regex(
        """[\(\[]\s*(?:Original\s+Motion\s+Picture\s+Soundtrack|Original\s+Soundtrack|Soundtrack|OST)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private fun extractMovieInfo(title: String): Pair<String, String>? {
        val match = FROM_MOVIE_REGEX.find(title)
        if (match != null) {
            val movie = match.groupValues[1].trim()
            var song = title.replace(match.value, "")
                .replace(Regex("""\s*-\s*Single\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*-\s*EP\b""", RegexOption.IGNORE_CASE), "")
                .trim(' ', '-', '•', ',')
            if (song.isBlank()) song = title
            if (movie.isNotBlank()) {
                return movie to song
            }
        }
        return null
    }

    private fun cleanMovieName(title: String): String {
        return title.replace(OST_SUFFIX_REGEX, "").trim(' ', '-', '•', ',')
    }

    private val INSTRUMENTAL_OR_COVER_REGEX = Regex(
        """\b(instrumental|karaoke|backing\s+track|minus\s+one|cover|acoustic\s+cover|tribute|tribute\s+to|piano\s+version|piano\s+cover|piano\s+instrumental|violin|flute|guitar\s+instrumental|saxophone|sax|orchestral|orchestra|string\s+quartet|lo-?fi|lo-?fi\s+flip|slowed\s*\+?\s*reverb|slowed\s+and\s+reverb|ringtone|tones|re-?make|meditation|sleep\s+music)\b""",
        RegexOption.IGNORE_CASE
    )

    private val THIRD_PARTY_PRODUCER_REGEX = Regex(
        """\b(instrumental|tribute|karaoke|cover|covers|orchestra|piano|sleep|relaxing|lo-?fi|flip|remix|dj|beats|soundtrack tribute|various artists)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun normalizeSongTitle(title: String): String {
        val withoutBrackets = title.replace(Regex("""[\(\[].*?[\)\]]"""), " ")
        val withoutSuffixes = withoutBrackets.replace(
            Regex("""\b(single|ep|version|mix|original|title track|audio|video|lyric video|remix)\b""", RegexOption.IGNORE_CASE),
            " "
        )
        return withoutSuffixes.replace(Regex("""[^a-zA-Z0-9]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }

    private fun isArtistMatch(name: String?, target: String): Boolean {
        if (name.isNullOrBlank()) return false
        val nLower = name.trim().lowercase()
        val tLower = target.trim().lowercase()
        return nLower == tLower || nLower.contains(tLower) || tLower.contains(nLower)
    }

    private fun isInstrumentalOrTributeOrCover(title: String, disambiguation: String? = null): Boolean {
        if (INSTRUMENTAL_OR_COVER_REGEX.containsMatchIn(title)) return true
        if (!disambiguation.isNullOrBlank() && INSTRUMENTAL_OR_COVER_REGEX.containsMatchIn(disambiguation)) return true
        return false
    }

    private fun isThirdPartyArtistOrMixer(name: String): Boolean {
        return THIRD_PARTY_PRODUCER_REGEX.containsMatchIn(name)
    }

    private fun isPlaceholderImage(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return lower.contains("default") || lower.contains("placeholder") || lower.contains("blank") || lower.contains("user_default")
    }
}
