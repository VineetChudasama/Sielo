package com.sielo.music.viewmodel

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylist
import com.sielo.music.core.playlist.UserPlaylistsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val playlistsRepository: UserPlaylistsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val playlists: StateFlow<List<UserPlaylist>> = playlistsRepository.playlists
    val favorites: Flow<List<FavoriteTrackEntity>> = favoriteTrackDao.getAllFavorites()

    fun isFavorite(trackId: String): Flow<Boolean> = favoriteTrackDao.isFavorite(trackId)

    fun playNow(track: SieloTrack) {
        playerManager.playTrack(track, listOf(track))
    }

    fun playNext(track: SieloTrack) {
        playerManager.playNext(track)
        Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: SieloTrack) {
        playerManager.appendToQueue(track)
        Toast.makeText(context, "Added to queue: ${track.title}", Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(track: SieloTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isFav = favoriteTrackDao.isFavorite(track.id).first()
                if (isFav) {
                    favoriteTrackDao.deleteById(track.id)
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Removed from Liked Songs", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    favoriteTrackDao.insertFavorite(
                        FavoriteTrackEntity(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            thumbnailUrl = track.thumbnailUrl
                        )
                    )
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Added to Liked Songs", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addToPlaylist(playlistId: String, track: SieloTrack, playlistTitle: String = "") {
        playlistsRepository.addTrackToPlaylist(playlistId, track)
        val msg = if (playlistTitle.isNotBlank()) "Added to $playlistTitle" else "Added to playlist"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun createPlaylistAndAdd(title: String, track: SieloTrack): UserPlaylist {
        val newPlaylist = playlistsRepository.createPlaylist(
            title = title.trim(),
            description = "Custom playlist",
            coverUrl = track.thumbnailUrl
        )
        playlistsRepository.addTrackToPlaylist(newPlaylist.id, track)
        Toast.makeText(context, "Created & added to ${newPlaylist.title}", Toast.LENGTH_SHORT).show()
        return newPlaylist
    }

    fun saveAlbumAsPlaylist(album: SieloAlbum, tracks: List<SieloTrack>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pl = playlistsRepository.createPlaylist(
                title = album.title,
                description = "Album by ${album.artist}",
                coverUrl = album.thumbnailUrl
            )
            tracks.forEach { tr ->
                playlistsRepository.addTrackToPlaylist(pl.id, tr)
            }
            launch(Dispatchers.Main) {
                Toast.makeText(context, "Album saved to your Playlists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareTrack(track: SieloTrack) {
        try {
            val shareText = "Listen to \"${track.title}\" by ${track.artist} on Sielo:\nsielo://track/${track.id}"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_TITLE, track.title)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share \"${track.title}\"").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't share this song", Toast.LENGTH_SHORT).show()
        }
    }
}
