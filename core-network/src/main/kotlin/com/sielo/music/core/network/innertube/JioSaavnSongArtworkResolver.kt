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

    suspend fun resolveSongArtwork(title: String?, artist: String?): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title?.trim() ?: ""
        if (cleanTitle.isBlank()) return@withContext null

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

        val query1 = "$filteredTitle $cleanArtist".trim()
        val query2 = filteredTitle.trim()

        fetchArtworkForQuery(query1) ?: if (query1 != query2) fetchArtworkForQuery(query2) else null
    }

    private fun fetchArtworkForQuery(query: String): String? {
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

            val firstSong = results.first().jsonObject
            val rawImage = firstSong["image"]?.jsonPrimitive?.content ?: return null

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
