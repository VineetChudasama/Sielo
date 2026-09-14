package com.sielo.music.core.recommendations.autoplay

import com.sielo.music.core.database.dao.RecommendationHistoryDao
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.recommendations.repository.SimilarArtistsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AutoplayQueueEngine @Inject constructor(
    private val candidatePoolBuilder: CandidatePoolBuilder,
    private val recommendationHistoryDao: RecommendationHistoryDao,
    private val similarArtistsRepository: SimilarArtistsRepository
) {

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val REENTRY_SIMILARITY_THRESHOLD = 0.4
        const val ARTIST_SEPARATION_DISTANCE = 3
    }

    /**
     * Generates a balanced batch of recommended songs for endless autoplay.
     *
     * @param seedSong The currently playing or anchor track.
     * @param excludedSongIds Songs that were actually played (hard exclusion - never resurfaced).
     * @param reentryCandidates Unplayed songs from an abandoned session eligible to be folded back if thematically relevant.
     * @param batchSize Desired batch size (defaults to 20).
     */
    suspend fun generateBatch(
        seedSong: SieloTrack,
        excludedSongIds: Set<String> = emptySet(),
        reentryCandidates: List<SieloTrack> = emptyList(),
        batchSize: Int = DEFAULT_BATCH_SIZE
    ): List<SieloTrack> = withContext(Dispatchers.IO) {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()

        // 1. Build all 3 candidate pools via CandidatePoolBuilder using the new seed
        val candidatePools = candidatePoolBuilder.buildCandidatePools(seedSong, today)

        // 2. Remove any song in excludedSongIds from all pools (hard exclusion)
        val hardExcluded = excludedSongIds + seedSong.id
        val poolA = candidatePools.poolA.filter { it.id !in hardExcluded }.distinctBy { it.id }
        var poolB = candidatePools.poolB.filter { it.id !in hardExcluded }.distinctBy { it.id }
        val poolC = candidatePools.poolC.filter { it.id !in hardExcluded }.distinctBy { it.id }

        // 3. Score reentryCandidates against the new seed's tag profile
        val qualifyingReentry = if (reentryCandidates.isNotEmpty()) {
            filterAndScoreReentryCandidates(seedSong, reentryCandidates, hardExcluded, today)
        } else {
            emptyList()
        }

        // Fold qualifying reentry candidates into Pool B
        if (qualifyingReentry.isNotEmpty()) {
            poolB = (qualifyingReentry + poolB).distinctBy { it.id }
        }

        // 4. Allocate slots: 60% Pool A, 25% Pool B, 15% Pool C (rounding / backfilling as needed)
        val selectedCandidates = allocatePoolSlots(poolA, poolB, poolC, batchSize)
            .filter { CandidatePoolBuilder.isCleanStudioTrack(it.title, it.artist) }

        // 5. Interleave results so consecutive songs aren't from the same artist (within 3 positions)
        val interleavedQueue = interleaveWithArtistSpacing(selectedCandidates, ARTIST_SEPARATION_DISTANCE)

        // 6. Mark all selected songs via RecommendationHistoryEntity.markRecommended() with today's local date
        val selectedIds = interleavedQueue.map { it.id }
        if (selectedIds.isNotEmpty()) {
            recommendationHistoryDao.markRecommended(selectedIds, today)
        }

        // 7. Return final queue
        interleavedQueue
    }

    /**
     * Scores unplayed reentry candidates against the new seed's artist tags.
     * Folds in any candidate with tag overlap > 0.4.
     */
    private suspend fun filterAndScoreReentryCandidates(
        seedSong: SieloTrack,
        reentryCandidates: List<SieloTrack>,
        hardExcluded: Set<String>,
        today: String
    ): List<SieloTrack> = coroutineScope {
        try {
            val eligibleReentry = reentryCandidates.filter { it.id !in hardExcluded }.distinctBy { it.id }
            if (eligibleReentry.isEmpty()) return@coroutineScope emptyList()

            val seedArtist = extractPrimaryArtist(seedSong.artist)
            val seedTags = similarArtistsRepository.getArtistTags(seedArtist)
            if (seedTags.isEmpty()) return@coroutineScope emptyList()

            val scoredDeferred = eligibleReentry.map { candidate ->
                async {
                    val candidateArtist = extractPrimaryArtist(candidate.artist)
                    val candidateTags = similarArtistsRepository.getArtistTags(candidateArtist)
                    val overlap = similarArtistsRepository.calculateTagOverlap(seedTags, candidateTags)
                    if (overlap > REENTRY_SIMILARITY_THRESHOLD) candidate else null
                }
            }

            val qualifying = scoredDeferred.awaitAll().filterNotNull()
            if (qualifying.isEmpty()) return@coroutineScope emptyList()

            val ineligibleIds = recommendationHistoryDao.getIneligibleSongIds(qualifying.map { it.id }, today).toSet()
            qualifying.filter { it.id !in ineligibleIds }
        } catch (e: Exception) {
            android.util.Log.e("AutoplayQueueEngine", "Error scoring reentry candidates: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Allocates candidates according to 60% Pool A, 25% Pool B, 15% Pool C.
     * Pro-rates / backfills if any pool has insufficient tracks.
     */
    private fun allocatePoolSlots(
        poolA: List<SieloTrack>,
        poolB: List<SieloTrack>,
        poolC: List<SieloTrack>,
        batchSize: Int
    ): List<SieloTrack> {
        val targetA = (batchSize * 0.60).roundToInt()
        val targetB = (batchSize * 0.25).roundToInt()
        val targetC = (batchSize - targetA - targetB).coerceAtLeast(0)

        val selectedA = poolA.take(targetA).toMutableList()
        val selectedB = poolB.take(targetB).toMutableList()
        val selectedC = poolC.take(targetC).toMutableList()

        val selectedSet = (selectedA + selectedB + selectedC).map { it.id }.toMutableSet()
        val totalSelected = selectedA.size + selectedB.size + selectedC.size

        if (totalSelected < batchSize) {
            val deficit = batchSize - totalSelected
            val leftoverA = poolA.filter { it.id !in selectedSet }
            val leftoverB = poolB.filter { it.id !in selectedSet }
            val leftoverC = poolC.filter { it.id !in selectedSet }

            val remainingPool = (leftoverA + leftoverB + leftoverC).distinctBy { it.id }
            val backfill = remainingPool.take(deficit)
            val result = (selectedA + selectedB + selectedC + backfill).toMutableList()
            if (result.size < batchSize) {
                val emergencyFallback = CuratedArtistClusters.defaultFallbackTracks().filter { it.id !in result.map { r -> r.id } }
                result.addAll(emergencyFallback.take(batchSize - result.size))
            }
            return result
        }

        return selectedA + selectedB + selectedC
    }

    /**
     * Interleaves tracks so no two songs by the same primary artist are placed within
     * `minDistance` positions of each other (e.g. minDistance = 3 means 2 other songs between them).
     */
    fun interleaveWithArtistSpacing(
        tracks: List<SieloTrack>,
        minDistance: Int = ARTIST_SEPARATION_DISTANCE
    ): List<SieloTrack> {
        if (tracks.size <= 2) return tracks

        val remaining = tracks.distinctBy { it.id }.toMutableList()
        val result = mutableListOf<SieloTrack>()

        while (remaining.isNotEmpty()) {
            val recentArtists = result.takeLast(minDistance - 1).map { extractPrimaryArtist(it.artist).lowercase() }

            // Find first track whose artist is not in recent artists
            val nextIndex = remaining.indexOfFirst { track ->
                val artist = extractPrimaryArtist(track.artist).lowercase()
                artist !in recentArtists
            }

            if (nextIndex != -1) {
                result.add(remaining.removeAt(nextIndex))
            } else {
                // If strict spacing cannot be satisfied, pick track that least recently appeared
                val fallbackIndex = remaining.indices.maxByOrNull { i ->
                    val trackArtist = extractPrimaryArtist(remaining[i].artist).lowercase()
                    val lastSeen = result.indexOfLast { extractPrimaryArtist(it.artist).lowercase() == trackArtist }
                    if (lastSeen == -1) Int.MAX_VALUE else (result.size - lastSeen)
                } ?: 0
                result.add(remaining.removeAt(fallbackIndex))
            }
        }

        return result
    }

    private fun extractPrimaryArtist(rawArtist: String): String {
        if (rawArtist.isBlank()) return "Unknown"
        val cleaned = rawArtist.split(Regex("(?i)\\s*(?:,|&|feat\\.?|ft\\.?|/|;|x)\\s*")).firstOrNull()?.trim()
        return if (!cleaned.isNullOrBlank()) cleaned else rawArtist.trim()
    }
}
