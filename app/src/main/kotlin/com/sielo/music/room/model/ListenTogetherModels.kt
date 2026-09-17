package com.sielo.music.room.model

import com.sielo.music.core.network.models.SieloTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomParticipant(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val joinedAt: Long = System.currentTimeMillis()
)

@Serializable
data class RoomChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isHost: Boolean = false,
    val isSystemEvent: Boolean = false,
    val replyToText: String? = null,
    val replyToSender: String? = null
)

@Serializable
data class RoomQueueItem(
    val track: SieloTrack,
    val addedBy: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
sealed class RoomEvent {
    @Serializable
    @SerialName("join")
    data class Join(
        val participant: RoomParticipant
    ) : RoomEvent()

    @Serializable
    @SerialName("leave")
    data class Leave(
        val participantId: String,
        val participantName: String
    ) : RoomEvent()

    @Serializable
    @SerialName("sync_state")
    data class SyncState(
        val hostId: String,
        val currentTrack: SieloTrack?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val timestampEpochMs: Long,
        val queue: List<RoomQueueItem>,
        val participants: List<RoomParticipant>
    ) : RoomEvent()

    @Serializable
    @SerialName("request_sync")
    data class RequestSync(
        val requesterId: String
    ) : RoomEvent()

    @Serializable
    @SerialName("playback")
    data class PlaybackAction(
        val action: String, // "PLAY", "PAUSE", "SEEK"
        val track: SieloTrack? = null,
        val positionMs: Long = 0L,
        val timestampEpochMs: Long = System.currentTimeMillis(),
        val triggeredBy: String
    ) : RoomEvent()

    @Serializable
    @SerialName("queue_add")
    data class AddToQueue(
        val item: RoomQueueItem
    ) : RoomEvent()

    @Serializable
    @SerialName("queue_skip")
    data class SkipTrack(
        val triggeredBy: String
    ) : RoomEvent()

    @Serializable
    @SerialName("chat")
    data class Chat(
        val message: RoomChatMessage
    ) : RoomEvent()

    @Serializable
    @SerialName("typing")
    data class Typing(
        val userId: String,
        val userName: String,
        val isTyping: Boolean
    ) : RoomEvent()
}

data class ActiveRoomState(
    val roomId: String = "",
    val roomKey: String = "",
    val localUserId: String = "",
    val localUserName: String = "",
    val isHost: Boolean = false,
    val participants: List<RoomParticipant> = emptyList(),
    val currentTrack: SieloTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val queue: List<RoomQueueItem> = emptyList(),
    val chatMessages: List<RoomChatMessage> = emptyList(),
    val typingUsers: Set<String> = emptySet(),
    val isConnected: Boolean = false,
    val connectionStatus: String = "Connecting...",
    val addedSongsCount: Map<String, Int> = emptyMap()
)

@Serializable
data class SavedRoomSession(
    val roomId: String,
    val roomKey: String,
    val userName: String,
    val isHost: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
