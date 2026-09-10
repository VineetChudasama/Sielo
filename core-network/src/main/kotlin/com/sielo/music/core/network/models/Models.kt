package com.sielo.music.core.network.models

import kotlinx.serialization.Serializable

@Serializable
data class SieloTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null
)

@Serializable
data class SearchResult(
    val tracks: List<SieloTrack> = emptyList(),
    val albums: List<SieloAlbum> = emptyList()
)

@Serializable
data class SieloAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class SieloArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val role: String? = "Artist"
)

@Serializable
data class ArtistDetails(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val bio: String? = null,
    val latestAlbum: SieloAlbum? = null,
    val topSongs: List<SieloTrack> = emptyList()
)
