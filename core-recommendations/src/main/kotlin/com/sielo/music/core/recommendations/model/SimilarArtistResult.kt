package com.sielo.music.core.recommendations.model

import kotlinx.serialization.Serializable

@Serializable
data class SimilarArtistResult(
    val id: String,
    val name: String,
    val country: String? = null,
    val imageUrl: String? = null,
    val matchScore: Double = 0.0,
    val lastfmScore: Double = 0.0,
    val tagOverlapScore: Double = 0.0,
    val originBonus: Double = 0.0,
    val sharedTags: List<String> = emptyList()
)
