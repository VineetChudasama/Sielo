package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.database.dao.CandidateSongStat
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.RecommendationHistoryDao
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.recommendations.BuildConfig
import com.sielo.music.core.recommendations.api.LastFmApiService
import com.sielo.music.core.recommendations.api.di.LastFmRateLimiter
import com.sielo.music.core.recommendations.repository.SimilarArtistsRepository
import com.sielo.music.core.recommendations.util.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

data class CandidatePools(
    val poolA: List<SieloTrack>, // Familiar (~60%)
    val poolB: List<SieloTrack>, // Discovery (~25%)
    val poolC: List<SieloTrack>  // Moderate rotation (~15%)
)

@Singleton
open class CandidatePoolBuilder @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val recommendationHistoryDao: RecommendationHistoryDao,
    private val similarArtistsRepository: SimilarArtistsRepository,
    private val lastFmApiService: LastFmApiService,
    @LastFmRateLimiter private val lastFmRateLimiter: RateLimiter,
    private val innerTubeClient: InnerTubeClient
) {

    companion object {
        const val RECENCY_DECAY_DAYS = 30.0
        const val MODERATE_ROTATION_LOOKBACK_DAYS = 60L
        const val TOP_ARTISTS_LIMIT = 15
        const val SIMILAR_ARTISTS_LIMIT = 8
        const val TRACKS_PER_SIMILAR_ARTIST = 4

        private val FORBIDDEN_TITLE_PATTERNS = listOf(
            "remix", "re-mix", "rmx",
            "edit", "radio edit", "club edit", "extended edit", "short edit", "dj edit",
            "slowed", "reverb", "sped up", "speed up", "speedup", "nightcore", "daycore",
            "tribute", "cover", "karaoke", "instrumental", "acoustic version",
            "bass boosted", "chopped and screwed", "lofi flip", "lo-fi flip",
            "remake", "parody", "mashup", "mash-up", "mash up"
        )

        fun isCleanStudioTrack(title: String, artist: String): Boolean {
            val lowerTitle = title.lowercase()
            for (pattern in FORBIDDEN_TITLE_PATTERNS) {
                val regex = Regex("""(?i)(?:\b|[\(\[\{_\-\/])${Regex.escape(pattern)}(?:\b|[\)\]\}_\-\/])""")
                if (regex.containsMatchIn(lowerTitle)) {
                    return false
                }
            }
            if (lowerTitle.contains("radio edit") || lowerTitle.contains("club edit") || lowerTitle.contains("extended mix") || lowerTitle.contains("sped up")) {
                return false
            }
            val lowerArtist = artist.lowercase()
            if (listOf("tribute", "karaoke", "cover band", "various artists").any { lowerArtist.contains(it) }) {
                return false
            }
            return true
        }

        fun isTitleTooSimilar(candidateTitle: String, seedTitle: String): Boolean {
            val cleanCandidate = candidateTitle.lowercase()
                .replace(Regex("""[\(\[\{].*?[\)\]\}]"""), "")
                .replace(Regex("""[^\w\s]"""), " ")
                .trim()
            val cleanSeed = seedTitle.lowercase()
                .replace(Regex("""[\(\[\{].*?[\)\]\}]"""), "")
                .replace(Regex("""[^\w\s]"""), " ")
                .trim()

            if (cleanCandidate.isBlank() || cleanSeed.isBlank()) return false
            if (cleanCandidate == cleanSeed) return true

            // If seed or candidate title has meaningful length (>= 3 chars), prevent overlap
            if (cleanSeed.length >= 3 && cleanCandidate.contains(cleanSeed)) return true
            if (cleanCandidate.length >= 3 && cleanSeed.contains(cleanCandidate)) return true

            return false
        }

        fun isArtistTitleInversion(candidateTitle: String, candidateArtist: String, targetArtist: String): Boolean {
            if (targetArtist.isBlank()) return false
            val t = targetArtist.lowercase().trim()
            val cTitle = candidateTitle.lowercase().trim()
            val cArtist = candidateArtist.lowercase().trim()
            // If the candidate's TITLE contains the target artist (e.g. title is "Lost Stories"),
            // but the candidate's ARTIST does NOT contain the target artist (e.g. artist is "Alas Conor"):
            // That's an inverted match! It's someone else's song named after the artist.
            if (cTitle.contains(t) && !cArtist.contains(t)) {
                return true
            }
            return false
        }
    }

    open suspend fun buildCandidatePools(
        seedSong: SieloTrack,
        today: String = LocalDate.now(ZoneId.systemDefault()).toString()
    ): CandidatePools = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val seedArtist = extractPrimaryArtist(seedSong.artist)

        coroutineScope {
            val poolADeferred = async { buildPoolA(seedSong, seedArtist, today, now) }
            val poolBDeferred = async { buildPoolB(seedSong, seedArtist, today) }
            val poolCDeferred = async { buildPoolC(seedSong, today, now) }

            CandidatePools(
                poolA = poolADeferred.await(),
                poolB = poolBDeferred.await(),
                poolC = poolCDeferred.await()
            )
        }
    }

    /**
     * Pool A — "Familiar" (~60% of final queue):
     * - Top-played songs by the seed artist AND user's overall top artists
     * - Weighted by decayed score: playCount * exp(-daysAgo / 30.0)
     * - Exclude remixes, edits, and ineligible songs today
     */
    suspend fun buildPoolA(
        seedSong: SieloTrack,
        seedArtist: String,
        today: String,
        now: Long = System.currentTimeMillis()
    ): List<SieloTrack> {
        val seedLanguage = SongClassifier.detectLanguage(seedSong)
        val seedVibe = SongClassifier.detectVibe(seedSong)

        val historyTracks = try {
            val topArtists = listeningHistoryDao.getTopArtistsSnapshot(sinceMs = 0L, limit = TOP_ARTISTS_LIMIT)
            val artistList = (listOf(seedArtist) + topArtists.map { it.artistName })
                .filter { it.isNotBlank() }
                .distinctBy { it.trim().lowercase() }

            if (artistList.isEmpty()) {
                emptyList()
            } else {
                val rawSongs = listeningHistoryDao.getSongsByArtists(artistList)
                    .filter { it.songId != seedSong.id }
                    .filter { isCleanStudioTrack(it.songTitle, it.artistName) }
                    .filterNot { isTitleTooSimilar(it.songTitle, seedSong.title) }
                    .filter {
                        val trackObj = SieloTrack(it.songId, it.songTitle, it.artistName)
                        SongClassifier.detectLanguage(trackObj) == seedLanguage
                    }

                val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(rawSongs.map { it.songId }, today).toSet()

                rawSongs
                    .filter { it.songId !in ineligibleIds }
                    .map { stat ->
                        val ageMs = (now - stat.lastPlayedMs).coerceAtLeast(0L)
                        val daysAgo = ageMs.toDouble() / (24.0 * 60 * 60 * 1000.0)
                        val recencyFactor = exp(-daysAgo / RECENCY_DECAY_DAYS)
                        val decayedScore = stat.playCount * recencyFactor
                        stat to decayedScore
                    }
                    .sortedByDescending { it.second }
                    .map { (stat, _) ->
                        SieloTrack(
                            id = stat.songId,
                            title = stat.songTitle,
                            artist = stat.artistName,
                            album = stat.albumName,
                            thumbnailUrl = stat.thumbnailUrl
                        )
                    }
                    .distinctBy { it.id }
            }
        } catch (e: Exception) {
            android.util.Log.e("CandidatePoolBuilder", "Error reading history for Pool A: ${e.message}", e)
            emptyList()
        }

        if (historyTracks.size >= 8) {
            return historyTracks
        }

        // Cold-start / low history fallback: resolve seed artist's top tracks adhering strictly to language and vibe
        val networkTracks = try {
            val query = when (seedLanguage) {
                SongLanguage.HINDI -> when (seedVibe) {
                    SongVibe.ROMANTIC -> if (seedArtist.isNotBlank()) "$seedArtist top bollywood romantic songs" else "Best Bollywood Romantic Songs"
                    SongVibe.SMOOTH_CALM -> if (seedArtist.isNotBlank()) "$seedArtist acoustic soothing songs" else "Hindi Acoustic Soothing Indie Songs"
                    SongVibe.LOFI -> if (seedArtist.isNotBlank()) "$seedArtist bollywood lofi chill" else "Bollywood Lofi Chill Songs"
                    SongVibe.RAP_HIPHOP -> if (seedArtist.isNotBlank()) "$seedArtist desi hip hop best songs" else "Desi Hip Hop Hits"
                    SongVibe.ENERGETIC_PARTY -> if (seedArtist.isNotBlank()) "$seedArtist party dance hits" else "Bollywood Party Dance Hits"
                    else -> if (seedArtist.isNotBlank()) "$seedArtist official songs" else "${seedSong.title} official"
                }
                SongLanguage.PUNJABI -> if (seedArtist.isNotBlank()) "$seedArtist top punjabi hits" else "Top Punjabi Hits"
                SongLanguage.SOUTH_INDIAN -> if (seedArtist.isNotBlank()) "$seedArtist top hits" else "Top South Indian Hits"
                SongLanguage.ENGLISH -> when (seedVibe) {
                    SongVibe.ROMANTIC -> if (seedArtist.isNotBlank()) "$seedArtist love songs" else "Top Romantic English Hits"
                    SongVibe.SMOOTH_CALM -> if (seedArtist.isNotBlank()) "$seedArtist acoustic calm indie" else "Smooth Soothing Calm Indie Songs"
                    SongVibe.LOFI -> if (seedArtist.isNotBlank()) "$seedArtist lofi chill beats" else "Chill Lofi Study Beats"
                    SongVibe.RAP_HIPHOP -> if (seedArtist.isNotBlank()) "$seedArtist top rap hits" else "Top Hip Hop Rap Bangers"
                    SongVibe.ENERGETIC_PARTY -> if (seedArtist.isNotBlank()) "$seedArtist edm dance hits" else "Top EDM Festival Hits"
                    else -> if (seedArtist.isNotBlank()) "$seedArtist greatest hits" else "${seedSong.title} official"
                }
                else -> if (seedArtist.isNotBlank()) "$seedArtist official songs" else "${seedSong.title} official"
            }
            innerTubeClient.search(query)
                .filter { it.id != seedSong.id }
                .filter { isCleanStudioTrack(it.title, it.artist) }
                .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                .filterNot { isArtistTitleInversion(it.title, it.artist, seedArtist) }
                .filter { SongClassifier.detectLanguage(it) == seedLanguage }
                .take(10)
        } catch (_: Exception) {
            emptyList()
        }

        val combined = (historyTracks + networkTracks + CuratedArtistClusters.defaultFallbackTracks(seedSong))
            .filter { it.id != seedSong.id }
            .filter { isCleanStudioTrack(it.title, it.artist) }
            .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
            .distinctBy { it.id }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

        val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(combined.map { it.id }, today).toSet()
        val eligible = combined.filter { it.id !in ineligibleIds }
        return if (eligible.isNotEmpty()) eligible else combined
    }

    /**
     * Pool B — "Discovery" (~25% of final queue):
     * - Similar artists tailored to the same language & vibe
     * - Concurrently fetch top tracks for top similar artists
     * - Exclude remixes, edits, and ineligible songs today
     */
    suspend fun buildPoolB(
        seedSong: SieloTrack,
        seedArtist: String,
        today: String
    ): List<SieloTrack> = coroutineScope {
        try {
            val seedLanguage = SongClassifier.detectLanguage(seedSong)
            val seedVibe = SongClassifier.detectVibe(seedSong)

            // 1. Try SimilarArtistsRepository with a short timeout
            var similarArtistNames = try {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val similarResult = similarArtistsRepository.getSimilarArtists(seedArtist, limit = SIMILAR_ARTISTS_LIMIT)
                    similarResult.getOrNull()?.map { it.name }
                        ?.filter { SongClassifier.isArtistCompatible(it, seedLanguage) }
                        .orEmpty()
                }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            // 2. Fallback to InnerTube similar artist names
            if (similarArtistNames.isEmpty()) {
                similarArtistNames = try {
                    kotlinx.coroutines.withTimeoutOrNull(2500L) {
                        innerTubeClient.getSimilarArtistNames(seedArtist)
                            .filter { SongClassifier.isArtistCompatible(it, seedLanguage) }
                    }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // 3. Fallback to curated in-memory artist clusters with language & vibe awareness
            if (similarArtistNames.isEmpty()) {
                similarArtistNames = CuratedArtistClusters.getSimilarArtists(seedArtist, seedSong)
            }

            val targetArtists = similarArtistNames
                .filterNot { it.equals(seedArtist, ignoreCase = true) }
                .take(4)

            if (targetArtists.isEmpty()) {
                return@coroutineScope CuratedArtistClusters.defaultFallbackTracks(seedSong).take(4)
            }

            // Fetch top tracks for target similar artists concurrently
            val tracksDeferred = targetArtists.map { artistName ->
                async {
                    try {
                        val q = when (seedVibe) {
                            SongVibe.SMOOTH_CALM -> "$artistName acoustic songs"
                            SongVibe.ROMANTIC -> "$artistName romantic songs"
                            SongVibe.RAP_HIPHOP -> "$artistName rap songs"
                            SongVibe.LOFI -> "$artistName chill lofi"
                            else -> "$artistName top songs"
                        }
                        innerTubeClient.search(q)
                            .filter { it.id != seedSong.id }
                            .filter { isCleanStudioTrack(it.title, it.artist) }
                            .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                            .filterNot { isArtistTitleInversion(it.title, it.artist, artistName) }
                            .filter { SongClassifier.detectLanguage(it) == seedLanguage }
                            .take(3)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val resolvedTracks = tracksDeferred.awaitAll().flatten()
                .filter { it.id != seedSong.id }
                .filter { isCleanStudioTrack(it.title, it.artist) }
                .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                .distinctBy { it.id }
                .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

            val poolWithFallback = if (resolvedTracks.size < 3) {
                (resolvedTracks + CuratedArtistClusters.defaultFallbackTracks(seedSong))
                    .distinctBy { it.id }
            } else {
                resolvedTracks
            }

            val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(poolWithFallback.map { it.id }, today).toSet()
            val eligible = poolWithFallback.filter { it.id !in ineligibleIds }
            if (eligible.isNotEmpty()) eligible else poolWithFallback
        } catch (e: Exception) {
            android.util.Log.e("CandidatePoolBuilder", "Error building Pool B: ${e.message}", e)
            CuratedArtistClusters.defaultFallbackTracks(seedSong).take(4)
        }
    }

    /**
     * Pool C — "Moderate rotation" (~15% of final queue):
     * - Songs user played 2-5 times in the last ~60 days
     * - Fallback to related artist tracks if listening history is insufficient
     * - Exclude remixes, edits, and ineligible songs today
     */
    suspend fun buildPoolC(
        seedSong: SieloTrack,
        today: String,
        now: Long = System.currentTimeMillis()
    ): List<SieloTrack> {
        val seedLanguage = SongClassifier.detectLanguage(seedSong)
        val seedArtist = extractPrimaryArtist(seedSong.artist)
        val moderateSongs = try {
            val sinceMs = now - (MODERATE_ROTATION_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L)
            val moderateStats = listeningHistoryDao.getSongsByPlayCountRange(sinceMs = sinceMs, minPlays = 2, maxPlays = 5)
                .filter { it.songId != seedSong.id }
                .filter { isCleanStudioTrack(it.songTitle, it.artistName) }
                .filterNot { isTitleTooSimilar(it.songTitle, seedSong.title) }
                .filter {
                    val trackObj = SieloTrack(it.songId, it.songTitle, it.artistName)
                    SongClassifier.detectLanguage(trackObj) == seedLanguage
                }

            if (moderateStats.isEmpty()) {
                emptyList()
            } else {
                val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(moderateStats.map { it.songId }, today).toSet()

                moderateStats
                    .filter { it.songId !in ineligibleIds }
                    .map { stat ->
                        SieloTrack(
                            id = stat.songId,
                            title = stat.songTitle,
                            artist = stat.artistName,
                            album = stat.albumName,
                            thumbnailUrl = stat.thumbnailUrl
                        )
                    }
                    .distinctBy { it.id }
            }
        } catch (e: Exception) {
            android.util.Log.e("CandidatePoolBuilder", "Error building Pool C: ${e.message}", e)
            emptyList()
        }

        if (moderateSongs.size >= 4) {
            return moderateSongs
        }

        // Clean fallback: query seed artist top songs filtered to matching language
        val fallbackSongs = try {
            val query = if (seedArtist.isNotBlank()) "$seedArtist top songs" else "${seedSong.title} official"
            innerTubeClient.search(query)
                .filter { it.id != seedSong.id }
                .filter { isCleanStudioTrack(it.title, it.artist) }
                .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                .filterNot { isArtistTitleInversion(it.title, it.artist, seedArtist) }
                .filter { SongClassifier.detectLanguage(it) == seedLanguage }
                .take(6)
        } catch (_: Exception) {
            emptyList()
        }

        val combined = (moderateSongs + fallbackSongs + CuratedArtistClusters.defaultFallbackTracks(seedSong).take(4))
            .filter { it.id != seedSong.id }
            .filter { isCleanStudioTrack(it.title, it.artist) }
            .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
            .distinctBy { it.id }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

        val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(combined.map { it.id }, today).toSet()
        val eligible = combined.filter { it.id !in ineligibleIds }
        return if (eligible.isNotEmpty()) eligible else combined
    }

    private fun extractPrimaryArtist(rawArtist: String): String {
        if (rawArtist.isBlank()) return ""
        val cleaned = rawArtist.split(Regex("(?i)\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|/|;|\\bx\\b|\\bwith\\b)\\s*")).firstOrNull()?.trim()
        return if (!cleaned.isNullOrBlank()) cleaned else rawArtist.trim()
    }
}
