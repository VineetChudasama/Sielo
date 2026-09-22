package com.sielo.music.core.playlist

import android.content.Context
import android.content.SharedPreferences
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UserPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<SieloTrack> = emptyList()
)

@Singleton
class UserPlaylistsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sielo_user_playlists", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(loadPlaylists())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private fun loadPlaylists(): List<UserPlaylist> {
        val saved = prefs.getString("user_playlists_json", null)
        if (!saved.isNullOrBlank()) {
            try {
                return json.decodeFromString<List<UserPlaylist>>(saved)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultList = defaultPlaylists()
        savePlaylistsInternal(defaultList)
        return defaultList
    }

    private fun savePlaylistsInternal(list: List<UserPlaylist>) {
        try {
            val encoded = json.encodeToString(list)
            prefs.edit().putString("user_playlists_json", encoded).apply()
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPlaylistWithTracks(title: String, tracks: List<com.sielo.music.core.network.models.SieloTrack>): UserPlaylist {
        val newPlaylist = UserPlaylist(
            id = java.util.UUID.randomUUID().toString(),
            title = title.trim(),
            description = "",
            coverUrl = tracks.firstOrNull()?.thumbnailUrl,
            createdAt = System.currentTimeMillis(),
            tracks = tracks
        )
        val updated = listOf(newPlaylist) + _playlists.value
        savePlaylistsInternal(updated)
        return newPlaylist
    }

    fun createPlaylist(title: String, description: String = "", coverUrl: String? = null): UserPlaylist {
        val newPlaylist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            coverUrl = coverUrl,
            createdAt = System.currentTimeMillis(),
            tracks = emptyList()
        )
        val updated = listOf(newPlaylist) + _playlists.value
        savePlaylistsInternal(updated)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filter { it.id != playlistId }
        savePlaylistsInternal(updated)
    }

    fun updatePlaylistName(playlistId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(title = cleanName) else pl
        }
        savePlaylistsInternal(updated)
    }

    fun addTrackToPlaylist(playlistId: String, track: SieloTrack) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.tracks.any { it.id == track.id }) pl
                else pl.copy(
                    tracks = pl.tracks + track,
                    coverUrl = pl.coverUrl ?: track.thumbnailUrl
                )
            } else pl
        }
        savePlaylistsInternal(updated)
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(tracks = pl.tracks.filter { it.id != trackId })
            } else pl
        }
        savePlaylistsInternal(updated)
    }

    private fun defaultPlaylists(): List<UserPlaylist> {
        return listOf(
            UserPlaylist(
                id = "sielo_starter_1",
                title = "Late Night Session",
                description = "Deep ambient, electronic & midnight focus mixtapes",
                coverUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg",
                createdAt = System.currentTimeMillis() - 86400000L * 5,
                tracks = listOf(
                    SieloTrack(id = "fW-Mxsnu", title = "Blinding Lights", artist = "The Weeknd", durationText = "3:20", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
                    SieloTrack(id = "TcDP-KUl", title = "Starboy", artist = "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg"),
                    SieloTrack(id = "3IoDK8qI", title = "Levitating", artist = "Dua Lipa", durationText = "3:23", thumbnailUrl = "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg")
                )
            ),
            UserPlaylist(
                id = "sielo_starter_2",
                title = "Workout Anthems",
                description = "High energy EDM, fast-paced rhythms & peak adrenaline",
                coverUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg",
                createdAt = System.currentTimeMillis() - 86400000L * 12,
                tracks = listOf(
                    SieloTrack(id = "workout_1", title = "Save Your Tears", artist = "The Weeknd", durationText = "3:35", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
                    SieloTrack(id = "workout_2", title = "Physical", artist = "Dua Lipa", durationText = "3:13", thumbnailUrl = "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg")
                )
            ),
            UserPlaylist(
                id = "sielo_starter_3",
                title = "Bollywood Acoustic & Soul",
                description = "Timeless melodies, soulful acoustic ballads & golden hits",
                coverUrl = "https://c.saavncdn.com/artists/Arijit_Singh_002_20240417064843_500x500.jpg",
                createdAt = System.currentTimeMillis() - 86400000L * 25,
                tracks = listOf(
                    SieloTrack(id = "BddP6PYo2gs", title = "Kesariya", artist = "Pritam & Arijit Singh", durationText = "4:28", thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
                    SieloTrack(id = "anuv_1", title = "Baarishein", artist = "Anuv Jain", durationText = "3:27", thumbnailUrl = "https://c.saavncdn.com/artists/Anuv_Jain_000_20230628084833_500x500.jpg")
                )
            )
        )
    }
}

