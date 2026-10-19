package com.sielo.music.core.network.recommendations

import kotlinx.serialization.Serializable

@Serializable
data class ClientListeningEvent(
    val eventId: String,
    val userId: String,
    val trackId: String,
    val eventType: String,
    val timestampMs: Long,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackAlbum: String? = null,
    val source: String? = "autoplay",
    val sessionId: String? = null
)

@Serializable
data class BatchEventsRequestDto(
    val events: List<ClientListeningEvent>
)

@Serializable
data class BatchEventsResponseDto(
    val accepted: Int = 0,
    val duplicatesIgnored: Int = 0,
    val errors: Int = 0,
    val message: String = ""
)

@Serializable
data class RecommendationRequestDto(
    val userId: String? = null,
    val seedTrackId: String? = null,
    val seedArtist: String? = null,
    val seedTitle: String? = null,
    val excludedTrackIds: List<String> = emptyList(),
    val limit: Int = 20,
    val diversityFactor: Float = 0.3f
)

@Serializable
data class RecommendationItemDto(
    val trackId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val score: Float,
    val reason: String,
    val source: String = "hybrid"
)

@Serializable
data class RecommendationResponseDto(
    val modelVersion: String,
    val isFallback: Boolean,
    val generatedAtMs: Long,
    val recommendations: List<RecommendationItemDto> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)
