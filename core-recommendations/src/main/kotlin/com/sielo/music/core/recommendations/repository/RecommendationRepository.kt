package com.sielo.music.core.recommendations.repository

import android.util.Log
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.network.recommendations.RecommendationApiClient
import com.sielo.music.core.network.recommendations.RecommendationRequestDto
import com.sielo.music.core.recommendations.autoplay.CandidatePoolBuilder
import com.sielo.music.core.recommendations.autoplay.CandidatePools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepository @Inject constructor(
    private val apiClient: RecommendationApiClient,
    private val candidatePoolBuilder: CandidatePoolBuilder,
    private val innerTubeClient: InnerTubeClient
) {
    companion object {
        private const val TAG = "RecommendationRepository"
    }

    var currentUserIdProvider: (() -> String)? = null

    /**
     * Attempts to query the hybrid recommendation backend.
     * If the backend succeeds, returns collaborative & hybrid candidates.
     * If offline or cold start, falls back smoothly to local candidate pools.
     */
    suspend fun getHybridCandidates(
        seedSong: SieloTrack,
        today: String,
        excludedIds: Set<String>,
        batchSize: Int = 20
    ): CandidatePools = withContext(Dispatchers.IO) {
        val userId = currentUserIdProvider?.invoke()

        val reqDto = RecommendationRequestDto(
            userId = userId,
            seedTrackId = seedSong.id,
            seedArtist = seedSong.artist,
            seedTitle = seedSong.title,
            excludedTrackIds = excludedIds.toList(),
            limit = batchSize,
            diversityFactor = 0.35f
        )

        val backendRes = apiClient.getRecommendations(reqDto)

        if (backendRes != null && backendRes.recommendations.isNotEmpty()) {
            Log.d(TAG, "Backend returned ${backendRes.recommendations.size} recommendations (model: ${backendRes.modelVersion}, isFallback: ${backendRes.isFallback})")

            val tracks = backendRes.recommendations.mapNotNull { item ->
                if (item.trackId.isBlank() || item.trackId in excludedIds) null
                else {
                    SieloTrack(
                        id = item.trackId,
                        title = item.title ?: "Unknown Title",
                        artist = item.artist ?: "Unknown Artist",
                        album = item.album,
                        thumbnailUrl = item.thumbnailUrl
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                // Split backend recommendations into familiarity (Pool A) and discovery (Pool B)
                val familiar = tracks.take((tracks.size * 0.6).toInt())
                val discovery = tracks.drop(familiar.size)
                
                // Backfill Pool C with moderate rotation local tracks to ensure balance
                val localPools = candidatePoolBuilder.buildCandidatePools(seedSong, today)
                return@withContext CandidatePools(
                    poolA = (familiar + localPools.poolA).distinctBy { it.id },
                    poolB = (discovery + localPools.poolB).distinctBy { it.id },
                    poolC = localPools.poolC
                )
            }
        }

        // Fallback to deterministic local candidate pools
        Log.d(TAG, "Using local CandidatePoolBuilder fallback engine.")
        candidatePoolBuilder.buildCandidatePools(seedSong, today)
    }
}
