package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.lyrics.lrclib.LrcLibClient
import com.sielo.music.core.lyrics.model.SieloLyrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val lrcLibClient: LrcLibClient
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    private val _lyrics = MutableStateFlow<SieloLyrics?>(null)
    val lyrics: StateFlow<SieloLyrics?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                val track = state.currentTrack
                if (track != null) {
                    fetchLyrics(track.title, track.artist, track.durationSeconds)
                } else {
                    _lyrics.value = null
                }
            }
        }
    }

    private fun fetchLyrics(track: String, artist: String, durationSec: Long) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _isLyricsLoading.value = true
            _lyrics.value = lrcLibClient.getLyrics(track, artist, durationSec)
            _isLyricsLoading.value = false
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun skipNext() = playerManager.skipNext()
    fun skipPrevious() = playerManager.skipPrevious()
}
