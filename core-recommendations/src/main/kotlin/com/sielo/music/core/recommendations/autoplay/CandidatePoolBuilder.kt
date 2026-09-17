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

        // Cold-start / low history fallback: resolve seed artist's top tracks
        val networkTracks = try {
            val query = if (seedArtist.isNotBlank()) "$seedArtist official songs" else "${seedSong.title} official"
            innerTubeClient.search(query)
                .filter { it.id != seedSong.id }
                .filter { isCleanStudioTrack(it.title, it.artist) }
                .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                .take(10)
        } catch (_: Exception) {
            emptyList()
        }

        val combined = (historyTracks + networkTracks + CuratedArtistClusters.defaultFallbackTracks())
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
     * - Similar artists from SimilarArtistsRepository -> InnerTube similar names -> Curated clusters
     * - Concurrently fetch top tracks for top similar artists
     * - Exclude remixes, edits, and ineligible songs today
     */
    suspend fun buildPoolB(
        seedSong: SieloTrack,
        seedArtist: String,
        today: String
    ): List<SieloTrack> = coroutineScope {
        try {
            // 1. Try SimilarArtistsRepository with a short timeout
            var similarArtistNames = try {
                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                    val similarResult = similarArtistsRepository.getSimilarArtists(seedArtist, limit = SIMILAR_ARTISTS_LIMIT)
                    similarResult.getOrNull()?.map { it.name }.orEmpty()
                }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            // 2. Fallback to InnerTube similar artist names
            if (similarArtistNames.isEmpty()) {
                similarArtistNames = try {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        innerTubeClient.getSimilarArtistNames(seedArtist)
                    }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // 3. Fallback to curated in-memory artist clusters
            if (similarArtistNames.isEmpty()) {
                similarArtistNames = CuratedArtistClusters.getSimilarArtists(seedArtist)
            }

            val targetArtists = similarArtistNames
                .filterNot { it.equals(seedArtist, ignoreCase = true) }
                .take(4)

            if (targetArtists.isEmpty()) {
                return@coroutineScope emptyList()
            }

            // Fetch top tracks for target similar artists concurrently
            val tracksDeferred = targetArtists.map { artistName ->
                async {
                    try {
                        innerTubeClient.search("$artistName top songs")
                            .filter { it.id != seedSong.id }
                            .filter { isCleanStudioTrack(it.title, it.artist) }
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

            val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(resolvedTracks.map { it.id }, today).toSet()
            val eligible = resolvedTracks.filter { it.id !in ineligibleIds }
            if (eligible.isNotEmpty()) eligible else resolvedTracks
        } catch (e: Exception) {
            android.util.Log.e("CandidatePoolBuilder", "Error building Pool B: ${e.message}", e)
            emptyList()
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
        val seedArtist = extractPrimaryArtist(seedSong.artist)
        val moderateSongs = try {
            val sinceMs = now - (MODERATE_ROTATION_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L)
            val moderateStats = listeningHistoryDao.getSongsByPlayCountRange(sinceMs = sinceMs, minPlays = 2, maxPlays = 5)
                .filter { it.songId != seedSong.id }
                .filter { isCleanStudioTrack(it.songTitle, it.artistName) }
                .filterNot { isTitleTooSimilar(it.songTitle, seedSong.title) }

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

        // Clean fallback: query seed artist top songs or similar artists, never query "radio"
        val fallbackSongs = try {
            val query = if (seedArtist.isNotBlank()) "$seedArtist greatest hits" else "${seedSong.title} original"
            innerTubeClient.search(query)
                .filter { it.id != seedSong.id }
                .filter { isCleanStudioTrack(it.title, it.artist) }
                .filterNot { isTitleTooSimilar(it.title, seedSong.title) }
                .take(6)
        } catch (_: Exception) {
            emptyList()
        }

        val combined = (moderateSongs + fallbackSongs)
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
        val cleaned = rawArtist.split(Regex("(?i)\\s*(?:,|&|feat\\.?|ft\\.?|/|;|x)\\s*")).firstOrNull()?.trim()
        return if (!cleaned.isNullOrBlank()) cleaned else rawArtist.trim()
    }
}
