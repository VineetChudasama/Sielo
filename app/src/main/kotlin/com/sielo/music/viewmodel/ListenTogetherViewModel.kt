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

    fun extractRoomCredentials(input: String, explicitKey: String = ""): Pair<String, String>? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        var parsedId = ""
        var parsedKey = explicitKey.trim()

        // 1. Extract from key query param or hash if present
        if (parsedKey.isBlank()) {
            if (trimmed.contains("#key=")) {
                parsedKey = trimmed.substringAfter("#key=").substringBefore("&").substringBefore(" ").substringBefore("\n").trim()
            } else if (trimmed.contains("key=")) {
                parsedKey = trimmed.substringAfter("key=").substringBefore("&").substringBefore(" ").substringBefore("\n").trim()
            }
        }

        // 2. Look for room URL pattern: sielo.app/room/XXXXXX or sielo://room/XXXXXX
        if (trimmed.contains("room/")) {
            val afterRoom = trimmed.substringAfter("room/")
            val candidate = afterRoom.substringBefore("?").substringBefore("#").substringBefore("/").substringBefore(" ").substringBefore("\n").trim()
            if (candidate.isNotBlank()) {
                parsedId = candidate
            }
        }

        if (parsedId.isBlank() && trimmed.contains("id=")) {
            val afterId = trimmed.substringAfter("id=")
            val candidate = afterId.substringBefore("&").substringBefore("#").substringBefore(" ").substringBefore("\n").trim()
            if (candidate.isNotBlank()) {
                parsedId = candidate
            }
        }
        if (parsedId.isBlank() && trimmed.contains("room=")) {
            val afterRoom = trimmed.substringAfter("room=")
            val candidate = afterRoom.substringBefore("&").substringBefore("#").substringBefore(" ").substringBefore("\n").trim()
            if (candidate.isNotBlank()) {
                parsedId = candidate
            }
        }

        // 3. Look for "Room Code: XXXXXX" in copied text
        if (parsedId.isBlank() && trimmed.contains("Room Code:", ignoreCase = true)) {
            val afterCode = trimmed.substringAfter("Room Code:").trim()
            val candidate = afterCode.substringBefore("\n").substringBefore(" ").trim()
            if (candidate.isNotBlank()) {
                parsedId = candidate
            }
        }

        // 4. If direct room ID pattern
        if (parsedId.isBlank()) {
            val regex = Regex("(?i)(SL-[A-Z0-9]{4}-[A-Z0-9]{4}|[A-Z0-9]{6})")
            val match = regex.find(trimmed)
            if (match != null) {
                parsedId = match.value
            } else {
                parsedId = trimmed.take(12)
            }
        }

        parsedId = parsedId.replace(Regex("[^A-Za-z0-9-]"), "").trim().uppercase()
        if (parsedId.length < 4) return null

        if (parsedKey.isBlank()) {
            parsedKey = com.sielo.music.room.crypto.RoomCrypto.deriveKey(parsedId)
        }

        return Pair(parsedId, parsedKey)
    }

    /**
     * Validates and extracts Room ID and Room Key from scanned QR text.
     * Returns null if the scanned code is not of a Sielo room.
     */
    fun parseRoomQr(rawResult: String): Pair<String, String>? {
        return extractRoomCredentials(rawResult)
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
     * Parses room input: either a raw room ID + key or a deep link / full message.
     */
    fun joinRoom(input: String, keyInput: String, userName: String): Boolean {
        saveUserName(userName)
        val creds = extractRoomCredentials(input, keyInput) ?: return false
        roomManager.joinRoom(creds.first, creds.second, userName)
        return true
    }

    fun skipPrevious() {
        roomManager.skipPrevious()
    }

    fun leaveRoom() {
        roomManager.leaveRoom()
    }

    fun transferHostAndLeave(newHostId: String) {
        roomManager.transferHostAndLeave(newHostId)
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

    fun removeTrackFromRoom(track: SieloTrack) {
        roomManager.removeTrackFromRoom(track)
    }

    fun getInviteLink(state: ActiveRoomState): String {
        return roomManager.getInviteLink(state)
    }

    fun shareRoomInvite(context: Context, state: ActiveRoomState) {
        val appLink = "sielo://room/${state.roomId}?key=${state.roomKey}"
        val webLink = getInviteLink(state)
        val shareText = "🎧 Join my Sielo Listen Together room!\n\nRoom Code: ${state.roomId}\n\nTap to join in Sielo:\n$appLink\n\nWeb Link:\n$webLink\n\n(Or open Sielo -> Listen Together -> tap Join!)"
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

    fun shareRoomQrWithImage(context: Context, state: ActiveRoomState) {
        val appLink = "sielo://room/${state.roomId}?key=${state.roomKey}"
        val webLink = getInviteLink(state)
        val shareText = "🎧 Scan or tap to join my Sielo Listen Together room!\n\nRoom Code: ${state.roomId}\n\nOpen in Sielo:\n$appLink\n\nWeb Link:\n$webLink\n\n(Or open Sielo -> Listen Together -> tap Join!)"
        try {
            val qrBitmap = com.sielo.music.room.qr.QrCodeGenerator.generateQrBitmap(webLink, context, sizePx = 600)
            val sharedDir = java.io.File(context.cacheDir, "shared_qr").apply { if (!exists()) mkdirs() }
            val qrFile = java.io.File(sharedDir, "sielo_room_${state.roomId}.png")
            java.io.FileOutputStream(qrFile).use { out ->
                qrBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                qrFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Join my Sielo Listen Together room")
                putExtra(Intent.EXTRA_TEXT, shareText)
                clipData = android.content.ClipData.newRawUri("Sielo QR Code", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share Room QR").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("ListenTogetherVM", "Failed to share QR image", e)
            shareRoomInvite(context, state)
        }
    }
}
