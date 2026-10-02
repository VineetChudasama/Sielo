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
                listOf(MusicBrainzMapper.toSieloArtist(matched))
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
        artistId: String? = null
    ): ArtistDetails? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext null

        // 1. Session Memory Cache Check
        val cached = artistProfileCache.get(cleanName)
        if (cached != null && !cached.musicBrainzId.isNullOrBlank()) {
            val hasDiscography = cached.originalAlbums.isNotEmpty() ||
                    cached.featuredAlbums.isNotEmpty() ||
                    cached.singles.isNotEmpty()
            if (hasDiscography) {
                Log.d(TAG, "Cache hit for artist: $cleanName")
                return@withContext cached
            }
        }

        Log.d(TAG, "Resolving artist discography from MusicBrainz for: $cleanName")

        coroutineScope {
            // Parallel metadata jobs
            val mbArtistDeferred = async { musicBrainzClient.searchArtist(cleanName) }
            val wikiBioDeferred = async { ArtistMetadataResolver.fetchWikipediaBio(cleanName) }
            val portraitDeferred = async { YouTubeArtistImageResolver.resolveArtistImageUrl(cleanName) ?: imageUrl }
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

            val mbArtist = mbArtistDeferred.await()
            val wikiBio = wikiBioDeferred.await()
            val resolvedPhoto = portraitDeferred.await()
            val topSongs = topTracksDeferred.await()

            // If MusicBrainz found the artist, build discography from MusicBrainz + CAA
            if (mbArtist != null) {
                val mbid = mbArtist.id
                Log.d(TAG, "MusicBrainz MBID resolved: $mbid for $cleanName")

                // Fetch release-groups and direct releases in parallel
                val rgsDeferred = async { musicBrainzClient.getArtistReleaseGroups(mbid, maxLimit = 150) }
                val releasesDeferred = async { musicBrainzClient.getArtistReleases(mbid, limit = 80) }

                val releaseGroups = rgsDeferred.await()
                val releases = releasesDeferred.await()

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

                // Categorize release groups into Studio Albums, Singles, EPs, Soundtracks, Compilations
                val studioRgList = mutableListOf<MbReleaseGroup>()
                val singleRgList = mutableListOf<MbReleaseGroup>()
                val epRgList = mutableListOf<MbReleaseGroup>()
                val soundtrackRgList = mutableListOf<MbReleaseGroup>()

                for (rg in rgMap.values) {
                    when (MusicBrainzMapper.categorizeReleaseGroup(rg)) {
                        ReleaseType.ALBUM -> {
                            if (!TrackMatchValidator.isCompilationAlbum(rg.title, cleanName)) {
                                studioRgList.add(rg)
                            }
                        }
                        ReleaseType.SINGLE -> {
                            if (!TrackMatchValidator.isCompilationAlbum(rg.title, cleanName)) {
                                singleRgList.add(rg)
                            }
                        }
                        ReleaseType.EP -> {
                            if (!TrackMatchValidator.isCompilationAlbum(rg.title, cleanName)) {
                                epRgList.add(rg)
                            }
                        }
                        ReleaseType.SOUNDTRACK -> {
                            soundtrackRgList.add(rg)
                        }
                        ReleaseType.COMPILATION, ReleaseType.UNKNOWN -> {
                            // Exclude pure compilations from studio albums
                        }
                    }
                }

                // Direct Cover Art Archive URLs (0 upfront network overhead, images load asynchronously via Coil)
                val fallbackCover = resolvedPhoto ?: topSongs.firstOrNull()?.thumbnailUrl

                // Map to SieloAlbum objects
                val studioAlbums = studioRgList.map { rg ->
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover
                    )
                }.sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val singles = singleRgList.map { rg ->
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover
                    )
                }.sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val eps = epRgList.map { rg ->
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover
                    )
                }.sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val soundtracks = soundtrackRgList.map { rg ->
                    MusicBrainzMapper.toSieloAlbum(
                        rg = rg,
                        coverUrls = coverArtArchiveClient.getDirectReleaseGroupUrls(rg.id),
                        fallbackArtistName = cleanName,
                        fallbackArtworkUrl = fallbackCover
                    )
                }.sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val pastAlbums = (studioAlbums + eps).distinctBy { it.id }
                val eligibleForLatest = (studioAlbums + singles + eps).ifEmpty { soundtracks }
                val latestAlbum = eligibleForLatest.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: studioAlbums.firstOrNull()

                val bioText = wikiBio?.bio?.takeIf { it.isNotBlank() }
                    ?: mbArtist.disambiguation?.takeIf { it.isNotBlank() }
                    ?: "$cleanName is a celebrated musical artist featured on Sielo, renowned for their acclaimed compositions, iconic releases, and globally streamed catalog."

                val genresList = if (mbArtist.tags.isNotEmpty()) {
                    mbArtist.tags.sortedByDescending { it.count }.map { it.name }.take(5)
                } else emptyList()

                val details = ArtistDetails(
                    id = mbid,
                    name = cleanName,
                    imageUrl = resolvedPhoto,
                    heroImageUrl = wikiBio?.photoUrl ?: resolvedPhoto,
                    bio = bioText,
                    latestAlbum = latestAlbum,
                    topSongs = topSongs,
                    pastAlbums = pastAlbums,
                    originalAlbums = studioAlbums,
                    featuredAlbums = soundtracks,
                    singles = singles,
                    similarArtists = emptyList(),
                    isVerified = true,
                    wikiUrl = wikiBio?.wikiUrl,
                    genres = genresList,
                    origin = wikiBio?.origin ?: mbArtist.country,
                    activeYears = wikiBio?.activeYears,
                    recordLabel = null,
                    description = wikiBio?.description,
                    musicBrainzId = mbid
                )

                Log.d(TAG, "MusicMetadata resolved: Albums=${studioAlbums.size} EPs=${eps.size} Singles=${singles.size} Soundtracks=${soundtracks.size}")

                artistProfileCache.put(cleanName, details)
                artistProfileCache.put(mbid, details)
                return@coroutineScope details
            }

            // Fallback path: If MusicBrainz had no match, fallback to InnerTubeClient guaranteed profile
            Log.w(TAG, "MusicBrainz artist not found. Delegating to fallback resolver for: $cleanName")
            val fallbackDetails = innerTubeClient.getArtistDetails(cleanName, resolvedPhoto, artistId)
            if (fallbackDetails != null) {
                artistProfileCache.put(cleanName, fallbackDetails)
            }
            fallbackDetails
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
}
