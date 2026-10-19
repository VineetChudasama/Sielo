package com.sielo.music.core.playlist

import android.content.Context
import android.content.SharedPreferences
import com.sielo.music.core.audio.OfflineCacheManager
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    companion object {
        const val PLAYLIST_DOWNLOADED_CACHE_ID = "sielo_downloaded_cache"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("sielo_user_playlists", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _rawPlaylists = MutableStateFlow<List<UserPlaylist>>(loadPlaylists())

    val playlists: StateFlow<List<UserPlaylist>> = combine(_rawPlaylists, OfflineCacheManager.cacheUpdated) { raw, _ ->
        val cachedTracks = OfflineCacheManager.getCachedTracks(context)
        val dynamicCachedPlaylist = UserPlaylist(
            id = PLAYLIST_DOWNLOADED_CACHE_ID,
            title = "Downloaded Cache",
            description = if (cachedTracks.isEmpty()) {
                "Offline Vault • Songs you play are cached here for offline replay"
            } else {
                "Offline Vault • ${cachedTracks.size} songs ready for offline replay"
            },
            coverUrl = cachedTracks.firstOrNull()?.thumbnailUrl
                ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            createdAt = Long.MAX_VALUE,
            tracks = cachedTracks
        )
        listOf(dynamicCachedPlaylist) + raw.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
    }.stateIn(scope, SharingStarted.Eagerly, listOf(getDynamicDownloadedPlaylist()))

    private fun getDynamicDownloadedPlaylist(): UserPlaylist {
        val cachedTracks = OfflineCacheManager.getCachedTracks(context)
        return UserPlaylist(
            id = PLAYLIST_DOWNLOADED_CACHE_ID,
            title = "Downloaded Cache",
            description = if (cachedTracks.isEmpty()) {
                "Offline Vault • Songs you play are cached here for offline replay"
            } else {
                "Offline Vault • ${cachedTracks.size} songs ready for offline replay"
            },
            coverUrl = cachedTracks.firstOrNull()?.thumbnailUrl
                ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            createdAt = Long.MAX_VALUE,
            tracks = cachedTracks
        )
    }

    private fun loadPlaylists(): List<UserPlaylist> {
        val saved = prefs.getString("user_playlists_json", null)
        if (!saved.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<UserPlaylist>>(saved)
                return list.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultList = defaultPlaylists()
        savePlaylistsInternal(defaultList)
        return defaultList
    }

    fun restorePlaylists(playlists: List<UserPlaylist>) {
        if (playlists.isEmpty()) return
        val sanitized = playlists.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
        val hasCustom = sanitized.any { !it.id.startsWith("sielo_starter_") }
        val current = if (hasCustom) {
            _rawPlaylists.value.filterNot { it.id.startsWith("sielo_starter_") || it.id == PLAYLIST_DOWNLOADED_CACHE_ID }
        } else {
            _rawPlaylists.value
        }
        val mergedMap = current.associateBy { it.id }.toMutableMap()
        for (pl in sanitized) {
            mergedMap[pl.id] = pl
        }
        val mergedList = mergedMap.values.toList()
        savePlaylistsInternal(mergedList)
    }

    fun resetToDefault() {
        val defaultList = defaultPlaylists().filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
        savePlaylistsInternal(defaultList)
    }

    fun getAllPlaylists(): List<UserPlaylist> {
        return playlists.value
    }

    private fun savePlaylistsInternal(list: List<UserPlaylist>) {
        try {
            val persistable = list.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
            val encoded = json.encodeToString(persistable)
            prefs.edit().putString("user_playlists_json", encoded).apply()
            _rawPlaylists.value = persistable
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPlaylistWithTracks(title: String, tracks: List<SieloTrack>): UserPlaylist {
        val newPlaylist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = "",
            coverUrl = tracks.firstOrNull()?.thumbnailUrl,
            createdAt = System.currentTimeMillis(),
            tracks = tracks
        )
        val updated = listOf(newPlaylist) + _rawPlaylists.value.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
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
        val updated = listOf(newPlaylist) + _rawPlaylists.value.filter { it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
        savePlaylistsInternal(updated)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        if (playlistId == PLAYLIST_DOWNLOADED_CACHE_ID) {
            OfflineCacheManager.clearCache(context)
            return
        }
        val updated = _rawPlaylists.value.filter { it.id != playlistId && it.id != PLAYLIST_DOWNLOADED_CACHE_ID }
        savePlaylistsInternal(updated)
    }

    fun updatePlaylistName(playlistId: String, newName: String) {
        if (playlistId == PLAYLIST_DOWNLOADED_CACHE_ID) return
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val updated = _rawPlaylists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(title = cleanName) else pl
        }
        savePlaylistsInternal(updated)
    }

    fun addTrackToPlaylist(playlistId: String, track: SieloTrack) {
        if (playlistId == PLAYLIST_DOWNLOADED_CACHE_ID) return
        val updated = _rawPlaylists.value.map { pl ->
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
        if (playlistId == PLAYLIST_DOWNLOADED_CACHE_ID) {
            OfflineCacheManager.removeTrack(context, trackId)
            return
        }
        val updated = _rawPlaylists.value.map { pl ->
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
