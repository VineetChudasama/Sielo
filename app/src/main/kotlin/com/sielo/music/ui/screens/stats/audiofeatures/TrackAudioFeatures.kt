package com.sielo.music.ui.screens.stats.audiofeatures

import kotlinx.serialization.Serializable

enum class FeatureSource {
    REMOTE_METADATA,
    LOCAL_AUDIO_ANALYSIS,
    CACHED,
    UNAVAILABLE
}

enum class FeatureCoverageLevel {
    HIGH,       // > 80% coverage
    MEDIUM,     // 50 - 80% coverage
    LIMITED,    // < 50% coverage
    NONE        // 0% or no features available
}

@Serializable
data class TrackAudioFeatures(
    val trackId: String,
    val title: String = "",
    val artist: String = "",
    val energy: Float? = null,      // Normalized 0.0 to 1.0 (musical intensity)
    val mood: Float? = null,        // Normalized 0.0 to 1.0 (musical valence/vibe)
    val tempo: Float? = null,       // BPM if available
    val valence: Float? = null,     // Raw valence if provided
    val confidence: Float? = null,  // 0.0 to 1.0
    val source: FeatureSource = FeatureSource.UNAVAILABLE,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isAvailable: Boolean
        get() = energy != null || mood != null
}

data class AggregatedVibeFeatures(
    val userEnergy: Float?,         // Clamped 0.0 to 100.0 or null if unavailable
    val userMood: Float?,           // Clamped 0.0 to 100.0 or null if unavailable
    val analyzedTracksCount: Int,
    val totalListenedTracksCount: Int,
    val coveragePercent: Int,
    val coverageLevel: FeatureCoverageLevel,
    val source: FeatureSource
) {
    val coverageDescription: String
        get() = when {
            totalListenedTracksCount == 0 -> "No listening data yet"
            coveragePercent > 0 -> "Based on $coveragePercent% of your listening"
            else -> "Audio feature analysis unavailable"
        }
}
