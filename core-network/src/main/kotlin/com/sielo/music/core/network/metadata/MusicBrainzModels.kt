package com.sielo.music.core.network.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard categorized release type for artist discography.
 */
enum class ReleaseType {
    ALBUM,
    EP,
    SINGLE,
    COMPILATION,
    SOUNDTRACK,
    UNKNOWN
}

/**
 * Cover Art URLs with varying resolution tiers.
 */
data class CoverArtUrls(
    val thumbnail: String? = null, // ~250px
    val medium: String? = null,    // ~500px
    val large: String? = null,     // ~1200px
    val original: String? = null
)

// ==========================================
// MusicBrainz REST API Responses
// ==========================================

@Serializable
data class MbArtistSearchResponse(
    val count: Int = 0,
    val offset: Int = 0,
    val artists: List<MbArtist> = emptyList()
)

@Serializable
data class MbArtist(
    val id: String,
    val name: String,
    @SerialName("sort-name") val sortName: String? = null,
    val type: String? = null,
    val score: Int? = null,
    val country: String? = null,
    val disambiguation: String? = null,
    val tags: List<MbTag> = emptyList()
)

@Serializable
data class MbTag(
    val count: Int = 0,
    val name: String
)

@Serializable
data class MbReleaseGroupListResponse(
    @SerialName("release-group-count") val releaseGroupCount: Int = 0,
    @SerialName("release-group-offset") val releaseGroupOffset: Int = 0,
    @SerialName("release-groups") val releaseGroups: List<MbReleaseGroup> = emptyList()
)

@Serializable
data class MbReleaseGroup(
    val id: String,
    val title: String,
    @SerialName("primary-type") val primaryType: String? = null,
    @SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    val disambiguation: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList()
)

@Serializable
data class MbArtistCredit(
    val name: String? = null,
    val joinphrase: String? = null,
    val artist: MbArtistRef? = null
)

@Serializable
data class MbArtistRef(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort-name") val sortName: String? = null
)

@Serializable
data class MbReleaseListResponse(
    @SerialName("release-count") val releaseCount: Int = 0,
    @SerialName("release-offset") val releaseOffset: Int = 0,
    val releases: List<MbRelease> = emptyList()
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String,
    val status: String? = null,
    val date: String? = null,
    val country: String? = null,
    val media: List<MbMedia> = emptyList(),
    @SerialName("release-group") val releaseGroup: MbReleaseGroup? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList()
)

@Serializable
data class MbMedia(
    val format: String? = null,
    @SerialName("track-count") val trackCount: Int = 0,
    @SerialName("track-offset") val trackOffset: Int = 0,
    val position: Int = 1,
    val tracks: List<MbTrack> = emptyList()
)

@Serializable
data class MbTrack(
    val id: String,
    val position: Int = 1,
    val number: String? = null,
    val title: String,
    val length: Long? = null, // in milliseconds
    val recording: MbRecording? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList()
)

@Serializable
data class MbRecording(
    val id: String,
    val title: String,
    val length: Long? = null,
    val isrcs: List<String> = emptyList(),
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList()
)

// ==========================================
// Cover Art Archive REST API Responses
// ==========================================

@Serializable
data class CaaReleaseGroupResponse(
    val images: List<CaaImage> = emptyList(),
    val release: String? = null
)

@Serializable
data class CaaImage(
    val id: Long? = null,
    val image: String,
    val front: Boolean = false,
    val back: Boolean = false,
    val edit: Long? = null,
    val approved: Boolean = false,
    val comment: String? = null,
    val thumbnails: CaaThumbnails? = null
)

@Serializable
data class CaaThumbnails(
    @SerialName("250") val small: String? = null,
    @SerialName("500") val medium: String? = null,
    @SerialName("1200") val large: String? = null,
    val front: String? = null,
    val back: String? = null,
    @SerialName("small") val legacySmall: String? = null,
    @SerialName("large") val legacyLarge: String? = null
)
