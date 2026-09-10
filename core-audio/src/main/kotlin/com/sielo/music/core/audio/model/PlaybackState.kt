package com.sielo.music.core.audio.model

import com.sielo.music.core.network.models.SieloTrack

data class PlaybackState(
    val currentTrack: SieloTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isMuted: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<SieloTrack> = emptyList(),
    val queueIndex: Int = 0
)
