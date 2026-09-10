package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.lyrics.lrclib.LrcLibClient
import com.sielo.music.core.lyrics.model.SieloLyrics
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    private var currentLoadedTrackId: String? = null
    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    if (currentLoadedTrackId != track.id) {
                        currentLoadedTrackId = track.id
                        fetchLyrics(track.id, track.title, track.artist, track.durationSeconds)
                    }
                } else {
                    currentLoadedTrackId = null
                    _lyrics.value = null
                    _isLyricsLoading.value = false
                }
            }
        }
    }

    private fun fetchLyrics(trackId: String, track: String, artist: String, durationSec: Long) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLyricsLoading.value = true
            val result = lrcLibClient.getLyrics(track, artist, durationSec)
            if (currentLoadedTrackId == trackId) {
                _lyrics.value = result
                _isLyricsLoading.value = false
            }
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun toggleMute() = playerManager.toggleMute()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun skipNext() = playerManager.skipNext()
    fun skipPrevious() = playerManager.skipPrevious()
    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) = playerManager.playTrack(track, queue)
}
