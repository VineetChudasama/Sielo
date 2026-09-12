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

@Singleton
class InnerTubeClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    companion object {
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
        val ytTracks = searchYouTube(query)
        
        // Combine results prioritizing official label tracks and unique title+artist
        val combined = (saavnTracks + ytTracks)
            .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
            .distinctBy { it.id }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

        if (combined.isNotEmpty()) combined else ytTracks
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
                val encUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
                val streamUrl = if (!encUrl.isNullOrBlank()) decryptDesUrl(encUrl) else null

                SieloTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = obj["album"]?.jsonPrimitive?.content?.let { unescapeHtml(it) },
                    durationSeconds = durationSec,
                    thumbnailUrl = image,
                    streamUrl = streamUrl
                )
            }.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchArtists(query: String): List<SieloArtist> = withContext(Dispatchers.IO) {
        val saavnArtists = searchArtistsSaavn(query)
        val ytPhoto = YouTubeArtistImageResolver.resolveArtistImageUrl(query)
        if (ytPhoto != null && saavnArtists.isNotEmpty()) {
            saavnArtists.mapIndexed { idx, artist ->
                if (idx == 0 && (artist.imageUrl.isNullOrBlank() || artist.name.equals(query, ignoreCase = true))) {
                    artist.copy(imageUrl = ytPhoto)
                } else artist
            }
        } else {
            saavnArtists
        }
    }

    suspend fun getArtistPhotoFromYouTube(artistName: String): String? {
        return YouTubeArtistImageResolver.resolveArtistImageUrl(artistName)
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

    suspend fun getArtistDetails(artistIdOrName: String, artistImageUrl: String? = null): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val ytPhoto = if (artistImageUrl.isNullOrBlank()) {
                YouTubeArtistImageResolver.resolveArtistImageUrl(artistIdOrName)
            } else {
                artistImageUrl
            }

            val artistId = if (artistIdOrName.all { it.isDigit() }) {
                artistIdOrName
            } else {
                val artists = searchArtists(artistIdOrName)
                artists.firstOrNull()?.id ?: return@withContext null
            }

            val url = "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$artistId&n_song=15&n_album=10"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            val root = json.parseToJsonElement(bodyString).jsonObject

            val name = unescapeHtml(root["name"]?.jsonPrimitive?.content ?: artistIdOrName)
            val rawImage = root["image"]?.jsonPrimitive?.content
                ?.replace("50x50", "500x500")
                ?.replace("150x150", "500x500")
            val finalImage = ytPhoto ?: rawImage

            // Top Songs
            val topSongsObj = root["topSongs"]?.jsonObject
            val songArray = topSongsObj?.get("songs")?.jsonArray ?: root["songs"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

            val topSongs = songArray.mapNotNull { item ->
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
             .take(5)

            // Top Albums & Latest Album
            val topAlbumsObj = root["topAlbums"]?.jsonObject
            val albumArray = topAlbumsObj?.get("albums")?.jsonArray ?: root["albums"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

            val albumList = albumArray.mapNotNull { item ->
                val obj = item.jsonObject
                val albumTitle = unescapeHtml(obj["album"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null)
                val year = obj["year"]?.jsonPrimitive?.content ?: "2024"
                val albumId = obj["albumid"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: albumTitle
                val cover = (obj["imageUrl"]?.jsonPrimitive?.content ?: obj["image"]?.jsonPrimitive?.content)
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")

                SieloAlbum(
                    id = albumId,
                    title = albumTitle,
                    artist = name,
                    year = year,
                    thumbnailUrl = cover
                )
            }

            val latestAlbum = albumList.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: albumList.firstOrNull()

            ArtistDetails(
                id = artistId,
                name = name,
                imageUrl = finalImage,
                bio = "Official Artist on Sielo",
                latestAlbum = latestAlbum,
                topSongs = topSongs
            )
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error fetching artist details: ${e.message}", e)
            null
        }
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

            val firstSong = results.first().jsonObject
            val encryptedUrl = firstSong["encrypted_media_url"]?.jsonPrimitive?.content
            if (!encryptedUrl.isNullOrBlank()) {
                val decryptedUrl = decryptDesUrl(encryptedUrl)
                if (!decryptedUrl.isNullOrBlank()) {
                    android.util.Log.d("InnerTubeClient", "Successfully DES-decrypted JioSaavn stream: $decryptedUrl")
                    return decryptedUrl
                }
            }

            // Fallback to preview url if available
            val previewUrl = firstSong["media_preview_url"]?.jsonPrimitive?.content
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

            val title = titleRuns?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return null

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
            val thumbUrl = if (rawThumbUrl != null && !rawThumbUrl.contains("i.ytimg.com")) {
                rawThumbUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                    .replace(Regex("=s\\d+.*"), "=s544-c-k-c0x00ffffff-no-rj")
            } else {
                null
            }

            if (!isPureMusicTrack(title, artist)) {
                return null
            }

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