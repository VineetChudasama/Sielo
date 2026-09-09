package com.sielo.music.core.lyrics.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

@Serializable
data class SieloLyrics(
    val id: String = "",
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val lines: List<LyricLine> = emptyList()
)
