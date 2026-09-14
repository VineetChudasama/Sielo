package com.sielo.music.core.recommendations.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistTag(
    val name: String,
    val count: Int = 0
)
