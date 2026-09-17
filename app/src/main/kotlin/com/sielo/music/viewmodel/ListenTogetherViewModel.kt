package com.sielo.music.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.room.manager.ListenTogetherManager
import com.sielo.music.room.model.ActiveRoomState
import com.sielo.music.room.model.SavedRoomSession
import com.sielo.music.room.qr.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roomManager: ListenTogetherManager,
    private val innerTubeClient: InnerTubeClient
) : ViewModel() {

    val roomState: StateFlow<ActiveRoomState?> = roomManager.roomState
    val roomNotification: StateFlow<String?> = roomManager.roomNotification
    val pendingRejoinSession: StateFlow<SavedRoomSession?> = roomManager.pendingRejoinSession

    fun checkPendingRejoinSession() {
        roomManager.checkPendingRejoinSession()
    }

    fun rejoinPreviousRoom() {
        roomManager.rejoinPreviousRoom()
    }

    fun dismissRejoinPrompt() {
        roomManager.dismissRejoinPrompt()
    }

    fun clearRoomNotification() {
        roomManager.clearRoomNotification()
    }

    /**
     * Validates and extracts Room ID and Room Key from scanned QR text.
     * Returns null if the scanned code is not of a Sielo room.
     */
    fun parseRoomQr(rawResult: String): Pair<String, String>? {
        val trimmed = rawResult.trim()
        var parsedRoomId = ""
        var parsedKey = ""

        if (trimmed.contains("room/") || trimmed.contains("sielo://")) {
            try {
                val afterRoom = trimmed.substringAfter("room/").substringBefore("?")
                parsedRoomId = afterRoom.substringBefore("#")
                if (trimmed.contains("#key=")) {
                    parsedKey = trimmed.substringAfter("#key=").substringBefore("&")
                } else if (trimmed.contains("key=")) {
                    parsedKey = trimmed.substringAfter("key=").substringBefore("&")
                }
            } catch (_: Exception) {}
        } else {
            parsedRoomId = trimmed
        }

        val roomPattern = Regex("(?i)^(SL-[A-Z0-9]{4}-[A-Z0-9]{4}|[A-Z0-9]{6}|[A-Z0-9]{4,12})$")
        if (parsedRoomId.matches(roomPattern)) {
            val upperId = parsedRoomId.uppercase()
            if (parsedKey.isBlank()) {
                parsedKey = com.sielo.music.room.crypto.RoomCrypto.deriveKey(upperId)
            }
            return Pair(upperId, parsedKey)
        }
        return null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SieloTrack>>(emptyList())
    val searchResults: StateFlow<List<SieloTrack>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null
    private var typingJob: Job? = null

    companion object {
        private const val PREFS_NAME = "sielo_room_prefs"
        private const val KEY_SAVED_USERNAME = "saved_username"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedUserName(): String {
        return prefs.getString(KEY_SAVED_USERNAME, null) ?: "User_${(100..999).random()}"
    }

    fun saveUserName(name: String) {
        if (name.isNotBlank()) {
            prefs.edit().putString(KEY_SAVED_USERNAME, name.trim()).apply()
        }
    }

    fun createRoom(hostName: String) {
        saveUserName(hostName)
        roomManager.createRoom(hostName)
    }

    /**
     * Parses room input: either a raw room ID + key or a deep link like:
     * sielo://room/SL-ABCD-1234#key=BASE64_KEY
     * or SL-ABCD-1234
     */
    fun joinRoom(input: String, keyInput: String, userName: String): Boolean {
        saveUserName(userName)
        val trimmed = input.trim()

        var resolvedRoomId = ""
        var resolvedKey = keyInput.trim()

        if (trimmed.contains("room/") || trimmed.contains("sielo://")) {
            try {
                val afterRoom = trimmed.substringAfter("room/").substringBefore("?")
                resolvedRoomId = afterRoom.substringBefore("#")

                if (trimmed.contains("#key=")) {
                    resolvedKey = trimmed.substringAfter("#key=").substringBefore("&")
                } else if (trimmed.contains("key=")) {
                    resolvedKey = trimmed.substringAfter("key=").substringBefore("&")
                }
            } catch (_: Exception) {}
        } else {
            resolvedRoomId = trimmed
        }

        if (resolvedKey.isBlank() && resolvedRoomId.isNotBlank()) {
            resolvedKey = com.sielo.music.room.crypto.RoomCrypto.deriveKey(resolvedRoomId)
        }

        if (resolvedRoomId.isBlank()) {
            return false
        }

        roomManager.joinRoom(resolvedRoomId, resolvedKey, userName)
        return true
    }

    fun skipPrevious() {
        roomManager.skipPrevious()
    }

    fun leaveRoom() {
        roomManager.leaveRoom()
    }

    fun sendChatMessage(text: String, replyToText: String? = null, replyToSender: String? = null) {
        typingJob?.cancel()
        roomManager.sendChatMessage(text, replyToText, replyToSender)
    }

    fun onChatInputChanged(text: String) {
        if (text.isNotBlank()) {
            roomManager.setTyping(true)
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(2500)
                roomManager.setTyping(false)
            }
        } else {
            roomManager.setTyping(false)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            _isSearching.value = true
            try {
                val tracks = innerTubeClient.search(query.trim())
                _searchResults.value = com.sielo.music.core.network.innertube.TrackMatchValidator.deduplicateTracks(tracks).distinctBy { it.id }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addTrackToRoom(track: SieloTrack) {
        roomManager.addTrackToRoom(track)
    }

    fun togglePlayPause() {
        roomManager.togglePlayPause()
    }

    fun seekTo(posMs: Long) {
        roomManager.seekTo(posMs)
    }

    fun skipTrack() {
        roomManager.skipTrack()
    }

    fun getInviteLink(state: ActiveRoomState): String {
        return roomManager.getInviteLink(state)
    }

    fun shareRoomInvite(context: Context, state: ActiveRoomState) {
        val link = getInviteLink(state)
        val shareText = "Join my synchronized Listen Together room on Sielo!\n\nRoom ID: ${state.roomId}\nTap to join: $link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join my Sielo Listen Together room")
            putExtra(Intent.EXTRA_TEXT, shareText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Share Room Invite").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
