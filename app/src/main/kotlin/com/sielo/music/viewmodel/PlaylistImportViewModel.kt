package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylistsRepository
import com.sielo.music.core.playlistimport.model.ImportSource
import com.sielo.music.core.playlistimport.repository.PlaylistCreator
import com.sielo.music.core.playlistimport.repository.PlaylistImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PlaylistImportState {
    object Idle : PlaylistImportState()
    data class FetchingMetadata(val source: String) : PlaylistImportState()
    data class Resolving(val progress: Int, val total: Int) : PlaylistImportState()
    data class Success(val albumId: String, val imported: Int, val skipped: Int) : PlaylistImportState()
    data class Error(val message: String) : PlaylistImportState()
}

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val importRepository: PlaylistImportRepository,
    private val userPlaylistsRepository: UserPlaylistsRepository
) : ViewModel(), PlaylistCreator {

    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    override suspend fun createPlaylist(name: String, tracks: List<SieloTrack>): String {
        return userPlaylistsRepository.createPlaylistWithTracks(name, tracks).id
    }

    fun resetState() {
        _importState.value = PlaylistImportState.Idle
    }

    fun importFromYouTube(playlistUrl: String, albumName: String) {
        startImport(ImportSource.YOUTUBE, playlistUrl, albumName)
    }

    fun importFromSpotifyOAuth(playlistId: String, albumName: String) {
        startImport(ImportSource.SPOTIFY_OAUTH, playlistId, albumName)
    }

    fun importFromPastedText(text: String, albumName: String) {
        startImport(ImportSource.SPOTIFY_MANUAL_PASTE, text, albumName)
    }

    private fun startImport(source: ImportSource, identifier: String, albumName: String) {
        if (identifier.isBlank()) {
            _importState.value = PlaylistImportState.Error("Import source cannot be empty")
            return
        }

        _importState.value = PlaylistImportState.FetchingMetadata(source.name)
        viewModelScope.launch {
            try {
                val result = importRepository.importPlaylist(
                    source = source,
                    sourceIdentifier = identifier,
                    albumName = albumName,
                    playlistCreator = this@PlaylistImportViewModel,
                    onProgress = { current, total ->
                        _importState.value = PlaylistImportState.Resolving(current, total)
                    }
                )
                _importState.value = PlaylistImportState.Success(
                    albumId = result.albumId,
                    imported = result.importedCount,
                    skipped = result.skippedCount
                )
            } catch (e: Exception) {
                _importState.value = PlaylistImportState.Error(e.message ?: "An unknown error occurred during import")
            }
        }
    }
}
