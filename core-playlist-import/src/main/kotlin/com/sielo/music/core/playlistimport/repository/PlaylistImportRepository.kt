package com.sielo.music.core.playlistimport.repository

import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlistimport.model.ImportResult
import com.sielo.music.core.playlistimport.model.ImportSource
import com.sielo.music.core.playlistimport.model.ImportedTrackCandidate
import com.sielo.music.core.playlistimport.spotify.ManualPasteParser
import com.sielo.music.core.playlistimport.youtube.YouTubePlaylistApiService
import com.sielo.music.core.playlistimport.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistImportRepository @Inject constructor(
    private val youtubeApi: YouTubePlaylistApiService,
    private val innerTubeClient: InnerTubeClient
) {
    suspend fun importPlaylist(
        source: ImportSource,
        sourceIdentifier: String,
        albumName: String,
        playlistCreator: PlaylistCreator,
        onProgress: (Int, Int) -> Unit
    ): ImportResult {
        if (source == ImportSource.YOUTUBE) {
            val playlistId = extractYouTubePlaylistId(sourceIdentifier)
                ?: throw Exception("Invalid YouTube playlist URL or ID.")

            // 1. Fetch direct YouTube playlist tracks via InnerTube (no API key required)
            val directTracks = innerTubeClient.getYouTubePlaylistSongs(playlistId)
            if (directTracks.isNotEmpty()) {
                onProgress(directTracks.size, directTracks.size)
                val albumId = playlistCreator.createPlaylist(albumName, directTracks)
                return ImportResult(
                    albumId = albumId,
                    totalCandidates = directTracks.size,
                    importedCount = directTracks.size,
                    skippedCount = 0
                )
            }

            // 2. Fallback to API if a real YOUTUBE_API_KEY is configured
            val apiKey = BuildConfig.YOUTUBE_API_KEY.trim()
            if (apiKey.isNotBlank() && !apiKey.contains("YOUR_API_KEY") && apiKey != "\"\"") {
                val candidates = fetchCandidatesFromYouTubeApi(playlistId)
                if (candidates.isNotEmpty()) {
                    return resolveCandidatesAndCreatePlaylist(candidates, albumName, playlistCreator, onProgress)
                }
            }

            throw Exception("Could not fetch YouTube/YouTube Music playlist tracks. Ensure the playlist is Public/Unlisted and accessible, or try copying the playlist link directly from YouTube Music.")
        }

        // For Spotify or manual text paste
        val candidates = fetchCandidates(source, sourceIdentifier)
        if (candidates.isEmpty()) {
            throw Exception("No valid tracks found in the provided source.")
        }

        return resolveCandidatesAndCreatePlaylist(candidates, albumName, playlistCreator, onProgress)
    }

    private suspend fun resolveCandidatesAndCreatePlaylist(
        candidates: List<ImportedTrackCandidate>,
        albumName: String,
        playlistCreator: PlaylistCreator,
        onProgress: (Int, Int) -> Unit
    ): ImportResult {
        val resolvedTracks = mutableListOf<SieloTrack>()
        val unimportedTracks = mutableListOf<ImportedTrackCandidate>()
        var progress = 0

        for (candidate in candidates) {
            val query = if (candidate.artistGuess.isNotBlank()) {
                "${candidate.title} ${candidate.artistGuess}"
            } else {
                candidate.title
            }

            try {
                val results = innerTubeClient.search(query)
                val isRequestedCover = com.sielo.music.core.network.innertube.TrackMatchValidator.isCoverOrKaraokeTrack(
                    candidate.title,
                    candidate.artistGuess
                )

                // 1. If user did NOT ask for a cover/karaoke, strictly prefer non-cover tracks
                // where the artist matches the requested artist.
                val match = if (!isRequestedCover) {
                    results.firstOrNull { res ->
                        !com.sielo.music.core.network.innertube.TrackMatchValidator.isCoverOrKaraokeTrack(res.title, res.artist) &&
                        com.sielo.music.core.network.innertube.TrackMatchValidator.isSongByOrFeaturingArtist(res.title, res.artist, candidate.artistGuess) &&
                        com.sielo.music.core.network.innertube.TrackMatchValidator.isFuzzyMatch(candidate.title, res.title, candidate.artistGuess, res.artist)
                    } ?: results.firstOrNull { res ->
                        !com.sielo.music.core.network.innertube.TrackMatchValidator.isCoverOrKaraokeTrack(res.title, res.artist) &&
                        com.sielo.music.core.network.innertube.TrackMatchValidator.isSongByOrFeaturingArtist(res.title, res.artist, candidate.artistGuess)
                    } ?: results.firstOrNull { res ->
                        !com.sielo.music.core.network.innertube.TrackMatchValidator.isCoverOrKaraokeTrack(res.title, res.artist) &&
                        com.sielo.music.core.network.innertube.TrackMatchValidator.isFuzzyMatch(candidate.title, res.title)
                    } ?: results.firstOrNull { res ->
                        !com.sielo.music.core.network.innertube.TrackMatchValidator.isCoverOrKaraokeTrack(res.title, res.artist)
                    } ?: results.firstOrNull()
                } else {
                    results.firstOrNull()
                }

                if (match != null) {
                    resolvedTracks.add(match)
                } else {
                    unimportedTracks.add(candidate)
                }
            } catch (e: Exception) {
                unimportedTracks.add(candidate)
            }

            progress++
            onProgress(progress, candidates.size)
        }

        if (resolvedTracks.isEmpty()) {
            throw Exception("Could not resolve any tracks on Sielo from the imported source.")
        }

        val albumId = playlistCreator.createPlaylist(albumName, resolvedTracks)

        return ImportResult(
            albumId = albumId,
            totalCandidates = candidates.size,
            importedCount = resolvedTracks.size,
            skippedCount = unimportedTracks.size,
            unimportedTracks = unimportedTracks
        )
    }

    private suspend fun fetchCandidates(source: ImportSource, sourceIdentifier: String): List<ImportedTrackCandidate> {
        return when (source) {
            ImportSource.SPOTIFY_MANUAL_PASTE, ImportSource.SPOTIFY_OAUTH -> {
                ManualPasteParser.parseAsync(sourceIdentifier)
            }
            ImportSource.YOUTUBE -> {
                val playlistId = extractYouTubePlaylistId(sourceIdentifier) ?: return emptyList()
                fetchCandidatesFromYouTubeApi(playlistId)
            }
        }
    }

    private suspend fun fetchCandidatesFromYouTubeApi(playlistId: String): List<ImportedTrackCandidate> {
        val candidates = mutableListOf<ImportedTrackCandidate>()
        var pageToken: String? = null

        do {
            try {
                val response = youtubeApi.getPlaylistItems(
                    playlistId = playlistId,
                    pageToken = pageToken,
                    key = BuildConfig.YOUTUBE_API_KEY
                )
                for (item in response.items) {
                    if (item.snippet.title != "Private video" && item.snippet.title != "Deleted video") {
                        val artist = item.snippet.videoOwnerChannelTitle.replace(" - Topic", "")
                        candidates.add(
                            ImportedTrackCandidate(
                                title = item.snippet.title,
                                artistGuess = artist,
                                sourcePlatform = "YouTube"
                            )
                        )
                    }
                }
                pageToken = response.nextPageToken
            } catch (e: Exception) {
                break
            }
        } while (pageToken != null)

        return candidates
    }

    private fun extractYouTubePlaylistId(url: String): String? {
        val trimmed = url.trim()
        // 1. Direct query parameter: ?list=... or &list=... (works for youtube.com, music.youtube.com, etc.)
        val listRegex = Regex("[?&]list=([^#\\&\\?]+)")
        val match = listRegex.find(trimmed)?.groupValues?.get(1)?.trim()
        if (!match.isNullOrBlank()) {
            return match
        }

        // 2. Direct path-based playlist/album ID: e.g. /playlist?list=... or /browse/VL... or /browse/MPREb_...
        val browseRegex = Regex("""/(?:browse|album|playlist)/([a-zA-Z0-9_\-]+)""")
        val browseMatch = browseRegex.find(trimmed)?.groupValues?.get(1)?.trim()
        if (!browseMatch.isNullOrBlank() && (browseMatch.startsWith("PL") || browseMatch.startsWith("VL") || browseMatch.startsWith("OLAK") || browseMatch.startsWith("MPREb_") || browseMatch.length in 12..60)) {
            return browseMatch
        }

        // 3. Raw IDs passed without URL scheme (e.g., PL..., VL..., OLAK5uy_..., MPREb_...)
        if (!trimmed.contains("http://") && !trimmed.contains("https://")) {
            val cleanCandidate = trimmed.substringAfter("list=").substringBefore("&").trim()
            if (cleanCandidate.startsWith("PL") ||
                cleanCandidate.startsWith("VL") ||
                cleanCandidate.startsWith("OLAK") ||
                cleanCandidate.startsWith("MPREb_") ||
                cleanCandidate.startsWith("RD") ||
                cleanCandidate.length in 12..60
            ) {
                return cleanCandidate
            }
        }
        return null
    }
}
