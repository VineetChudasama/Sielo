package com.sielo.music.core.network.metadata

import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack

object MusicBrainzMapper {

    /**
     * Determines the ReleaseType category from a MusicBrainz release group.
     */
    fun categorizeReleaseGroup(rg: MbReleaseGroup): ReleaseType {
        val primary = rg.primaryType?.trim()?.lowercase() ?: ""
        val secondaries = rg.secondaryTypes.map { it.trim().lowercase() }
        val titleLower = rg.title.trim().lowercase()

        // 1. Soundtrack takes precedence
        if (secondaries.contains("soundtrack") || primary == "soundtrack" ||
            titleLower.contains("soundtrack") || titleLower.contains("original motion picture") ||
            titleLower.contains("(ost)") || titleLower.contains(" ost") || titleLower.contains("from \"") ||
            titleLower.contains("from '")
        ) {
            return ReleaseType.SOUNDTRACK
        }

        // 2. Compilations / DJ-mixes
        if (secondaries.contains("compilation") || secondaries.contains("mixtape/street") ||
            secondaries.contains("dj-mix")
        ) {
            return ReleaseType.COMPILATION
        }

        // 3. Primary types
        return when (primary) {
            "single" -> ReleaseType.SINGLE
            "ep" -> ReleaseType.EP
            "album" -> ReleaseType.ALBUM
            else -> ReleaseType.UNKNOWN
        }
    }

    /**
     * Formats artist credits array into a human-readable display string.
     * E.g. [{"name": "Pritam", "joinphrase": " & "}, {"name": "Arijit Singh", "joinphrase": ""}] -> "Pritam & Arijit Singh"
     */
    fun formatArtistCredit(credits: List<MbArtistCredit>, fallback: String = "Various Artists"): String {
        if (credits.isEmpty()) return fallback
        val sb = StringBuilder()
        for (c in credits) {
            val name = c.name?.trim() ?: c.artist?.name?.trim() ?: ""
            val join = c.joinphrase ?: ""
            sb.append(name).append(join)
        }
        val result = sb.toString().trim()
        return if (result.isNotBlank()) result else fallback
    }

    /**
     * Extracts a 4-digit year string from a date string (e.g. "2024-05-19" -> "2024").
     */
    fun extractYear(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val match = Regex("""\b(19\d\d|20\d\d)\b""").find(dateStr)
        return match?.value
    }

    /**
     * Maps a MusicBrainz release group to a SieloAlbum.
     */
    fun toSieloAlbum(
        rg: MbReleaseGroup,
        coverUrls: CoverArtUrls? = null,
        fallbackArtistName: String? = null,
        fallbackArtworkUrl: String? = null,
        tracks: List<SieloTrack> = emptyList(),
        explicitTrackCount: Int? = null
    ): SieloAlbum {
        val category = categorizeReleaseGroup(rg)
        val artistDisplay = formatArtistCredit(rg.artistCredit, fallbackArtistName ?: "Various Artists")
        val yearStr = extractYear(rg.firstReleaseDate)

        val typeString = when (category) {
            ReleaseType.ALBUM -> "Album"
            ReleaseType.EP -> "EP"
            ReleaseType.SINGLE -> "Single"
            ReleaseType.SOUNDTRACK -> "Soundtrack"
            ReleaseType.COMPILATION -> "Compilation"
            ReleaseType.UNKNOWN -> "Album"
        }

        val artwork = coverUrls?.medium ?: coverUrls?.large ?: coverUrls?.thumbnail ?: fallbackArtworkUrl
        val calculatedCount = when {
            tracks.isNotEmpty() -> tracks.size
            explicitTrackCount != null && explicitTrackCount > 0 -> explicitTrackCount
            category == ReleaseType.SINGLE -> 1
            else -> 0
        }

        return SieloAlbum(
            id = rg.id,
            title = rg.title,
            artist = artistDisplay,
            year = yearStr,
            thumbnailUrl = artwork,
            tracks = tracks,
            songCount = calculatedCount,
            type = typeString,
            releaseDate = rg.firstReleaseDate,
            musicBrainzId = rg.id,
            releaseType = category.name
        )
    }

    /**
     * Maps an MbTrack to a SieloTrack.
     */
    fun toSieloTrack(
        track: MbTrack,
        albumTitle: String,
        albumId: String,
        albumArtworkUrl: String? = null
    ): SieloTrack {
        val artistDisplay = formatArtistCredit(track.artistCredit)
        val durMs = track.length ?: track.recording?.length ?: 0L
        val durSec = durMs / 1000L
        val formattedDur = if (durSec > 0) {
            val mins = durSec / 60
            val secs = durSec % 60
            "$mins:${secs.toString().padStart(2, '0')}"
        } else null

        val recId = track.recording?.id ?: track.id
        val isrc = track.recording?.isrcs?.firstOrNull()

        return SieloTrack(
            id = "mb_rec_$recId",
            title = track.title,
            artist = artistDisplay,
            album = albumTitle,
            durationText = formattedDur,
            durationSeconds = durSec,
            thumbnailUrl = albumArtworkUrl,
            musicBrainzRecordingId = recId,
            isrc = isrc,
            albumId = albumId
        )
    }

    /**
     * Maps an MbArtist to SieloArtist.
     */
    fun toSieloArtist(
        artist: MbArtist,
        imageUrl: String? = null
    ): SieloArtist {
        return SieloArtist(
            id = artist.id,
            name = artist.name,
            imageUrl = imageUrl,
            role = artist.type ?: "Artist"
        )
    }

    /**
     * Filters tracks of a release/soundtrack to return only those on which the target artist is credited.
     */
    fun filterTracksForArtist(tracks: List<SieloTrack>, artistName: String): List<SieloTrack> {
        val cleanTarget = artistName.trim().lowercase()
        return tracks.filter { track ->
            val trackArtist = track.artist.lowercase()
            trackArtist.contains(cleanTarget)
        }
    }
}
