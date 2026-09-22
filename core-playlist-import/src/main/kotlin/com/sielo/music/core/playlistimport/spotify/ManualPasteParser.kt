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
        Json { ignoreUnknownKeys = true }
    }

    suspend fun parseAsync(pastedText: String): List<ImportedTrackCandidate> = withContext(Dispatchers.IO) {
        val trimmed = pastedText.trim()

        // 1. Check if input contains a Spotify URL (playlist, album, track)
        val isSpotifyUrl = trimmed.contains("spotify.com") || trimmed.contains("spotify:") || trimmed.contains("spoti.fi")
        if (isSpotifyUrl) {
            val tracks = fetchSpotifyTracksFromUrl(trimmed)
            if (tracks.isNotEmpty()) return@withContext tracks
        }

        // 2. Multiline / Plain Text fallback parsing
        parsePlainText(pastedText)
    }

    fun parse(pastedText: String): List<ImportedTrackCandidate> {
        return parsePlainText(pastedText)
    }

    private fun parsePlainText(pastedText: String): List<ImportedTrackCandidate> {
        return pastedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
            .map { line ->
                val cleanedLine = line.replaceFirst(Regex("^\\d+\\s*[.\\-\\)]\\s*"), "").trim()
                val parts = cleanedLine.split(Regex("\\s+-\\s+"), limit = 2)
                if (parts.size == 2) {
                    ImportedTrackCandidate(
                        title = parts[0].trim(),
                        artistGuess = parts[1].trim(),
                        sourcePlatform = "Spotify_Manual"
                    )
                } else {
                    ImportedTrackCandidate(
                        title = cleanedLine,
                        artistGuess = "",
                        sourcePlatform = "Spotify_Manual"
                    )
                }
            }
            .filter { it.title.isNotBlank() }
    }

    private suspend fun fetchSpotifyTracksFromUrl(inputUrl: String): List<ImportedTrackCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ImportedTrackCandidate>()

        val playlistId = extractSpotifyId(inputUrl, "playlist")
        val albumId = extractSpotifyId(inputUrl, "album")
        val trackId = extractSpotifyId(inputUrl, "track")

        // Strategy A: Try Spotify Embed API v1
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

        // Strategy B: Try Spotify Embed Page HTML Parsing
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

        // Strategy C: Spotify oEmbed fallback
        val oembedTrack = fetchSpotifyOembed(inputUrl)
        if (oembedTrack != null) {
            candidates.add(oembedTrack)
        }

        candidates
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
            return candidates.distinctBy { "${it.title.lowercase()}_${it.artistGuess.lowercase()}" }
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
                if (candidates.isNotEmpty()) return candidates.distinctBy { "${it.title.lowercase()}_${it.artistGuess.lowercase()}" }
            } catch (e: Exception) {
                // Continue to next match
            }
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

        if (candidates.isNotEmpty()) return candidates.distinctBy { "${it.title.lowercase()}_${it.artistGuess.lowercase()}" }

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

        if (candidates.isNotEmpty()) return candidates.distinctBy { "${it.title.lowercase()}_${it.artistGuess.lowercase()}" }

        val ogTitleRegex = Regex("""<meta[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""")
        val ogTitleMatch = ogTitleRegex.find(html)?.groupValues?.get(1)
        if (!ogTitleMatch.isNullOrBlank() && !ogTitleMatch.contains("Spotify")) {
            val parts = ogTitleMatch.split(" - ", limit = 2)
            val title = if (parts.isNotEmpty()) parts[0].trim() else ogTitleMatch.trim()
            val artist = if (parts.size > 1) parts[1].replace("by ", "").trim() else ""
            candidates.add(ImportedTrackCandidate(title = title, artistGuess = artist, sourcePlatform = "Spotify"))
        }

        return candidates.distinctBy { "${it.title.lowercase()}_${it.artistGuess.lowercase()}" }
    }

    private fun extractTracksFromJsonElement(element: JsonElement, outList: MutableList<ImportedTrackCandidate>) {
        when (element) {
            is JsonObject -> {
                val name = element["name"]?.jsonPrimitive?.contentOrNull ?: element["title"]?.jsonPrimitive?.contentOrNull
                val type = element["type"]?.jsonPrimitive?.contentOrNull

                val isAlbumOrPlaylist = type == "album" || type == "playlist" || element.containsKey("tracks") || element.containsKey("items")

                // An element is a track candidate ONLY if it is specifically a track (NOT an album/playlist container)
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
