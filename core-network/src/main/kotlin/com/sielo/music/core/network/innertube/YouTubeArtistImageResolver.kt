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

    suspend fun resolveArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext null

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
            parseArtistPhoto(bodyString, cleanName)
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

                    if (sub.contains("artist") || title.contains(cleanTarget) || cleanTarget.contains(title)) {
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

                        if (sub.contains("artist") && (name.contains(cleanTarget) || cleanTarget.contains(name))) {
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

    private fun upgradeImageUrl(url: String): String {
        return url.replace(Regex("=w\\d+-h\\d+.*"), "=w600-h600-p-l90-rj")
            .replace(Regex("=s\\d+.*"), "=s600-c-k-c0x00ffffff-no-rj")
    }
}
