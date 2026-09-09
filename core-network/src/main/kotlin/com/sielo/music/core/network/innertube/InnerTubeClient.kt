package com.sielo.music.core.network.innertube

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

@Singleton
class InnerTubeClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun search(query: String): List<SieloTrack> = withContext(Dispatchers.IO) {
        val ytTracks = searchYouTube(query)
        if (ytTracks.isNotEmpty()) {
            return@withContext ytTracks
        }
        searchJioSaavn(query)
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
                    "query": "${query.replace("\"", "\\\"")}"
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
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
                val image = obj["image"]?.jsonPrimitive?.content?.replace("150x150", "500x500")
                val durStr = obj["duration"]?.jsonPrimitive?.content
                val durationSec = durStr?.toLongOrNull() ?: 0L

                SieloTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = obj["album"]?.jsonPrimitive?.content?.let { unescapeHtml(it) },
                    durationSeconds = durationSec,
                    thumbnailUrl = image
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String, title: String? = null, artist: String? = null): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("InnerTubeClient", "getStreamUrl start: videoId=$videoId, title=$title, artist=$artist")
        // 1. Primary: JioSaavn direct high-bitrate (320kbps / 160kbps AAC in MP4)
        val saavnStream = resolveJioSaavnStream(title, artist, videoId)
        if (!saavnStream.isNullOrBlank()) {
            android.util.Log.d("InnerTubeClient", "JioSaavn stream resolved: $saavnStream")
            return@withContext saavnStream
        }

        // 2. Secondary: iTunes crystal-clear AAC stream fallback
        val itunesStream = resolveItunesStream(title, artist)
        if (!itunesStream.isNullOrBlank()) {
            android.util.Log.d("InnerTubeClient", "iTunes stream resolved: $itunesStream")
            return@withContext itunesStream
        }

        // 3. Tertiary: YouTube player endpoint
        val ytStream = resolveYouTubeStream(videoId)
        android.util.Log.d("InnerTubeClient", "YouTube stream resolved: $ytStream")
        ytStream
    }

    private fun resolveJioSaavnStream(title: String?, artist: String?, fallbackQuery: String): String? {
        return try {
            val query = if (!title.isNullOrBlank()) {
                val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
                val cleanArtist = artist?.replace(Regex("(?i)\\b(song|video|official|audio|remix)\\b"), "")
                    ?.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")?.trim() ?: ""
                "$cleanTitle $cleanArtist".trim()
            } else {
                fallbackQuery
            }

            android.util.Log.d("InnerTubeClient", "Querying JioSaavn for: $query")
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&includeMetaTags=1&p=1&n=5&q=$encoded"
            
            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: return null
            val root = json.parseToJsonElement(searchBody).jsonObject
            val results = root["results"]?.jsonArray ?: return null

            if (results.isEmpty()) {
                android.util.Log.w("InnerTubeClient", "JioSaavn returned 0 results for: $query")
                return null
            }

            val firstSong = results.first().jsonObject
            val encryptedUrl = firstSong["encrypted_media_url"]?.jsonPrimitive?.content ?: return null
            
            // 1. Try DES Decryption for direct high-bitrate stream URL (320kbps -> 160kbps -> 96kbps)
            val direct320 = decryptSaavnUrl(encryptedUrl, 320)
            if (!direct320.isNullOrBlank() && isUrlReachable(direct320)) {
                android.util.Log.d("InnerTubeClient", "JioSaavn direct 320k DES stream resolved: $direct320")
                return direct320
            }
            val direct160 = decryptSaavnUrl(encryptedUrl, 160)
            if (!direct160.isNullOrBlank() && isUrlReachable(direct160)) {
                android.util.Log.d("InnerTubeClient", "JioSaavn direct 160k DES stream resolved: $direct160")
                return direct160
            }
            val directDefault = decryptSaavnUrl(encryptedUrl, 96)
            if (!directDefault.isNullOrBlank()) {
                android.util.Log.d("InnerTubeClient", "JioSaavn direct stream fallback: $directDefault")
                return directDefault
            }

            // 2. Fallback to auth token generation
            val encodedMediaUrl = URLEncoder.encode(encryptedUrl, "UTF-8")
            var authUrl = fetchSaavnAuthUrl(encodedMediaUrl, 320)
            if (authUrl.isNullOrBlank()) {
                authUrl = fetchSaavnAuthUrl(encodedMediaUrl, 160)
            }
            android.util.Log.d("InnerTubeClient", "JioSaavn auth_url resolved: $authUrl")
            authUrl
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error resolving JioSaavn stream: ${e.message}", e)
            null
        }
    }

    private fun decryptSaavnUrl(encryptedUrl: String, bitrate: Int = 320): String? {
        return try {
            val keySpec = javax.crypto.spec.SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decoded = android.util.Base64.decode(encryptedUrl, android.util.Base64.DEFAULT)
            val decrypted = String(cipher.doFinal(decoded), Charsets.UTF_8).trim()
            val basePath = decrypted.replace(Regex("_(96|160|320)\\.mp4$|\\.mp4$"), "")
            val targetSuffix = if (bitrate == 320) "_320.mp4" else if (bitrate == 160) "_160.mp4" else "_96.mp4"
            "$basePath$targetSuffix"
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "DES Decryption error: ${e.message}", e)
            null
        }
    }

    private fun isUrlReachable(url: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchSaavnAuthUrl(encodedMediaUrl: String, bitrate: Int): String? {
        return try {
            val authUrlEndpoint = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$encodedMediaUrl&bitrate=$bitrate&api_version=4&_format=json&ctx=web6dot0&_marker=0"
            val authReq = Request.Builder()
                .url(authUrlEndpoint)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.jiosaavn.com/")
                .build()

            val authResp = client.newCall(authReq).execute()
            val authBody = authResp.body?.string() ?: return null
            val authObj = json.parseToJsonElement(authBody).jsonObject
            authObj["auth_url"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveItunesStream(title: String?, artist: String?): String? {
        if (title.isNullOrBlank()) return null
        return try {
            val query = URLEncoder.encode("$title ${artist ?: ""}".trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=1"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return null
            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonArray ?: return null
            if (results.isEmpty()) return null
            results.first().jsonObject["previewUrl"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            e.printStackTrace()
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

            // Prefer MP4/M4A (AAC, itag 140) for universal smooth hardware-accelerated playback
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

            val title = titleRuns?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return null

            val artistRuns = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            val artist = artistRuns?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Artist"

            val videoId = item["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                ?: item["navigationEndpoint"]?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.content
                ?: return null

            val thumbnails = item["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            return SieloTrack(
                id = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
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