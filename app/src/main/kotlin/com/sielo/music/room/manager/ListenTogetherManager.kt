
package com.sielo.music.room.manager

import android.content.Context
import android.util.Log
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.room.crypto.RoomCrypto
import com.sielo.music.room.model.ActiveRoomState
import com.sielo.music.room.model.RoomChatMessage
import com.sielo.music.room.model.RoomEvent
import com.sielo.music.room.model.RoomParticipant
import com.sielo.music.room.model.RoomQueueItem
import com.sielo.music.room.model.SavedRoomSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "ListenTogether"
private const val PRIMARY_BROKER = "tcp://broker.emqx.io:1883"
private val BROKER_URLS = arrayOf("tcp://broker.emqx.io:1883", "tcp://broker.hivemq.com:1883")
private const val SESSION_PREFS = "sielo_room_session_prefs"
private const val HOST_SYNC_INTERVAL_MS = 1000L
private const val DRIFT_CHECK_INTERVAL_MS = 500L
private const val SOFT_DRIFT_MS = 150L
private const val HARD_SYNC_DRIFT_MS = 750L
private const val MIN_HARD_SYNC_INTERVAL_MS = 1500L
private const val TRACK_START_LEAD_MS = 1500L
private const val EMPTY_ROOM_GRACE_PERIOD_MS = 120_000L

// Member playback controls are applied optimistically on the member device,
// then reconciled against the host's authoritative event.

