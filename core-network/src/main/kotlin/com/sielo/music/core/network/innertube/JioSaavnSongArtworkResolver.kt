package com.sielo.music.core.network.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * On-demand JioSaavn Song Artwork Resolver.
 * Dynamically imports original high-res studio album artwork directly from JioSaavn on the fly.
 * Does NOT store or cache anything locally on disk.
 */
object JioSaavnSongArtworkResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Persistent in-memory cache so resolved studio artworks are instantly retrieved without re-fetching
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun cacheKey(title: String?, artist: String?): String {
        return "${title?.trim()?.lowercase()}::${artist?.trim()?.lowercase()}"
    }

    fun getCachedArtwork(title: String?, artist: String?): String? {
        val key = cacheKey(title, artist)
        return memoryCache[key]
    }

    suspend fun resolveSongArtwork(title: String?, artist: String?): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title?.trim() ?: ""
        if (cleanTitle.isBlank()) return@withContext null

        val key = cacheKey(title, artist)
        memoryCache[key]?.let { return@withContext it }

        val filteredTitle = cleanTitle
            .replace(Regex("(?i)\\s*\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[official.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(video.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[video.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(audio.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[audio.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(lyric.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[lyric.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(remix.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[remix.*?\\]"), "")
            .replace(Regex("[|/•~]"), " ")
            .trim()

        val cleanArtist = artist?.trim()
            ?.replace(Regex("(?i)\\b(topic|vevo|official|channel|music)\\b"), "")
            ?.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
            ?.trim() ?: ""

        val query = if (cleanArtist.isNotBlank()) "$filteredTitle $cleanArtist".trim() else filteredTitle.trim()

        val resolved = fetchArtworkForQuery(
            query = query,
            expectedTitle = filteredTitle,
            expectedArtist = cleanArtist.ifBlank { null }
        )
        if (!resolved.isNullOrBlank()) {
            memoryCache[key] = resolved
        }
        resolved
    }

    private fun fetchArtworkForQuery(
        query: String,
        expectedTitle: String,
        expectedArtist: String?
    ): String? {
        if (query.isBlank()) return null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=5&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null
            val root = json.parseToJsonElement(bodyString).jsonObject
            val results = root["results"]?.jsonArray ?: return null

            if (results.isEmpty()) return null

            val matchingSong = results.mapNotNull { it.jsonObject }.firstOrNull { obj ->
                val songTitle = obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: ""
                val songArtist = obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: ""
                TrackMatchValidator.isFuzzyMatch(expectedTitle, songTitle, expectedArtist, songArtist)
            } ?: return null

            val rawImage = matchingSong["image"]?.jsonPrimitive?.content ?: return null

            val highResImage = rawImage
                .replace("50x50", "500x500")
                .replace("150x150", "500x500")
                .replace("http://", "https://")

            return if (highResImage.isNotBlank() && !highResImage.contains("default-music")) {
                highResImage
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
