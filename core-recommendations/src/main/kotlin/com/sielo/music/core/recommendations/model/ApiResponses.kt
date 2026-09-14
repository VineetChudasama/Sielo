package com.sielo.music.core.recommendations.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Last.fm Models ---

@Serializable
data class LastFmImage(
    @SerialName("#text") val url: String? = null,
    val size: String? = null
)

@Serializable
data class LastFmSimilarArtistDto(
    val name: String,
    val mbid: String? = null,
    val match: String? = null,
    val url: String? = null,
    val image: List<LastFmImage>? = null
)

@Serializable
data class LastFmSimilarArtistsContainer(
    val artist: List<LastFmSimilarArtistDto> = emptyList()
)

@Serializable
data class LastFmSimilarArtistsResponse(
    val similarartists: LastFmSimilarArtistsContainer? = null
)

@Serializable
data class LastFmTagDto(
    val name: String,
    val count: Int? = null,
    val url: String? = null
)

@Serializable
data class LastFmTopTagsContainer(
    val tag: List<LastFmTagDto> = emptyList()
)

@Serializable
data class LastFmTopTagsResponse(
    val toptags: LastFmTopTagsContainer? = null
)

@Serializable
data class LastFmTopArtistDto(
    val name: String,
    val mbid: String? = null,
    val url: String? = null,
    val image: List<LastFmImage>? = null
)

@Serializable
data class LastFmTagTopArtistsContainer(
    val artist: List<LastFmTopArtistDto> = emptyList()
)

@Serializable
data class LastFmTagTopArtistsResponse(
    val topartists: LastFmTagTopArtistsContainer? = null
)

@Serializable
data class LastFmTopTrackArtistDto(
    val name: String,
    val mbid: String? = null,
    val url: String? = null
)

@Serializable
data class LastFmTopTrackDto(
    val name: String,
    val playcount: String? = null,
    val listeners: String? = null,
    val mbid: String? = null,
    val url: String? = null,
    val artist: LastFmTopTrackArtistDto? = null,
    val image: List<LastFmImage>? = null
)

@Serializable
data class LastFmTopTracksContainer(
    val track: List<LastFmTopTrackDto> = emptyList()
)

@Serializable
data class LastFmTopTracksResponse(
    val toptracks: LastFmTopTracksContainer? = null
)

// --- MusicBrainz Models ---

@Serializable
data class MusicBrainzArea(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort-name") val sortName: String? = null,
    @SerialName("iso-3166-1-codes") val isoCodes: List<String>? = null
)

@Serializable
data class MusicBrainzArtistDto(
    val id: String,
    val name: String? = null,
    val country: String? = null,
    val area: MusicBrainzArea? = null,
    @SerialName("begin-area") val beginArea: MusicBrainzArea? = null
)

@Serializable
data class MusicBrainzSearchResponse(
    val count: Int? = null,
    val artists: List<MusicBrainzArtistDto> = emptyList()
)