@Singleton
class ListenTogetherManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val sessionPrefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)

    private val _pendingRejoinSession = MutableStateFlow<SavedRoomSession?>(null)
    val pendingRejoinSession: StateFlow<SavedRoomSession?> = _pendingRejoinSession.asStateFlow()

    private val _roomState = MutableStateFlow<ActiveRoomState?>(null)
    val roomState: StateFlow<ActiveRoomState?> = _roomState.asStateFlow()

    private val _roomNotification = MutableStateFlow<String?>(null)
    val roomNotification: StateFlow<String?> = _roomNotification.asStateFlow()

    fun clearRoomNotification() {
        _roomNotification.value = null
    }

    private var hostPresenceJob: Job? = null
    private var hostHeartbeatJob: Job? = null
    private var lastHostPacketTimeMs: Long = 0L
    private var mqttClient: MqttAsyncClient? = null
    private var currentTopic: String? = null
    private var typingTimeoutJobs = mutableMapOf<String, Job>()
    private var isSuppressingLocalPlayerSync = false
    private var hasSubscribedSuccessfully = false
    private var hostSequenceNumber: Long = 0L
    private var lastAppliedSequenceNumber: Long = 0L
    private var pendingSyncEvent: RoomEvent? = null
    private var clientDriftJob: Job? = null
    private var lastHardSyncAtMs: Long = 0L
    private var lastAuthoritativeHostTimestampMs: Long = 0L
    private var lastAuthoritativePositionMs: Long = 0L
    private var lastAuthoritativeIsPlaying: Boolean = false
    private var lastAuthoritativeTrackId: String? = null
    private var rejoinValidationJob: Job? = null

    // Prevent the host's local PlayerManager observer from rebroadcasting the
    // same command that was just handled by the room controller.
    private var suppressHostPlaybackObserverUntilMs: Long = 0L

    init {
        checkPendingRejoinSession()
        // Observe local player to sync to room if local user is host
        scope.launch {
            playerManager.playbackState.collect { playback ->
                val state = _roomState.value
                if (state != null) {
                    if (state.isHost && !isSuppressingLocalPlayerSync &&
                        System.currentTimeMillis() >= suppressHostPlaybackObserverUntilMs) {
                        val trackChanged = state.currentTrack?.id != playback.currentTrack?.id
                        val playStateChanged = state.isPlaying != playback.isPlaying
                        if (trackChanged || playStateChanged) {
                            _roomState.update {
                                it?.copy(
                                    currentTrack = playback.currentTrack,
                                    isPlaying = playback.isPlaying,
                                    currentPositionMs = playback.currentPositionMs
                                )
                            }
                            broadcastPlayback(
                                action = if (playback.isPlaying) "PLAY" else "PAUSE",
                                track = playback.currentTrack,
                                positionMs = playback.currentPositionMs
                            )
                        }
                    } else if (!state.isHost && !playback.isBuffering && playerManager.currentlyLoadingTrackId == null && pendingSyncEvent != null) {
                        processPendingSyncIfReady()
                    }
                }
            }
        }
    }

    private fun startClientDriftMonitor() {
        clientDriftJob?.cancel()
        clientDriftJob = scope.launch {
            while (true) {
                delay(DRIFT_CHECK_INTERVAL_MS)
                val state = _roomState.value ?: break
                if (state.isHost || !state.isConnected) continue
                applyContinuousDriftCorrection()
            }
        }
    }

    private fun stopClientDriftMonitor() {
        clientDriftJob?.cancel()
        clientDriftJob = null
    }

    private fun applyContinuousDriftCorrection() {
        val state = _roomState.value ?: return
        val track = state.currentTrack ?: return
        if (playerManager.currentlyLoadingTrackId != null) return

        val authoritativeTimestamp = lastAuthoritativeHostTimestampMs
        if (authoritativeTimestamp <= 0L || lastAuthoritativeTrackId != track.id) return

        val targetPosition = if (lastAuthoritativeIsPlaying) {
            lastAuthoritativePositionMs + (System.currentTimeMillis() - authoritativeTimestamp).coerceAtLeast(0L)
        } else {
            lastAuthoritativePositionMs
        }

        val local = playerManager.playbackState.value
        if (local.currentTrack?.id != track.id || local.isBuffering) return

        if (lastAuthoritativeIsPlaying != local.isPlaying) {
            applySyncedPlayback(track, targetPosition, lastAuthoritativeIsPlaying)
            return
        }

        val drift = targetPosition - local.currentPositionMs
        val absDrift = abs(drift)
        if (absDrift < SOFT_DRIFT_MS) return

        val now = System.currentTimeMillis()
        if (absDrift >= HARD_SYNC_DRIFT_MS && now - lastHardSyncAtMs >= MIN_HARD_SYNC_INTERVAL_MS) {
            lastHardSyncAtMs = now
            scope.launch(Dispatchers.Main) {
                isSuppressingLocalPlayerSync = true
                try {
                    playerManager.seekTo(targetPosition)
                } finally {
                    isSuppressingLocalPlayerSync = false
                }
            }
        }
    }

    /**
     * Checks whether the saved room is still alive before exposing the rejoin popup.
     *
     * A saved session only means that this device was previously in a room. It does
     * NOT prove that the MQTT room is still active. We therefore send a RequestSync
     * probe and wait briefly for a real room response. The probe does not publish a
     * Join event, so it never creates a phantom participant.
     */
    fun checkPendingRejoinSession() {
        rejoinValidationJob?.cancel()

        if (_roomState.value != null) {
            _pendingRejoinSession.value = null
            return
        }

        val roomId = sessionPrefs.getString("saved_room_id", null)
        val roomKey = sessionPrefs.getString("saved_room_key", null)
        val userName = sessionPrefs.getString("saved_user_name", null)
        val isHost = sessionPrefs.getBoolean("saved_is_host", false)
        val timestamp = sessionPrefs.getLong("saved_timestamp", 0L)
        val wasIntentionalExit = sessionPrefs.getBoolean("was_intentional_exit", true)

        val isRecent = timestamp > 0L &&
                (System.currentTimeMillis() - timestamp) < 12 * 60 * 60 * 1000L

        if (roomId.isNullOrBlank() || roomKey.isNullOrBlank() || wasIntentionalExit || !isRecent) {
            _pendingRejoinSession.value = null
            return
        }

        // Do not show the popup until the room has been verified.
        _pendingRejoinSession.value = null

        rejoinValidationJob = scope.launch(Dispatchers.IO) {
            val exists = probeRoomExists(roomId, roomKey)

            if (exists) {
                _pendingRejoinSession.value = SavedRoomSession(
                    roomId = roomId,
                    roomKey = roomKey,
                    userName = userName ?: "User",
                    isHost = isHost,
                    timestamp = timestamp
                )
            } else {
                Log.d(TAG, "Saved room $roomId is no longer active; clearing rejoin session.")
                sessionPrefs.edit()
                    .remove("saved_room_id")
                    .remove("saved_room_key")
                    .remove("saved_user_name")
                    .remove("saved_is_host")
                    .remove("saved_timestamp")
                    .putBoolean("was_intentional_exit", true)
                    .apply()
                _pendingRejoinSession.value = null
            }
        }
    }

    /**
     * Returns true only when a live participant/host responds from the room.
     * This is intentionally independent of the main room MQTT client because this
     * check can run while the app is sitting on the home screen.
     */
    private suspend fun probeRoomExists(roomId: String, roomKey: String): Boolean {
        val topic = "sielo/v3/rooms/${roomId.uppercase().trim()}"
        val clientId = "Sielo_RejoinProbe_${UUID.randomUUID().toString().take(8)}"
        val responseReceived = AtomicBoolean(false)
        val probeClient = try {
            MqttAsyncClient(PRIMARY_BROKER, clientId, MemoryPersistence())
        } catch (e: Exception) {
            Log.e(TAG, "Could not create rejoin probe client", e)
            return false
        }

        return try {
            val options = MqttConnectOptions().apply {
                setServerURIs(BROKER_URLS)
                isCleanSession = true
                connectionTimeout = 5
                keepAliveInterval = 10
                isAutomaticReconnect = false
            }

            probeClient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    // Initial connect callback is handled by connect() below.
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.d(TAG, "Rejoin probe connection lost: ${cause?.message}")
                }

                override fun messageArrived(receivedTopic: String?, message: MqttMessage?) {
                    if (message == null || responseReceived.get()) return
                    val decrypted = RoomCrypto.decrypt(
                        String(message.payload, Charsets.UTF_8),
                        roomKey
                    ) ?: return

                    try {
                        val event = json.decodeFromString<RoomEvent>(decrypted)
                        // RequestSync is not considered a response. A SyncState is the
                        // authoritative proof that a live room participant is present.
                        if (event is RoomEvent.SyncState) {
                            responseReceived.set(true)
                        }
                    } catch (_: Exception) {
                        // Ignore packets that are not valid for this saved room key.
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            val connected = kotlinx.coroutines.CompletableDeferred<Boolean>()
            probeClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    connected.complete(true)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Rejoin probe connection failed", exception)
                    connected.complete(false)
                }
            })

            if (!connected.await()) return false

            val subscribed = kotlinx.coroutines.CompletableDeferred<Boolean>()
            probeClient.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    subscribed.complete(true)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Rejoin probe subscription failed", exception)
                    subscribed.complete(false)
                }
            })

            if (!subscribed.await()) return false

            // Ask the live host for its current state. We deliberately do not send
            // Join, so the probe never changes the room's participant list.
            val request = RoomEvent.RequestSync("rejoin-probe-${UUID.randomUUID()}")
            val encrypted = RoomCrypto.encrypt(json.encodeToString(request), roomKey)
            val message = MqttMessage(encrypted.toByteArray(Charsets.UTF_8)).apply { qos = 1 }
            probeClient.publish(topic, message)

            // Host heartbeat is 1 second; allow several intervals for mobile networks.
            var waited = 0L
            while (waited < 4500L && !responseReceived.get()) {
                delay(100L)
                waited += 100L
            }

            responseReceived.get()
        } catch (e: Exception) {
            Log.d(TAG, "Rejoin room probe failed", e)
            false
        } finally {
            try {
                if (probeClient.isConnected) probeClient.disconnect()
                probeClient.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveActiveSession(roomId: String, roomKey: String, userName: String, isHost: Boolean) {
        sessionPrefs.edit()
            .putString("saved_room_id", roomId)
            .putString("saved_room_key", roomKey)
            .putString("saved_user_name", userName)
            .putBoolean("saved_is_host", isHost)
            .putLong("saved_timestamp", System.currentTimeMillis())
            .putBoolean("was_intentional_exit", false)
            .apply()
    }

    fun markSessionIntentionalExit() {
        sessionPrefs.edit()
            .remove("saved_room_id")
            .remove("saved_room_key")
            .putBoolean("was_intentional_exit", true)
            .apply()
        _pendingRejoinSession.value = null
    }

    private var emptyRoomTimerJob: Job? = null
    private var roomShutdownJob: Job? = null

    /**
     * A room is considered alive while at least one live participant is
     * connected. A host by itself is therefore a valid live room and must
     * never be destroyed by this check.
     *
     * Important MQTT limitation: once the last device has actually
     * disconnected, there is no process left that can execute a 10-second
     * timer. In that situation the room is effectively dead immediately,
     * because the rejoin probe can only keep a room alive when a live
     * participant/host answers RequestSync.
     *
     * The 10-second timer below is consequently a safety timer for a local
     * room state that temporarily reaches zero participants (for example
     * while leave/disconnect events are being reconciled). It is never
     * started while a host or any participant is present.
     */
    private fun checkEmptyRoomDestruction() {
        val state = _roomState.value ?: return

        val hasHost = state.participants.any { it.isHost }
        val hasParticipants = state.participants.isNotEmpty()

        // A host alone is a valid room. Any remaining participant is also
        // enough to keep the room alive while host election/reconnection
        // takes place.
        if (hasHost || hasParticipants) {
            emptyRoomTimerJob?.cancel()
            emptyRoomTimerJob = null
            return
        }

        // No host and no participants: start the 10-second safety window.
        if (emptyRoomTimerJob?.isActive != true) {
            emptyRoomTimerJob = scope.launch {
                delay(EMPTY_ROOM_GRACE_PERIOD_MS)

                val currentState = _roomState.value ?: return@launch
                val currentHasHost = currentState.participants.any { it.isHost }
                val currentHasParticipants = currentState.participants.isNotEmpty()

                // Re-check immediately before destruction. If anyone has
                // appeared, the room is alive again and must be preserved.
                if (!currentHasHost && !currentHasParticipants) {
                    Log.d(
                        TAG,
                        "Room ${currentState.roomId} expired after 2 minutes with no host and no participants."
                    )

                    sessionPrefs.edit()
                        .remove("saved_room_id")
                        .remove("saved_room_key")
                        .remove("saved_user_name")
                        .remove("saved_is_host")
                        .remove("saved_timestamp")
                        .putBoolean("was_intentional_exit", true)
                        .apply()

                    leaveRoom(isIntentional = false)
                } else {
                    emptyRoomTimerJob = null
                }
            }
        }
    }

    private fun processPendingSyncIfReady() {
        val event = pendingSyncEvent ?: return
        val currentLocal = playerManager.playbackState.value
        if (currentLocal.isBuffering || playerManager.currentlyLoadingTrackId != null) return

        pendingSyncEvent = null
        val elapsedMs = (System.currentTimeMillis() - when (event) {
            is RoomEvent.SyncState -> event.timestampEpochMs
            is RoomEvent.PlaybackAction -> event.timestampEpochMs
            else -> System.currentTimeMillis()
        }).coerceAtLeast(0L)

        val track = when (event) {
            is RoomEvent.SyncState -> event.currentTrack
            is RoomEvent.PlaybackAction -> event.track
            else -> null
        }
        val isPlaying = when (event) {
            is RoomEvent.SyncState -> event.isPlaying
            is RoomEvent.PlaybackAction -> event.action == "PLAY"
            else -> false
        }
        val basePos = when (event) {
            is RoomEvent.SyncState -> event.positionMs
            is RoomEvent.PlaybackAction -> event.positionMs
            else -> 0L
        }

        val targetPos = if (isPlaying) (basePos + elapsedMs) else basePos
        lastAuthoritativeHostTimestampMs = when (event) {
            is RoomEvent.SyncState -> event.timestampEpochMs
            is RoomEvent.PlaybackAction -> event.timestampEpochMs
            else -> System.currentTimeMillis()
        }
        lastAuthoritativePositionMs = basePos
        lastAuthoritativeIsPlaying = isPlaying
        lastAuthoritativeTrackId = track?.id
        val startAtTimestampMs = if (isPlaying) lastAuthoritativeHostTimestampMs else null
        applySyncedPlayback(track, targetPos, isPlaying, startAtTimestampMs)
    }

    fun dismissRejoinPrompt() {
        markSessionIntentionalExit()
    }

    fun rejoinPreviousRoom() {
        val session = _pendingRejoinSession.value ?: return
        _pendingRejoinSession.value = null
        if (session.isHost) {
            rejoinAsHost(session.roomId, session.roomKey, session.userName)
        } else {
            joinRoom(session.roomId, session.roomKey, session.userName)
        }
    }

    private fun getOrCreateLocalUserId(): String {
        val existing = sessionPrefs.getString("persistent_participant_id", null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString().take(8)
        sessionPrefs.edit().putString("persistent_participant_id", newId).apply()
        return newId
    }

    /**
     * Creates a new private encrypted room.
     */
    fun createRoom(hostName: String): ActiveRoomState {
        leaveRoom(isIntentional = false) // Cleanup any previous room
        val roomId = generateRoomId()
        val roomKey = RoomCrypto.deriveKey(roomId)
        val localUserId = getOrCreateLocalUserId()
        val host = RoomParticipant(
            id = localUserId,
            name = hostName.ifBlank { "Host" },
            isHost = true
        )

        val currentLocalTrack = playerManager.playbackState.value.currentTrack
        val currentLocalPlaying = playerManager.playbackState.value.isPlaying
        val currentLocalPos = playerManager.playbackState.value.currentPositionMs

        val initialQueue = if (currentLocalTrack != null) {
            listOf(RoomQueueItem(currentLocalTrack, host.name))
        } else emptyList()

        hostSequenceNumber = 0L
        lastAppliedSequenceNumber = 0L
        pendingSyncEvent = null
        lastAuthoritativeHostTimestampMs = 0L
        lastAuthoritativePositionMs = 0L
        lastAuthoritativeIsPlaying = false
        lastAuthoritativeTrackId = null

        val newState = ActiveRoomState(
            roomId = roomId,
            roomKey = roomKey,
            localUserId = localUserId,
            localUserName = host.name,
            isHost = true,
            participants = listOf(host),
            currentTrack = currentLocalTrack,
            isPlaying = currentLocalPlaying,
            currentPositionMs = currentLocalPos,
            queue = initialQueue,
            isConnected = false,
            connectionStatus = "Connecting to room..."
        )

        _roomState.value = newState
        saveActiveSession(roomId, roomKey, host.name, isHost = true)
        connectToBroker(roomId, roomKey, host)
        return newState
    }

    /**
     * Re-joins a previously hosted room without resetting the room ID.
     */
    fun rejoinAsHost(roomId: String, roomKey: String, hostName: String): ActiveRoomState {
        leaveRoom(isIntentional = false)
        val cleanedRoomId = roomId.uppercase().trim()
        val resolvedKey = if (roomKey.isNotBlank()) roomKey.trim() else RoomCrypto.deriveKey(cleanedRoomId)
        val localUserId = getOrCreateLocalUserId()
        val host = RoomParticipant(
            id = localUserId,
            name = hostName.ifBlank { "Host" },
            isHost = true
        )

        val currentLocalTrack = playerManager.playbackState.value.currentTrack
        val currentLocalPlaying = playerManager.playbackState.value.isPlaying
        val currentLocalPos = playerManager.playbackState.value.currentPositionMs

        val initialQueue = if (currentLocalTrack != null) {
            listOf(RoomQueueItem(currentLocalTrack, host.name))
        } else emptyList()

        hostSequenceNumber = 0L
        lastAppliedSequenceNumber = 0L
        pendingSyncEvent = null
        lastAuthoritativeHostTimestampMs = 0L
        lastAuthoritativePositionMs = 0L
        lastAuthoritativeIsPlaying = false
        lastAuthoritativeTrackId = null

        val newState = ActiveRoomState(
            roomId = cleanedRoomId,
            roomKey = resolvedKey,
            localUserId = localUserId,
            localUserName = host.name,
            isHost = true,
            participants = listOf(host),
            currentTrack = currentLocalTrack,
            isPlaying = currentLocalPlaying,
            currentPositionMs = currentLocalPos,
            queue = initialQueue,
            isConnected = false,
            connectionStatus = "Reconnecting as Host..."
        )

        _roomState.value = newState
        saveActiveSession(cleanedRoomId, resolvedKey, host.name, isHost = true)
        connectToBroker(cleanedRoomId, resolvedKey, host)
        return newState
    }

    /**
     * Joins an existing room using Room ID and decryption Key.
     */
    fun joinRoom(roomId: String, roomKey: String, participantName: String) {
        leaveRoom(isIntentional = false)
        val cleanedRoomId = roomId.uppercase().trim()
        val resolvedKey = if (roomKey.isNotBlank()) roomKey.trim() else RoomCrypto.deriveKey(cleanedRoomId)
        val localUserId = getOrCreateLocalUserId()
        val participant = RoomParticipant(
            id = localUserId,
            name = participantName.ifBlank { "Listener" },
            isHost = false
        )

        hostSequenceNumber = 0L
        lastAppliedSequenceNumber = 0L
        pendingSyncEvent = null
        lastAuthoritativeHostTimestampMs = 0L
        lastAuthoritativePositionMs = 0L
        lastAuthoritativeIsPlaying = false
        lastAuthoritativeTrackId = null

        val newState = ActiveRoomState(
            roomId = cleanedRoomId,
            roomKey = resolvedKey,
            localUserId = localUserId,
            localUserName = participant.name,
            isHost = false,
            participants = listOf(participant),
            isConnected = false,
            connectionStatus = "Connecting to room..."
        )

        _roomState.value = newState
        saveActiveSession(cleanedRoomId, resolvedKey, participant.name, isHost = false)
        startHostPresenceWatchdog()
        connectToBroker(newState.roomId, newState.roomKey, participant)
    }

    private fun startHostHeartbeat() {
        hostHeartbeatJob?.cancel()
        hostHeartbeatJob = scope.launch {
            while (true) {
                delay(HOST_SYNC_INTERVAL_MS)
                val state = _roomState.value
                if (state != null && state.isHost && state.isConnected) {
                    broadcastSyncState()
                    checkEmptyRoomDestruction()
                } else if (state == null || !state.isHost) {
                    break
                }
            }
        }
    }

    private fun startHostPresenceWatchdog() {
        hostPresenceJob?.cancel()
        hostPresenceJob = scope.launch {
            // Initial grace period for connection and sync
            delay(20000)
            while (true) {
                val state = _roomState.value ?: break
                if (state.isHost) break
                val now = System.currentTimeMillis()

                // Check if an election message was posted recently to avoid duplicate alerts
                val hasRecentlyElected = state.chatMessages.takeLast(6).any {
                    it.text.contains("is now the host of the room", ignoreCase = true) &&
                            (now - it.timestamp) < 15000L
                }
                if (hasRecentlyElected) {
                    delay(4000)
                    continue
                }

                val hasHostInParticipants = state.participants.any { it.isHost }
                val timeSinceLastHostPacket = if (lastHostPacketTimeMs > 0) now - lastHostPacketTimeMs else 25000L

                // If no host in participant list OR host has been silent for > 18s
                if (!hasHostInParticipants || (lastHostPacketTimeMs > 0 && timeSinceLastHostPacket > 18000L)) {
                    val remaining = state.participants.filter { !it.isHost }
                    if (remaining.isNotEmpty()) {
                        val nextHost = remaining.minByOrNull { it.joinedAt }
                        if (nextHost != null) {
                            val isLocalNextHost = (nextHost.id == state.localUserId)
                            val updatedWithNewHost = state.participants.map { it.copy(isHost = it.id == nextHost.id) }
                            val abruptHostMsg = RoomChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = "system",
                                senderName = "System",
                                text = "👑 Host disconnected abruptly. ${nextHost.name} is now the host of the room.",
                                timestamp = System.currentTimeMillis(),
                                isSystemEvent = true
                            )
                            _roomState.update { current ->
                                current?.copy(
                                    isHost = if (isLocalNextHost) true else current.isHost,
                                    participants = updatedWithNewHost,
                                    chatMessages = current.chatMessages + abruptHostMsg,
                                    connectionStatus = if (isLocalNextHost) "Connected (You are Host)" else "Connected (Host: ${nextHost.name})"
                                )
                            }
                            if (isLocalNextHost) {
                                saveActiveSession(state.roomId, state.roomKey, state.localUserName, isHost = true)
                                startHostHeartbeat()
                                broadcastSyncState()
                            }
                            break
                        }
                    }
                }
                delay(4000)
            }
        }
    }

    private fun subscribeAndAnnounce(client: MqttAsyncClient, topic: String, localParticipant: RoomParticipant) {
        try {
            client.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed successfully to $topic")
                    hasSubscribedSuccessfully = true
                    _roomState.update { it?.copy(isConnected = true, connectionStatus = if (localParticipant.isHost) "Connected (Host)" else "Connected") }
                    broadcastEvent(RoomEvent.Join(localParticipant))
                    if (localParticipant.isHost) {
                        stopClientDriftMonitor()
                        broadcastSyncState()
                        startHostHeartbeat()
                    } else {
                        startClientDriftMonitor()
                        broadcastEvent(RoomEvent.RequestSync(localParticipant.id))
                        // Secondary RequestSync after 1.5s in case host subscribed concurrently
                        scope.launch {
                            delay(1500)
                            if (_roomState.value?.currentTrack == null && !_roomState.value!!.isHost) {
                                broadcastEvent(RoomEvent.RequestSync(localParticipant.id))
                            }
                        }
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Subscription failure", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic", e)
        }
    }

    private fun connectToBroker(roomId: String, roomKey: String, localParticipant: RoomParticipant) {
        val topic = "sielo/v3/rooms/$roomId"
        currentTopic = topic
        hasSubscribedSuccessfully = false
        val clientId = "Sielo_${localParticipant.id}_${System.currentTimeMillis() % 100000}_${(1000..9999).random()}"

        try {
            val client = MqttAsyncClient(PRIMARY_BROKER, clientId, MemoryPersistence())
            mqttClient = client

            val options = MqttConnectOptions().apply {
                setServerURIs(BROKER_URLS)
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = true

                // MQTT Last Will handles members that leave because the app is
                // killed, crashes, loses power, or loses its network connection.
                // Other room participants will receive a Leave event even when
                // leaveRoom() cannot run normally.
                val willEvent = RoomEvent.Leave(
                    localParticipant.id,
                    localParticipant.name
                )
                val willPayload = RoomCrypto.encrypt(
                    json.encodeToString(willEvent),
                    roomKey
                ).toByteArray(Charsets.UTF_8)
                setWill(topic, willPayload, 1, false)
            }

            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "Connected to room broker: $serverURI (reconnect=$reconnect)")
                    _roomState.update { it?.copy(isConnected = true, connectionStatus = "Connected") }
                    subscribeAndAnnounce(client, topic, localParticipant)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "Connection lost: ${cause?.message}")
                    _roomState.update { it?.copy(isConnected = false, connectionStatus = "Reconnecting...") }
                }

                override fun messageArrived(receivedTopic: String?, message: MqttMessage?) {
                    if (message == null) return
                    val encryptedPayload = String(message.payload, Charsets.UTF_8)
                    handleIncomingEncryptedMessage(encryptedPayload, roomKey)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Initial connect success")
                    subscribeAndAnnounce(client, topic, localParticipant)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Initial connect failed, retrying via fallback", exception)
                    _roomState.update { it?.copy(connectionStatus = "Connecting to room server...") }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT error", e)
            _roomState.update { it?.copy(connectionStatus = "Connection Error: ${e.localizedMessage}") }
        }
    }

    private fun handleIncomingEncryptedMessage(encryptedPayload: String, roomKey: String) {
        val decryptedJson = RoomCrypto.decrypt(encryptedPayload, roomKey) ?: return
        try {
            val event = json.decodeFromString<RoomEvent>(decryptedJson)
            processRoomEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RoomEvent", e)
        }
    }

    private fun processRoomEvent(event: RoomEvent) {
        val state = _roomState.value ?: return

        when (event) {
            is RoomEvent.Join -> {
                if (event.participant.id != state.localUserId) {
                    // A new participant cancels a pending last-host shutdown.
                    roomShutdownJob?.cancel()
                    roomShutdownJob = null
                    if (event.participant.isHost) {
                        hostPresenceJob?.cancel()
                    }
                    val joinMsg = RoomChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = "system",
                        senderName = "System",
                        text = "👋 ${event.participant.name} joined the room",
                        timestamp = System.currentTimeMillis(),
                        isSystemEvent = true
                    )
                    // Filter out duplicate participant by ID or name
                    val updatedParticipants = (state.participants.filter {
                        it.id != event.participant.id && !it.name.equals(event.participant.name, ignoreCase = true)
                    } + event.participant)
                    _roomState.update {
                        it?.copy(
                            participants = updatedParticipants,
                            chatMessages = if (it.chatMessages.none { m -> m.text == joinMsg.text && System.currentTimeMillis() - m.timestamp < 3000 }) it.chatMessages + joinMsg else it.chatMessages,
                            connectionStatus = if (event.participant.isHost) "Connected (Host online)" else it.connectionStatus
                        )
                    }
                    // Host replies with current sync state
                    if (state.isHost) {
                        broadcastSyncState()
                    }
                    checkEmptyRoomDestruction()
                }
            }

            is RoomEvent.Leave -> {
                // Always remove the participant immediately and add a visible
                // leave message. This handles both an explicit leave packet and
                // the MQTT Last-Will packet from an unexpected disconnect.
                val leavingParticipant = state.participants.find { it.id == event.participantId }
                val wasHost = leavingParticipant?.isHost == true
                val updated = state.participants.filter {
                    it.id != event.participantId &&
                            !it.name.equals(event.participantName, ignoreCase = true)
                }

                if (wasHost) {
                    hostPresenceJob?.cancel()
                }

                val leaveMsg = RoomChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "system",
                    senderName = "System",
                    text = "🚪 ${event.participantName} left the room",
                    timestamp = System.currentTimeMillis(),
                    isSystemEvent = true
                )

                if (wasHost && updated.isNotEmpty()) {
                    val nextHost = updated.minByOrNull { it.joinedAt }

                    if (nextHost != null) {
                        val isLocalNextHost = nextHost.id == state.localUserId
                        val updatedWithNewHost = updated.map {
                            it.copy(isHost = it.id == nextHost.id)
                        }
                        val hostElectedMsg = RoomChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "system",
                            senderName = "System",
                            text = "👑 ${nextHost.name} is now the host of the room.",
                            timestamp = System.currentTimeMillis(),
                            isSystemEvent = true
                        )

                        _roomState.update { current ->
                            current?.copy(
                                isHost = if (isLocalNextHost) true else current.isHost,
                                participants = updatedWithNewHost,
                                chatMessages = current.chatMessages + leaveMsg + hostElectedMsg,
                                connectionStatus = if (isLocalNextHost) {
                                    "Connected (You are Host)"
                                } else {
                                    "Connected (Host: ${nextHost.name})"
                                }
                            )
                        }

                        if (isLocalNextHost) {
                            saveActiveSession(
                                state.roomId,
                                state.roomKey,
                                state.localUserName,
                                isHost = true
                            )
                            scope.launch {
                                delay(300)
                                broadcastSyncState()
                            }
                        }
                    }
                } else {
                    _roomState.update { current ->
                        current?.copy(
                            participants = updated,
                            chatMessages = current.chatMessages + leaveMsg,
                            connectionStatus = if (wasHost) {
                                "Host disconnected. Waiting for host..."
                            } else {
                                current.connectionStatus
                            }
                        )
                    }

                    if (state.isHost) {
                        broadcastSyncState()
                    }
                }

                // If this was the last participant, arm the 2-minute safety
                // expiry. If someone is still present, this cancels any stale
                // expiry job.
                checkEmptyRoomDestruction()
            }

            is RoomEvent.TransferHost -> {
                val isLocalNewHost = (event.newHostId == state.localUserId)
                val updatedParticipants = state.participants.map {
                    it.copy(isHost = it.id == event.newHostId)
                }
                val transferMsg = RoomChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "system",
                    senderName = "System",
                    text = "👑 ${event.newHostName} is now the host of the room.",
                    timestamp = System.currentTimeMillis(),
                    isSystemEvent = true
                )
                _roomState.update { current ->
                    current?.copy(
                        isHost = isLocalNewHost,
                        participants = updatedParticipants,
                        chatMessages = current.chatMessages + transferMsg,
                        connectionStatus = if (isLocalNewHost) "Connected (You are Host)" else "Connected (Host: ${event.newHostName})"
                    )
                }
                if (isLocalNewHost) {
                    saveActiveSession(state.roomId, state.roomKey, state.localUserName, isHost = true)
                    broadcastSyncState()
                }
            }

            is RoomEvent.RequestSync -> {
                if (state.isHost) {
                    broadcastSyncState()
                }
            }

            is RoomEvent.SyncState -> {
                lastHostPacketTimeMs = System.currentTimeMillis()
                if (!state.isHost) {
                    if (event.sequenceNumber > 0L && event.sequenceNumber <= lastAppliedSequenceNumber) {
                        return
                    }
                    if (event.sequenceNumber > 0L) {
                        lastAppliedSequenceNumber = event.sequenceNumber
                    }

                    val currentLocal = playerManager.playbackState.value
                    if (currentLocal.isBuffering || playerManager.currentlyLoadingTrackId != null) {
                        pendingSyncEvent = event
                        return
                    }

                    lastAuthoritativeHostTimestampMs = event.timestampEpochMs
                    lastAuthoritativePositionMs = event.positionMs
                    lastAuthoritativeIsPlaying = event.isPlaying
                    lastAuthoritativeTrackId = event.currentTrack?.id

                    val drift = (System.currentTimeMillis() - event.timestampEpochMs).coerceAtLeast(0L)
                    val syncedPos = if (event.isPlaying) event.positionMs + drift else event.positionMs

                    // Retain local participant in participant list so listener does not disappear
                    val localP = state.participants.find { it.id == state.localUserId }
                        ?: RoomParticipant(state.localUserId, state.localUserName, isHost = false)

                    // Deduplicate participants, keeping local participant first and ensuring only 1 host
                    val otherParticipants = event.participants.filter {
                        it.id != state.localUserId && !it.name.equals(state.localUserName, ignoreCase = true)
                    }.distinctBy { it.name.lowercase().trim() }

                    val mergedParticipants = (listOf(localP.copy(isHost = (localP.id == event.hostId))) +
                            otherParticipants.map { it.copy(isHost = (it.id == event.hostId)) })
                        .distinctBy { it.id }

                    _roomState.update {
                        it?.copy(
                            participants = mergedParticipants,
                            currentTrack = event.currentTrack,
                            isPlaying = event.isPlaying,
                            currentPositionMs = syncedPos,
                            queue = event.queue,
                            connectionStatus = "Connected (Host online)"
                        )
                    }

                    // Align local player
                    applySyncedPlayback(event.currentTrack, syncedPos, event.isPlaying, event.timestampEpochMs.takeIf { event.isPlaying && it > System.currentTimeMillis() })
                }
            }

            is RoomEvent.PlaybackAction -> {
                // Queue removal uses the same host-authoritative channel as playback
                // controls, but it must not alter playback timing state.
                if (event.action == "REMOVE_QUEUE") {
                    if (state.isHost && event.sequenceNumber <= 0L) {
                        handleMemberControlRequest(event)
                    } else if (!state.isHost && event.sequenceNumber > 0L) {
                        val removeId = event.track?.id ?: return
                        _roomState.update { current ->
                            current?.copy(queue = current.queue.filter { it.track.id != removeId })
                        }
                    }
                    return
                }

                // Every playback control is authoritative through the host.
                // Members send a control request; the host executes it locally and
                // broadcasts the resulting authoritative playback state to everyone.
                // This prevents two phones from creating competing timelines.
                // Requests (sequence 0) are handled only by the host.
                // Authoritative events (positive sequence) are consumed only by
                // members; the host already executed the command locally. Do NOT
                // use participant names to suppress events because two users can
                // have the same display name.
                if (state.isHost && event.sequenceNumber <= 0L) {
                    handleMemberControlRequest(event)
                    return
                }

                // The host is the source of authoritative playback events, so it
                // must never re-apply its own broadcast.
                if (state.isHost && event.sequenceNumber > 0L) return

                if (event.sequenceNumber > 0L && event.sequenceNumber <= lastAppliedSequenceNumber) {
                    return
                }
                if (event.sequenceNumber > 0L) {
                    lastAppliedSequenceNumber = event.sequenceNumber
                }

                val currentLocal = playerManager.playbackState.value
                if (currentLocal.isBuffering || playerManager.currentlyLoadingTrackId != null) {
                    pendingSyncEvent = event
                    return
                }

                lastAuthoritativeHostTimestampMs = event.timestampEpochMs
                lastAuthoritativePositionMs = event.positionMs
                lastAuthoritativeIsPlaying = event.action == "PLAY"
                lastAuthoritativeTrackId = (event.track ?: state.currentTrack)?.id

                val drift = (System.currentTimeMillis() - event.timestampEpochMs).coerceAtLeast(0L)
                val targetPos = if (event.action == "PLAY") event.positionMs + drift else event.positionMs

                when (event.action) {
                    "PLAY" -> {
                        _roomState.update { it?.copy(currentTrack = event.track ?: it.currentTrack, isPlaying = true, currentPositionMs = targetPos) }
                        applySyncedPlayback(
                            event.track ?: state.currentTrack,
                            targetPos,
                            true,
                            event.timestampEpochMs.takeIf { it > System.currentTimeMillis() }
                        )
                    }
                    "PAUSE" -> {
                        _roomState.update { it?.copy(isPlaying = false, currentPositionMs = event.positionMs) }
                        applySyncedPlayback(state.currentTrack, event.positionMs, false, null)
                    }
                    "SEEK" -> {
                        _roomState.update { it?.copy(currentPositionMs = event.positionMs) }
                        playerManager.seekTo(event.positionMs)
                    }
                    "PREVIOUS" -> {
                        performSkipPrevious()
                    }
                    "NEXT" -> {
                        performSkipNext()
                    }
                }
            }

            is RoomEvent.AddToQueue -> {
                val alreadyExists = state.queue.any { it.track.id == event.item.track.id }
                if (!alreadyExists) {
                    val newQueue = state.queue + event.item
                    val systemMsg = RoomChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = "system",
                        senderName = "System",
                        text = "${event.item.addedBy} added \"${event.item.track.title}\" to the queue",
                        timestamp = System.currentTimeMillis(),
                        isSystemEvent = true
                    )
                    val user = event.item.addedBy
                    val curCount = state.addedSongsCount[user] ?: 0
                    val updatedCountMap = state.addedSongsCount + (user to (curCount + 1))
                    _roomState.update { it?.copy(
                        queue = newQueue,
                        chatMessages = it.chatMessages + systemMsg,
                        addedSongsCount = updatedCountMap
                    ) }
                    // If no track currently playing, start playing this track
                    if (state.currentTrack == null) {
                        playRoomTrack(event.item.track)
                    }
                }
            }

            is RoomEvent.SkipTrack -> {
                // Legacy next-track event support. A member request is still executed
                // only by the host so the host remains the single playback authority.
                if (event.triggeredBy == state.localUserName) return
                if (state.isHost) {
                    performSkipNext()
                }
            }

            is RoomEvent.Chat -> {
                if (state.chatMessages.none { it.id == event.message.id }) {
                    val updatedChat = state.chatMessages + event.message
                    _roomState.update { it?.copy(chatMessages = updatedChat) }
                }
            }

            is RoomEvent.Typing -> {
                if (event.userId != state.localUserId) {
                    val currentTyping = state.typingUsers.toMutableSet()
                    if (event.isTyping) {
                        currentTyping.add(event.userName)
                        _roomState.update { it?.copy(typingUsers = currentTyping) }
                        // Cancel previous reset job and set a 3-second auto-clear
                        typingTimeoutJobs[event.userId]?.cancel()
                        typingTimeoutJobs[event.userId] = scope.launch {
                            delay(3000)
                            _roomState.update { s ->
                                s?.copy(typingUsers = s.typingUsers - event.userName)
                            }
                        }
                    } else {
                        currentTyping.remove(event.userName)
                        _roomState.update { it?.copy(typingUsers = currentTyping) }
                    }
                }
            }
        }
    }

    /**
     * Handles playback controls requested by a room member.
     *
     * The member NEVER starts/stops/seeks/skips its own player directly. The host
     * performs the operation using the host's real PlayerManager state and then
     * sends one authoritative event to every device.
     */
    private fun handleMemberControlRequest(event: RoomEvent.PlaybackAction) {
        val state = _roomState.value ?: return
        if (!state.isHost) return

        when (event.action) {
            "PLAY" -> {
                val requestedTrack = event.track
                val hostTrack = playerManager.playbackState.value.currentTrack ?: state.currentTrack

                if (requestedTrack != null && requestedTrack.id != hostTrack?.id) {
                    // A member selected a different track. Host loads and starts it.
                    playRoomTrack(requestedTrack)
                } else if (!playerManager.playbackState.value.isPlaying) {
                    // Resume using the host's actual position, not the member's clock.
                    togglePlayPause()
                } else {
                    // Already playing: publish the current authoritative state so the
                    // requesting member immediately converges without restarting audio.
                    broadcastSyncState()
                }
            }

            "PAUSE" -> {
                if (playerManager.playbackState.value.isPlaying) {
                    togglePlayPause()
                } else {
                    broadcastSyncState()
                }
            }

            "SEEK" -> {
                val duration = playerManager.playbackState.value.durationMs
                val requestedPosition = event.positionMs.coerceAtLeast(0L)
                val safePosition = if (duration > 0L) {
                    requestedPosition.coerceAtMost(duration)
                } else {
                    requestedPosition
                }
                seekTo(safePosition)
            }

            "REMOVE_QUEUE" -> {
                val trackId = event.track?.id ?: return
                val track = state.queue.firstOrNull { it.track.id == trackId }?.track ?: return

                // The currently playing track is not an "up next" item. Keep it in
                // the queue so playback and previous/next navigation remain valid.
                if (track.id == state.currentTrack?.id) return

                _roomState.update { current ->
                    current?.copy(queue = current.queue.filter { it.track.id != trackId })
                }

                broadcastPlayback(
                    action = "REMOVE_QUEUE",
                    track = track,
                    positionMs = 0L
                )
            }

            "PREVIOUS" -> {
                skipPrevious()
            }

            "NEXT" -> {
                skipTrack()
            }
        }
    }

    private fun applySyncedPlayback(
        track: SieloTrack?,
        positionMs: Long,
        isPlaying: Boolean,
        startAtTimestampMs: Long? = null
    ) {
        if (track == null) return

        scope.launch(Dispatchers.Main) {
            isSuppressingLocalPlayerSync = true
            try {
                val currentLocal = playerManager.playbackState.value
                val isSameTrack = currentLocal.currentTrack?.id == track.id
                val scheduledStart = startAtTimestampMs?.takeIf { it > System.currentTimeMillis() }

                if (!isSameTrack) {
                    // Stop the previous song first. This prevents the old audio from
                    // continuing while the new room track is being prepared.
                    if (currentLocal.isPlaying) {
                        playerManager.pause()
                    }

                    playerManager.playTrack(
                        track = track,
                        queue = _roomState.value?.queue?.map { it.track } ?: listOf(track),
                        seekToMs = positionMs,
                        autoPlay = false
                    )

                    var waitedMs = 0L
                    var loaded = false
                    while (waitedMs < 10000L) {
                        val playback = playerManager.playbackState.value
                        if (playerManager.currentlyLoadingTrackId == null &&
                            playback.currentTrack?.id == track.id) {
                            loaded = true
                            break
                        }
                        delay(50L)
                        waitedMs += 50L
                    }

                    if (!loaded) {
                        Log.w(TAG, "Timed out waiting for synced room track: ${track.id}")
                        return@launch
                    }

                    if (isPlaying) {
                        val waitMs = (scheduledStart?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) } ?: 0L)
                        delay(waitMs)
                        if (playerManager.playbackState.value.currentTrack?.id == track.id &&
                            playerManager.currentlyLoadingTrackId == null) {
                            playerManager.play()
                        }
                    }
                } else {
                    if (!currentLocal.isBuffering && playerManager.currentlyLoadingTrackId == null) {
                        val currentPos = currentLocal.currentPositionMs
                        val absDiff = abs(positionMs - currentPos)

                        if (absDiff >= HARD_SYNC_DRIFT_MS && scheduledStart == null) {
                            playerManager.seekTo(positionMs)
                            lastHardSyncAtMs = System.currentTimeMillis()
                        }
                    }

                    if (scheduledStart != null && isPlaying) {
                        if (currentLocal.isPlaying) playerManager.pause()
                        val waitMs = (scheduledStart - System.currentTimeMillis()).coerceAtLeast(0L)
                        delay(waitMs)
                        if (playerManager.playbackState.value.currentTrack?.id == track.id) {
                            playerManager.play()
                        }
                    } else if (isPlaying && !currentLocal.isPlaying) {
                        playerManager.play()
                    } else if (!isPlaying && currentLocal.isPlaying) {
                        playerManager.pause()
                    }
                }
            } finally {
                isSuppressingLocalPlayerSync = false
            }
        }
    }

    /**
     * Send chat message into the room.
     */
    fun sendChatMessage(text: String, replyToText: String? = null, replyToSender: String? = null) {
        val state = _roomState.value ?: return
        if (text.isBlank()) return

        val msg = RoomChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = state.localUserId,
            senderName = state.localUserName,
            text = text.trim(),
            timestamp = System.currentTimeMillis(),
            isHost = state.isHost,
            replyToText = replyToText,
            replyToSender = replyToSender
        )

        // Optimistically update local chat
        _roomState.update { it?.copy(chatMessages = it.chatMessages + msg) }
        broadcastEvent(RoomEvent.Chat(msg))
        setTyping(false)
    }

    /**
     * Broadcasts typing status to the room.
     */
    fun setTyping(isTyping: Boolean) {
        val state = _roomState.value ?: return
        broadcastEvent(
            RoomEvent.Typing(
                userId = state.localUserId,
                userName = state.localUserName,
                isTyping = isTyping
            )
        )
    }

    /**
     * Adds track to room queue and broadcasts to all listeners.
     */
    fun addTrackToRoom(track: SieloTrack) {
        val state = _roomState.value ?: return
        val item = RoomQueueItem(
            track = track,
            addedBy = state.localUserName,
            addedAt = System.currentTimeMillis()
        )
        val newQueue = state.queue + item
        val systemMsg = RoomChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "system",
            senderName = "System",
            text = "${state.localUserName} added \"${track.title}\" to the queue",
            timestamp = System.currentTimeMillis(),
            isSystemEvent = true
        )
        val user = state.localUserName
        val curCount = state.addedSongsCount[user] ?: 0
        val updatedCountMap = state.addedSongsCount + (user to (curCount + 1))
        _roomState.update { it?.copy(
            queue = newQueue,
            chatMessages = it.chatMessages + systemMsg,
            addedSongsCount = updatedCountMap
        ) }
        broadcastEvent(RoomEvent.AddToQueue(item))

        // If nothing is playing, play immediately
        if (state.currentTrack == null) {
            playRoomTrack(track)
        }
    }

    /**
     * Play/Pause control.
     *
     * Host: executes locally and broadcasts the authoritative state.
     * Member: sends a request to the host and waits for the host's authoritative event.
     */
    fun togglePlayPause() {
        val state = _roomState.value ?: return
        val playback = playerManager.playbackState.value
        val track = playback.currentTrack ?: state.currentTrack
        if (track == null) return

        if (!state.isHost) {
            // The host is authoritative. Send the member request without changing
            // the local player first; the host response is then applied with the
            // host timestamp, giving every device one common playback timeline.
            sendMemberPlaybackRequest(
                action = if (playback.isPlaying) "PAUSE" else "PLAY",
                track = track,
                positionMs = playback.currentPositionMs
            )
            return
        }

        val newPlaying = !playback.isPlaying

        scope.launch(Dispatchers.Main) {
            isSuppressingLocalPlayerSync = true
            suppressHostPlaybackObserverUntilMs = System.currentTimeMillis() + 750L
            try {
                if (newPlaying) {
                    playerManager.play()
                } else {
                    playerManager.pause()
                }

                // Give PlayerManager a moment to publish the new playback state.
                // The event still carries the exact command state, so it does not
                // depend on an asynchronous StateFlow update being immediate.
                delay(40L)
                val authoritative = playerManager.playbackState.value
                val authoritativePosition = authoritative.currentPositionMs
                val timestamp = System.currentTimeMillis()

                _roomState.update {
                    it?.copy(
                        currentTrack = authoritative.currentTrack ?: track,
                        isPlaying = newPlaying,
                        currentPositionMs = authoritativePosition
                    )
                }

                broadcastPlayback(
                    action = if (newPlaying) "PLAY" else "PAUSE",
                    track = authoritative.currentTrack ?: track,
                    positionMs = authoritativePosition,
                    timestampEpochMs = timestamp
                )
            } finally {
                isSuppressingLocalPlayerSync = false
            }
        }
    }

    /**
     * Synchronized seek across the room.
     */
    fun seekTo(positionMs: Long) {
        val state = _roomState.value ?: return
        val safePosition = positionMs.coerceAtLeast(0L)

        if (!state.isHost) {
            // Optimistic seek gives the member immediate UI/audio feedback.
            _roomState.update {
                it?.copy(currentPositionMs = safePosition)
            }

            scope.launch(Dispatchers.Main) {
                isSuppressingLocalPlayerSync = true
                try {
                    playerManager.seekTo(safePosition)
                } finally {
                    isSuppressingLocalPlayerSync = false
                }
            }

            // Host will broadcast the authoritative seek position.
            sendMemberPlaybackRequest(
                action = "SEEK",
                track = state.currentTrack,
                positionMs = safePosition
            )
            return
        }

        _roomState.update { it?.copy(currentPositionMs = safePosition) }

        scope.launch(Dispatchers.Main) {
            isSuppressingLocalPlayerSync = true
            try {
                playerManager.seekTo(safePosition)

                val authoritativePosition = playerManager.playbackState.value.currentPositionMs
                broadcastPlayback(
                    action = "SEEK",
                    track = state.currentTrack,
                    positionMs = authoritativePosition,
                    timestampEpochMs = System.currentTimeMillis()
                )
            } finally {
                isSuppressingLocalPlayerSync = false
            }
        }
    }

    /**
     * Removes a non-current track from the shared room queue. Members request the
     * host; the host updates the queue and broadcasts the authoritative result.
     */
    fun removeTrackFromRoom(track: SieloTrack) {
        val state = _roomState.value ?: return
        if (track.id == state.currentTrack?.id) return

        if (!state.isHost) {
            sendMemberPlaybackRequest(
                action = "REMOVE_QUEUE",
                track = track,
                positionMs = 0L
            )
            return
        }

        val exists = state.queue.any { it.track.id == track.id }
        if (!exists) return

        _roomState.update { current ->
            current?.copy(queue = current.queue.filter { it.track.id != track.id })
        }

        broadcastPlayback(
            action = "REMOVE_QUEUE",
            track = track,
            positionMs = 0L
        )
    }

    /**
     * Previous-track control. Members request the host to perform the action.
     */
    fun skipPrevious() {
        val state = _roomState.value ?: return

        if (!state.isHost) {
            sendMemberPlaybackRequest(
                action = "PREVIOUS",
                track = state.currentTrack,
                positionMs = playerManager.playbackState.value.currentPositionMs
            )
            return
        }

        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val previousTrack = if (currentIndex > 0) {
            currentQueue[currentIndex - 1].track
        } else {
            // Standard previous-button behavior at the beginning: restart current track.
            state.currentTrack ?: currentQueue.firstOrNull()?.track
        } ?: return

        playRoomTrack(previousTrack)
    }

    private fun performSkipPrevious() {
        val state = _roomState.value ?: return
        if (!state.isHost) return

        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val previousTrack = if (currentIndex > 0) {
            currentQueue[currentIndex - 1].track
        } else {
            state.currentTrack ?: currentQueue.firstOrNull()?.track
        } ?: return

        playRoomTrack(previousTrack)
    }

    /**
     * Next-track control. Members request the host to perform the action.
     */
    fun skipTrack() {
        val state = _roomState.value ?: return

        if (!state.isHost) {
            sendMemberPlaybackRequest(
                action = "NEXT",
                track = state.currentTrack,
                positionMs = playerManager.playbackState.value.currentPositionMs
            )
            return
        }

        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val nextTrack = if (currentIndex >= 0 && currentIndex + 1 in currentQueue.indices) {
            currentQueue[currentIndex + 1].track
        } else {
            currentQueue.firstOrNull()?.track
        } ?: return

        playRoomTrack(nextTrack)
    }

    private fun performSkipNext() {
        val state = _roomState.value ?: return
        if (!state.isHost) return

        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val nextTrack = if (currentIndex >= 0 && currentIndex + 1 in currentQueue.indices) {
            currentQueue[currentIndex + 1].track
        } else {
            currentQueue.firstOrNull()?.track
        } ?: return

        playRoomTrack(nextTrack)
    }

    /**
     * Starts a specific room track. Members send a PLAY request to the host; only
     * the host starts the actual audio and broadcasts the synchronized start.
     */
    fun playRoomTrack(track: SieloTrack) {
        val state = _roomState.value ?: return

        if (!state.isHost) {
            sendMemberPlaybackRequest(
                action = "PLAY",
                track = track,
                positionMs = 0L
            )
            return
        }

        _roomState.update {
            it?.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L
            )
        }

        scope.launch(Dispatchers.Main) {
            isSuppressingLocalPlayerSync = true
            suppressHostPlaybackObserverUntilMs = System.currentTimeMillis() + 12000L
            try {
                // Stop the old audio before replacing the active track. PlayerManager
                // loads the new track asynchronously, so calling play() immediately
                // after playTrack() can otherwise resume the previous song.
                if (playerManager.playbackState.value.currentTrack?.id != track.id) {
                    playerManager.pause()
                }

                playerManager.playTrack(
                    track = track,
                    queue = state.queue.map { it.track },
                    seekToMs = 0L,
                    autoPlay = false
                )

                // Do not start the old player while the new track is still loading.
                // Wait until PlayerManager confirms that this exact track is active.
                var waitedMs = 0L
                var loaded = false
                while (waitedMs < 10000L) {
                    val playback = playerManager.playbackState.value
                    if (playerManager.currentlyLoadingTrackId == null &&
                        playback.currentTrack?.id == track.id) {
                        loaded = true
                        break
                    }
                    delay(50L)
                    waitedMs += 50L
                }

                if (!loaded) {
                    Log.w(TAG, "Timed out waiting for room track to load: ${track.id}")
                    return@launch
                }

                val authoritativeStartAt = System.currentTimeMillis() + TRACK_START_LEAD_MS

                // Broadcast only after the host has the correct track loaded.
                broadcastPlayback(
                    action = "PLAY",
                    track = track,
                    positionMs = 0L,
                    timestampEpochMs = authoritativeStartAt
                )

                val waitMs = (authoritativeStartAt - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(waitMs)

                // Re-check the active track before starting. Never call play() on the
                // previous song if PlayerManager changed during loading.
                if (playerManager.playbackState.value.currentTrack?.id == track.id &&
                    playerManager.currentlyLoadingTrackId == null) {
                    playerManager.play()
                }
            } finally {
                isSuppressingLocalPlayerSync = false
            }
        }
    }

    /**
     * Sends a playback control request from a member to the host.
     *
     * sequenceNumber=0 marks this as a request, not an authoritative event.
     * For PLAY/PAUSE/SEEK the member may already have applied the command locally;
     * the host response is still authoritative and reconciles every device.
     */
    private fun sendMemberPlaybackRequest(
        action: String,
        track: SieloTrack?,
        positionMs: Long
    ) {
        val state = _roomState.value ?: return
        if (state.isHost) return

        val request = RoomEvent.PlaybackAction(
            action = action,
            track = track,
            positionMs = positionMs,
            timestampEpochMs = System.currentTimeMillis(),
            triggeredBy = state.localUserName,
            sequenceNumber = 0L
        )

        broadcastEvent(request)
    }

    private fun broadcastPlayback(
        action: String,
        track: SieloTrack?,
        positionMs: Long,
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        val state = _roomState.value ?: return
        if (!state.isHost) return
        val sequence = ++hostSequenceNumber
        val event = RoomEvent.PlaybackAction(
            action = action,
            track = track,
            positionMs = positionMs,
            timestampEpochMs = timestampEpochMs,
            triggeredBy = state.localUserName,
            sequenceNumber = sequence
        )
        broadcastEvent(event)
    }

    private fun broadcastSyncState() {
        val state = _roomState.value ?: return
        if (!state.isHost) return

        val playback = playerManager.playbackState.value
        val currentTrack = playback.currentTrack ?: state.currentTrack
        val isPlaying = playback.isPlaying
        val positionMs = playback.currentPositionMs
        val timestamp = System.currentTimeMillis()
        val sequence = ++hostSequenceNumber

        _roomState.update {
            it?.copy(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = positionMs
            )
        }

        val event = RoomEvent.SyncState(
            hostId = state.localUserId,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            positionMs = positionMs,
            timestampEpochMs = timestamp,
            queue = state.queue,
            participants = state.participants,
            sequenceNumber = sequence
        )
        broadcastEvent(event)
    }

    private fun broadcastEvent(event: RoomEvent) {
        val client = mqttClient ?: return
        val topic = currentTopic ?: return
        val roomKey = _roomState.value?.roomKey ?: return

        scope.launch {
            try {
                val jsonString = json.encodeToString(event)
                val encrypted = RoomCrypto.encrypt(jsonString, roomKey)
                val qos = when (event) {
                    is RoomEvent.PlaybackAction, is RoomEvent.SyncState, is RoomEvent.RequestSync -> 1
                    else -> 0
                }
                val message = MqttMessage(encrypted.toByteArray(Charsets.UTF_8)).apply { this.qos = qos }
                client.publish(topic, message)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting event", e)
            }
        }
    }

    /**
     * Transfers hostship to the selected participant and leaves the room.
     */
    fun transferHostAndLeave(newHostId: String) {
        val state = _roomState.value ?: return
        val newHost = state.participants.find { it.id == newHostId }
        if (newHost != null) {
            val transferEvent = RoomEvent.TransferHost(
                newHostId = newHost.id,
                newHostName = newHost.name,
                previousHostName = state.localUserName
            )
            broadcastEvent(transferEvent)
            val chatMsg = RoomChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "system",
                senderName = "System",
                text = "👑 ${state.localUserName} transferred host to ${newHost.name}",
                timestamp = System.currentTimeMillis(),
                isHost = false,
                isSystemEvent = true
            )
            broadcastEvent(RoomEvent.Chat(chatMsg))
        }
        leaveRoom(isIntentional = true)
    }

    /**
     * Leaves and cleans up the active room.
     *
     * If the host is the only participant and intentionally leaves, keep the
     * room alive for a 10-second grace period. This gives the room an actual
     * expiry window even though MQTT has no server-side room database. If a
     * participant joins during that window, the shutdown is cancelled.
     */
    fun leaveRoom(isIntentional: Boolean = true) {
        val state = _roomState.value
        if (isIntentional && state != null && state.isHost && state.participants.size == 1) {
            if (roomShutdownJob?.isActive == true) return

            Log.d(TAG, "Host is the only participant. Scheduling room shutdown in 10s.")
            roomShutdownJob = scope.launch {
                delay(EMPTY_ROOM_GRACE_PERIOD_MS)

                val current = _roomState.value
                val stillOnlyHost = current != null &&
                        current.isHost &&
                        current.participants.size == 1 &&
                        current.participants.any { it.id == current.localUserId && it.isHost }

                if (stillOnlyHost) {
                    Log.d(TAG, "10s host-only grace period expired. Closing room ${current.roomId}.")
                    roomShutdownJob = null
                    leaveRoomNow(isIntentional = true)
                } else {
                    Log.d(TAG, "Room shutdown cancelled because another participant is present.")
                    roomShutdownJob = null
                }
            }
            return
        }

        leaveRoomNow(isIntentional)
    }

    /** Performs the actual immediate cleanup. */
    private fun leaveRoomNow(isIntentional: Boolean = true) {
        if (isIntentional) {
            markSessionIntentionalExit()
        }
        val state = _roomState.value
        val client = mqttClient
        val topic = currentTopic
        val key = state?.roomKey

        if (state != null && client != null && topic != null && key != null) {
            try {
                val leaveEvent = RoomEvent.Leave(state.localUserId, state.localUserName)
                val jsonString = json.encodeToString(leaveEvent)
                val encrypted = RoomCrypto.encrypt(jsonString, key)
                val message = MqttMessage(encrypted.toByteArray(Charsets.UTF_8)).apply { qos = 1 }
                client.publish(topic, message)

                val systemMsg = RoomChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "system",
                    senderName = "System",
                    text = "🚪 ${state.localUserName} left the room",
                    timestamp = System.currentTimeMillis(),
                    isSystemEvent = true
                )
                val chatEvent = RoomEvent.Chat(systemMsg)
                val chatJson = json.encodeToString(chatEvent)
                val chatEncrypted = RoomCrypto.encrypt(chatJson, key)
                client.publish(topic, MqttMessage(chatEncrypted.toByteArray(Charsets.UTF_8)).apply { qos = 1 })
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing leave packet", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            delay(500)
            try {
                client?.disconnect()
                client?.close()
            } catch (_: Exception) {}
        }

        mqttClient = null
        currentTopic = null
        roomShutdownJob?.cancel()
        roomShutdownJob = null
        hostPresenceJob?.cancel()
        hostPresenceJob = null
        hostHeartbeatJob?.cancel()
        hostHeartbeatJob = null
        stopClientDriftMonitor()
        emptyRoomTimerJob?.cancel()
        emptyRoomTimerJob = null
        rejoinValidationJob?.cancel()
        rejoinValidationJob = null
        lastHostPacketTimeMs = 0L
        hasSubscribedSuccessfully = false
        hostSequenceNumber = 0L
        lastAppliedSequenceNumber = 0L
        pendingSyncEvent = null
        lastAuthoritativeHostTimestampMs = 0L
        lastAuthoritativePositionMs = 0L
        lastAuthoritativeIsPlaying = false
        lastAuthoritativeTrackId = null
        lastHardSyncAtMs = 0L
        suppressHostPlaybackObserverUntilMs = 0L
        typingTimeoutJobs.values.forEach { it.cancel() }
        typingTimeoutJobs.clear()
        _roomState.value = null
    }

    private fun generateRoomId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun getInviteLink(state: ActiveRoomState): String {
        return "https://vineetchudasama.github.io/Sielo/room/?id=${state.roomId}&key=${state.roomKey}"
    }
}
