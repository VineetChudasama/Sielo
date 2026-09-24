package com.sielo.music.core.network.innertube

import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class InnerTubeClient @Inject constructor(
    private val artistProfileCache: com.sielo.music.core.network.cache.ArtistProfileCache
) {
    constructor() : this(com.sielo.music.core.network.cache.ArtistProfileCache())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    companion object {
        var preferredBitrateSuffix: String = "_320.mp4"
        private val NON_MUSIC_KEYWORDS = listOf(
            "mashup", "mash-up", "mash up", "mega mashup", "megamashup",
            "jukebox", "full album", "all songs", "top songs collection",
            "compilation", "megamix", "mega mix", "non stop", "nonstop",
            "song collection", "best songs of", "hit songs of", "greatest hits", "greatest hits of",
            "best romantic songs", "best romantic", "romantic songs by", "best of",
            "audio jukebox", "video jukebox", "full audio songs", "continuous mix",
            "1 hour loop", "10 hours loop", "1 hour", "10 hours", "hour loop", "hour mix",
            "vlog", "reaction", "reacting to", "podcast", "podcasts", "gameplay", "tutorial",
            "trailer", "official trailer", "teaser", "behind the scenes", "bts of",
            "making of", "episode", "season", "review", "unboxing", "interview",
            "livestream", "live stream", "roast", "prank", "shorts", "#shorts",
            "web series", "full movie", "funny video", "meme", "ko lekar",
            "dialogues", "dialogue", "scene", "scenes", "status", "bgm", "ringtone",
            "talk", "speech", "speech video", "audiobook", "audio book"
        )

        private val FORBIDDEN_SUBTITLES = setOf(
            "episode", "episodes", "video", "videos", "podcast", "podcasts",
            "station", "channel", "playlist", "community"
        )

        fun isPureMusicTrack(title: String, artist: String = "", durationSeconds: Long = 0L): Boolean {
            val lowerTitle = title.lowercase()
            val lowerArtist = artist.lowercase()

            for (keyword in NON_MUSIC_KEYWORDS) {
                if (lowerTitle.contains(keyword)) return false
            }

            if (lowerArtist.contains("podcast") || lowerArtist.contains("reaction") || lowerArtist.contains("vlog") || lowerArtist.contains("interview")) {
                return false
            }

            if (durationSeconds > 660L) return false
            if (durationSeconds in 1..25) return false

            return true
        }
    }

    suspend fun search(query: String): List<SieloTrack> = withContext(Dispatchers.IO) {
        val saavnTracks = searchJioSaavn(query)
        val normalizedQuery = query.replace(Regex("(?i)\\bhe\\b"), "hi")
        val altSaavnTracks = if (normalizedQuery != query) searchJioSaavn(normalizedQuery) else emptyList()
        val ytTracks = searchYouTube(query)
        val altYtTracks = if (normalizedQuery != query && ytTracks.isEmpty()) searchYouTube(normalizedQuery) else emptyList()

        // Combine results prioritizing official label tracks and canonical deduplication
        val combined = (saavnTracks + altSaavnTracks + ytTracks + altYtTracks)
            .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
        val deduplicated = TrackMatchValidator.deduplicateTracks(combined)
            .distinctBy { it.id }

        if (deduplicated.isNotEmpty()) deduplicated else ytTracks
    }

    private fun searchYouTube(query: String): List<SieloTrack> {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20260114.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "${query.replace("\"", "\\\"")}",
                    "params": "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            parseMusicSearchResults(bodyString)
                .distinctBy { it.id }
                .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun searchJioSaavn(query: String): List<SieloTrack> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=20&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(bodyString).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            results.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = unescapeHtml(obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "Unknown")
                val artist = unescapeHtml(obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: "Artist")
                val image = obj["image"]?.jsonPrimitive?.content
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")
                val durStr = obj["duration"]?.jsonPrimitive?.content
                val durationSec = durStr?.toLongOrNull() ?: 0L
                val durFormatted = if (durationSec > 0) "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}" else null
                val encUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
                val streamUrl = if (!encUrl.isNullOrBlank()) decryptDesUrl(encUrl) else null

                // Ensure JioSaavn results have authentic relevance to the search query
                if (!TrackMatchValidator.isFuzzyMatch(query, title, null, artist)) {
                    return@mapNotNull null
                }

                SieloTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = obj["album"]?.jsonPrimitive?.content?.let { unescapeHtml(it) },
                    durationText = durFormatted,
                    durationSeconds = durationSec,
                    thumbnailUrl = image,
                    streamUrl = streamUrl
                )
            }.let { TrackMatchValidator.deduplicateTracks(it) }.distinctBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchArtists(query: String): List<SieloArtist> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Primary: Search YouTube Music for Verified Official Artists
        val rawYtArtists = searchYouTubeArtists(cleanQuery)
            .filter { isValidOfficialArtist(it.name) }
        val ytArtists = filterFakeAndDuplicateArtists(rawYtArtists)

        // 2. Secondary: JioSaavn verified artist search with strict filtering of fake/typo/collaboration profiles
        val rawSaavnArtists = searchArtistsSaavn(cleanQuery)
            .filter { isValidOfficialArtist(it.name) }
        val saavnArtists = filterFakeAndDuplicateArtists(rawSaavnArtists)

        // Combine YouTube verified artists first, then JioSaavn artists, then filter cross-source duplicates
        val combined = filterFakeAndDuplicateArtists(ytArtists + saavnArtists)

        // Attach official YouTube profile photo to verified artists, guaranteeing NO TWO ARTISTS SHARE THE SAME IMAGE
        val seenImages = mutableSetOf<String>()
        val result = mutableListOf<SieloArtist>()

        for (artist in combined) {
            val candidatePhoto = if (!isPlaceholderImage(artist.imageUrl)) artist.imageUrl else null
            val resolvedPhoto = if (candidatePhoto.isNullOrBlank()) {
                YouTubeArtistImageResolver.resolveArtistImageUrl(artist.name)
            } else candidatePhoto

            if (!resolvedPhoto.isNullOrBlank() && !seenImages.contains(resolvedPhoto)) {
                seenImages.add(resolvedPhoto)
                result.add(artist.copy(imageUrl = resolvedPhoto))
            } else {
                result.add(artist.copy(imageUrl = null))
            }
        }

        result
    }

    private fun isPlaceholderImage(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return lower.contains("default") || lower.contains("placeholder") || lower.contains("blank") || lower.contains("user_default")
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun isValidOfficialArtist(name: String): Boolean {
        val lower = name.trim().lowercase()
        if (lower.isBlank()) return false
        val spamKeywords = listOf("tribute", "karaoke", "cover band", "fan club", "various artists", "various", "dj remix", "compilation", "soundtrack")
        if (spamKeywords.any { lower.contains(it) }) return false
        return true
    }

    private fun filterFakeAndDuplicateArtists(artists: List<SieloArtist>): List<SieloArtist> {
        // 1. Remove combined / collaborative pseudo-artists (e.g. "Arijit Singh, Shreya Ghoshal" or "Arijit Singh & Pritam")
        val singleArtists = artists.filter { artist ->
            val name = artist.name.trim()
            if (name.isBlank()) return@filter false
            if (!isValidOfficialArtist(name)) return@filter false
            val lower = name.lowercase()
            if (lower.contains(",") || lower.contains(" feat.") || lower.contains(" feat ") ||
                lower.contains(" ft.") || lower.contains(" ft ") || lower.contains(" / ") ||
                lower.contains(" & ") || lower.contains(" and ") || lower.contains(" vs ") ||
                lower.contains(" x ")) {
                return@filter false
            }
            true
        }

        // 2. Remove alias duplicates and typos/prefixes
        val canonicalAccepted = mutableListOf<SieloArtist>()
        // Prioritize full names with more words and longer length first so full canonical name (e.g. "Arijit Singh") is accepted first
        val sortedCandidates = singleArtists.sortedWith(
            compareByDescending<SieloArtist> { it.name.trim().split(Regex("\\s+")).size }
                .thenByDescending { it.name.trim().length }
        )

        for (candidate in sortedCandidates) {
            val candNorm = candidate.name.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
            val candWords = candNorm.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (candWords.isEmpty()) continue

            val isDuplicate = canonicalAccepted.any { canonical ->
                val canonNorm = canonical.name.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
                val canonWords = canonNorm.split(Regex("\\s+")).filter { it.isNotBlank() }

                // Exact match
                if (candNorm == canonNorm) return@any true

                // Single word matching the first word of a multi-word canonical artist (e.g. "Arijit" when "Arijit Singh" exists)
                if (candWords.size == 1 && canonWords.size > 1 && canonWords.first() == candWords.first()) {
                    return@any true
                }

                // Incomplete prefix (e.g. "Arij" or "Arijit" starting canonical name)
                if (canonNorm.startsWith(candNorm) && candNorm.length < canonNorm.length) {
                    return@any true
                }

                // Near-edit-distance typo (e.g. "Arijit Sing" vs "Arijit Singh" with distance <= 2)
                if (Math.abs(candNorm.length - canonNorm.length) <= 2 && levenshteinDistance(candNorm, canonNorm) <= 2) {
                    return@any true
                }

                // Quoted alias: e.g. Abel "The Weeknd" Tesfaye
                if (canonical.name.contains("\"${candidate.name}\"", ignoreCase = true) ||
                    candidate.name.contains("\"${canonical.name}\"", ignoreCase = true)) {
                    return@any true
                }

                false
            }

            if (!isDuplicate) {
                canonicalAccepted.add(candidate)
            }
        }

        // Return preserving original ranking order of singleArtists for accepted names
        val acceptedNames = canonicalAccepted.map { it.name.trim().lowercase() }.toSet()
        return singleArtists.filter { acceptedNames.contains(it.name.trim().lowercase()) }
            .distinctBy { it.name.trim().lowercase() }
    }

    private fun searchYouTubeArtists(query: String): List<SieloArtist> {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20260114.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "${query.replace("\"", "\\\"")}",
                    "params": "EgWKAQIgAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            parseYouTubeArtistSearchResults(bodyString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseYouTubeArtistSearchResults(jsonString: String): List<SieloArtist> {
        val artists = mutableListOf<SieloArtist>()
        try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val tabs = root["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray

            val contents = tabs?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in contents) {
                // 1. Check musicCardShelfRenderer (Top Result)
                val cardShelf = section.jsonObject["musicCardShelfRenderer"]?.jsonObject
                if (cardShelf != null) {
                    val titleRuns = cardShelf["title"]?.jsonObject?.get("runs")?.jsonArray
                    val title = titleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                        ?.joinToString("")?.trim() ?: ""
                    val browseId = cardShelf["title"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                        ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.content ?: ""

                    val thumbs = cardShelf["thumbnail"]?.jsonObject
                        ?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject
                        ?.get("thumbnails")?.jsonArray
                    val rawUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    val imgUrl = if (!rawUrl.isNullOrBlank()) YouTubeArtistImageResolver.upgradeImageUrl(rawUrl) else null

                    if (title.isNotBlank() && isValidOfficialArtist(title)) {
                        artists.add(
                            SieloArtist(
                                id = browseId.ifBlank { title },
                                name = title,
                                imageUrl = imgUrl,
                                role = "Artist"
                            )
                        )
                    }
                }

                // 2. Check musicShelfRenderer
                val shelfItems = section.jsonObject["musicShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray
                    ?: section.jsonObject["itemSectionRenderer"]?.jsonObject?.get("contents")?.jsonArray

                shelfItems?.forEach { item ->
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (responsiveItem != null) {
                        val flexCols = responsiveItem["flexColumns"]?.jsonArray
                        val nameRuns = flexCols?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                        val name = nameRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                            ?.joinToString("")?.trim() ?: ""

                        val browseId = nameRuns?.getOrNull(0)?.jsonObject
                            ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.content ?: ""

                        val thumbs = responsiveItem["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                        val rawUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        val imgUrl = if (!rawUrl.isNullOrBlank()) YouTubeArtistImageResolver.upgradeImageUrl(rawUrl) else null

                        if (name.isNotBlank() && isValidOfficialArtist(name)) {
                            artists.add(
                                SieloArtist(
                                    id = browseId.ifBlank { name },
                                    name = name,
                                    imageUrl = imgUrl,
                                    role = "Artist"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return filterFakeAndDuplicateArtists(artists)
    }

    suspend fun getArtistPhotoFromYouTube(artistName: String): String? {
        return YouTubeArtistImageResolver.resolveArtistImageUrl(artistName)
    }

    suspend fun getSimilarArtistNames(artistName: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=5&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(bodyString).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            val firstArtistId = results.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: return@withContext emptyList()

            val pageUrl = "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$firstArtistId&n_song=5&n_album=5"
            val pageReq = Request.Builder()
                .url(pageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val pageResp = client.newCall(pageReq).execute()
            val pageBody = pageResp.body?.string() ?: return@withContext emptyList()
            val pageRoot = json.parseToJsonElement(pageBody).jsonObject
            val similar = pageRoot["similarArtists"]?.jsonArray ?: return@withContext emptyList()
            similar.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content?.let { n -> unescapeHtml(n) }
            }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchArtistsSaavn(query: String): List<SieloArtist> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=15&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(bodyString).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            results.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = unescapeHtml(obj["name"]?.jsonPrimitive?.content ?: "Artist")
                val image = obj["image"]?.jsonPrimitive?.content
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")
                val role = obj["role"]?.jsonPrimitive?.content ?: "Artist"

                SieloArtist(
                    id = id,
                    name = name,
                    imageUrl = image,
                    role = role
                )
            }.distinctBy { it.id }.distinctBy { it.name.trim().lowercase() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSimilarArtists(artistName: String): List<SieloArtist> = withContext(Dispatchers.IO) {
        try {
            val results = searchArtistsSaavn(artistName)
            results.filter { !it.imageUrl.isNullOrBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAlbumSongs(albumId: String, albumTitle: String? = null, artistName: String? = null): List<SieloTrack> = withContext(Dispatchers.IO) {
        try {
            // JioSaavn's album endpoint is the primary source because it returns
            // the complete release track list. Do not require a numeric album id:
            // some JioSaavn responses use string/slug-style ids.
            val encodedAlbumId = URLEncoder.encode(albumId, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&cc=in&albumid=$encodedAlbumId"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (bodyString.isNotBlank()) {
                val root = json.parseToJsonElement(bodyString).jsonObject
                val songArray = root["songs"]?.jsonArray
                    ?: root["list"]?.jsonArray
                    ?: root["results"]?.jsonArray
                    ?: root["data"]?.jsonObject?.get("songs")?.jsonArray

                if (songArray != null) {
                    val jioTracks = songArray.mapNotNull { item ->
                        val obj = item.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val songTitle = unescapeHtml(
                            obj["song"]?.jsonPrimitive?.content
                                ?: obj["title"]?.jsonPrimitive?.content
                                ?: return@mapNotNull null
                        )
                        val songArtist = unescapeHtml(
                            obj["primary_artists"]?.jsonPrimitive?.content
                                ?: obj["singers"]?.jsonPrimitive?.content
                                ?: obj["artist"]?.jsonPrimitive?.content
                                ?: "Artist"
                        )
                        val album = obj["album"]?.jsonPrimitive?.content?.let { unescapeHtml(it) }
                        val image = (obj["image"]?.jsonPrimitive?.content
                            ?: obj["album_image"]?.jsonPrimitive?.content)
                            ?.replace("50x50", "500x500")
                            ?.replace("150x150", "500x500")
                        val durSec = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: obj["more_info"]?.jsonObject?.get("duration")?.jsonPrimitive?.content?.toLongOrNull()
                            ?: 0L
                        val durationText = if (durSec > 0) {
                            "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}"
                        } else "3:30"
                        val encUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
                            ?: obj["more_info"]?.jsonObject?.get("encrypted_media_url")?.jsonPrimitive?.content
                        val streamUrl = if (!encUrl.isNullOrBlank()) decryptDesUrl(encUrl) else null

                        SieloTrack(
                            id = id,
                            title = songTitle,
                            artist = songArtist,
                            album = album ?: albumTitle,
                            durationText = durationText,
                            durationSeconds = durSec,
                            thumbnailUrl = image,
                            streamUrl = streamUrl
                        )
                    }
                        .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                        .distinctBy { it.id }

                    if (jioTracks.isNotEmpty()) {
                        return@withContext jioTracks
                    }
                }
            }

            // Fallback for releases that JioSaavn's album endpoint does not
            // expose correctly. Search YouTube using the actual release title
            // and keep only tracks that belong to that album.
            val title = albumTitle?.trim().orEmpty()
            val artist = artistName?.trim().orEmpty()
            if (title.isNotBlank()) {
                val query = if (artist.isNotBlank()) "$artist $title" else title
                val normalizedAlbum = normalizeAlbumKey(title)
                val fallback = searchYouTube(query)
                    .filter { track ->
                        if (!TrackMatchValidator.isSongByOrFeaturingArtist(track.title, track.artist, artist)) {
                            false
                        } else {
                            val trackAlbum = normalizeAlbumKey(track.album.orEmpty())
                            trackAlbum.isBlank() ||
                                    trackAlbum == normalizedAlbum ||
                                    trackAlbum.contains(normalizedAlbum) ||
                                    normalizedAlbum.contains(trackAlbum)
                        }
                    }
                    .distinctBy { it.id }

                if (fallback.isNotEmpty()) {
                    return@withContext fallback
                }
            }

            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error fetching album songs for $albumId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getArtistDetails(artistIdOrName: String, artistImageUrl: String? = null, artistId: String? = null): ArtistDetails? = withContext(Dispatchers.IO) {
        // 1. Check in-memory session cache first
        val cached = artistProfileCache.get(artistIdOrName)
        if (cached != null) {
            val hasDiscography = cached.originalAlbums.isNotEmpty() ||
                    cached.featuredAlbums.isNotEmpty() ||
                    cached.singles.isNotEmpty()
            if (hasDiscography) {
                return@withContext cached
            }
        }

        try {
                        val ytPhoto = YouTubeArtistImageResolver.resolveArtistImageUrl(artistIdOrName) ?: artistImageUrl

            val artistId = if (artistIdOrName.all { it.isDigit() }) {
                artistIdOrName
            } else {
                val artists = searchArtists(artistIdOrName)
                artists.firstOrNull()?.id ?: return@withContext createGuaranteedArtistProfile(artistIdOrName, ytPhoto)
            }

            val url = "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$artistId&n_song=50&n_album=50"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext createGuaranteedArtistProfile(artistIdOrName, ytPhoto)
            val root = json.parseToJsonElement(bodyString).jsonObject

            val rawName = unescapeHtml(root["name"]?.jsonPrimitive?.content ?: artistIdOrName)
            val name = if (rawName.equals("Artist", ignoreCase = true) || rawName.isBlank()) {
                if (!artistIdOrName.equals("Artist", ignoreCase = true) && artistIdOrName.isNotBlank()) artistIdOrName else "Official Artist"
            } else rawName

            val rawImage = root["image"]?.jsonPrimitive?.content
                ?.replace("50x50", "500x500")
                ?.replace("150x150", "500x500")
            val finalImage = ytPhoto ?: rawImage

            // Top Songs
            val topSongsObj = root["topSongs"]?.jsonObject
            val songArray = topSongsObj?.get("songs")?.jsonArray ?: root["songs"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

            val parsedTopSongs = songArray.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val songTitle = unescapeHtml(obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "Unknown")
                val songArtist = unescapeHtml(obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: name)
                val album = obj["album"]?.jsonPrimitive?.content?.let { unescapeHtml(it) }
                val image = obj["image"]?.jsonPrimitive?.content
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")
                val durSec = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val durationText = if (durSec > 0) "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}" else "3:30"
                val encUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
                val streamUrl = if (!encUrl.isNullOrBlank()) decryptDesUrl(encUrl) else null

                SieloTrack(
                    id = id,
                    title = songTitle,
                    artist = songArtist,
                    album = album,
                    durationText = durationText,
                    durationSeconds = durSec,
                    thumbnailUrl = image,
                    streamUrl = streamUrl
                )
            }.filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

            // Ensure top songs is never empty
            val topSongs = if (parsedTopSongs.size >= 5) {
                parsedTopSongs.take(20)
            } else {
                val searchFallback = searchYouTube("$name hits")
                    .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                    .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, name) }
                (parsedTopSongs + searchFallback).distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.take(12)
            }

            // Top Albums & Past Albums with full tracks
            val topAlbumsObj = root["topAlbums"]?.jsonObject
            val albumArray = topAlbumsObj?.get("albums")?.jsonArray
                ?: runCatching { root["albums"]?.jsonArray }.getOrNull()
                ?: runCatching { root["albums"]?.jsonObject?.get("albums")?.jsonArray }.getOrNull()
                ?: runCatching { root["artistAlbums"]?.jsonArray }.getOrNull()
                ?: kotlinx.serialization.json.JsonArray(emptyList())

            val rawAlbums = albumArray.mapNotNull { item ->
                val obj = item.jsonObject
                val albumTitle = unescapeHtml(obj["album"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null)
                val year = obj["year"]?.jsonPrimitive?.content ?: "2024"
                val albumId = obj["albumid"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: albumTitle
                val cover = (obj["imageUrl"]?.jsonPrimitive?.content ?: obj["image"]?.jsonPrimitive?.content)
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")

                val isMovieStr = obj["is_movie"]?.jsonPrimitive?.content
                    ?: obj["more_info"]?.jsonObject?.get("is_movie")?.jsonPrimitive?.content
                val isMovie = isMovieStr == "1" || isMovieStr.equals("true", ignoreCase = true)
                val songCount = obj["song_count"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["more_info"]?.jsonObject?.get("song_count")?.jsonPrimitive?.content?.toIntOrNull()
                    ?: 0
                val rawType = obj["type"]?.jsonPrimitive?.content
                    ?: obj["more_info"]?.jsonObject?.get("type")?.jsonPrimitive?.content
                    ?: "album"

                val isSingle = songCount == 1 || rawType.equals("single", ignoreCase = true) || albumTitle.contains("single", ignoreCase = true)
                val isFeaturedSoundtrack = isMovie || (!isSingle && (albumTitle.lowercase().contains("soundtrack") || albumTitle.lowercase().contains("ost") || albumTitle.lowercase().contains("from \"") || albumTitle.lowercase().contains("from '")))

                val calculatedType = when {
                    isSingle -> "Single"
                    isFeaturedSoundtrack -> "Soundtrack"
                    else -> "Album"
                }

                SieloAlbum(
                    id = albumId,
                    title = albumTitle,
                    artist = name,
                    year = year,
                    thumbnailUrl = cover,
                    type = calculatedType,
                    songCount = songCount
                )
            }

            // Concurrently fetch tracks for each album
            val fullAlbums = rawAlbums.map { album ->
                val songs = if (album.id.isNotBlank()) {
                    getAlbumSongs(album.id, album.title, name)
                } else emptyList()

                val resolvedSongs = if (songs.isNotEmpty()) {
                    songs
                } else {
                    topSongs.filter { it.album.equals(album.title, ignoreCase = true) }
                }

                album.copy(
                    tracks = resolvedSongs,
                    songCount = if (resolvedSongs.isNotEmpty()) resolvedSongs.size else album.songCount
                )
            }

            // Fallback: If no albums or all empty, dynamically group top tracks or search songs to ensure profile is never empty
            val pastAlbums = if (fullAlbums.isNotEmpty() && fullAlbums.any { it.tracks.isNotEmpty() }) {
                fullAlbums
            } else {
                val searchFallback = search("$name album hits").take(12)
                val combinedSongs = (topSongs + searchFallback).distinctBy { it.id }

                // Fallback search metadata can incorrectly put the song title in
                // the album field. Normalize that case into one collection so a
                // search result never becomes its own one-song album card.
                val grouped = combinedSongs.groupBy { track ->
                    val rawAlbum = track.album?.trim().orEmpty()
                    if (rawAlbum.isBlank() || normalizeAlbumKey(rawAlbum) == normalizeAlbumKey(track.title) || TrackMatchValidator.isCompilationAlbum(rawAlbum, name)) {
                        "$name Essentials"
                    } else {
                        rawAlbum
                    }
                }
                val fallbackList = mutableListOf<SieloAlbum>()
                for ((albName, trks) in grouped) {
                    fallbackList.add(
                        SieloAlbum(
                            id = "alb_${kotlin.math.abs((name + normalizeAlbumKey(albName)).hashCode())}",
                            title = albName,
                            artist = name,
                            year = "2023",
                            thumbnailUrl = trks.firstOrNull()?.thumbnailUrl ?: finalImage,
                            tracks = trks,
                            songCount = trks.size
                        )
                    )
                }
                if (fallbackList.isEmpty()) {
                    listOf(
                        SieloAlbum(
                            id = "alb_essential",
                            title = "$name Essentials",
                            artist = name,
                            year = "2024",
                            thumbnailUrl = finalImage,
                            tracks = topSongs,
                            songCount = topSongs.size
                        )
                    )
                } else fallbackList
            }

            val searchedJioAlbums = searchJioSaavnAlbums(name)
            val ytArtistAlbums = searchYouTubeArtistAlbums(name)

            // Merge releases from every source by album title instead of using
            // distinctBy(). This is important because the same album can arrive
            // from JioSaavn, the artist page and YouTube with different track lists.
            // Keeping only the first item can make an album appear as a separate
            // one-song album for every track.
            val allArtistAlbums = mergeArtistAlbums(
                pastAlbums + searchedJioAlbums + ytArtistAlbums,
                name
            )

                        val featList = allArtistAlbums.filter {
                it.type.equals("Soundtrack", true) || it.title.lowercase().contains("soundtrack") || it.title.lowercase().contains("movie") || it.title.lowercase().contains(" ost") || it.title.lowercase().contains("(ost)")
            }
            val singList = allArtistAlbums.filter {
                !featList.contains(it) && (it.type.equals("Single", true) || it.type.equals("EP", true) || (it.songCount == 1 && !it.type.equals("Album", true)))
            }
            val origList = allArtistAlbums.filter {
                !featList.contains(it) && !singList.contains(it) && !TrackMatchValidator.isCompilationAlbum(it.title, name)
            }

                        val eligibleForLatest = (origList + singList).ifEmpty { featList }.filter { !TrackMatchValidator.isCompilationAlbum(it.title, name) }
            val latestAlbum = eligibleForLatest.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: allArtistAlbums.firstOrNull()

            val details = ArtistDetails(
                id = artistId,
                name = name,
                imageUrl = finalImage,
                bio = "Official Artist on Sielo",
                latestAlbum = latestAlbum,
                topSongs = topSongs,
                originalAlbums = if (origList.isNotEmpty()) {
                    origList
                } else {
                    pastAlbums.filter {
                        it.tracks.size >= 2 &&
                                it.type != "Soundtrack" &&
                                it.type != "EP" &&
                                !TrackMatchValidator.isCompilationAlbum(it.title, name)
                    }
                },
                featuredAlbums = featList,
                singles = singList,
                pastAlbums = allArtistAlbums,
                isVerified = true
            )

            // Save in session cache
            artistProfileCache.put(name, details)
            artistProfileCache.put(artistIdOrName, details)
            details
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error fetching artist details: ${e.message}", e)
            val fallback = createGuaranteedArtistProfile(artistIdOrName, artistImageUrl)
            artistProfileCache.put(artistIdOrName, fallback)
            fallback
        }
    }

    /**
     * Searches JioSaavn's album index directly. The artist-page response can be
     * incomplete for older releases, while search.getAlbumResults exposes a
     * broader release set.
     */
    private suspend fun searchJioSaavnAlbums(artistName: String): List<SieloAlbum> {
        return try {
            val encodedQuery = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=50&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val results = json.parseToJsonElement(bodyString).jsonObject["results"]?.jsonArray
                ?: return emptyList()

            results.mapNotNull { item ->
                val obj = item.jsonObject
                val title = unescapeHtml(
                    obj["title"]?.jsonPrimitive?.content
                        ?: obj["album"]?.jsonPrimitive?.content
                        ?: obj["name"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                )

                val albumId = obj["albumid"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null

                val artistText = unescapeHtml(
                    obj["primary_artists"]?.jsonPrimitive?.content
                        ?: obj["artist"]?.jsonPrimitive?.content
                        ?: obj["subtitle"]?.jsonPrimitive?.content
                        ?: obj["more_info"]?.jsonObject?.get("primary_artists")?.jsonPrimitive?.content
                        ?: ""
                )

                if (artistText.isNotBlank() &&
                    !artistText.contains(artistName, ignoreCase = true) &&
                    !artistName.contains(artistText, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                val image = (obj["image"]?.jsonPrimitive?.content
                    ?: obj["imageUrl"]?.jsonPrimitive?.content)
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")

                val year = obj["year"]?.jsonPrimitive?.content
                    ?: obj["release_year"]?.jsonPrimitive?.content
                    ?: "2024"

                val songCount = obj["song_count"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["more_info"]?.jsonObject?.get("song_count")?.jsonPrimitive?.content?.toIntOrNull()
                    ?: 0

                val isMovieText = obj["is_movie"]?.jsonPrimitive?.content
                    ?: obj["more_info"]?.jsonObject?.get("is_movie")?.jsonPrimitive?.content
                val isMovie = isMovieText == "1" || isMovieText.equals("true", ignoreCase = true)

                val rawType = obj["type"]?.jsonPrimitive?.content
                    ?: obj["more_info"]?.jsonObject?.get("type")?.jsonPrimitive?.content
                    ?: ""

                val lowerTitle = title.lowercase()
                // Do not use song_count == 1 here. The album-search endpoint can
                // report an incomplete/incorrect count for a release. The final
                // type is determined again after the complete album track list is
                // loaded below.
                val type = when {
                    isMovie || rawType.equals("soundtrack", true) ||
                            lowerTitle.contains("soundtrack") ||
                            lowerTitle.contains(" ost") ||
                            lowerTitle.contains("(ost)") ||
                            lowerTitle.contains("movie") -> "Soundtrack"
                    rawType.equals("ep", true) -> "EP"
                    rawType.equals("single", true) || lowerTitle.contains("single") -> "Single"
                    else -> "Album"
                }

                SieloAlbum(
                    id = albumId,
                    title = title,
                    artist = artistName,
                    year = year,
                    thumbnailUrl = image,
                    type = type,
                    songCount = songCount
                )
            }
                .groupBy { normalizeAlbumKey(it.title) }
                .map { (_, sameTitleAlbums) ->
                    val base = sameTitleAlbums.first()
                    val tracks = sameTitleAlbums.flatMap { it.tracks }.distinctBy { it.id }
                    base.copy(
                        tracks = tracks,
                        songCount = maxOf(base.songCount, tracks.size)
                    )
                }
                .map { album ->
                    // Album search results normally contain the album id but not
                    // its complete track list. Resolve it once so all songs from
                    // the same release are displayed inside one album card.
                    val tracks = if (album.id.isNotBlank()) {
                        getAlbumSongs(album.id, album.title, artistName)
                    } else emptyList()

                    if (tracks.isNotEmpty()) {
                        val lowerTitle = album.title.lowercase()
                        val resolvedType = when {
                            album.type == "Soundtrack" ||
                                    lowerTitle.contains("soundtrack") ||
                                    lowerTitle.contains(" ost") ||
                                    lowerTitle.contains("(ost)") ||
                                    lowerTitle.contains("movie") -> "Soundtrack"
                            album.type == "EP" -> "EP"
                            album.type == "Single" && lowerTitle.contains("single") -> "Single"
                            tracks.size == 1 -> "Single"
                            else -> "Album"
                        }
                        album.copy(
                            tracks = tracks,
                            type = resolvedType,
                            songCount = tracks.size
                        )
                    } else {
                        album
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error searching artist albums: ${e.message}", e)
            emptyList()
        }
    }

    private fun searchYouTubeArtistAlbums(artistName: String): List<SieloAlbum> {
        return try {
            val tracks = searchYouTube("$artistName album")
                .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, artistName) }
                .distinctBy { it.id }

            // YouTube search metadata is not consistent: some results expose the
            // song title as the album, while others expose the real release name.
            // Never create a new album for a result whose album field is actually
            // just that track's title. Those tracks are kept in one fallback group.
            val grouped = tracks.groupBy { track ->
                val rawAlbum = track.album?.trim().orEmpty()
                val normalizedAlbum = normalizeAlbumKey(rawAlbum)
                val normalizedTrack = normalizeAlbumKey(track.title)

                if (rawAlbum.isBlank() || normalizedAlbum == normalizedTrack) {
                    "${artistName.trim()} Collection"
                } else {
                    rawAlbum
                }
            }

            grouped.mapNotNull { (albTitle, albTracks) ->
                if (albTracks.isEmpty()) return@mapNotNull null

                val lowerTitle = albTitle.lowercase()
                val count = albTracks.size
                val isSoundtrack = lowerTitle.contains("soundtrack") ||
                        lowerTitle.contains(" ost") ||
                        lowerTitle.contains("(ost)") ||
                        lowerTitle.contains("movie") ||
                        lowerTitle.contains("from \"" ) ||
                        lowerTitle.contains("from '")
                val looksLikeCollection = normalizeAlbumKey(albTitle) == normalizeAlbumKey("${artistName.trim()} Collection")

                // A one-track group is a real single only when YouTube supplied a
                // meaningful release name. The synthetic Collection group is not
                // a single and must remain one grouped fallback album.
                val type = when {
                    isSoundtrack -> "Soundtrack"
                    count == 1 && !looksLikeCollection -> "Single"
                    else -> "Album"
                }

                SieloAlbum(
                    id = "alb_${abs((artistName + normalizeAlbumKey(albTitle)).hashCode())}",
                    title = albTitle,
                    artist = artistName,
                    year = "2024",
                    thumbnailUrl = albTracks.firstOrNull()?.thumbnailUrl,
                    tracks = albTracks,
                    type = type,
                    songCount = albTracks.size
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Combines the same release coming from JioSaavn, the artist page and
     * YouTube into ONE SieloAlbum. Track lists are merged instead of selecting
     * whichever source happened to appear first.
     */
    private fun mergeArtistAlbums(albums: List<SieloAlbum>, artistName: String): List<SieloAlbum> {
        return albums
            .filter { it.title.isNotBlank() }
            .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artistName) }
            .groupBy { normalizeAlbumKey(it.title) }
            .mapNotNull { (_, sameTitleAlbums) ->
                val base = sameTitleAlbums
                    .sortedByDescending { it.tracks.size }
                    .firstOrNull() ?: return@mapNotNull null

                val mergedTracks = sameTitleAlbums
                    .flatMap { it.tracks }
                    .distinctBy {
                        // IDs are preferred, but a few sources can generate
                        // different IDs for the same recording. The title/artist
                        // fallback prevents duplicate copies of the same song.
                        val trackArtist = it.artist.trim().lowercase()
                        "${it.title.trim().lowercase()}|$trackArtist"
                    }

                val lowerTitle = base.title.lowercase()
                val type = when {
                    sameTitleAlbums.any { it.type == "Soundtrack" } ||
                            lowerTitle.contains("soundtrack") ||
                            lowerTitle.contains(" ost") ||
                            lowerTitle.contains("(ost)") ||
                            lowerTitle.contains("movie") -> "Soundtrack"
                    sameTitleAlbums.any { it.type == "EP" } -> "EP"
                    // The API can incorrectly label a multi-track release as a
                    // Single. The actual merged track count is authoritative.
                    mergedTracks.size >= 2 -> "Album"
                    else -> "Single"
                }

                base.copy(
                    tracks = mergedTracks,
                    type = type,
                    songCount = maxOf(mergedTracks.size, sameTitleAlbums.maxOfOrNull { it.songCount } ?: 0)
                )
            }
            .sortedWith(
                compareByDescending<SieloAlbum> { it.year?.toIntOrNull() ?: 0 }
                    .thenBy { it.title.lowercase() }
            )
    }

    /** Normalizes album names so casing, whitespace and harmless punctuation do not
     * create separate album cards for the same release. */
    private fun normalizeAlbumKey(title: String): String {
        return title
            .trim()
            .lowercase()
            .replace(Regex("&"), "and")
            .replace(Regex("[\\\"'`’]"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    

    private suspend fun createGuaranteedArtistProfile(artistName: String, imageUrl: String?): ArtistDetails {
        val cleanName = if (artistName.equals("Artist", ignoreCase = true) || artistName.isBlank()) "Official Artist" else artistName.trim()
        val photo = imageUrl ?: YouTubeArtistImageResolver.resolveArtistImageUrl(cleanName)

        val hits = searchYouTube("$cleanName hits")
            .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
            .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, cleanName) }
        val topTracks = if (hits.isNotEmpty()) hits.distinctBy { it.id }.take(20) else search("$cleanName songs").take(8)

        val jioAlbums = searchJioSaavnAlbums(cleanName)
        val ytAlbums = searchYouTubeArtistAlbums(cleanName)
        val allAlbums = mergeArtistAlbums(jioAlbums + ytAlbums, cleanName)

                        val featuredAlbums = allAlbums.filter {
            it.type.equals("Soundtrack", true) || it.title.lowercase().contains("soundtrack") || it.title.lowercase().contains("movie") || it.title.lowercase().contains(" ost") || it.title.lowercase().contains("(ost)")
        }
        val singlesList = allAlbums.filter {
            !featuredAlbums.contains(it) && (it.type.equals("Single", true) || it.type.equals("EP", true) || (it.songCount == 1 && !it.type.equals("Album", true)))
        }
        val origAlbums = allAlbums.filter {
            !featuredAlbums.contains(it) && !singlesList.contains(it) && !TrackMatchValidator.isCompilationAlbum(it.title, cleanName)
        }

        val mainImage = photo ?: topTracks.firstOrNull()?.thumbnailUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80"

                val eligibleForLatest = (origAlbums + singlesList).ifEmpty { featuredAlbums }.filter { !TrackMatchValidator.isCompilationAlbum(it.title, cleanName) }
        val defaultAlbum = eligibleForLatest.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: SieloAlbum(
            id = "alb_${abs(cleanName.hashCode())}",
            title = "$cleanName Essentials",
            artist = cleanName,
            year = "2024",
            thumbnailUrl = mainImage,
            tracks = topTracks,
            songCount = topTracks.size
        )

        val pastList = if (origAlbums.isNotEmpty()) origAlbums else listOf(defaultAlbum)

        return ArtistDetails(
            id = cleanName,
            name = cleanName,
            imageUrl = mainImage,
            bio = "Official Artist on Sielo",
            latestAlbum = defaultAlbum,
            topSongs = topTracks,
            originalAlbums = origAlbums,
            featuredAlbums = featuredAlbums,
            singles = singlesList,
            pastAlbums = if (allAlbums.isNotEmpty()) allAlbums else pastList,
            isVerified = true
        )
    }

    suspend fun getStreamUrl(videoId: String, title: String? = null, artist: String? = null): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("InnerTubeClient", "getStreamUrl start: videoId=$videoId, title=$title, artist=$artist")
        // 1. Primary: JioSaavn direct studio-quality 320kbps DES decrypted stream
        val saavnStream = resolveJioSaavnStream(title, artist, videoId)
        if (!saavnStream.isNullOrBlank()) {
            android.util.Log.d("InnerTubeClient", "JioSaavn direct stream resolved: $saavnStream")
            return@withContext saavnStream
        }

        // 2. Secondary: YouTube stream fallback
        val ytStream = resolveYouTubeStream(videoId)
        if (!ytStream.isNullOrBlank()) {
            android.util.Log.d("InnerTubeClient", "YouTube stream resolved: $ytStream")
            return@withContext ytStream
        }

        null
    }

    private fun resolveJioSaavnStream(title: String?, artist: String?, fallbackQuery: String): String? {
        return try {
            val query = if (!title.isNullOrBlank()) {
                val cleanTitle = title
                    .replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(video.*?\\)|\\[video.*?\\]|\\(audio.*?\\)|\\[audio.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(remix.*?\\)|\\[remix.*?\\]"), "")
                    .replace(Regex("(?i)\\b(official music video|official video|official audio|full video|hd 4k|4k|audio|lyric video|remix)\\b"), "")
                    .replace(Regex("[|/•~]"), " ")
                    .trim()

                val cleanArtist = artist
                    ?.replace(Regex("(?i)\\b(topic|vevo|official|channel|music)\\b"), "")
                    ?.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
                    ?.trim() ?: ""

                "$cleanTitle $cleanArtist".trim()
            } else {
                fallbackQuery
            }

            android.util.Log.d("InnerTubeClient", "Querying JioSaavn for stream: '$query'")
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=5&q=$encoded"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: return null
            val root = json.parseToJsonElement(searchBody).jsonObject
            val results = root["results"]?.jsonArray ?: return null

            if (results.isEmpty()) {
                android.util.Log.w("InnerTubeClient", "JioSaavn returned 0 results for query: '$query'")
                return null
            }

            val matchingSong = results.mapNotNull { it.jsonObject }.firstOrNull { obj ->
                val songTitle = unescapeHtml(obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "")
                val songArtist = unescapeHtml(obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: "")
                if (!title.isNullOrBlank()) {
                    TrackMatchValidator.isFuzzyMatch(title, songTitle, artist, songArtist)
                } else {
                    true
                }
            }

            if (matchingSong == null) {
                android.util.Log.w("InnerTubeClient", "No JioSaavn result matched requested title '$title'. Falling back to YouTube stream.")
                return null
            }

            val encryptedUrl = matchingSong["encrypted_media_url"]?.jsonPrimitive?.content
            if (!encryptedUrl.isNullOrBlank()) {
                val decryptedUrl = decryptDesUrl(encryptedUrl)
                if (!decryptedUrl.isNullOrBlank()) {
                    android.util.Log.d("InnerTubeClient", "Successfully DES-decrypted JioSaavn stream: $decryptedUrl")
                    return decryptedUrl
                }
            }

            // Fallback to preview url if available
            val previewUrl = matchingSong["media_preview_url"]?.jsonPrimitive?.content
            if (!previewUrl.isNullOrBlank()) {
                val highResPreview = previewUrl.replace("_96_p.mp4", "_320.mp4")
                    .replace("preview.saavncdn.com", "aac.saavncdn.com")
                return highResPreview
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error resolving JioSaavn stream: ${e.message}", e)
            null
        }
    }

    fun getYouTubePlaylistSongs(playlistId: String): List<SieloTrack> {
        return try {
            val cleanId = playlistId.substringAfter("list=").substringBefore("&").trim()
            val browseId = if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20260114.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "$browseId"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse")
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(bodyString).jsonObject

            val tabs = root["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray

            val sectionList = tabs?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            val musicPlaylistShelf = sectionList?.getOrNull(0)?.jsonObject
                ?.get("musicPlaylistShelfRenderer")?.jsonObject
                ?: sectionList?.getOrNull(0)?.jsonObject
                    ?.get("musicShelfRenderer")?.jsonObject

            val items = musicPlaylistShelf?.get("contents")?.jsonArray ?: return emptyList()
            val tracks = mutableListOf<SieloTrack>()

            items.forEach { item ->
                val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                parseResponsiveItem(responsiveItem)?.let { tracks.add(it) }
            }
            tracks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun decryptDesUrl(encryptedMediaUrl: String): String? {
        return try {
            val key = "38346591".toByteArray(Charsets.US_ASCII)
            val keySpec = javax.crypto.spec.SecretKeySpec(key, "DES")
            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decoded = android.util.Base64.decode(encryptedMediaUrl, android.util.Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decoded)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8)
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
                .replace("_48.mp4", "_320.mp4")
                .replace("http://", "https://")
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "DES decryption error: ${e.message}", e)
            null
        }
    }

    private fun resolveYouTubeStream(videoId: String): String? {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID_MUSIC",
                            "clientVersion": "6.42.52",
                            "androidSdkVersion": 34,
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player")
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .addHeader("User-Agent", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14)")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null
            val root = json.parseToJsonElement(bodyString).jsonObject
            val streamingData = root["streamingData"]?.jsonObject ?: return null

            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray
            val audioFormats = adaptiveFormats?.mapNotNull { it.jsonObject }
                ?.filter { it["mimeType"]?.jsonPrimitive?.content?.startsWith("audio/") == true }

            val m4aFormat = audioFormats?.filter { it["mimeType"]?.jsonPrimitive?.content?.contains("audio/mp4") == true }
                ?.maxByOrNull { it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }

            val bestFormat = m4aFormat ?: audioFormats?.maxByOrNull { it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }

            bestFormat?.get("url")?.jsonPrimitive?.content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseMusicSearchResults(jsonString: String): List<SieloTrack> {
        val tracks = mutableListOf<SieloTrack>()
        try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val tabs = root["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray

            val contents = tabs?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            contents?.forEach { section ->
                val musicShelf = section.jsonObject["musicShelfRenderer"]?.jsonObject
                val shelfItems = musicShelf?.get("contents")?.jsonArray
                shelfItems?.forEach { item ->
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                    parseResponsiveItem(responsiveItem)?.let { tracks.add(it) }
                }

                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val sectionItems = itemSection?.get("contents")?.jsonArray
                sectionItems?.forEach { item ->
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                    parseResponsiveItem(responsiveItem)?.let { tracks.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    private fun parseResponsiveItem(item: kotlinx.serialization.json.JsonObject): SieloTrack? {
        try {
            val flexColumns = item["flexColumns"]?.jsonArray ?: return null
            val titleRuns = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            val title = titleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                ?.joinToString("")
                ?.trim()
            if (title.isNullOrBlank()) return null

            val secondColRuns = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            // Check all text runs in subtitle for forbidden content types (Episode, Video, Podcast, etc.)
            val allSubtitleTexts = secondColRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() } ?: emptyList()
            for (text in allSubtitleTexts) {
                val lower = text.lowercase()
                if (FORBIDDEN_SUBTITLES.contains(lower) || lower.startsWith("episode") || lower.startsWith("video")) {
                    return null
                }
            }

            val artist = allSubtitleTexts.firstOrNull { it != "•" && it != "Song" && !it.contains("views") } ?: "Artist"

            val videoId = item["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                ?: item["navigationEndpoint"]?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.content
                ?: return null

            val thumbnails = item["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val rawThumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
            val thumbUrl = if (rawThumbUrl != null) {
                if (rawThumbUrl.contains("i.ytimg.com")) {
                    rawThumbUrl.replace("default.jpg", "hqdefault.jpg")
                        .replace("mqdefault.jpg", "hqdefault.jpg")
                } else {
                    rawThumbUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                        .replace(Regex("=s\\d+.*"), "=s544-c-k-c0x00ffffff-no-rj")
                }
            } else {
                null
            }

            val fixedColumns = item["fixedColumns"]?.jsonArray
            val fixedRuns = fixedColumns?.flatMap { col ->
                col.jsonObject["musicResponsiveListItemFixedColumnRenderer"]?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() } ?: emptyList()
            } ?: emptyList()

            val allPotentialDurations = (allSubtitleTexts + fixedRuns)
            val durationText = allPotentialDurations.firstOrNull { it.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")) }
            val durationSeconds = durationText?.let { parseDurationToSeconds(it) } ?: 0L

            if (!isPureMusicTrack(title, artist)) {
                return null
            }

            val resolvedDurationText = durationText ?: if (durationSeconds > 0) {
                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                "$mins:${secs.toString().padStart(2, '0')}"
            } else null

            return SieloTrack(
                id = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbUrl,
                durationText = resolvedDurationText,
                durationSeconds = durationSeconds
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseDurationToSeconds(text: String): Long {
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}







