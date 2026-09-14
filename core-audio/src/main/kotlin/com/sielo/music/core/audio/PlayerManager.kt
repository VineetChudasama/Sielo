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
import com.sielo.music.core.recommendations.autoplay.AutoplayQueueEngine
import com.sielo.music.core.recommendations.autoplay.AutoplaySession
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val innerTubeClient: InnerTubeClient,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val autoplayQueueEngine: AutoplayQueueEngine,
    private val autoplaySession: AutoplaySession
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null
    private var currentEventId: Long? = null
    private var currentTrackAccumulatedPlayedMs: Long = 0L
    private var lastTrackingTimestamp: Long = 0L
    private var lastDbFlushTimestamp: Long = 0L
    private var autoplayJob: Job? = null
    private val autoplayMutex = Mutex()
    private var originalQueue: List<SieloTrack>? = null

    companion object {
        private const val PREFS_NAME = "sielo_player_state"
        private const val KEY_TRACK = "last_track"
        private const val KEY_QUEUE = "last_queue"
        private const val KEY_QUEUE_INDEX = "last_queue_index"
        private const val KEY_POSITION_MS = "last_position_ms"
        private const val KEY_DURATION_MS = "last_duration_ms"
        private const val KEY_SHUFFLE = "last_shuffle"
    }

    init {
        restorePlaybackState()
        scope.launch {
            getController()
            withContext(Dispatchers.IO) {
                try {
                    listeningHistoryDao.sanitizeLegacyRecords()
                } catch (_: Exception) {}
            }
        }
    }

    private fun restorePlaybackState() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val trackJson = prefs.getString(KEY_TRACK, null)
            val queueJson = prefs.getString(KEY_QUEUE, null)
            val queueIndex = prefs.getInt(KEY_QUEUE_INDEX, 0)
            val positionMs = prefs.getLong(KEY_POSITION_MS, 0L)
            val durationMs = prefs.getLong(KEY_DURATION_MS, 0L)
            val isShuffle = prefs.getBoolean(KEY_SHUFFLE, false)

            if (!trackJson.isNullOrBlank()) {
                val track = json.decodeFromString<SieloTrack>(trackJson)
                val queue = if (!queueJson.isNullOrBlank()) {
                    try {
                        json.decodeFromString<List<SieloTrack>>(queueJson)
                    } catch (_: Exception) {
                        listOf(track)
                    }
                } else {
                    listOf(track)
                }

                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        queue = queue,
                        queueIndex = queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
                        currentPositionMs = positionMs,
                        durationMs = if (durationMs > 0L) durationMs else (track.durationSeconds * 1000L),
                        isPlaying = false,
                        isBuffering = false,
                        isShuffle = isShuffle
                    )
                }

                if (queue.size - queueIndex <= 2) {
                    triggerAutoplayGeneration(track, isReseed = true)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SieloAudio", "Error restoring playback state: ${e.message}", e)
        }
    }

    private fun savePlaybackState(
        track: SieloTrack?,
        queue: List<SieloTrack>,
        queueIndex: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (track != null) {
                    putString(KEY_TRACK, json.encodeToString(track))
                    putString(KEY_QUEUE, json.encodeToString(queue))
                    putInt(KEY_QUEUE_INDEX, queueIndex)
                    putLong(KEY_POSITION_MS, positionMs)
                    putLong(KEY_DURATION_MS, durationMs)
                    putBoolean(KEY_SHUFFLE, _playbackState.value.isShuffle)
                }
                apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("SieloAudio", "Error saving playback state: ${e.message}", e)
        }
    }

    private fun savePositionOnly(positionMs: Long, durationMs: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_POSITION_MS, positionMs)
                .putLong(KEY_DURATION_MS, durationMs)
                .apply()
        } catch (_: Exception) {}
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
                } else {
                    stopProgressTracking()
                    flushCurrentListeningDuration()
                    mediaController?.let {
                        savePositionOnly(it.currentPosition, it.duration.coerceAtLeast(0L))
                    }
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
                    flushCurrentListeningDuration()
                    skipNext()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("SieloAudio", "ExoPlayer Error: ${error.errorCodeName} - ${error.message}", error)
                _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
                stopProgressTracking()
                flushCurrentListeningDuration()
            }
        })
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack> = listOf(track), seekToMs: Long = 0L) {
        scope.launch {
            // Flush any previously tracked duration
            flushCurrentListeningDuration()

            currentTrackAccumulatedPlayedMs = 0L
            lastTrackingTimestamp = System.currentTimeMillis()
            lastDbFlushTimestamp = System.currentTimeMillis()

            val originalIdx = queue.indexOf(track).coerceAtLeast(0)
            originalQueue = queue
            val activeQueue = if (_playbackState.value.isShuffle && queue.size > 1) {
                val pastTracks = queue.take(originalIdx)
                val upcomingTracks = queue.drop(originalIdx + 1).shuffled()
                pastTracks + listOf(track) + upcomingTracks
            } else {
                queue
            }
            val index = activeQueue.indexOf(track).coerceAtLeast(0)
            val expectedDurationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            _playbackState.update { 
                it.copy(
                    currentTrack = track,
                    queue = activeQueue,
                    queueIndex = index,
                    currentPositionMs = seekToMs,
                    durationMs = expectedDurationMs,
                    isBuffering = true
                )
            }

            savePlaybackState(track, activeQueue, index, seekToMs, expectedDurationMs)

            // Track autoplay session state & trigger background queue generation
            val isKnownAutoplayTrack = autoplaySession.isTrackInGeneratedQueue(track.id)
            if (isKnownAutoplayTrack) {
                autoplaySession.onTrackPlaying(track)
            }

            val shouldReseed = !isKnownAutoplayTrack && (queue.size <= 1 || autoplaySession.currentSeed.value?.id != track.id)
            if (shouldReseed) {
                triggerAutoplayGeneration(track, isReseed = true)
            } else if (queue.size - index <= 3) {
                triggerAutoplayGeneration(track, isReseed = false)
            }

            // Immediately record start of history entry with 0ms played
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
                            if (seekToMs > 0L) {
                                seekTo(seekToMs)
                            }
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

    private fun triggerAutoplayGeneration(
        seedTrack: SieloTrack,
        isReseed: Boolean,
        autoPlayFirst: Boolean = false
    ) {
        autoplayJob?.cancel()
        autoplayJob = scope.launch(Dispatchers.IO) {
            autoplayMutex.withLock {
                try {
                    val batch = if (isReseed) {
                        val reseedContext = autoplaySession.startOrReseedSession(seedTrack)
                        autoplayQueueEngine.generateBatch(
                            seedSong = reseedContext.seedSong,
                            excludedSongIds = reseedContext.excludedSongIds,
                            reentryCandidates = reseedContext.reentryCandidates
                        )
                    } else {
                        autoplayQueueEngine.generateBatch(
                            seedSong = seedTrack,
                            excludedSongIds = emptySet(),
                            reentryCandidates = emptyList()
                        )
                    }

                    if (batch.isNotEmpty()) {
                        autoplaySession.appendBatch(batch)
                        withContext(Dispatchers.Main) {
                            var trackToAutoPlay: SieloTrack? = null
                            var newQueueSnapshot: List<SieloTrack> = emptyList()
                            _playbackState.update { state ->
                                val existingIds = state.queue.map { it.id }.toSet()
                                val currentTrack = state.currentTrack ?: seedTrack
                                val currentTitle = currentTrack.title.trim()
                                val currentArtist = currentTrack.artist.trim()
                                val newTracks = batch.filter { bTrack ->
                                    bTrack.id !in existingIds &&
                                    bTrack.id != currentTrack.id &&
                                    !(bTrack.title.trim().equals(currentTitle, ignoreCase = true) &&
                                      bTrack.artist.trim().equals(currentArtist, ignoreCase = true))
                                }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                                val finalNewTracks = if (state.isShuffle) newTracks.shuffled() else newTracks
                                val updatedQueue = if (state.queue.isEmpty()) listOf(seedTrack) + finalNewTracks else state.queue + finalNewTracks
                                newQueueSnapshot = updatedQueue
                                if (autoPlayFirst && finalNewTracks.isNotEmpty()) {
                                    trackToAutoPlay = finalNewTracks.first()
                                }
                                savePlaybackState(
                                    state.currentTrack,
                                    updatedQueue,
                                    state.queueIndex,
                                    state.currentPositionMs,
                                    state.durationMs
                                )
                                state.copy(queue = updatedQueue)
                            }

                            if (trackToAutoPlay != null) {
                                playTrack(trackToAutoPlay!!, newQueueSnapshot)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SieloAudio", "Error in triggerAutoplayGeneration: ${e.message}", e)
                }
            }
        }
    }

    private fun extractPrimaryArtist(rawArtist: String): String {
        if (rawArtist.isBlank()) return "Unknown Artist"
        val cleaned = rawArtist.split(Regex("(?i)\\s*(?:,|&|feat\\.?|ft\\.?|/|;|x)\\s*")).firstOrNull()?.trim()
        return if (!cleaned.isNullOrBlank()) cleaned else rawArtist.trim()
    }

    private fun recordTrackStart(track: SieloTrack) {
        scope.launch(Dispatchers.IO) {
            try {
                val primaryArtist = extractPrimaryArtist(track.artist)
                val expectedDurationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L
                val eventId = listeningHistoryDao.insertEvent(
                    ListeningEventEntity(
                        songId = track.id,
                        songTitle = track.title,
                        artistName = primaryArtist,
                        albumName = track.album,
                        thumbnailUrl = track.thumbnailUrl,
                        durationPlayedMs = 0L,
                        songDurationMs = expectedDurationMs
                    )
                )
                currentEventId = eventId
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun flushCurrentListeningDuration() {
        val eventId = currentEventId ?: return
        val durationMs = currentTrackAccumulatedPlayedMs
        if (durationMs > 0L) {
            scope.launch(Dispatchers.IO) {
                try {
                    listeningHistoryDao.updateDurationPlayed(eventId, durationMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController
        val currentTrack = _playbackState.value.currentTrack
        if (ctrl != null && ctrl.currentMediaItem != null) {
            if (ctrl.isPlaying) {
                ctrl.pause()
                savePositionOnly(ctrl.currentPosition, ctrl.duration.coerceAtLeast(0L))
            } else {
                ctrl.play()
            }
        } else if (currentTrack != null) {
            playTrack(currentTrack, _playbackState.value.queue, seekToMs = _playbackState.value.currentPositionMs)
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
        savePositionOnly(positionMs, _playbackState.value.durationMs)
    }

    fun skipNext() {
        val currentState = _playbackState.value
        var nextIndex = currentState.queueIndex + 1
        val current = currentState.currentTrack
        while (nextIndex in currentState.queue.indices && current != null) {
            val candidate = currentState.queue[nextIndex]
            val isSame = candidate.id == current.id ||
                (candidate.title.trim().equals(current.title.trim(), ignoreCase = true) &&
                 candidate.artist.trim().equals(current.artist.trim(), ignoreCase = true))
            if (isSame) {
                nextIndex++
            } else {
                break
            }
        }
        if (nextIndex in currentState.queue.indices) {
            playTrack(currentState.queue[nextIndex], currentState.queue)
        } else {
            if (current != null) {
                triggerAutoplayGeneration(current, isReseed = false, autoPlayFirst = true)
            }
        }
    }

    fun skipPrevious() {
        val currentState = _playbackState.value
        val prevIndex = currentState.queueIndex - 1
        if (prevIndex in currentState.queue.indices) {
            playTrack(currentState.queue[prevIndex], currentState.queue)
        }
    }

    fun appendToQueue(tracks: List<SieloTrack>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val currentState = _playbackState.value
            if (currentState.currentTrack == null || currentState.queue.isEmpty()) {
                playTrack(tracks.first(), tracks)
            } else {
                val existingIds = currentState.queue.map { it.id }.toSet()
                val currentTrack = currentState.currentTrack
                val currentTitle = currentTrack?.title?.trim()
                val currentArtist = currentTrack?.artist?.trim()
                val newTracks = tracks.filter { bTrack ->
                    bTrack.id !in existingIds &&
                    (currentTrack == null || bTrack.id != currentTrack.id) &&
                    !(currentTitle != null && currentArtist != null &&
                      bTrack.title.trim().equals(currentTitle, ignoreCase = true) &&
                      bTrack.artist.trim().equals(currentArtist, ignoreCase = true))
                }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                if (newTracks.isEmpty()) return@launch
                val updatedQueue = currentState.queue + newTracks
                _playbackState.update { it.copy(queue = updatedQueue) }
                savePlaybackState(
                    currentState.currentTrack,
                    updatedQueue,
                    currentState.queueIndex,
                    currentState.currentPositionMs,
                    currentState.durationMs
                )
            }
        }
    }

    fun appendToQueue(track: SieloTrack) {
        appendToQueue(listOf(track))
    }

    fun triggerAutoplayIfLow(seedTrack: SieloTrack? = _playbackState.value.currentTrack) {
        val targetTrack = seedTrack ?: return
        val state = _playbackState.value
        if (state.queue.size - state.queueIndex <= 2) {
            triggerAutoplayGeneration(targetTrack, isReseed = false)
        }
    }

    fun toggleShuffle() {
        val currentState = _playbackState.value
        val newShuffle = !currentState.isShuffle
        if (newShuffle) {
            originalQueue = currentState.queue
            val currentIdx = currentState.queueIndex
            val queue = currentState.queue
            if (queue.isNotEmpty() && currentIdx in queue.indices) {
                val currentTrack = queue[currentIdx]
                val pastTracks = queue.take(currentIdx)
                val upcomingTracks = queue.drop(currentIdx + 1).shuffled()
                val shuffledQueue = pastTracks + listOf(currentTrack) + upcomingTracks
                val newIdx = pastTracks.size
                _playbackState.update { it.copy(isShuffle = true, queue = shuffledQueue, queueIndex = newIdx) }
                savePlaybackState(currentTrack, shuffledQueue, newIdx, currentState.currentPositionMs, currentState.durationMs)
            } else {
                val shuffledQueue = queue.shuffled()
                _playbackState.update { it.copy(isShuffle = true, queue = shuffledQueue) }
            }
        } else {
            val currentTrack = currentState.currentTrack
            val restoredQueue = originalQueue ?: currentState.queue
            val newIdx = if (currentTrack != null) {
                restoredQueue.indexOfFirst { it.id == currentTrack.id }.takeIf { it >= 0 } ?: currentState.queueIndex
            } else currentState.queueIndex
            _playbackState.update { it.copy(isShuffle = false, queue = restoredQueue, queueIndex = newIdx) }
            savePlaybackState(currentTrack, restoredQueue, newIdx, currentState.currentPositionMs, currentState.durationMs)
        }
    }

    fun shuffleQueue() {
        val currentState = _playbackState.value
        val currentIdx = currentState.queueIndex
        val queue = currentState.queue
        if (queue.isNotEmpty() && currentIdx in queue.indices) {
            val currentTrack = queue[currentIdx]
            val pastTracks = queue.take(currentIdx)
            val currentTitleClean = currentTrack.title.trim()
            val currentArtistClean = currentTrack.artist.trim()
            val upcomingTracks = queue.drop(currentIdx + 1).filter { qTrack ->
                qTrack.id != currentTrack.id &&
                !(qTrack.title.trim().equals(currentTitleClean, ignoreCase = true) &&
                  qTrack.artist.trim().equals(currentArtistClean, ignoreCase = true))
            }.shuffled()
            val shuffledQueue = pastTracks + listOf(currentTrack) + upcomingTracks
            val newIdx = pastTracks.size
            _playbackState.update { it.copy(queue = shuffledQueue, queueIndex = newIdx) }
            savePlaybackState(currentTrack, shuffledQueue, newIdx, currentState.currentPositionMs, currentState.durationMs)
        } else if (queue.isNotEmpty()) {
            val shuffledQueue = queue.shuffled()
            _playbackState.update { it.copy(queue = shuffledQueue) }
        }
    }

    private fun startProgressTracking() {
        progressTrackerJob?.cancel()
        lastTrackingTimestamp = System.currentTimeMillis()
        progressTrackerJob = scope.launch {
            while (isActive) {
                val isPlaying = mediaController?.isPlaying == true
                val current = mediaController?.currentPosition ?: 0L
                val dur = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                _playbackState.update { it.copy(currentPositionMs = current, durationMs = dur) }

                if (isPlaying) {
                    val now = System.currentTimeMillis()
                    val delta = (now - lastTrackingTimestamp).coerceIn(0L, 1000L)
                    currentTrackAccumulatedPlayedMs += delta
                    lastTrackingTimestamp = now

                    // Periodic DB & Prefs sync every 2 seconds
                    if (now - lastDbFlushTimestamp >= 2000L) {
                        lastDbFlushTimestamp = now
                        flushCurrentListeningDuration()
                        savePositionOnly(current, dur)
                    }
                } else {
                    lastTrackingTimestamp = System.currentTimeMillis()
                }

                delay(50)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackerJob?.cancel()
    }
}