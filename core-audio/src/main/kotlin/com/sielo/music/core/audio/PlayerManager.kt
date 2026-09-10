package com.sielo.music.core.audio

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.audio.service.MusicPlaybackService
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val innerTubeClient: InnerTubeClient,
    private val listeningHistoryDao: ListeningHistoryDao
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null
    private var currentPlayingEventLogged = false

    init {
        scope.launch {
            getController()
        }
    }

    private suspend fun getController(): MediaController? = suspendCancellableCoroutine { cont ->
        val current = mediaController
        if (current != null) {
            cont.resume(current) {}
            return@suspendCancellableCoroutine
        }
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                val ctrl = future.get()
                mediaController = ctrl
                setupPlayerListeners()
                if (cont.isActive) {
                    cont.resume(ctrl) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("SieloAudio", "Failed to connect MediaController: ${e.message}", e)
                if (cont.isActive) {
                    cont.resume(null) {}
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListeners() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying, isBuffering = false) }
                if (isPlaying) {
                    startProgressTracking()
                    recordHistoryIfEligible()
                } else {
                    stopProgressTracking()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _playbackState.update { 
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }

                if (playbackState == Player.STATE_ENDED) {
                    skipNext()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("SieloAudio", "ExoPlayer Error: ${error.errorCodeName} - ${error.message}", error)
                _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
            }
        })
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack> = listOf(track)) {
        scope.launch {
            currentPlayingEventLogged = false
            _playbackState.update { 
                it.copy(
                    currentTrack = track,
                    queue = queue,
                    queueIndex = queue.indexOf(track).coerceAtLeast(0),
                    isBuffering = true
                )
            }

            // Immediately record start of history entry
            recordTrackStart(track)

            android.util.Log.d("SieloAudio", "Playing track: ${track.title} by ${track.artist} (id=${track.id})")
            val streamUrl = innerTubeClient.getStreamUrl(track.id, track.title, track.artist)
            android.util.Log.d("SieloAudio", "Resolved stream URL: $streamUrl")
            if (streamUrl != null) {
                val updatedTrack = track.copy(streamUrl = streamUrl)
                _playbackState.update { it.copy(currentTrack = updatedTrack) }

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUrl?.toUri())
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(track.id)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                try {
                    val controller = mediaController ?: getController()
                    if (controller != null) {
                        controller.run {
                            setMediaItem(mediaItem, true)
                            prepare()
                            play()
                        }
                        android.util.Log.d("SieloAudio", "MediaItem sent to ExoPlayer, play() invoked.")
                    } else {
                        android.util.Log.e("SieloAudio", "Controller is null, could not start playback.")
                        _playbackState.update { it.copy(isBuffering = false) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SieloAudio", "Error starting playback: ${e.message}", e)
                    _playbackState.update { it.copy(isBuffering = false) }
                }
            } else {
                android.util.Log.w("SieloAudio", "Failed to resolve stream URL for track: ${track.title}")
                _playbackState.update { it.copy(isBuffering = false) }
            }
        }
    }

    private fun recordTrackStart(track: SieloTrack) {
        scope.launch(Dispatchers.IO) {
            try {
                listeningHistoryDao.insertEvent(
                    ListeningEventEntity(
                        songId = track.id,
                        songTitle = track.title,
                        artistName = track.artist,
                        albumName = track.album,
                        thumbnailUrl = track.thumbnailUrl,
                        durationPlayedMs = 30000L,
                        songDurationMs = track.durationSeconds * 1000L
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun recordHistoryIfEligible() {
        val track = _playbackState.value.currentTrack ?: return
        if (!currentPlayingEventLogged) {
            currentPlayingEventLogged = true
            recordTrackStart(track)
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun toggleMute() {
        val currentlyMuted = _playbackState.value.isMuted
        val newMuted = !currentlyMuted
        mediaController?.let {
            it.volume = if (newMuted) 0f else 1f
        }
        _playbackState.update { it.copy(isMuted = newMuted) }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun skipNext() {
        val currentState = _playbackState.value
        val nextIndex = currentState.queueIndex + 1
        if (nextIndex in currentState.queue.indices) {
            playTrack(currentState.queue[nextIndex], currentState.queue)
        }
    }

    fun skipPrevious() {
        val currentState = _playbackState.value
        val prevIndex = currentState.queueIndex - 1
        if (prevIndex in currentState.queue.indices) {
            playTrack(currentState.queue[prevIndex], currentState.queue)
        }
    }

    private fun startProgressTracking() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                val current = mediaController?.currentPosition ?: 0L
                val dur = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                _playbackState.update { it.copy(currentPositionMs = current, durationMs = dur) }
                delay(50)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackerJob?.cancel()
    }
}