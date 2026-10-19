package com.sielo.music.core.recommendations.events

import android.util.Log
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.network.recommendations.ClientListeningEvent
import com.sielo.music.core.network.recommendations.RecommendationApiClient
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningEventDispatcher @Inject constructor(
    private val apiClient: RecommendationApiClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventQueue = ConcurrentLinkedQueue<ClientListeningEvent>()
    private var flushJob: Job? = null

    var currentUserIdProvider: (() -> String)? = null
    var isPrivateListeningEnabled: Boolean = false

    companion object {
        private const val TAG = "ListeningEventDispatcher"
        private const val FLUSH_INTERVAL_MS = 30_000L // Flush every 30 seconds
        private const val MAX_BATCH_SIZE = 50
    }

    init {
        startPeriodicFlush()
    }

    fun recordPlayStarted(track: SieloTrack, source: String = "autoplay", sessionId: String? = null) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "play_started",
            playbackPositionMs = 0L,
            source = source,
            sessionId = sessionId
        )
    }

    fun recordProgressCheckpoint(track: SieloTrack, positionMs: Long, source: String = "autoplay", sessionId: String? = null) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "playback_progress",
            playbackPositionMs = positionMs,
            source = source,
            sessionId = sessionId
        )
    }

    fun recordPlayCompleted(track: SieloTrack, durationMs: Long, source: String = "autoplay", sessionId: String? = null) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "play_completed",
            playbackPositionMs = durationMs,
            source = source,
            sessionId = sessionId
        )
    }

    fun recordSkipped(track: SieloTrack, positionMs: Long, source: String = "autoplay", sessionId: String? = null) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "skipped",
            playbackPositionMs = positionMs,
            source = source,
            sessionId = sessionId
        )
    }

    fun recordLiked(track: SieloTrack) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "liked",
            playbackPositionMs = 0L,
            source = "favorites"
        )
    }

    fun recordUnliked(track: SieloTrack) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "unliked",
            playbackPositionMs = 0L,
            source = "favorites"
        )
    }

    fun recordPlaylistAdded(track: SieloTrack, playlistId: String?) {
        if (isPrivateListeningEnabled) return
        emitEvent(
            track = track,
            eventType = "playlist_added",
            playbackPositionMs = 0L,
            source = "playlist_$playlistId"
        )
    }

    private fun emitEvent(
        track: SieloTrack,
        eventType: String,
        playbackPositionMs: Long,
        source: String?,
        sessionId: String? = null
    ) {
        val userId = currentUserIdProvider?.invoke() ?: "guest_listener"
        val now = System.currentTimeMillis()
        val durationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

        // Deterministic event ID for idempotency
        val eventId = "${userId}_${track.id}_${now}_${eventType}"

        val event = ClientListeningEvent(
            eventId = eventId,
            userId = userId,
            trackId = track.id,
            eventType = eventType,
            timestampMs = now,
            playbackPositionMs = playbackPositionMs,
            durationMs = durationMs,
            trackTitle = track.title,
            trackArtist = track.artist,
            trackAlbum = track.album,
            source = source,
            sessionId = sessionId
        )

        eventQueue.offer(event)
        if (eventQueue.size >= MAX_BATCH_SIZE) {
            scope.launch { flush() }
        }
    }

    private fun startPeriodicFlush() {
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    suspend fun flush() {
        if (eventQueue.isEmpty()) return

        val batch = mutableListOf<ClientListeningEvent>()
        while (batch.size < MAX_BATCH_SIZE && !eventQueue.isEmpty()) {
            val item = eventQueue.poll() ?: break
            batch.add(item)
        }

        if (batch.isEmpty()) return

        try {
            val success = apiClient.sendBatchEvents(batch)
            if (!success) {
                // If network failure, re-queue (bounded to prevent memory overflow)
                if (eventQueue.size < 200) {
                    batch.forEach { eventQueue.offer(it) }
                }
            } else {
                try {
                    Log.d(TAG, "Successfully synced ${batch.size} listening events to backend")
                } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            try {
                Log.d(TAG, "Deferred flushing listening events: ${e.message}")
            } catch (_: Throwable) {}
        }
    }
}
