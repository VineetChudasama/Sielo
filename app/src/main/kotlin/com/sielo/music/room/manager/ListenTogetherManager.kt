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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ListenTogether"
private const val PRIMARY_BROKER = "tcp://broker.hivemq.com:1883"
private const val BACKUP_BROKER = "tcp://broker.emqx.io:1883"
private const val SESSION_PREFS = "sielo_room_session_prefs"

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
    private var mqttClient: MqttAsyncClient? = null
    private var currentTopic: String? = null
    private var typingTimeoutJobs = mutableMapOf<String, Job>()
    private var isSuppressingLocalPlayerSync = false

    init {
        checkPendingRejoinSession()
        // Observe local player to sync to room if local user is host
        scope.launch {
            playerManager.playbackState.collect { playback ->
                val state = _roomState.value
                if (state != null && state.isHost && !isSuppressingLocalPlayerSync) {
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
                }
            }
        }
    }

    fun checkPendingRejoinSession() {
        try {
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

            // Prompt if session is active (abrupt exit) and within 12 hours
            val isRecent = (System.currentTimeMillis() - timestamp) < 12 * 60 * 60 * 1000L
            if (!roomId.isNullOrBlank() && !roomKey.isNullOrBlank() && !wasIntentionalExit && isRecent) {
                _pendingRejoinSession.value = SavedRoomSession(
                    roomId = roomId,
                    roomKey = roomKey,
                    userName = userName ?: "User",
                    isHost = isHost,
                    timestamp = timestamp
                )
            } else {
                _pendingRejoinSession.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending rejoin session", e)
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
            .putBoolean("was_intentional_exit", true)
            .apply()
        _pendingRejoinSession.value = null
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

    /**
     * Creates a new private encrypted room.
     */
    fun createRoom(hostName: String): ActiveRoomState {
        leaveRoom(isIntentional = false) // Cleanup any previous room
        val roomId = generateRoomId()
        val roomKey = RoomCrypto.deriveKey(roomId)
        val localUserId = UUID.randomUUID().toString().take(8)
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
        val localUserId = UUID.randomUUID().toString().take(8)
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
        val localUserId = UUID.randomUUID().toString().take(8)
        val participant = RoomParticipant(
            id = localUserId,
            name = participantName.ifBlank { "Listener" },
            isHost = false
        )

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

    private fun startHostPresenceWatchdog() {
        hostPresenceJob?.cancel()
        hostPresenceJob = scope.launch {
            delay(10000)
            val state = _roomState.value
            if (state != null && !state.isHost) {
                val hasHost = state.participants.any { it.isHost }
                if (!hasHost) {
                    _roomState.update { it?.copy(connectionStatus = "Waiting for host to reconnect...") }
                }
            }
        }
    }

    private fun connectToBroker(roomId: String, roomKey: String, localParticipant: RoomParticipant) {
        val topic = "sielo/v3/rooms/$roomId"
        currentTopic = topic
        val clientId = "SieloClient_${localParticipant.id}_${System.currentTimeMillis() % 10000}"

        try {
            val client = MqttAsyncClient(PRIMARY_BROKER, clientId, MemoryPersistence())
            mqttClient = client

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = true
            }

            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "Connected to room broker: $serverURI (reconnect=$reconnect)")
                    _roomState.update { it?.copy(isConnected = true, connectionStatus = "Connected") }
                    client.subscribe(topic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(TAG, "Subscribed to $topic")
                            broadcastEvent(RoomEvent.Join(localParticipant))
                            if (localParticipant.isHost) {
                                broadcastSyncState()
                            } else {
                                broadcastEvent(RoomEvent.RequestSync(localParticipant.id))
                            }
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Subscription failure", exception)
                        }
                    })
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
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Initial connect failed, trying backup broker", exception)
                    connectBackupBroker(roomId, roomKey, localParticipant)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT error", e)
            _roomState.update { it?.copy(connectionStatus = "Connection Error: ${e.localizedMessage}") }
        }
    }

    private fun connectBackupBroker(roomId: String, roomKey: String, localParticipant: RoomParticipant) {
        val topic = "sielo/v3/rooms/$roomId"
        currentTopic = topic
        val clientId = "SieloBackup_${localParticipant.id}_${System.currentTimeMillis() % 10000}"
        try {
            val client = MqttAsyncClient(BACKUP_BROKER, clientId, MemoryPersistence())
            mqttClient = client
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 12
                keepAliveInterval = 20
                isAutomaticReconnect = true
            }
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    client.subscribe(topic, 1)
                    _roomState.update { it?.copy(isConnected = true, connectionStatus = "Connected") }
                    broadcastEvent(RoomEvent.Join(localParticipant))
                    if (localParticipant.isHost) {
                        broadcastSyncState()
                    } else {
                        broadcastEvent(RoomEvent.RequestSync(localParticipant.id))
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _roomState.update { it?.copy(connectionStatus = "Failed to connect to room server") }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Backup broker connect failed", e)
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
                    if (event.participant.isHost) {
                        hostPresenceJob?.cancel()
                    }
                    val updatedParticipants = (state.participants.filter { it.id != event.participant.id } + event.participant)
                    _roomState.update {
                        it?.copy(
                            participants = updatedParticipants,
                            connectionStatus = if (event.participant.isHost) "Connected (Host online)" else it.connectionStatus
                        )
                    }
                    // Host replies with current sync state
                    if (state.isHost) {
                        broadcastSyncState()
                    }
                }
            }

            is RoomEvent.Leave -> {
                val leavingParticipant = state.participants.find { it.id == event.participantId }
                val wasHost = leavingParticipant?.isHost == true || event.participantName.contains("Host", ignoreCase = true)
                val updated = state.participants.filter { it.id != event.participantId }
                val systemMsg = RoomChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "system",
                    senderName = "System",
                    text = "${event.participantName} ${if (wasHost) "disconnected (Host)" else "left the room"}",
                    timestamp = System.currentTimeMillis(),
                    isSystemEvent = true
                )
                _roomState.update { current ->
                    current?.copy(
                        participants = updated,
                        chatMessages = current.chatMessages + systemMsg,
                        connectionStatus = if (wasHost && !current.isHost) "Host disconnected. Waiting for host to reconnect..." else current.connectionStatus
                    )
                }
            }

            is RoomEvent.RequestSync -> {
                if (state.isHost) {
                    broadcastSyncState()
                }
            }

            is RoomEvent.SyncState -> {
                hostPresenceJob?.cancel()
                if (!state.isHost) {
                    val drift = (System.currentTimeMillis() - event.timestampEpochMs).coerceAtLeast(0L)
                    val syncedPos = event.positionMs + if (event.isPlaying) drift else 0L

                    _roomState.update {
                        it?.copy(
                            participants = event.participants,
                            currentTrack = event.currentTrack,
                            isPlaying = event.isPlaying,
                            currentPositionMs = syncedPos,
                            queue = event.queue
                        )
                    }

                    // Align local player
                    applySyncedPlayback(event.currentTrack, syncedPos, event.isPlaying)
                }
            }

            is RoomEvent.PlaybackAction -> {
                val drift = (System.currentTimeMillis() - event.timestampEpochMs).coerceAtLeast(0L)
                val targetPos = event.positionMs + if (event.action == "PLAY") drift else 0L

                when (event.action) {
                    "PLAY" -> {
                        _roomState.update { it?.copy(currentTrack = event.track ?: it.currentTrack, isPlaying = true, currentPositionMs = targetPos) }
                        applySyncedPlayback(event.track ?: state.currentTrack, targetPos, true)
                    }
                    "PAUSE" -> {
                        _roomState.update { it?.copy(isPlaying = false, currentPositionMs = event.positionMs) }
                        applySyncedPlayback(state.currentTrack, event.positionMs, false)
                    }
                    "SEEK" -> {
                        _roomState.update { it?.copy(currentPositionMs = event.positionMs) }
                        playerManager.seekTo(event.positionMs)
                    }
                    "PREVIOUS" -> {
                        performSkipPrevious()
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
                performSkipNext()
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

    private fun applySyncedPlayback(track: SieloTrack?, positionMs: Long, isPlaying: Boolean) {
        if (track == null) return
        scope.launch(Dispatchers.Main) {
            isSuppressingLocalPlayerSync = true
            try {
                val currentLocal = playerManager.playbackState.value
                val isSameTrack = currentLocal.currentTrack?.id == track.id
                if (!isSameTrack) {
                    playerManager.playTrack(track, _roomState.value?.queue?.map { it.track } ?: listOf(track), seekToMs = positionMs)
                    if (!isPlaying) {
                        playerManager.togglePlayPause()
                    }
                } else {
                    val posDiff = kotlin.math.abs(currentLocal.currentPositionMs - positionMs)
                    if (posDiff > 1500) {
                        playerManager.seekTo(positionMs)
                    }
                    if (currentLocal.isPlaying != isPlaying) {
                        playerManager.togglePlayPause()
                    }
                }
            } finally {
                delay(300)
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
     * Play/Pause toggle synchronized across the room.
     */
    fun togglePlayPause() {
        val state = _roomState.value ?: return
        val newPlaying = !state.isPlaying
        _roomState.update { it?.copy(isPlaying = newPlaying) }
        broadcastPlayback(
            action = if (newPlaying) "PLAY" else "PAUSE",
            track = state.currentTrack,
            positionMs = state.currentPositionMs
        )
        scope.launch(Dispatchers.Main) {
            playerManager.togglePlayPause()
        }
    }

    /**
     * Synchronized seek across the room.
     */
    fun seekTo(positionMs: Long) {
        val state = _roomState.value ?: return
        _roomState.update { it?.copy(currentPositionMs = positionMs) }
        broadcastPlayback(action = "SEEK", track = state.currentTrack, positionMs = positionMs)
        playerManager.seekTo(positionMs)
    }

    /**
     * Skip to previous song in the room queue.
     */
    fun skipPrevious() {
        val state = _roomState.value ?: return
        broadcastPlayback(action = "PREVIOUS", track = null, positionMs = 0L)
        performSkipPrevious()
    }

    private fun performSkipPrevious() {
        val state = _roomState.value ?: return
        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val prevIndex = currentIndex - 1
        if (prevIndex in currentQueue.indices) {
            playRoomTrack(currentQueue[prevIndex].track)
        } else {
            state.currentTrack?.let { playRoomTrack(it) }
        }
    }

    /**
     * Skip to next song in the room queue.
     */
    fun skipTrack() {
        val state = _roomState.value ?: return
        broadcastEvent(RoomEvent.SkipTrack(triggeredBy = state.localUserName))
        performSkipNext()
    }

    private fun performSkipNext() {
        val state = _roomState.value ?: return
        val currentQueue = state.queue
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.track.id == state.currentTrack?.id }
        val nextIndex = currentIndex + 1
        if (nextIndex in currentQueue.indices) {
            val nextTrack = currentQueue[nextIndex].track
            playRoomTrack(nextTrack)
        } else {
            // Reached end of queue; loop or keep last
            if (currentQueue.isNotEmpty()) {
                playRoomTrack(currentQueue.first().track)
            }
        }
    }

    fun playRoomTrack(track: SieloTrack) {
        val state = _roomState.value ?: return
        _roomState.update {
            it?.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L
            )
        }
        broadcastPlayback(action = "PLAY", track = track, positionMs = 0L)
        scope.launch(Dispatchers.Main) {
            playerManager.playTrack(track, state.queue.map { it.track }, seekToMs = 0L)
        }
    }

    private fun broadcastPlayback(action: String, track: SieloTrack?, positionMs: Long) {
        val state = _roomState.value ?: return
        val event = RoomEvent.PlaybackAction(
            action = action,
            track = track,
            positionMs = positionMs,
            timestampEpochMs = System.currentTimeMillis(),
            triggeredBy = state.localUserName
        )
        broadcastEvent(event)
    }

    private fun broadcastSyncState() {
        val state = _roomState.value ?: return
        val event = RoomEvent.SyncState(
            hostId = state.localUserId,
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            positionMs = state.currentPositionMs,
            timestampEpochMs = System.currentTimeMillis(),
            queue = state.queue,
            participants = state.participants
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
                val message = MqttMessage(encrypted.toByteArray(Charsets.UTF_8)).apply { qos = 1 }
                client.publish(topic, message)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting event", e)
            }
        }
    }

    /**
     * Leaves and cleans up the active room.
     */
    fun leaveRoom(isIntentional: Boolean = true) {
        if (isIntentional) {
            markSessionIntentionalExit()
        }
        val state = _roomState.value
        if (state != null) {
            try {
                broadcastEvent(RoomEvent.Leave(state.localUserId, state.localUserName))
            } catch (_: Exception) {}
        }

        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (_: Exception) {}

        mqttClient = null
        currentTopic = null
        hostPresenceJob?.cancel()
        hostPresenceJob = null
        typingTimeoutJobs.values.forEach { it.cancel() }
        typingTimeoutJobs.clear()
        _roomState.value = null
    }

    private fun generateRoomId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun getInviteLink(state: ActiveRoomState): String {
        return "sielo://room/${state.roomId}#key=${state.roomKey}"
    }
}
