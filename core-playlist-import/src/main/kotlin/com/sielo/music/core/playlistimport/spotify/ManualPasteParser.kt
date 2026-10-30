package com.sielo.music.core.playlistimport.spotify

import com.sielo.music.core.playlistimport.model.ImportedTrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ManualPasteParser {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val jsonParser by lazy {
        Json { ignoreUnknownKeys = true; isLenient = true }
    }

    suspend fun parseAsync(pastedText: String): List<ImportedTrackCandidate> = withContext(Dispatchers.IO) {
        val trimmed = pastedText.trim()

        // 1. Check for Spotify Blend invite links that cannot be resolved without private user OAuth
        if ((trimmed.contains("spotify.app.link") || trimmed.contains("/blend/invite")) && !trimmed.contains("/playlist/")) {
            throw IllegalArgumentException(
                "This Spotify Blend link is a private invite link. To import this Blend into Sielo, open the playlist in your Spotify app, tap the 3 dots (...) -> Share -> Copy link to playlist (or copy the track list text), and paste it here."
            )
        }

        // 2. Check if input contains a single Spotify URL (playlist, album, track)
        val isSingleSpotifyUrl = (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("spotify:")) &&
                !trimmed.contains("\n") &&
                (trimmed.contains("spotify.com") || trimmed.contains("spotify:") || trimmed.contains("spoti.fi"))

        if (isSingleSpotifyUrl) {
            val tracks = fetchSpotifyTracksFromUrl(trimmed)
            if (tracks.isNotEmpty()) return@withContext tracks
        }

        // 3. Multiline / Plain Text fallback parsing (handles text lines and multiple track URLs)
        parsePlainText(pastedText)
    }

    fun parse(pastedText: String): List<ImportedTrackCandidate> {
        return parsePlainText(pastedText)
    }

    private fun parsePlainText(pastedText: String): List<ImportedTrackCandidate> {
        val candidates = mutableListOf<ImportedTrackCandidate>()
        val lines = pastedText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            // Check if line is a Spotify track URL
            if (line.contains("spotify.com/track/") || line.contains("spotify:track:")) {
                val track = fetchSpotifyOembed(line)
                if (track != null) {
                    candidates.add(track)
                    continue
                }
            }

            // Clean leading track numbering: e.g. "1. ", "01 - ", "1) "
            val cleanedLine = line.replaceFirst(Regex("^\\d+\\s*[.\\-\\)]\\s*"), "").trim()
            if (cleanedLine.isBlank() || cleanedLine.startsWith("http://") || cleanedLine.startsWith("https://")) {
                continue
            }

            // Split by " - " or " by "
            val hyphenParts = cleanedLine.split(Regex("\\s+-\\s+"), limit = 2)
            if (hyphenParts.size == 2) {
                candidates.add(
                    ImportedTrackCandidate(
                        title = hyphenParts[0].trim(),
                        artistGuess = hyphenParts[1].trim(),
                        sourcePlatform = "Spotify_Manual"
                    )
                )
                continue
            }

            val byParts = cleanedLine.split(Regex("\\s+by\\s+", RegexOption.IGNORE_CASE), limit = 2)
            if (byParts.size == 2) {
                candidates.add(
                    ImportedTrackCandidate(
                        title = byParts[0].trim(),
                        artistGuess = byParts[1].trim(),
                        sourcePlatform = "Spotify_Manual"
                    )
                )
                continue
            }

            candidates.add(
                ImportedTrackCandidate(
                    title = cleanedLine,
                    artistGuess = "",
                    sourcePlatform = "Spotify_Manual"
                )
            )
        }

        return candidates.filter { it.title.isNotBlank() }
    }

    private suspend fun fetchSpotifyTracksFromUrl(inputUrl: String): List<ImportedTrackCandidate> = withContext(Dispatchers.IO) {
        val playlistId = extractSpotifyId(inputUrl, "playlist")
        val albumId = extractSpotifyId(inputUrl, "album")
        val trackId = extractSpotifyId(inputUrl, "track")

        // Strategy A: Complete Spotify Web API pagination (supports 50, 100, 320+ tracks)
        if (playlistId != null) {
            val apiTracks = SpotifyPlaylistFetcher.fetchPlaylistTracksPaginated(playlistId)
            if (!apiTracks.isNullOrEmpty()) {
                return@withContext apiTracks
            }
        }

        // Strategy B: Spotify Embed API v1
        if (playlistId != null) {
            val apiTracks = fetchSpotifyEmbedApi("playlist", playlistId)
            if (apiTracks.isNotEmpty()) return@withContext apiTracks
        } else if (albumId != null) {
            val apiTracks = fetchSpotifyEmbedApi("album", albumId)
            if (apiTracks.isNotEmpty()) return@withContext apiTracks
        } else if (trackId != null) {
            val apiTracks = fetchSpotifyEmbedApi("track", trackId)
            if (apiTracks.isNotEmpty()) return@withContext apiTracks
        }

        // Strategy C: Spotify Embed Page HTML Parsing
        val targetId = playlistId ?: albumId ?: trackId
        val targetType = when {
            playlistId != null -> "playlist"
            albumId != null -> "album"
            else -> "track"
        }

        if (targetId != null) {
            val htmlTracks = fetchSpotifyEmbedPage(targetType, targetId)
            if (htmlTracks.isNotEmpty()) return@withContext htmlTracks
        }

        // Strategy D: Spotify oEmbed fallback
        val oembedTrack = fetchSpotifyOembed(inputUrl)
        if (oembedTrack != null) {
            return@withContext listOf(oembedTrack)
        }

        emptyList()
    }

    private fun extractSpotifyId(input: String, type: String): String? {
        val pattern = Regex("open\\.spotify\\.com(?:/embed)?/$type/([a-zA-Z0-9]+)")
        val match = pattern.find(input)?.groupValues?.get(1)
        if (match != null) return match

        val colonPattern = Regex("spotify:$type:([a-zA-Z0-9]+)")
        return colonPattern.find(input)?.groupValues?.get(1)
    }

    private fun fetchSpotifyEmbedApi(type: String, id: String): List<ImportedTrackCandidate> {
        try {
            val url = "https://open.spotify.com/embed-api/v1/$type/$id"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()

            val candidates = mutableListOf<ImportedTrackCandidate>()
            val element = jsonParser.parseToJsonElement(bodyString)
            extractTracksFromJsonElement(element, candidates)
            return candidates
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun fetchSpotifyEmbedPage(type: String, id: String): List<ImportedTrackCandidate> {
        try {
            val url = "https://open.spotify.com/embed/$type/$id"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()

            return parseSpotifyEmbedHtml(html)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun fetchSpotifyOembed(inputUrl: String): ImportedTrackCandidate? {
        try {
            val url = "https://open.spotify.com/oembed?url=${URLEncoder.encode(inputUrl, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null

            val root = jsonParser.parseToJsonElement(bodyString).jsonObject
            val title = root["title"]?.jsonPrimitive?.contentOrNull ?: return null
            val author = root["author_name"]?.jsonPrimitive?.contentOrNull ?: ""

            return ImportedTrackCandidate(
                title = unescapeUnicode(title),
                artistGuess = unescapeUnicode(author),
                sourcePlatform = "Spotify"
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseSpotifyEmbedHtml(html: String): List<ImportedTrackCandidate> {
        val candidates = mutableListOf<ImportedTrackCandidate>()

        val scriptRegex = Regex("""<script[^>]*id=["'](?:initial-state|__NEXT_DATA__|resource|session)["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val scriptMatches = scriptRegex.findAll(html)
        for (match in scriptMatches) {
            val jsonText = match.groupValues[1]
            try {
                val element = jsonParser.parseToJsonElement(jsonText)
                extractTracksFromJsonElement(element, candidates)
                if (candidates.isNotEmpty()) return candidates
            } catch (_: Exception) {}
        }

        val trackRegex = Regex("""["']name["']\s*:\s*["']([^"']+)["']\s*,\s*["']artists["']\s*:\s*\[\s*\{[^}]*["']name["']\s*:\s*["']([^"']+)["']""")
        val matches = trackRegex.findAll(html)
        for (match in matches) {
            val title = unescapeUnicode(match.groupValues[1])
            val artist = unescapeUnicode(match.groupValues[2])
            if (title.isNotBlank() && title != "Spotify") {
                candidates.add(
                    ImportedTrackCandidate(
                        title = title,
                        artistGuess = artist,
                        sourcePlatform = "Spotify"
                    )
                )
            }
        }

        if (candidates.isNotEmpty()) return candidates

        val titleArtistRegex = Regex("""["']title["']\s*:\s*["']([^"']+)["']\s*,\s*["']subtitle["']\s*:\s*["']([^"']+)["']""")
        val matches2 = titleArtistRegex.findAll(html)
        for (match in matches2) {
            val title = unescapeUnicode(match.groupValues[1])
            val artist = unescapeUnicode(match.groupValues[2])
            if (title.isNotBlank() && title != "Spotify") {
                candidates.add(
                    ImportedTrackCandidate(
                        title = title,
                        artistGuess = artist,
                        sourcePlatform = "Spotify"
                    )
                )
            }
        }

        if (candidates.isNotEmpty()) return candidates

        val ogTitleRegex = Regex("""<meta[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""")
        val ogTitleMatch = ogTitleRegex.find(html)?.groupValues?.get(1)
        if (!ogTitleMatch.isNullOrBlank() && !ogTitleMatch.contains("Spotify")) {
            val parts = ogTitleMatch.split(" - ", limit = 2)
            val title = if (parts.isNotEmpty()) parts[0].trim() else ogTitleMatch.trim()
            val artist = if (parts.size > 1) parts[1].replace("by ", "").trim() else ""
            candidates.add(ImportedTrackCandidate(title = title, artistGuess = artist, sourcePlatform = "Spotify"))
        }

        return candidates
    }

    private fun extractTracksFromJsonElement(element: JsonElement, outList: MutableList<ImportedTrackCandidate>) {
        when (element) {
            is JsonObject -> {
                // If entity contains trackList array, parse trackList directly to preserve exact playlist order
                val trackList = element["trackList"]?.jsonArray
                if (trackList != null && trackList.isNotEmpty()) {
                    for (trackItem in trackList) {
                        if (trackItem is JsonObject) {
                            val title = trackItem["title"]?.jsonPrimitive?.contentOrNull
                                ?: trackItem["name"]?.jsonPrimitive?.contentOrNull
                            val subtitle = trackItem["subtitle"]?.jsonPrimitive?.contentOrNull
                                ?: trackItem["artist"]?.jsonPrimitive?.contentOrNull
                                ?: ""
                            if (!title.isNullOrBlank() && title != "Spotify") {
                                outList.add(
                                    ImportedTrackCandidate(
                                        title = unescapeUnicode(title),
                                        artistGuess = unescapeUnicode(subtitle),
                                        sourcePlatform = "Spotify"
                                    )
                                )
                            }
                        }
                    }
                    return
                }

                val name = element["name"]?.jsonPrimitive?.contentOrNull ?: element["title"]?.jsonPrimitive?.contentOrNull
                val type = element["type"]?.jsonPrimitive?.contentOrNull

                val isAlbumOrPlaylist = type == "album" || type == "playlist" || element.containsKey("tracks") || element.containsKey("items")

                val isTrack = !name.isNullOrBlank() && !isAlbumOrPlaylist && (type == "track" || element.containsKey("duration_ms") || element.containsKey("track_number"))

                if (isTrack) {
                    var artistName = ""
                    val artistsArray = element["artists"]?.jsonArray
                    if (artistsArray != null && artistsArray.isNotEmpty()) {
                        artistName = artistsArray.mapNotNull {
                            if (it is JsonObject) it["name"]?.jsonPrimitive?.contentOrNull else if (it is JsonPrimitive) it.contentOrNull else null
                        }.joinToString(", ")
                    } else {
                        artistName = element["subtitle"]?.jsonPrimitive?.contentOrNull ?: element["artist"]?.jsonPrimitive?.contentOrNull ?: ""
                    }
                    if (name != "Spotify" && name!!.isNotBlank()) {
                        outList.add(
                            ImportedTrackCandidate(
                                title = unescapeUnicode(name),
                                artistGuess = unescapeUnicode(artistName),
                                sourcePlatform = "Spotify"
                            )
                        )
                    }
                } else {
                    for ((_, value) in element) {
                        extractTracksFromJsonElement(value, outList)
                    }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    extractTracksFromJsonElement(item, outList)
                }
            }
            else -> {}
        }
    }

    private fun unescapeUnicode(text: String): String {
        return text.replace("\\u0026", "&")
            .replace("\\u0027", "'")
            .replace("\\u0022", "\"")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
