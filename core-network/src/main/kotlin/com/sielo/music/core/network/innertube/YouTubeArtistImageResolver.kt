package com.sielo.music.core.network.innertube

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
import java.util.concurrent.TimeUnit

/**
 * On-demand YouTube Artist Photo Resolver.
 * Imports artist portraits directly from YouTube Music on the fly.
 * Does NOT store or cache anything locally on disk.
 */
object YouTubeArtistImageResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // In-memory cache so resolved artist photos are retained across screens without re-fetching
    private val artistMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCachedArtistImageUrl(artistName: String): String? {
        val key = artistName.trim().lowercase()
        return artistMemoryCache[key]
    }

    suspend fun resolveArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext null

        val key = cleanName.lowercase()
        artistMemoryCache[key]?.let { return@withContext it }

        try {
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
                    "query": "${cleanName.replace("\"", "\\\"")}"
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
            val bodyString = response.body?.string() ?: return@withContext null
            val resolved = parseArtistPhoto(bodyString, cleanName)
            if (!resolved.isNullOrBlank()) {
                artistMemoryCache[key] = resolved
            }
            resolved
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseArtistPhoto(jsonString: String, targetArtist: String): String? {
        try {
            val cleanTarget = targetArtist.lowercase()
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
                // 1. Check musicCardShelfRenderer (Top Result card)
                val cardShelf = section.jsonObject["musicCardShelfRenderer"]?.jsonObject
                if (cardShelf != null) {
                    val titleRuns = cardShelf["title"]?.jsonObject?.get("runs")?.jsonArray
                    val title = titleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                        ?.joinToString("")?.trim()?.lowercase() ?: ""
                    val subRuns = cardShelf["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                    val sub = subRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                        ?.joinToString("")?.trim()?.lowercase() ?: ""

                    if ((sub.contains("artist") || sub.isBlank()) && isStrictArtistMatch(title, cleanTarget)) {
                        val thumbs = cardShelf["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                        val rawUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        if (!rawUrl.isNullOrBlank()) {
                            return upgradeImageUrl(rawUrl)
                        }
                    }
                }

                // 2. Check musicResponsiveListItemRenderer in shelves
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
                            ?.joinToString("")?.trim()?.lowercase() ?: ""

                        val subRuns = flexCols?.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                        val sub = subRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                            ?.joinToString("")?.trim()?.lowercase() ?: ""

                        if (sub.contains("artist") && isStrictArtistMatch(name, cleanTarget)) {
                            val thumbs = responsiveItem["thumbnail"]?.jsonObject
                                ?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject
                                ?.get("thumbnails")?.jsonArray
                            val rawUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!rawUrl.isNullOrBlank()) {
                                return upgradeImageUrl(rawUrl)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun upgradeImageUrl(url: String): String {
        return url.replace(Regex("=w\\d+-h\\d+.*"), "=w600-h600-p-l90-rj")
            .replace(Regex("=s\\d+.*"), "=s600-c-k-c0x00ffffff-no-rj")
    }

    private fun isStrictArtistMatch(candidate: String, target: String): Boolean {
        val c = candidate.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
        val t = target.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
        if (c == t) return true
        val cWords = c.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tWords = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (cWords == tWords) return true
        if (cWords.size == tWords.size && cWords.isNotEmpty()) {
            return cWords.zip(tWords).all { (w1, w2) ->
                w1 == w2 || (w1.length > 4 && w2.length > 4 && levenshteinDistance(w1, w2) <= 1)
            }
        }
        return false
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
}
