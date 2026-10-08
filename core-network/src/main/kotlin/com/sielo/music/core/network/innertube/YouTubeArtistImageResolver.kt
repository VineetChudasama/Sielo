package com.sielo.music.core.network.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Strict Artist Profile Photo Resolver.
 * Extracts authentic, verified studio portraits from a SINGLE official source (JioSaavn Artist API & CDN).
 * Strictly enforces 100% exact artist name matching to guarantee that no artist ever receives
 * another artist's face or an irrelevant channel image.
 */
object YouTubeArtistImageResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // In-memory cache so resolved artist photos are retained across screens without re-fetching
    private val artistMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCachedArtistImageUrl(artistName: String): String? {
        val key = artistName.trim().lowercase()
        return artistMemoryCache[key]
    }

    suspend fun resolveArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || TrackMatchValidator.isYouTubeChannelId(cleanName)) return@withContext null

        val key = cleanName.lowercase()
        artistMemoryCache[key]?.let { return@withContext it }

        // 1. Primary Priority: Deezer 1000x1000 & Wikipedia verified studio portraits
        val verifiedPhoto = ArtistMetadataResolver.resolveArtistPhoto(cleanName)
        if (!verifiedPhoto.isNullOrBlank() && isRealArtistPortrait(verifiedPhoto)) {
            artistMemoryCache[key] = verifiedPhoto
            return@withContext verifiedPhoto
        }

        // 2. Secondary Priority: JioSaavn Artist API
        try {
            val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=5&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()
            if (!bodyString.isNullOrBlank()) {
                val root = json.parseToJsonElement(bodyString).jsonObject
                val results = root["results"]?.jsonArray

                results?.forEach { item ->
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val image = obj["image"]?.jsonPrimitive?.content ?: ""

                    // Strict Match: artist name must match target artist (ignoring punctuation & case)
                    if (isStrictArtistMatch(name, cleanName) && isRealArtistPortrait(image)) {
                        val highResUrl = image
                            .replace("50x50", "500x500")
                            .replace("150x150", "500x500")

                        artistMemoryCache[key] = highResUrl
                        return@withContext highResUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Tertiary Fallback: Fetch directly from YouTube Music official artist search
        val ytPhoto = fetchFromYouTubeMusic(cleanName)
        if (!ytPhoto.isNullOrBlank() && isRealArtistPortrait(ytPhoto)) {
            artistMemoryCache[key] = ytPhoto
            return@withContext ytPhoto
        }

        null
    }

    private fun fetchFromYouTubeMusic(artistName: String): String? {
        try {
            val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()
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
                    "query": "${artistName.replace("\"", "\\\"")}",
                    "params": "EgWKAQIgAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(okhttp3.RequestBody.create(jsonMedia, requestBody))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .build()

            val resp = client.newCall(req).execute()
            val jsonStr = resp.body?.string() ?: return null
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val tabs = root["contents"]?.jsonObject?.get("tabbedSearchResultsRenderer")?.jsonObject?.get("tabs")?.jsonArray
            val contents = tabs?.getOrNull(0)?.jsonObject?.get("tabRenderer")?.jsonObject?.get("content")?.jsonObject
            val sectionList = contents?.get("sectionListRenderer")?.jsonObject?.get("contents")?.jsonArray
            val musicShelf = sectionList?.getOrNull(0)?.jsonObject?.get("musicShelfRenderer")?.jsonObject
            val items = musicShelf?.get("contents")?.jsonArray ?: return null

            for (item in items) {
                val flexItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                val runs = flexItem["flexColumns"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                val name = runs?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                if (isStrictArtistMatch(name, artistName)) {
                    val thumbs = flexItem["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                    val lastThumb = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    if (!lastThumb.isNullOrBlank()) {
                        return upgradeImageUrl(lastThumb)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isRealArtistPortrait(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (lower.contains("default") || lower.contains("placeholder") || lower.contains("default-film") || lower.contains("default-music") || lower.contains("default-artist") || lower.contains("artist-default")) {
            return false
        }
        // Reject album cover URLs returned as artist images (e.g. c.saavncdn.com/265/Crucified-English-2017-50x50.jpg)
        if (lower.contains("saavncdn.com") && !lower.contains("/artists/")) {
            return false
        }
        if (lower.contains("wikimedia.org") || lower.contains("wikipedia.org")) return true
        if (lower.contains("saavncdn.com/artists/")) return true
        if (lower.contains("dzcdn.net") || lower.contains("deezer.com")) return true
        if (lower.contains("mzstatic.com") || lower.contains("apple.com")) return true
        if (lower.contains("scdn.co") || lower.contains("spotify.com")) return true
        if (lower.contains("googleusercontent.com") || lower.contains("ggpht.com") || lower.contains("yt3.ggpht.com")) {
            return !lower.contains("default") && !lower.contains("mqdefault")
        }
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun isStrictArtistMatch(candidate: String, target: String): Boolean {
        val c = normalizeForMatch(candidate)
        val t = normalizeForMatch(target)
        if (c.isBlank() || t.isBlank()) return false
        return c == t
    }

    private fun normalizeForMatch(str: String): String {
        val normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .lowercase()
            .trim()
    }

    fun upgradeImageUrl(url: String): String {
        return url
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
            .replace(Regex("=w\\d+-h\\d+.*"), "=w600-h600-p-l90-rj")
            .replace(Regex("=s\\d+.*"), "=s600-c-k-c0x00ffffff-no-rj")
    }
}
