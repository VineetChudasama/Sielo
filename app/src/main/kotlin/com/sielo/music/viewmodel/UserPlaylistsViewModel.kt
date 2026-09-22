package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylist
import com.sielo.music.core.playlist.UserPlaylistsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPlaylistsViewModel @Inject constructor(
    private val playlistsRepository: UserPlaylistsRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val playlists: StateFlow<List<UserPlaylist>> = playlistsRepository.playlists

    private val _selectedPlaylist = MutableStateFlow<UserPlaylist?>(null)
    val selectedPlaylist: StateFlow<UserPlaylist?> = _selectedPlaylist.asStateFlow()

    fun createPlaylist(title: String, description: String = "") {
        val created = playlistsRepository.createPlaylist(title, description)
    }

    fun deletePlaylist(playlistId: String) {
        if (_selectedPlaylist.value?.id == playlistId) {
            _selectedPlaylist.value = null
        }
        playlistsRepository.deletePlaylist(playlistId)
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        playlistsRepository.updatePlaylistName(playlistId, cleanName)
        val current = _selectedPlaylist.value
        if (current?.id == playlistId) {
            _selectedPlaylist.value = current.copy(title = cleanName)
        }
    }

    fun openPlaylist(playlist: UserPlaylist) {
        _selectedPlaylist.value = playlist
    }

    fun closePlaylist() {
        _selectedPlaylist.value = null
    }

    fun playPlaylist(playlist: UserPlaylist) {
        if (playlist.tracks.isNotEmpty()) {
            playerManager.playTrack(playlist.tracks.first(), playlist.tracks)
        }
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) {
        playerManager.playTrack(track, queue)
    }

    fun removeTrack(playlistId: String, trackId: String) {
        playlistsRepository.removeTrackFromPlaylist(playlistId, trackId)
        val current = _selectedPlaylist.value
        if (current?.id == playlistId) {
            _selectedPlaylist.value = current.copy(
                tracks = current.tracks.filter { it.id != trackId }
            )
        }
    }
}
