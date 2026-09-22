package com.sielo.music.ui.screens.stats.audiofeatures

import com.sielo.music.core.database.entity.ListeningEventEntity

/**
 * Aggregates track audio features into user-level Energy and Mood scores.
 * Uses play-weighted averaging and strictly calculates coverage.
 */
object VibeAggregator {

    fun aggregate(
        events: List<ListeningEventEntity>,
        featureLookup: (trackId: String) -> TrackAudioFeatures?
    ): AggregatedVibeFeatures {
        if (events.isEmpty()) {
            return AggregatedVibeFeatures(
                userEnergy = null,
                userMood = null,
                analyzedTracksCount = 0,
                totalListenedTracksCount = 0,
                coveragePercent = 0,
                coverageLevel = FeatureCoverageLevel.NONE,
                source = FeatureSource.UNAVAILABLE
            )
        }

        // Group plays by songId
        val songPlayCounts = events.groupBy { it.songId }.mapValues { it.value.size }
        val totalUniqueTracks = songPlayCounts.size

        var totalEnergyWeight = 0f
        var weightedEnergySum = 0f

        var totalMoodWeight = 0f
        var weightedMoodSum = 0f

        var analyzedTracksCount = 0
        var dominantSource = FeatureSource.UNAVAILABLE

        songPlayCounts.forEach { (songId, playCount) ->
            val feat = featureLookup(songId)
            if (feat != null && feat.isAvailable) {
                analyzedTracksCount++
                dominantSource = feat.source

                feat.energy?.let { energyVal ->
                    val weight = playCount.toFloat()
                    weightedEnergySum += (energyVal.coerceIn(0f, 1f) * weight)
                    totalEnergyWeight += weight
                }

                val moodVal = feat.mood ?: feat.valence
                moodVal?.let {
                    val weight = playCount.toFloat()
                    weightedMoodSum += (it.coerceIn(0f, 1f) * weight)
                    totalMoodWeight += weight
                }
            }
        }

        val finalEnergy = if (totalEnergyWeight > 0f) {
            ((weightedEnergySum / totalEnergyWeight) * 100f).coerceIn(0f, 100f)
        } else null

        val finalMood = if (totalMoodWeight > 0f) {
            ((weightedMoodSum / totalMoodWeight) * 100f).coerceIn(0f, 100f)
        } else null

        val coveragePercent = if (totalUniqueTracks > 0) {
            ((analyzedTracksCount.toFloat() / totalUniqueTracks.toFloat()) * 100f).toInt()
        } else 0

        val coverageLevel = when {
            analyzedTracksCount == 0 -> FeatureCoverageLevel.NONE
            coveragePercent >= 80 -> FeatureCoverageLevel.HIGH
            coveragePercent >= 50 -> FeatureCoverageLevel.MEDIUM
            else -> FeatureCoverageLevel.LIMITED
        }

        return AggregatedVibeFeatures(
            userEnergy = finalEnergy,
            userMood = finalMood,
            analyzedTracksCount = analyzedTracksCount,
            totalListenedTracksCount = totalUniqueTracks,
            coveragePercent = coveragePercent,
            coverageLevel = coverageLevel,
            source = dominantSource
        )
    }
}
