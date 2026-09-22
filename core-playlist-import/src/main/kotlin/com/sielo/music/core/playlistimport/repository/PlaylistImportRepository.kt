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

            // 2. Fallback to API if YOUTUBE_API_KEY is configured
            if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                val candidates = fetchCandidatesFromYouTubeApi(playlistId)
                if (candidates.isNotEmpty()) {
                    return resolveCandidatesAndCreatePlaylist(candidates, albumName, playlistCreator, onProgress)
                }
            }

            throw Exception("Could not fetch YouTube playlist tracks. Make sure the playlist is Public or Unlisted.")
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
        var progress = 0

        for (candidate in candidates) {
            val query = if (candidate.artistGuess.isNotBlank()) {
                "${candidate.title} ${candidate.artistGuess}"
            } else {
                candidate.title
            }

            try {
                val results = innerTubeClient.search(query)
                val topMatch = results.firstOrNull()
                if (topMatch != null) {
                    resolvedTracks.add(topMatch)
                }
            } catch (e: Exception) {
                // Silently skip if search fails
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
            skippedCount = candidates.size - resolvedTracks.size
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
        val regex = Regex("[?&]list=([^#\\&\\?]+)")
        val match = regex.find(trimmed)?.groupValues?.get(1)
        if (!match.isNullOrBlank()) return match
        if (!trimmed.contains("http") && (trimmed.startsWith("PL") || trimmed.startsWith("VL") || trimmed.length in 12..50)) {
            return trimmed
        }
        return null
    }
}
