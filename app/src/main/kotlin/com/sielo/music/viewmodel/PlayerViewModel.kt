package com.sielo.music.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.lyrics.lrclib.LrcLibClient
import com.sielo.music.core.lyrics.model.SieloLyrics
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val lrcLibClient: LrcLibClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    private val _lyrics = MutableStateFlow<SieloLyrics?>(null)
    val lyrics: StateFlow<SieloLyrics?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _lyricOffsetMs = MutableStateFlow(0L)
    val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs.asStateFlow()

    private val _isLyricOffsetSaved = MutableStateFlow(true)
    val isLyricOffsetSaved: StateFlow<Boolean> = _isLyricOffsetSaved.asStateFlow()

    private val lyricPrefs by lazy {
        context.getSharedPreferences("sielo_user_lyric_offsets", Context.MODE_PRIVATE)
    }

    private var currentLoadedTrackId: String? = null
    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    val durationSec = if (track.durationSeconds > 0) track.durationSeconds else (state.durationMs / 1000)
                    if (currentLoadedTrackId != track.id) {
                        currentLoadedTrackId = track.id
                        val savedOffset = getSavedLyricOffset(track.id, track.title, track.artist)
                        _lyricOffsetMs.value = savedOffset
                        _isLyricOffsetSaved.value = true
                        fetchLyrics(track.id, track.title, track.artist, durationSec)
                    } else if (durationSec > 0 && (_lyrics.value == null || _lyrics.value?.lines.isNullOrEmpty()) && !_isLyricsLoading.value) {
                        fetchLyrics(track.id, track.title, track.artist, durationSec)
                    }
                } else {
                    currentLoadedTrackId = null
                    _lyrics.value = null
                    _lyricOffsetMs.value = 0L
                    _isLyricOffsetSaved.value = true
                    _isLyricsLoading.value = false
                }
            }
        }
    }

    private fun getLyricKey(trackId: String): String = "offset_id_$trackId"
    private fun getLyricFallbackKey(title: String, artist: String): String =
        "offset_norm_${title.trim().lowercase()}_${artist.trim().lowercase()}"

    fun getSavedLyricOffset(trackId: String, title: String, artist: String): Long {
        val key = getLyricKey(trackId)
        if (lyricPrefs.contains(key)) {
            return lyricPrefs.getLong(key, 0L)
        }
        val fallbackKey = getLyricFallbackKey(title, artist)
        return lyricPrefs.getLong(fallbackKey, 0L)
    }

    fun saveCurrentLyricOffset() {
        val track = playbackState.value.currentTrack ?: return
        val offset = _lyricOffsetMs.value
        lyricPrefs.edit()
            .putLong(getLyricKey(track.id), offset)
            .putLong(getLyricFallbackKey(track.title, track.artist), offset)
            .apply()
        _isLyricOffsetSaved.value = true
    }

    fun adjustLyricOffset(deltaMs: Long) {
        _lyricOffsetMs.value += deltaMs
        val track = playbackState.value.currentTrack
        if (track != null) {
            val saved = getSavedLyricOffset(track.id, track.title, track.artist)
            _isLyricOffsetSaved.value = (_lyricOffsetMs.value == saved)
        } else {
            _isLyricOffsetSaved.value = false
        }
    }

    fun resetLyricOffset() {
        _lyricOffsetMs.value = 0L
        val track = playbackState.value.currentTrack
        if (track != null) {
            val saved = getSavedLyricOffset(track.id, track.title, track.artist)
            _isLyricOffsetSaved.value = (0L == saved)
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

    fun removeUpcomingTrack(track: SieloTrack) {
        playerManager.removeTrackFromQueue(track)
    }

    fun moveUpcomingTrack(fromUpcomingIndex: Int, toUpcomingIndex: Int) {
        playerManager.moveUpcomingTrack(fromUpcomingIndex, toUpcomingIndex)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun toggleMute() = playerManager.toggleMute()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun skipNext() = playerManager.skipNext()
    fun skipPrevious() = playerManager.skipPrevious()
    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) = playerManager.playTrack(track, queue)
    fun playAlbum(track: SieloTrack, queue: List<SieloTrack>) = playerManager.playAlbum(track, queue)
    fun playArtistRadio(track: SieloTrack, queue: List<SieloTrack>, artistName: String) = playerManager.playArtistRadio(track, queue, artistName)
    fun appendToQueue(tracks: List<SieloTrack>) = playerManager.appendToQueue(tracks)
    fun appendToQueue(track: SieloTrack) = playerManager.appendToQueue(track)
    fun playNext(track: SieloTrack) = playerManager.playNext(track)
    fun triggerAutoplayIfLow(seedTrack: SieloTrack? = null) = playerManager.triggerAutoplayIfLow(seedTrack)
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun shuffleQueue() = playerManager.shuffleQueue()
}
