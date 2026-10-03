package com.sielo.music.core.network.innertube

import android.util.Log
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
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
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
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

        coroutineScope {
            // 1. Query YouTube Music first for verified authentic artists and collaborators
            val ytDeferred = async { searchYouTubeArtists(cleanQuery) }
            val rawYtArtists = ytDeferred.await().filter { isValidOfficialArtist(it.name) }
            val ytArtists = filterFakeAndDuplicateArtists(rawYtArtists)

            // If YouTube returned authentic artists, use them directly to prevent JioSaavn placeholder/fake injection
            if (ytArtists.isNotEmpty()) {
                return@coroutineScope filterDuplicateArtistImages(ytArtists)
            }

            // Fallback to JioSaavn only if YouTube returned no artists
            val saavnDeferred = async { searchArtistsSaavn(cleanQuery) }
            val rawSaavnArtists = saavnDeferred.await().filter {
                isValidOfficialArtist(it.name) && !isPlaceholderImage(it.imageUrl)
            }
            val saavnArtists = filterFakeAndDuplicateArtists(rawSaavnArtists)
            filterDuplicateArtistImages(saavnArtists)
        }
    }

    private fun filterDuplicateArtistImages(artists: List<SieloArtist>): List<SieloArtist> {
        val seenImages = mutableSetOf<String>()
        val result = mutableListOf<SieloArtist>()

        for (artist in artists) {
            val candidatePhoto = if (!isPlaceholderImage(artist.imageUrl)) artist.imageUrl else null
            if (!candidatePhoto.isNullOrBlank() && !seenImages.contains(candidatePhoto)) {
                seenImages.add(candidatePhoto)
                result.add(artist.copy(imageUrl = candidatePhoto))
            } else if (!candidatePhoto.isNullOrBlank()) {
                result.add(artist.copy(imageUrl = null))
            } else {
                result.add(artist)
            }
        }
        return result
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
        val spamKeywords = listOf(
            "tribute", "karaoke", "cover band", "fan club", "various artists", "various",
            "dj remix", "compilation", "soundtrack", " party", " bar", " stanna", " fan",
            "ai studio", "ai music", " topic"
        )
        if (spamKeywords.any { lower.contains(it) }) return false
        return true
    }

    private fun isAuthenticArtistSubtitle(subText: String, hasExistingAuthentic: Boolean): Boolean {
        val lower = subText.lowercase()
        if (lower.contains("monthly audience")) {
            return true
        }
        if (lower.contains("k subscriber") || lower.contains("m subscriber") || lower.contains("b subscriber")) {
            return true
        }
        // Exclude raw subscriber numbers under 10,000 (e.g. "3 subscribers", "11 subscribers", "49 subscribers", "582 subscribers")
        val subMatch = Regex("([0-9,]+)\\s+subscribers?\\b").find(lower)
        if (subMatch != null) {
            val count = subMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
            return count >= 10000L
        }
        // Exclude profiles, topic channels
        if (lower.contains("profile") || lower.contains("topic")) {
            return false
        }
        // If an authentic artist was already found, any subsequent item with plain "Artist" (no audience/subscribers) is an unverified copycat!
        if (hasExistingAuthentic) {
            return false
        }
        return false
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
                lower.contains(" x ") || lower.contains(";")) {
                return@filter false
            }
            true
        }

        // 2. Preserve authentic artist order and deduplicate by normalized name
        // Never delete authentic short artist names (e.g. 'sombr', 'The Weeknd', 'Drake', 'Prince', 'Cher')
        val canonicalAccepted = mutableListOf<SieloArtist>()
        val seenNormalized = mutableSetOf<String>()

        for (candidate in singleArtists) {
            val candNorm = candidate.name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            if (candNorm.isBlank()) continue

            // Deduplicate exact normalized names (preserving the first/best candidate)
            if (seenNormalized.contains(candNorm)) continue

            // If a candidate is a spam variant of an already accepted canonical artist (e.g. "Weeknd Bar", "The Weeknd Party")
            val isSpamVariant = canonicalAccepted.any { canonical ->
                val canonNorm = canonical.name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                val candLower = candidate.name.lowercase()
                candNorm.contains(canonNorm) && (candLower.contains("bar") || candLower.contains("party") || candLower.contains("tribute") || candLower.contains("club") || candLower.contains("fan") || candLower.contains("stanna"))
            }

            if (!isSpamVariant) {
                seenNormalized.add(candNorm)
                canonicalAccepted.add(candidate)
            }
        }

        return canonicalAccepted
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

                        val subRuns = flexCols?.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                        val subText = subRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                            ?.joinToString("")?.trim() ?: ""

                        val browseId = responsiveItem["navigationEndpoint"]?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.content
                            ?: nameRuns?.getOrNull(0)?.jsonObject
                                ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
                                ?.get("browseId")?.jsonPrimitive?.content ?: ""

                        val thumbs = responsiveItem["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                        val rawUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        val imgUrl = if (!rawUrl.isNullOrBlank()) YouTubeArtistImageResolver.upgradeImageUrl(rawUrl) else null

                        val hasExistingAuthentic = artists.isNotEmpty()
                        if (name.isNotBlank() && isValidOfficialArtist(name) && isAuthenticArtistSubtitle(subText, hasExistingAuthentic)) {
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
            getSimilarArtistsForArtist(artistName)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAlbumSongs(albumId: String, albumTitle: String? = null, artistName: String? = null): List<SieloTrack> = withContext(Dispatchers.IO) {
        try {
            // 1. iTunes Collection Lookup
            if (albumId.startsWith("itunes_")) {
                val collectionId = albumId.removePrefix("itunes_")
                if (collectionId.toLongOrNull() != null) {
                    try {
                        val itunesUrl = "https://itunes.apple.com/lookup?id=$collectionId&entity=song"
                        val req = Request.Builder()
                            .url(itunesUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val root = json.parseToJsonElement(body).jsonObject
                            val results = root["results"]?.jsonArray
                            val tracks = results?.mapNotNull { item ->
                                val obj = item.jsonObject
                                val wrapperType = obj["wrapperType"]?.jsonPrimitive?.content
                                if (wrapperType != "track") return@mapNotNull null
                                val songName = obj["trackName"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val artist = obj["artistName"]?.jsonPrimitive?.content ?: artistName ?: "Artist"
                                val album = obj["collectionName"]?.jsonPrimitive?.content ?: albumTitle
                                val trackId = obj["trackId"]?.jsonPrimitive?.content ?: "it_${Math.abs((songName + artist).hashCode())}"
                                val durMs = obj["trackTimeMillis"]?.jsonPrimitive?.content?.toLongOrNull() ?: 210000L
                                val durSec = durMs / 1000
                                val durationText = "%d:%02d".format(durSec / 60, durSec % 60)
                                val artwork = obj["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "600x600bb")
                                    ?.replace("100x100", "600x600")
                                val previewUrl = obj["previewUrl"]?.jsonPrimitive?.content
                                SieloTrack(
                                    id = trackId,
                                    title = songName,
                                    artist = artist,
                                    album = album,
                                    durationText = durationText,
                                    durationSeconds = durSec,
                                    thumbnailUrl = artwork,
                                    streamUrl = previewUrl
                                )
                            }.orEmpty()
                            if (tracks.isNotEmpty()) {
                                return@withContext tracks
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("InnerTubeClient", "Error in itunes lookup: ${e.message}")
                    }
                }
            }

            // 2. Deezer Album Lookup
            if (albumId.startsWith("dz_")) {
                val dzId = albumId.removePrefix("dz_")
                if (dzId.toLongOrNull() != null) {
                    try {
                        val dzUrl = "https://api.deezer.com/album/$dzId/tracks"
                        val req = Request.Builder()
                            .url(dzUrl)
                            .addHeader("User-Agent", "Mozilla/5.0")
                            .build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val root = json.parseToJsonElement(body).jsonObject
                            val data = root["data"]?.jsonArray
                            val tracks = data?.mapNotNull { item ->
                                val obj = item.jsonObject
                                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: artistName ?: "Artist"
                                val durSec = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 210L
                                val durationText = "%d:%02d".format(durSec / 60, durSec % 60)
                                val previewUrl = obj["preview"]?.jsonPrimitive?.content
                                SieloTrack(
                                    id = "dz_track_$id",
                                    title = title,
                                    artist = artist,
                                    album = albumTitle,
                                    durationText = durationText,
                                    durationSeconds = durSec,
                                    thumbnailUrl = null,
                                    streamUrl = previewUrl
                                )
                            }.orEmpty()
                            if (tracks.isNotEmpty()) {
                                return@withContext tracks
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("InnerTubeClient", "Error in deezer album lookup: ${e.message}")
                    }
                }
            }

            // 3. If YouTube browse ID (e.g. MPREb_... or VL... or FE...)
            if (albumId.startsWith("MPREb_") || albumId.startsWith("VL") || albumId.startsWith("FE")) {
                val ytTracks = getYouTubePlaylistSongs(albumId)
                if (ytTracks.isNotEmpty()) {
                    return@withContext ytTracks
                }
            }

            // 2. JioSaavn's album endpoint
            if (!albumId.startsWith("alb_") && albumId.isNotBlank()) {
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

                    if (songArray != null && songArray.isNotEmpty()) {
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
                                    ?: artistName ?: "Artist"
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
            }

            // 3. Fallback: Search JioSaavn album search
            val title = albumTitle?.trim().orEmpty()
            val artist = artistName?.trim().orEmpty()
            if (title.isNotBlank()) {
                val albumQuery = if (artist.isNotBlank()) "$artist $title" else title
                try {
                    val encoded = URLEncoder.encode(albumQuery, "UTF-8")
                    val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&cc=in&p=1&n=5&q=$encoded"
                    val req = Request.Builder().url(searchUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                    val resp = client.newCall(req).execute()
                    val bStr = resp.body?.string().orEmpty()
                    if (bStr.isNotBlank()) {
                        val results = json.parseToJsonElement(bStr).jsonObject["results"]?.jsonArray
                        val matchedAlbum = results?.mapNotNull { it.jsonObject }?.firstOrNull { obj ->
                            val aTitle = unescapeHtml(obj["title"]?.jsonPrimitive?.content ?: obj["album"]?.jsonPrimitive?.content ?: "")
                            aTitle.contains(title, ignoreCase = true) || title.contains(aTitle, ignoreCase = true)
                        }
                        val matchedId = matchedAlbum?.get("id")?.jsonPrimitive?.content ?: matchedAlbum?.get("albumid")?.jsonPrimitive?.content
                        if (!matchedId.isNullOrBlank()) {
                            val innerSongs = getAlbumSongs(matchedId, title, artist)
                            if (innerSongs.isNotEmpty()) {
                                return@withContext innerSongs
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 4. Fallback: Search YouTube using release title
                val query = if (artist.isNotBlank()) "$artist $title" else title
                val normalizedAlbum = normalizeAlbumKey(title)
                val fallback = searchYouTube(query)
                    .filter { track ->
                        val trackAlbum = normalizeAlbumKey(track.album.orEmpty())
                        trackAlbum.isBlank() ||
                                trackAlbum == normalizedAlbum ||
                                trackAlbum.contains(normalizedAlbum) ||
                                normalizedAlbum.contains(trackAlbum) ||
                                track.title.contains(title, ignoreCase = true) ||
                                title.contains(track.title, ignoreCase = true)
                    }
                    .distinctBy { it.id }

                if (fallback.isNotEmpty()) {
                    return@withContext fallback
                }

                // 5. Fallback: Search general music search for this release
                val searchTracks = search(query).take(10).distinctBy { it.id }
                if (searchTracks.isNotEmpty()) {
                    return@withContext searchTracks
                }

                // 6. Guarantee release is NEVER empty
                val fallbackTrack = SieloTrack(
                    id = if (albumId.isNotBlank() && !albumId.startsWith("alb_")) albumId else "track_${kotlin.math.abs((title + artist).hashCode())}",
                    title = title,
                    artist = if (artist.isNotBlank()) artist else "Artist",
                    album = title,
                    durationText = "3:30",
                    durationSeconds = 210,
                    thumbnailUrl = null
                )
                return@withContext listOf(fallbackTrack)
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
            coroutineScope {
                val cleanQuery = if (artistIdOrName.equals("Artist", ignoreCase = true) || artistIdOrName.isBlank()) "Official Artist" else artistIdOrName.trim()

                // Kick off parallel metadata fetches
                val discographyDeferred = async { ArtistMetadataResolver.fetchVerifiedDiscography(cleanQuery) }
                val wikiBioDeferred = async { ArtistMetadataResolver.fetchWikipediaBio(cleanQuery) }
                val ytPhotoDeferred = async { YouTubeArtistImageResolver.resolveArtistImageUrl(artistIdOrName) ?: artistImageUrl }
                val ytArtistAlbumsDeferred = async { searchYouTubeArtistAlbums(cleanQuery) }
                val searchedJioAlbumsDeferred = async { searchJioSaavnAlbums(cleanQuery) }

                // Fetch JioSaavn artist details
                val jioArtistPageDeferred = async {
                    try {
                        val resolvedId = if (artistId?.all { it.isDigit() } == true) {
                            artistId
                        } else if (artistIdOrName.all { it.isDigit() }) {
                            artistIdOrName
                        } else {
                            val saavnResults = searchArtistsSaavn(cleanQuery)
                            saavnResults.firstOrNull { it.name.trim().equals(cleanQuery, ignoreCase = true) }?.id
                                ?: saavnResults.firstOrNull()?.id
                        }
                        if (resolvedId != null) {
                            val url = "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$resolvedId&n_song=50&n_album=50"
                            val request = Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                                .build()
                            val response = client.newCall(request).execute()
                            response.body?.string()
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }

                val bodyString = jioArtistPageDeferred.await()
                val ytPhoto = ytPhotoDeferred.await()

                if (bodyString.isNullOrBlank()) {
                    // JioSaavn didn't have artist details -> create profile from YouTube / iTunes / Deezer
                    val verifiedDiscography = discographyDeferred.await()
                    val wikiBio = wikiBioDeferred.await()
                    val searchedJioAlbums = searchedJioAlbumsDeferred.await()
                    val ytArtistAlbums = ytArtistAlbumsDeferred.await()
                    val fallback = buildGuaranteedArtistProfile(
                        cleanName = cleanQuery,
                        imageUrl = ytPhoto,
                        verifiedDiscography = verifiedDiscography,
                        wikiBio = wikiBio,
                        jioAlbums = searchedJioAlbums,
                        ytAlbums = ytArtistAlbums
                    )
                    return@coroutineScope fallback
                }

                val root = json.parseToJsonElement(bodyString).jsonObject
                val rawName = unescapeHtml(root["name"]?.jsonPrimitive?.content ?: artistIdOrName)
                val name = if (rawName.equals("Artist", ignoreCase = true) || rawName.isBlank()) {
                    if (!artistIdOrName.equals("Artist", ignoreCase = true) && artistIdOrName.isNotBlank()) artistIdOrName else "Official Artist"
                } else rawName

                val rawImage = root["image"]?.jsonPrimitive?.content
                    ?.replace("50x50", "500x500")
                    ?.replace("150x150", "500x500")
                val finalImage = ytPhoto ?: rawImage

                // Top Songs from JioSaavn
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

                val topSongs = if (parsedTopSongs.size >= 5) {
                    parsedTopSongs.take(20)
                } else {
                    val searchFallback = searchYouTube("$name hits")
                        .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                        .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, name) }
                    (parsedTopSongs + searchFallback).distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.take(12)
                }

                // Top Albums & Past Albums from JioSaavn response
                val topAlbumsObj = root["topAlbums"]?.jsonObject
                val albumArray = topAlbumsObj?.get("albums")?.jsonArray
                    ?: runCatching { root["albums"]?.jsonArray }.getOrNull()
                    ?: runCatching { root["albums"]?.jsonObject?.get("albums")?.jsonArray }.getOrNull()
                    ?: runCatching { root["artistAlbums"]?.jsonArray }.getOrNull()
                    ?: kotlinx.serialization.json.JsonArray(emptyList())

                val rawAlbums = albumArray.mapNotNull { item ->
                    val obj = item.jsonObject
                    val albumTitle = unescapeHtml(obj["album"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null)
                    if (TrackMatchValidator.isCompilationAlbum(albumTitle, name)) return@mapNotNull null
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

                // In-memory track correlation with topSongs (no blocking HTTP requests)
                val fullAlbums = rawAlbums.map { album ->
                    val resolvedSongs = topSongs.filter { it.album.equals(album.title, ignoreCase = true) }
                    album.copy(
                        tracks = resolvedSongs,
                        songCount = if (resolvedSongs.isNotEmpty()) maxOf(album.songCount, resolvedSongs.size) else album.songCount
                    )
                }

                val pastAlbums = if (fullAlbums.isNotEmpty()) {
                    fullAlbums
                } else {
                    emptyList()
                }

                val searchedJioAlbums = searchedJioAlbumsDeferred.await()
                val ytArtistAlbums = ytArtistAlbumsDeferred.await()

                val allArtistAlbums = mergeArtistAlbums(
                    pastAlbums + searchedJioAlbums + ytArtistAlbums,
                    name
                )

                val featList = allArtistAlbums.filter {
                    TrackMatchValidator.isSoundtrackRelease(it.title, it.type)
                }
                // If the album contains 1 song, show it in singles
                val singList = allArtistAlbums.filter {
                    !featList.contains(it) && (it.type.equals("Single", true) || it.type.equals("EP", true) || it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1))
                }
                val origList = allArtistAlbums.filter {
                    !featList.contains(it) && !singList.contains(it) && !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && (it.songCount > 1 || it.tracks.size > 1) && !TrackMatchValidator.isCompilationAlbum(it.title, name)
                }

                val eligibleForLatest = (origList + singList).ifEmpty { featList }.filter { !TrackMatchValidator.isCompilationAlbum(it.title, name) }
                val latestAlbum = eligibleForLatest.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: allArtistAlbums.firstOrNull()

                val verifiedDiscography = discographyDeferred.await()
                val wikiBio = wikiBioDeferred.await()

                val resolvedPhoto = wikiBio?.photoUrl
                    ?: ytPhoto
                    ?: rawImage
                    ?: finalImage

                // Separate 1-song releases from studio albums to guarantee they appear in singles
                val singleStudioAlbums = verifiedDiscography.studioAlbums.filter { it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1) }
                val multiSongStudioAlbums = verifiedDiscography.studioAlbums.filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && (it.songCount > 1 || it.tracks.size > 1) && it.songCount != 1 }

                val mergedStudioAlbums = (multiSongStudioAlbums + origList)
                    .filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) }
                    .filter { it.title.isNotBlank() && !TrackMatchValidator.isCompilationAlbum(it.title, name) }
                    .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, name) }
                    .filter { it.songCount > 1 || it.tracks.size > 1 }
                    .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
                    .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val mergedSingles = (verifiedDiscography.singlesAndEPs + singList + singleStudioAlbums)
                    .filter { it.title.isNotBlank() && !TrackMatchValidator.isCompilationAlbum(it.title, name) }
                    .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, name) }
                    .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
                    .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

                val bioText = wikiBio?.bio?.takeIf { it.isNotBlank() }
                    ?: root["bio"]?.jsonPrimitive?.content?.takeIf { !it.isNullOrBlank() && !it.equals("null", true) }
                    ?: "${name} is a celebrated musical artist featured on Sielo, renowned for their acclaimed compositions, iconic releases, and globally streamed catalog."

                val mergedGenres = (verifiedDiscography.primaryGenres + (root["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList())).distinct()
                val similarList = resolveSimilarArtistsForArtist(name, root["similarArtists"]?.jsonArray ?: root["similar_artists"]?.jsonArray)

                val details = ArtistDetails(
                    id = artistId ?: name,
                    name = name,
                    imageUrl = resolvedPhoto,
                    bio = bioText,
                    latestAlbum = latestAlbum ?: mergedStudioAlbums.firstOrNull() ?: mergedSingles.firstOrNull(),
                    topSongs = topSongs,
                    originalAlbums = mergedStudioAlbums,
                    featuredAlbums = featList,
                    singles = mergedSingles,
                    pastAlbums = if (mergedStudioAlbums.isNotEmpty() || mergedSingles.isNotEmpty()) (mergedStudioAlbums + mergedSingles) else allArtistAlbums,
                    similarArtists = similarList,
                    isVerified = true,
                    wikiUrl = wikiBio?.wikiUrl,
                    genres = mergedGenres,
                    origin = wikiBio?.origin,
                    activeYears = wikiBio?.activeYears,
                    recordLabel = verifiedDiscography.recordLabel,
                    description = wikiBio?.description
                )

                // Return fresh details without contaminating MusicBrainz cache
                details
            }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error fetching artist details: ${e.message}", e)
            val fallback = createGuaranteedArtistProfile(artistIdOrName, artistImageUrl)
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

                if (TrackMatchValidator.isCompilationAlbum(title, artistName)) {
                    return@mapNotNull null
                }
                if (!TrackMatchValidator.isAlbumMadeByArtist(title, artistText, artistName, primaryArtists = artistText)) {
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
                val type = when {
                    isMovie || rawType.equals("soundtrack", true) ||
                            lowerTitle.contains("soundtrack") ||
                            lowerTitle.contains(" ost") ||
                            lowerTitle.contains("(ost)") ||
                            lowerTitle.contains("movie") -> "Soundtrack"
                    rawType.equals("ep", true) -> "EP"
                    songCount == 1 || rawType.equals("single", true) || lowerTitle.contains("single") -> "Single"
                    songCount > 1 || rawType.equals("album", true) -> "Album"
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
                    val maxSongCount = maxOf(base.songCount, sameTitleAlbums.maxOfOrNull { it.songCount } ?: 0, tracks.size)
                    base.copy(
                        tracks = tracks,
                        songCount = maxSongCount
                    )
                }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error searching artist albums: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun searchYouTubeArtistAlbums(artistName: String): List<SieloAlbum> = withContext(Dispatchers.IO) {
        return@withContext try {
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
                    "query": "$artistName",
                    "params": "EgWKAQIYAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"
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
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val parsedAlbums = parseYouTubeMusicAlbums(bodyString, artistName)
            if (parsedAlbums.isNotEmpty()) {
                parsedAlbums
            } else {
                fallbackSearchYouTubeArtistAlbums(artistName)
            }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error in searchYouTubeArtistAlbums: ${e.message}", e)
            fallbackSearchYouTubeArtistAlbums(artistName)
        }
    }

    private fun parseYouTubeMusicAlbums(jsonString: String, artistName: String): List<SieloAlbum> {
        val albums = mutableListOf<SieloAlbum>()
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
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (responsiveItem != null) {
                        parseYouTubeAlbumItem(responsiveItem, artistName)?.let { albums.add(it) }
                    }
                    val twoRowItem = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                    if (twoRowItem != null) {
                        parseYouTubeTwoRowAlbumItem(twoRowItem, artistName)?.let { albums.add(it) }
                    }
                }

                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val sectionItems = itemSection?.get("contents")?.jsonArray
                sectionItems?.forEach { item ->
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (responsiveItem != null) {
                        parseYouTubeAlbumItem(responsiveItem, artistName)?.let { albums.add(it) }
                    }
                    val twoRowItem = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                    if (twoRowItem != null) {
                        parseYouTubeTwoRowAlbumItem(twoRowItem, artistName)?.let { albums.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "parseYouTubeMusicAlbums error: ${e.message}", e)
        }
        return albums
    }

    private fun parseYouTubeAlbumItem(item: kotlinx.serialization.json.JsonObject, artistName: String): SieloAlbum? {
        return try {
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
            val subtitleTexts = secondColRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() } ?: emptyList()

            val rawType = subtitleTexts.firstOrNull { it.equals("Album", true) || it.equals("Single", true) || it.equals("EP", true) } ?: "Album"
            val artistText = subtitleTexts.firstOrNull { !it.equals("•") && !it.equals("Album", true) && !it.equals("Single", true) && !it.equals("EP", true) && !it.matches(Regex("""^\d{4}$""")) } ?: artistName

            if (TrackMatchValidator.isCompilationAlbum(title, artistName)) {
                return null
            }

            val targetLower = artistName.lowercase().trim()
            val artistLower = artistText.lowercase().trim()
            val splitArtists = artistLower.split(Regex("[,&/]|\\b(ft|feat|featuring)\\b")).map { it.trim() }
            val artistMatches = artistLower.contains(targetLower) || targetLower.contains(artistLower) ||
                    splitArtists.any { it.contains(targetLower) || targetLower.contains(it) }

            if (!artistMatches) {
                return null
            }

            if (!TrackMatchValidator.isAlbumMadeByArtist(title, artistText, artistName)) {
                return null
            }

            val year = subtitleTexts.firstOrNull { it.matches(Regex("""^\d{4}$""")) } ?: "2024"

            val browseId = item["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.content
                ?: item["overlay"]?.jsonObject
                    ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("musicPlayButtonRenderer")?.jsonObject
                    ?.get("playNavigationEndpoint")?.jsonObject
                    ?.get("watchPlaylistEndpoint")?.jsonObject
                    ?.get("playlistId")?.jsonPrimitive?.content
                ?: "alb_${abs((artistName + normalizeAlbumKey(title)).hashCode())}"

            val thumbnails = item["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val rawThumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
            val thumbUrl = if (rawThumbUrl != null) {
                if (rawThumbUrl.contains("i.ytimg.com")) {
                    rawThumbUrl.replace("default.jpg", "hqdefault.jpg").replace("mqdefault.jpg", "hqdefault.jpg")
                } else {
                    rawThumbUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                        .replace(Regex("=s\\d+.*"), "=s544-c-k-c0x00ffffff-no-rj")
                }
            } else null

            val lowerTitle = title.lowercase()
            val type = when {
                TrackMatchValidator.isSoundtrackRelease(title, rawType) -> "Soundtrack"
                rawType.equals("ep", true) -> "EP"
                rawType.equals("single", true) || lowerTitle.contains("single") -> "Single"
                else -> "Album"
            }

            SieloAlbum(
                id = browseId,
                title = title,
                artist = artistName,
                year = year,
                thumbnailUrl = thumbUrl,
                type = type,
                songCount = if (type == "Single") 1 else 0
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseYouTubeTwoRowAlbumItem(item: kotlinx.serialization.json.JsonObject, artistName: String): SieloAlbum? {
        return try {
            val titleRuns = item["title"]?.jsonObject?.get("runs")?.jsonArray
            val title = titleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }?.joinToString("")?.trim()
            if (title.isNullOrBlank()) return null

            val subtitleRuns = item["subtitle"]?.jsonObject?.get("runs")?.jsonArray
            val subtitleTexts = subtitleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() } ?: emptyList()

            val rawType = subtitleTexts.firstOrNull { it.equals("Album", true) || it.equals("Single", true) || it.equals("EP", true) } ?: "Album"
            val artistText = subtitleTexts.firstOrNull { !it.equals("•") && !it.equals("Album", true) && !it.equals("Single", true) && !it.equals("EP", true) && !it.matches(Regex("""^\d{4}$""")) } ?: artistName

            if (TrackMatchValidator.isCompilationAlbum(title, artistName)) {
                return null
            }

            val targetLower = artistName.lowercase().trim()
            val artistLower = artistText.lowercase().trim()
            val splitArtists = artistLower.split(Regex("[,&/]|\\b(ft|feat|featuring)\\b")).map { it.trim() }
            val artistMatches = artistLower.contains(targetLower) || targetLower.contains(artistLower) ||
                    splitArtists.any { it.contains(targetLower) || targetLower.contains(it) }

            if (!artistMatches) {
                return null
            }

            if (!TrackMatchValidator.isAlbumMadeByArtist(title, artistText, artistName)) {
                return null
            }

            val year = subtitleTexts.firstOrNull { it.matches(Regex("""^\d{4}$""")) } ?: "2024"

            val browseId = item["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.content
                ?: "alb_${abs((artistName + normalizeAlbumKey(title)).hashCode())}"

            val thumbnails = item["thumbnailRenderer"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val rawThumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
            val thumbUrl = if (rawThumbUrl != null) {
                if (rawThumbUrl.contains("i.ytimg.com")) {
                    rawThumbUrl.replace("default.jpg", "hqdefault.jpg").replace("mqdefault.jpg", "hqdefault.jpg")
                } else {
                    rawThumbUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                        .replace(Regex("=s\\d+.*"), "=s544-c-k-c0x00ffffff-no-rj")
                }
            } else null

            val lowerTitle = title.lowercase()
            val type = when {
                TrackMatchValidator.isSoundtrackRelease(title, rawType) -> "Soundtrack"
                rawType.equals("ep", true) -> "EP"
                rawType.equals("single", true) || lowerTitle.contains("single") -> "Single"
                else -> "Album"
            }

            SieloAlbum(
                id = browseId,
                title = title,
                artist = artistName,
                year = year,
                thumbnailUrl = thumbUrl,
                type = type,
                songCount = if (type == "Single") 1 else 0
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackSearchYouTubeArtistAlbums(artistName: String): List<SieloAlbum> {
        // Do not fabricate fake albums with duplicate track thumbnails from arbitrary video searches
        return emptyList()
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
            .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artistName) }
            .groupBy { normalizeAlbumKey(it.title) }
            .mapNotNull { (_, sameTitleAlbums) ->
                val base = sameTitleAlbums
                    .sortedByDescending { it.tracks.size }
                    .firstOrNull() ?: return@mapNotNull null

                val mergedTracks = sameTitleAlbums
                    .flatMap { it.tracks }
                    .distinctBy {
                        val trackArtist = it.artist.trim().lowercase()
                        "${it.title.trim().lowercase()}|$trackArtist"
                    }

                val lowerTitle = base.title.lowercase()
                val maxSongCount = maxOf(mergedTracks.size, sameTitleAlbums.maxOfOrNull { it.songCount } ?: 0)
                val type = when {
                    maxSongCount == 1 || (mergedTracks.size == 1 && maxSongCount <= 1) -> "Single"
                    sameTitleAlbums.any { it.type == "Soundtrack" } ||
                            lowerTitle.contains("soundtrack") ||
                            lowerTitle.contains(" ost") ||
                            lowerTitle.contains("(ost)") ||
                            lowerTitle.contains("movie") -> "Soundtrack"
                    sameTitleAlbums.any { it.type == "EP" } || lowerTitle.contains(" ep") || lowerTitle.contains("(ep)") -> "EP"
                    sameTitleAlbums.any { it.type == "Album" } || maxSongCount > 1 || mergedTracks.size >= 2 -> "Album"
                    sameTitleAlbums.any { it.type == "Single" } || lowerTitle.contains("single") -> "Single"
                    else -> if (maxSongCount <= 1) "Single" else "Album"
                }

                base.copy(
                    tracks = mergedTracks,
                    type = type,
                    songCount = maxSongCount
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

    private fun isSelfOrNameVariation(candidate: String, targetArtist: String): Boolean {
        val cLower = candidate.trim().lowercase()
        val tLower = targetArtist.trim().lowercase()
        if (cLower == tLower) return true
        val cTokens = cLower.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val tTokens = tLower.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tTokens.size == 1 && cTokens.contains(tTokens.first())) return true
        if (cTokens.size == 1 && tTokens.contains(cTokens.first())) return true
        if (cTokens.toSet() == tTokens.toSet()) return true
        return false
    }

    suspend fun getSimilarArtistsForArtist(artistName: String): List<SieloArtist> = withContext(Dispatchers.IO) {
        val clusterNames = getCuratedClusterArtists(artistName)
        val seenNames = mutableSetOf<String>()
        seenNames.add(artistName.trim().lowercase())

        coroutineScope {
            clusterNames.filter { !isSelfOrNameVariation(it, artistName) && seenNames.add(it.lowercase()) }.map { name: String ->
                async {
                    val photo = YouTubeArtistImageResolver.getCachedArtistImageUrl(name)
                        ?: ArtistMetadataResolver.fetchWikipediaBio(name)?.photoUrl
                        ?: YouTubeArtistImageResolver.resolveArtistImageUrl(name)
                    SieloArtist(
                        id = name,
                        name = name,
                        imageUrl = photo,
                        role = "Artist"
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun resolveSimilarArtistsForArtist(
        artistName: String,
        saavnSimilarArray: kotlinx.serialization.json.JsonArray?
    ): List<SieloArtist> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<SieloArtist>()
        val seenNames = mutableSetOf<String>()
        seenNames.add(artistName.trim().lowercase())

        // 1. First parse JioSaavn similar artists if provided
        saavnSimilarArray?.forEach { element ->
            try {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: obj["artistid"]?.jsonPrimitive?.content ?: ""
                val name = unescapeHtml(obj["name"]?.jsonPrimitive?.content ?: "").trim()
                if (name.isNotBlank() && !isSelfOrNameVariation(name, artistName) && seenNames.add(name.lowercase())) {
                    val rawImg = (obj["image"]?.jsonPrimitive?.content ?: obj["imageUrl"]?.jsonPrimitive?.content)
                        ?.replace("50x50", "500x500")
                        ?.replace("150x150", "500x500")
                    val role = obj["role"]?.jsonPrimitive?.content ?: "Artist"
                    resultList.add(
                        SieloArtist(
                            id = if (id.isNotBlank()) id else name,
                            name = name,
                            imageUrl = if (rawImg.isNullOrBlank() || rawImg.contains("default")) null else rawImg,
                            role = role
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. If JioSaavn returned fewer than 6, supplement from curated cluster
        if (resultList.size < 6) {
            val clusterNames = getCuratedClusterArtists(artistName)
            for (clusterName in clusterNames) {
                if (!isSelfOrNameVariation(clusterName, artistName) && seenNames.add(clusterName.lowercase())) {
                    resultList.add(
                        SieloArtist(
                            id = clusterName,
                            name = clusterName,
                            imageUrl = null,
                            role = "Artist"
                        )
                    )
                }
                if (resultList.size >= 10) break
            }
        }

        // 3. Resolve images in parallel so similar artists appear instantly without lag
        val finalArtists = coroutineScope {
            resultList.map { artist: SieloArtist ->
                async {
                    if (!artist.imageUrl.isNullOrBlank() && !artist.imageUrl.contains("default")) {
                        artist
                    } else {
                        val photo = YouTubeArtistImageResolver.getCachedArtistImageUrl(artist.name)
                            ?: ArtistMetadataResolver.fetchWikipediaBio(artist.name)?.photoUrl
                            ?: YouTubeArtistImageResolver.resolveArtistImageUrl(artist.name)
                        artist.copy(imageUrl = photo)
                    }
                }
            }.awaitAll()
        }

        finalArtists
    }

    private fun getCuratedClusterArtists(artistName: String): List<String> {
        val lower = artistName.trim().lowercase()
        return when {
            // Classical / Golden Era Indian Playback Legends
            lower.contains("mukesh") -> listOf(
                "Kishore Kumar", "Mohammed Rafi", "Manna Dey", "Lata Mangeshkar",
                "Asha Bhosle", "Hemant Kumar", "Talat Mahmood", "Mahendra Kapoor", "K. L. Saigal"
            )
            lower.contains("kishore") -> listOf(
                "Mohammed Rafi", "Mukesh", "Manna Dey", "Lata Mangeshkar",
                "Asha Bhosle", "Hemant Kumar", "R. D. Burman", "Mahendra Kapoor", "Amit Kumar"
            )
            lower.contains("rafi") -> listOf(
                "Kishore Kumar", "Mukesh", "Manna Dey", "Lata Mangeshkar",
                "Asha Bhosle", "Talat Mahmood", "Mahendra Kapoor", "Hemant Kumar", "K. L. Saigal"
            )
            lower.contains("lata mangeshkar") || lower.contains("lata ji") -> listOf(
                "Asha Bhosle", "Mohammed Rafi", "Kishore Kumar", "Mukesh",
                "Geeta Dutt", "Manna Dey", "Suman Kalyanpur", "Hemant Kumar", "Usha Mangeshkar"
            )
            lower.contains("asha bhosle") -> listOf(
                "Lata Mangeshkar", "Mohammed Rafi", "Kishore Kumar", "R. D. Burman",
                "Geeta Dutt", "Mukesh", "Manna Dey", "Usha Mangeshkar", "Alisha Chinai"
            )
            lower.contains("manna dey") || lower.contains("hemant kumar") || lower.contains("talat mahmood") ||
            lower.contains("mahendra kapoor") || lower.contains("saigal") || lower.contains("geeta dutt") -> listOf(
                "Mukesh", "Kishore Kumar", "Mohammed Rafi", "Hemant Kumar",
                "Manna Dey", "Talat Mahmood", "Geeta Dutt", "Lata Mangeshkar", "Mahendra Kapoor"
            )
            lower.contains("jagjit") || lower.contains("pankaj udhas") || lower.contains("ghulam ali") ||
            lower.contains("mehdi hassan") || lower.contains("chitra singh") || lower.contains("bhupinder") -> listOf(
                "Pankaj Udhas", "Jagjit Singh", "Ghulam Ali", "Mehdi Hassan",
                "Chitra Singh", "Bhupinder Singh", "Talat Aziz", "Anup Jalota", "Hariharan"
            )
            lower.contains("yesudas") || lower.contains("balasubrahmanyam") || lower.contains("spb") ||
            lower.contains("s. p. b") || lower.contains("chithra") || lower.contains("susheela") || lower.contains("s. janaki") -> listOf(
                "S. P. Balasubrahmanyam", "K. J. Yesudas", "K. S. Chithra", "S. Janaki",
                "P. Susheela", "Vani Jairam", "Hariharan", "Mano", "Unni Menon"
            )

            // Indian Classical (Hindustani / Carnatic)
            lower.contains("bhimsen") || lower.contains("jasraj") || lower.contains("amonkar") ||
            lower.contains("gandharva") || lower.contains("bade ghulam") -> listOf(
                "Pandit Jasraj", "Pandit Bhimsen Joshi", "Kishori Amonkar", "Kumar Gandharva",
                "Bade Ghulam Ali Khan", "Girija Devi", "Gangubai Hangal", "Ustad Amir Khan"
            )
            lower.contains("ravi shankar") || lower.contains("hariprasad") || lower.contains("chaurasia") ||
            lower.contains("zakir hussain") || lower.contains("shivkumar") || lower.contains("bismillah") || lower.contains("amjad ali") -> listOf(
                "Hariprasad Chaurasia", "Shivkumar Sharma", "Zakir Hussain", "Ravi Shankar",
                "Ustad Bismillah Khan", "Amjad Ali Khan", "Ustad Vilayat Khan", "L. Subramaniam"
            )
            lower.contains("subbulakshmi") || lower.contains("balamuralikrishna") || lower.contains("semmangudi") -> listOf(
                "M. S. Subbulakshmi", "M. Balamuralikrishna", "D. K. Pattammal", "M. L. Vasanthakumari",
                "K. J. Yesudas", "Chembai Vaidyanatha Bhagavatar"
            )

            // Western Golden Era / Crooners / Traditional Pop
            lower.contains("sinatra") || lower.contains("dean martin") || lower.contains("nat king cole") ||
            lower.contains("bing crosby") || lower.contains("perry como") || lower.contains("tony bennett") -> listOf(
                "Dean Martin", "Nat King Cole", "Tony Bennett", "Bing Crosby",
                "Perry Como", "Sammy Davis Jr.", "Bobby Darin", "Andy Williams", "Elvis Presley"
            )
            lower.contains("elvis") || lower.contains("presley") || lower.contains("chuck berry") ||
            lower.contains("buddy holly") || lower.contains("roy orbison") || lower.contains("johnny cash") -> listOf(
                "Johnny Cash", "Roy Orbison", "Buddy Holly", "Chuck Berry",
                "Jerry Lee Lewis", "Little Richard", "Carl Perkins", "Ricky Nelson"
            )
            lower.contains("armstrong") || lower.contains("ella fitzgerald") || lower.contains("billie holiday") ||
            lower.contains("chet baker") || lower.contains("miles davis") -> listOf(
                "Ella Fitzgerald", "Billie Holiday", "Louis Armstrong", "Chet Baker",
                "Miles Davis", "Sarah Vaughan", "Nina Simone", "John Coltrane"
            )

            // sombr & Bedroom Pop / Indie Alternative
            lower == "sombr" || lower.contains("sombr") -> listOf(
                "BoyWithUke", "d4vd", "JVKE", "David Kushner",
                "Stephen Sanchez", "Matt Maltese", "Conan Gray", "Current Joys", "Alec Benjamin"
            )

            lower.contains("twenty one pilots") || lower.contains("21 pilots") -> listOf(
                "Imagine Dragons", "Fall Out Boy", "Panic! At The Disco", "Coldplay",
                "OneRepublic", "Linkin Park", "The 1975", "My Chemical Romance",
                "Bastille", "Arctic Monkeys", "The Neighbourhood", "Paramore"
            )
            lower.contains("imagine dragons") || lower.contains("onerepublic") -> listOf(
                "Twenty One Pilots", "Fall Out Boy", "Coldplay", "The Script",
                "Bastille", "Maroon 5", "Panic! At The Disco", "X Ambassadors", "American Authors"
            )
            lower.contains("coldplay") -> listOf(
                "OneRepublic", "The Script", "Keane", "Maroon 5", "Imagine Dragons",
                "Snow Patrol", "U2", "Oasis", "The Killers", "Bastille"
            )
            lower.contains("linkin park") || lower.contains("evanescence") || lower.contains("green day") -> listOf(
                "Evanescence", "Green Day", "Three Days Grace", "Breaking Benjamin",
                "Papa Roach", "System Of A Down", "Slipknot", "Bring Me The Horizon", "Skillet", "Avenged Sevenfold"
            )
            lower.contains("arctic monkeys") || lower.contains("the strokes") -> listOf(
                "The Strokes", "The Neighbourhood", "Cage The Elephant", "Wallows",
                "Franz Ferdinand", "Foals", "Two Door Cinema Club", "The 1975", "Cigarettes After Sex"
            )
            lower.contains("cigarettes after sex") || lower.contains("the neighbourhood") -> listOf(
                "The Neighbourhood", "Beach House", "Current Joys", "TV Girl",
                "girl in red", "Mac DeMarco", "Men I Trust", "Joji", "Slowdive", "Lana Del Rey"
            )
            lower.contains("weeknd") -> listOf(
                "Drake", "Bruno Mars", "Post Malone", "Justin Bieber", "Dua Lipa",
                "Travis Scott", "Frank Ocean", "SZA", "Khalid", "Daft Punk"
            )
            lower.contains("taylor swift") -> listOf(
                "Olivia Rodrigo", "Billie Eilish", "Selena Gomez", "Ariana Grande",
                "Katy Perry", "Lorde", "Sabrina Carpenter", "Gracie Abrams", "Lana Del Rey", "Ed Sheeran"
            )
            lower.contains("billie eilish") || lower.contains("finneas") -> listOf(
                "FINNEAS", "Olivia Rodrigo", "Melanie Martinez", "Lana Del Rey",
                "girl in red", "Conan Gray", "Lorde", "Clairo", "Phoebe Bridges", "Sub Urban"
            )
            lower.contains("ed sheeran") || lower.contains("shawn mendes") || lower.contains("charlie puth") -> listOf(
                "Shawn Mendes", "James Arthur", "Charlie Puth", "Lewis Capaldi",
                "Sam Smith", "Dean Lewis", "George Ezra", "Calum Scott", "Harry Styles", "Niall Horan"
            )
            lower.contains("ariana grande") || lower.contains("dua lipa") || lower.contains("selena gomez") -> listOf(
                "Dua Lipa", "Camila Cabello", "Selena Gomez", "Doja Cat",
                "Sabrina Carpenter", "Olivia Rodrigo", "Ava Max", "Bebe Rexha", "Katy Perry"
            )
            lower.contains("post malone") || lower.contains("juice wrld") -> listOf(
                "The Weeknd", "Juice WRLD", "Swae Lee", "Khalid", "24kGoldn",
                "Iann Dior", "The Kid LAROI", "Lil Peep", "Trippie Redd", "Machine Gun Kelly"
            )
            lower.contains("lana del rey") -> listOf(
                "Lorde", "Marina", "Mitski", "Arctic Monkeys", "Cigarettes After Sex",
                "Phoebe Bridges", "Billie Eilish", "Florence + The Machine", "Clairo"
            )
            lower.contains("drake") || lower.contains("travis scott") || lower.contains("kendrick") -> listOf(
                "Travis Scott", "Kendrick Lamar", "Future", "21 Savage", "Kanye West",
                "J. Cole", "Lil Baby", "Post Malone", "The Weeknd", "Metro Boomin"
            )
            lower.contains("eminem") || lower.contains("50 cent") -> listOf(
                "50 Cent", "Dr. Dre", "Snoop Dogg", "Jay-Z", "Kendrick Lamar",
                "NF", "Machine Gun Kelly", "Tupac Shakur", "Lil Wayne", "Joyner Lucas"
            )
            lower.contains("arijit") || lower.contains("atif") -> listOf(
                "Atif Aslam", "Mohit Chauhan", "Shreya Ghoshal", "Armaan Malik",
                "Vishal Mishra", "Jubin Nautiyal", "Anuv Jain", "Darshan Raval", "Sonu Nigam", "Prateek Kuhad"
            )
            lower.contains("rahman") || lower.contains("pritam") || lower.contains("trivedi") -> listOf(
                "Pritam", "Amit Trivedi", "Shankar Mahadevan", "Anirudh Ravichander",
                "Ilaiyaraaja", "Harris Jayaraj", "Santhosh Narayanan", "Devi Sri Prasad", "Vishal-Shekhar", "Salim-Sulaiman"
            )
            lower.contains("shreya ghoshal") || lower.contains("sunidhi") -> listOf(
                "Sunidhi Chauhan", "Alka Yagnik", "Neha Kakkar", "Monali Thakur",
                "Palak Muchhal", "Jonita Gandhi", "Shilpa Rao", "Jasleen Royal", "Arijit Singh"
            )
            lower.contains("kk") || lower.contains("krishnakumar") || lower.contains("lucky ali") -> listOf(
                "Mohit Chauhan", "Lucky Ali", "Shaan", "Sonu Nigam",
                "Javed Ali", "Papon", "Shafqat Amanat Ali", "Atif Aslam", "Arijit Singh"
            )
            lower.contains("anuv jain") || lower.contains("prateek kuhad") || lower.contains("zaeden") -> listOf(
                "Prateek Kuhad", "Zaeden", "Jasleen Royal", "When Chai Met Toast",
                "The Local Train", "Osho Jain", "Twin Strings", "Aditya A"
            )
            lower.contains("diljit") || lower.contains("ap dhillon") || lower.contains("karan aujla") || lower.contains("sidhu") -> listOf(
                "AP Dhillon", "Karan Aujla", "Sidhu Moose Wala", "Shubh",
                "Guru Randhawa", "Amrinder Gill", "Amrit Maan", "Garry Sandhu", "B Praak"
            )
            lower.contains("anirudh") || lower.contains("santhosh narayanan") || lower.contains("sid sriram") -> listOf(
                "Santhosh Narayanan", "Sid Sriram", "G.V. Prakash Kumar", "Harris Jayaraj",
                "Yuvan Shankar Raja", "Devi Sri Prasad", "Thaman S", "A.R. Rahman", "Hiphop Tamizha"
            )
            lower.contains("martin garrix") || lower.contains("alan walker") || lower.contains("avicii") || lower.contains("marshmello") -> listOf(
                "Avicii", "David Guetta", "Alan Walker", "The Chainsmokers",
                "Calvin Harris", "Marshmello", "Tiësto", "Alesso", "Kygo", "Zedd", "DJ Snake"
            )
            lower.contains("hans zimmer") || lower.contains("john williams") -> listOf(
                "John Williams", "Ennio Morricone", "Ramin Djawadi", "Howard Shore",
                "Ludovico Einaudi", "Max Richter", "Danny Elfman", "James Horner", "Alan Silvestri"
            )
            lower.contains("bts") || lower.contains("blackpink") || lower.contains("stray kids") -> listOf(
                "TXT", "Stray Kids", "ENHYPEN", "SEVENTEEN", "BLACKPINK",
                "EXO", "TWICE", "NewJeans", "ATEEZ", "Jung Kook"
            )
            lower.contains("queen") || lower.contains("beatles") || lower.contains("pink floyd") -> listOf(
                "The Beatles", "Led Zeppelin", "Pink Floyd", "Elton John",
                "David Bowie", "The Rolling Stones", "AC/DC", "Guns N' Roses", "Fleetwood Mac"
            )
            else -> listOf(
                "The Weeknd", "Imagine Dragons", "Coldplay", "Taylor Swift",
                "Billie Eilish", "Drake", "Ed Sheeran", "Post Malone"
            )
        }
    }

    private suspend fun createGuaranteedArtistProfile(artistName: String, imageUrl: String?): ArtistDetails = coroutineScope {
        val cleanName = if (artistName.equals("Artist", ignoreCase = true) || artistName.isBlank()) "Official Artist" else artistName.trim()

        val discographyDeferred = async { ArtistMetadataResolver.fetchVerifiedDiscography(cleanName) }
        val wikiBioDeferred = async { ArtistMetadataResolver.fetchWikipediaBio(cleanName) }
        val photoDeferred = async { imageUrl ?: YouTubeArtistImageResolver.resolveArtistImageUrl(cleanName) }
        val hitsDeferred = async {
            searchYouTube("$cleanName hits")
                .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, cleanName) }
        }
        val jioAlbumsDeferred = async { searchJioSaavnAlbums(cleanName) }
        val ytAlbumsDeferred = async { searchYouTubeArtistAlbums(cleanName) }

        val verifiedDiscography = discographyDeferred.await()
        val wikiBio = wikiBioDeferred.await()
        val photo = photoDeferred.await()
        val hits = hitsDeferred.await()
        val jioAlbums = jioAlbumsDeferred.await()
        val ytAlbums = ytAlbumsDeferred.await()

        buildGuaranteedArtistProfile(
            cleanName = cleanName,
            imageUrl = photo,
            verifiedDiscography = verifiedDiscography,
            wikiBio = wikiBio,
            jioAlbums = jioAlbums,
            ytAlbums = ytAlbums,
            preloadedHits = hits
        )
    }

    private suspend fun buildGuaranteedArtistProfile(
        cleanName: String,
        imageUrl: String?,
        verifiedDiscography: VerifiedDiscography,
        wikiBio: VerifiedArtistBio?,
        jioAlbums: List<SieloAlbum>,
        ytAlbums: List<SieloAlbum>,
        preloadedHits: List<SieloTrack>? = null
    ): ArtistDetails {
        val topTracks = if (!preloadedHits.isNullOrEmpty()) {
            preloadedHits.distinctBy { it.id }.take(20)
        } else {
            val hits = searchYouTube("$cleanName hits")
                .filter { isPureMusicTrack(it.title, it.artist, it.durationSeconds) }
                .filter { TrackMatchValidator.isSongByOrFeaturingArtist(it.title, it.artist, cleanName) }
            if (hits.isNotEmpty()) hits.distinctBy { it.id }.take(20) else search("$cleanName songs").take(8)
        }

        val allAlbums = mergeArtistAlbums(jioAlbums + ytAlbums, cleanName)

        val featuredAlbums = allAlbums.filter {
            TrackMatchValidator.isSoundtrackRelease(it.title, it.type)
        }
        // If the album contains 1 song, show it in singles
        val rawSingles = allAlbums.filter {
            !featuredAlbums.contains(it) && (it.type.equals("Single", true) || it.type.equals("EP", true) || it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1))
        }
        val rawOrig = allAlbums.filter {
            !featuredAlbums.contains(it) && !rawSingles.contains(it) && !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && (it.songCount > 1 || it.tracks.size > 1) && !TrackMatchValidator.isCompilationAlbum(it.title, cleanName)
        }

        // Separate 1-song releases from verified studio albums to guarantee they appear in singles
        val singleStudioAlbums = verifiedDiscography.studioAlbums.filter { it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1) }
        val multiSongStudioAlbums = verifiedDiscography.studioAlbums.filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && (it.songCount > 1 || it.tracks.size > 1) && it.songCount != 1 }

        val origAlbums = (multiSongStudioAlbums + rawOrig)
            .filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) }
            .filter { it.title.isNotBlank() && !TrackMatchValidator.isCompilationAlbum(it.title, cleanName) }
            .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, cleanName) }
            .filter { it.songCount > 1 || it.tracks.size > 1 }
            .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
            .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

        val singlesList = (verifiedDiscography.singlesAndEPs + rawSingles + singleStudioAlbums)
            .filter { it.title.isNotBlank() && !TrackMatchValidator.isCompilationAlbum(it.title, cleanName) }
            .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, cleanName) }
            .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
            .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

        val mainImage = imageUrl ?: wikiBio?.photoUrl ?: topTracks.firstOrNull()?.thumbnailUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80"

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

        val similarList = resolveSimilarArtistsForArtist(cleanName, null)
        val bioText = wikiBio?.bio?.takeIf { it.isNotBlank() }
            ?: "${cleanName} is a celebrated musical artist featured on Sielo, renowned for their distinctive sound, compelling compositions, and globally streamed catalog."

        return ArtistDetails(
            id = cleanName,
            name = cleanName,
            imageUrl = mainImage,
            bio = bioText,
            latestAlbum = defaultAlbum,
            topSongs = topTracks,
            originalAlbums = origAlbums,
            featuredAlbums = featuredAlbums,
            singles = singlesList,
            pastAlbums = if (origAlbums.isNotEmpty() || singlesList.isNotEmpty()) (origAlbums + singlesList) else listOf(defaultAlbum),
            similarArtists = similarList,
            isVerified = true,
            wikiUrl = wikiBio?.wikiUrl,
            genres = verifiedDiscography.primaryGenres,
            origin = wikiBio?.origin,
            activeYears = wikiBio?.activeYears,
            recordLabel = verifiedDiscography.recordLabel,
            description = wikiBio?.description
        )
    }

    data class CachedStream(val url: String, val expiresAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    private fun cacheStreamUrl(videoId: String, url: String) {
        val expiresAtMs = try {
            val expSec = url.substringAfter("expire=").substringBefore("&").toLongOrNull()
            if (expSec != null) expSec * 1000L else (System.currentTimeMillis() + (4 * 3600 * 1000L))
        } catch (_: Exception) {
            System.currentTimeMillis() + (4 * 3600 * 1000L)
        }
        streamCache[videoId] = CachedStream(url, expiresAtMs)
    }

    suspend fun getStreamUrl(videoId: String, title: String? = null, artist: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isNotBlank()) {
            val cached = streamCache[cleanId]
            if (cached != null && cached.expiresAtMs > System.currentTimeMillis() + 60_000L) {
                android.util.Log.d("InnerTubeClient", "Instant stream cache hit for videoId=$cleanId")
                return@withContext cached.url
            }
        }
        android.util.Log.d("InnerTubeClient", "getStreamUrl start: videoId=$cleanId, title=$title, artist=$artist")

        // 1. Primary: YouTube InnerTube stream for the exact tapped track (full length)
        if (cleanId.isNotBlank()) {
            val ytStream = resolveYouTubeStream(cleanId)
            if (!ytStream.isNullOrBlank()) {
                android.util.Log.d("InnerTubeClient", "YouTube stream resolved for $cleanId")
                cacheStreamUrl(cleanId, ytStream)
                return@withContext ytStream
            }
        }

        // 2. Secondary: JioSaavn direct high-bitrate stream with strict title/artist validation
        if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
            val saavnStream = resolveJioSaavnStream(title, artist, cleanId)
            if (!saavnStream.isNullOrBlank()) {
                android.util.Log.d("InnerTubeClient", "JioSaavn stream resolved: $saavnStream")
                cacheStreamUrl(cleanId, saavnStream)
                return@withContext saavnStream
            }
        }

        android.util.Log.w("InnerTubeClient", "Failed to resolve stream for videoId=$cleanId, title=$title")
        null
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

            if (query.isBlank()) return null

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

            // Strictly validate that the JioSaavn search result matches the requested song!
            // Never pick an unrelated song!
            val matchingSong = results.mapNotNull { it.jsonObject }.firstOrNull { obj ->
                val songTitle = unescapeHtml(obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "")
                val songArtist = unescapeHtml(obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: "")
                if (!title.isNullOrBlank()) {
                    TrackMatchValidator.isFuzzyMatch(title, songTitle, artist, songArtist)
                } else {
                    true
                }
            } ?: run {
                android.util.Log.w("InnerTubeClient", "No JioSaavn result matched title='$title', artist='$artist'")
                return null
            }

            val encryptedUrl = matchingSong["encrypted_media_url"]?.jsonPrimitive?.content ?: return null

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

    fun decryptSaavnUrl(encryptedUrl: String, bitrate: Int = 320): String? {
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

    fun decryptDesUrl(encryptedMediaUrl: String): String? {
        return decryptSaavnUrl(encryptedMediaUrl, 320)
            ?: decryptSaavnUrl(encryptedMediaUrl, 160)
            ?: decryptSaavnUrl(encryptedMediaUrl, 96)
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

    @Volatile
    private var cachedVisitorData: String? = "Cgt2ZWVNRHFaS2J0QSj5y_7VBjIKCgJJThIEGgAgLGLfAgrcAjIyLllUPUt2cFJla1VBbWVKVE5MaFBpNVRhN1pPS2syd2c1V2Z5eFFob1BCVkd3c181RFBrMFZvMmpEaFNfWUVmZnZjNzVVRV9aTHNkaElHSXd2U1d6NHl2YXNqeDh6SVVzb1RnNXpaNVpHanY1SU5sSWtoZW1IbW9HbkUyRDFYcU5tRXE1eS1IMXJOMExHQ0FUWmRKLUVOSENRd25EOVFaR0dOcTZxQjBPbTdtTTZ6bUdvYjZkVTVKWFF0dEpyY3o1VjNhNHM4YXRHd1Z4aDFhbjZKelo5Ung5eXhocC0yMTN5NXBoVi1mOTJHOFVOdGtEWjNWaTNwNXdSZlRCWFhnTldPVDlCcGt6eUhvNWxkVkpYYnBfVUpCbjdYUmFUQUVMeXpVWTNTTTZBcF9DekQzeVpVMTJES0llWHpOOVRUOTJDcU9KclRSQXN5MGstclI1OEY3ODNDdzZDUQ%3D%3D"

    private fun getVisitorData(): String? {
        val cached = cachedVisitorData
        if (!cached.isNullOrBlank()) return cached
        return try {
            val req = Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", StreamClientUtils.USER_AGENT_WEB)
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return null
            if (body.length > 5) {
                val data = json.parseToJsonElement(body.substring(5)).jsonArray
                val candidates = data.getOrNull(0)?.jsonArray?.getOrNull(2)?.jsonArray
                val visitor = candidates?.firstNotNullOfOrNull { elem ->
                    val s = (elem as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (s != null && s.startsWith("Cg")) s else null
                }
                if (!visitor.isNullOrBlank()) {
                    cachedVisitorData = visitor
                    visitor
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveYouTubeStream(videoId: String): String? {
        val visitorData = getVisitorData()

        // 1. Primary: Velune Mobile Android Client (most reliable unthrottled streams, full length)
        val androidResult = resolveYouTubeStreamWithClient(
            videoId = videoId,
            clientName = "ANDROID",
            clientVersion = "21.10.38",
            userAgent = StreamClientUtils.USER_AGENT_ANDROID,
            endpoint = "https://www.youtube.com/youtubei/v1/player",
            clientId = "3",
            visitorData = visitorData,
            deviceSpecs = """
                "osName": "Android",
                "osVersion": "15",
                "deviceMake": "Google",
                "deviceModel": "Pixel 9 Pro",
                "androidSdkVersion": 35
            """.trimIndent()
        )
        if (!androidResult.isNullOrBlank()) return androidResult

        // 2. Fallback: Android VR client
        val vrResult = resolveYouTubeStreamWithClient(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            endpoint = "https://www.youtube.com/youtubei/v1/player",
            clientId = "28",
            visitorData = visitorData,
            deviceSpecs = """
                "osName": "Android",
                "osVersion": "12",
                "deviceMake": "Oculus",
                "deviceModel": "Quest 3",
                "androidSdkVersion": 32
            """.trimIndent()
        )
        if (!vrResult.isNullOrBlank()) return vrResult

        return null
    }

    private fun resolveYouTubeStreamWithClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        endpoint: String,
        clientId: String,
        visitorData: String? = null,
        deviceSpecs: String = ""
    ): String? {
        return try {
            val visitorField = if (!visitorData.isNullOrBlank()) """, "visitorData": "$visitorData"""" else ""
            val deviceFields = if (deviceSpecs.isNotBlank()) """, $deviceSpecs""" else ""

            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "$clientName",
                            "clientVersion": "$clientVersion",
                            "hl": "en",
                            "gl": "US"
                            $deviceFields
                            $visitorField
                        },
                        "request": {
                            "internalExperimentFlags": [],
                            "useSsl": true
                        },
                        "user": {
                            "lockedSafetyMode": false
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody.toRequestBody(JSON_MEDIA))
                .header("User-Agent", userAgent)
                .header("X-YouTube-Client-Name", clientId)
                .header("X-YouTube-Client-Version", clientVersion)
                .header("X-Goog-Api-Format-Version", "1")

            if (!visitorData.isNullOrBlank()) {
                reqBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val bodyString = response.body?.string() ?: return null
            val root = json.parseToJsonElement(bodyString).jsonObject

            val playabilityStatus = root["playabilityStatus"]?.jsonObject
            val status = playabilityStatus?.get("status")?.jsonPrimitive?.content
            if (status != "OK") {
                android.util.Log.w("InnerTubeClient", "YouTube client $clientName status: $status (reason: ${playabilityStatus?.get("reason")?.jsonPrimitive?.content})")
                return null
            }

            val streamingData = root["streamingData"]?.jsonObject ?: return null

            // 1. Check direct audio formats from adaptiveFormats
            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray?.mapNotNull { it.jsonObject }
            val directAudioFormats = adaptiveFormats?.filter {
                it["mimeType"]?.jsonPrimitive?.content?.startsWith("audio/") == true &&
                    !it["url"]?.jsonPrimitive?.content.isNullOrBlank()
            }

            if (!directAudioFormats.isNullOrEmpty()) {
                val bestAudio = directAudioFormats.maxByOrNull {
                    it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
                val url = bestAudio?.get("url")?.jsonPrimitive?.content
                if (!url.isNullOrBlank()) {
                    android.util.Log.d("InnerTubeClient", "Resolved adaptive direct audio stream from $clientName: bitrate=${bestAudio["bitrate"]?.jsonPrimitive?.content}")
                    return url
                }
            }

            // 2. Check direct progressive formats (ITAG 18/22: full length MP4 with AAC audio track)
            val formats = streamingData["formats"]?.jsonArray?.mapNotNull { it.jsonObject }
            val directFormats = formats?.filter { !it["url"]?.jsonPrimitive?.content.isNullOrBlank() }
            if (!directFormats.isNullOrEmpty()) {
                val progFormat = directFormats.firstOrNull {
                    it["mimeType"]?.jsonPrimitive?.content?.contains("mp4") == true
                } ?: directFormats.first()
                val url = progFormat["url"]?.jsonPrimitive?.content
                if (!url.isNullOrBlank()) {
                    val itag = progFormat["itag"]?.jsonPrimitive?.content
                    val dur = progFormat["approxDurationMs"]?.jsonPrimitive?.content
                    android.util.Log.d("InnerTubeClient", "Resolved progressive direct stream from $clientName: itag=$itag, durationMs=$dur")
                    return url
                }
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error resolving with $clientName: ${e.message}")
            null
        }
    }

    fun getYouTubePlaylistSongs(playlistId: String): List<SieloTrack> {
        return try {
            val cleanId = playlistId.substringAfter("list=").substringBefore("&").trim()
            val browseId = if (cleanId.startsWith("VL") || cleanId.startsWith("MPREb_") || cleanId.startsWith("FE")) {
                cleanId
            } else {
                "VL$cleanId"
            }
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

            val contents = root["contents"]?.jsonObject
            val tabs = contents?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?: contents?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                    ?.get("tabs")?.jsonArray

            val sectionList = tabs?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?: contents?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                    ?.get("secondaryContents")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray

            val tracks = mutableListOf<SieloTrack>()

            sectionList?.forEach { section ->
                val shelf = section.jsonObject["musicPlaylistShelfRenderer"]?.jsonObject
                    ?: section.jsonObject["musicShelfRenderer"]?.jsonObject
                shelf?.get("contents")?.jsonArray?.forEach { item ->
                    val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                    parseResponsiveItem(responsiveItem, isAlbumOrPlaylist = true)?.let { tracks.add(it) }
                }
            }

            val directShelf = tabs?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlaylistShelfRenderer")?.jsonObject
                ?: tabs?.getOrNull(0)?.jsonObject
                    ?.get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("musicShelfRenderer")?.jsonObject
                ?: contents?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                    ?.get("secondaryContents")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("musicPlaylistShelfRenderer")?.jsonObject
            directShelf?.get("contents")?.jsonArray?.forEach { item ->
                val responsiveItem = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                parseResponsiveItem(responsiveItem, isAlbumOrPlaylist = true)?.let { tracks.add(it) }
            }

            // Velune album extraction: If tracks is empty (or browseId was MPREb_), extract canonical playlistId
            if (tracks.isEmpty()) {
                val canonicalPlaylistId = root["microformat"]?.jsonObject
                    ?.get("microformatDataRenderer")?.jsonObject
                    ?.get("urlCanonical")?.jsonPrimitive?.content
                    ?.substringAfterLast("=", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: root["header"]?.jsonObject
                        ?.get("musicDetailHeaderRenderer")?.jsonObject
                        ?.get("menu")?.jsonObject
                        ?.get("menuRenderer")?.jsonObject
                        ?.get("topLevelButtons")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("buttonRenderer")?.jsonObject
                        ?.get("navigationEndpoint")?.jsonObject
                        ?.get("watchPlaylistEndpoint")?.jsonObject
                        ?.get("playlistId")?.jsonPrimitive?.content

                if (!canonicalPlaylistId.isNullOrBlank() && canonicalPlaylistId != cleanId && !cleanId.endsWith(canonicalPlaylistId)) {
                    val resolvedTracks = getYouTubePlaylistSongs("VL$canonicalPlaylistId")
                    if (resolvedTracks.isNotEmpty()) {
                        return resolvedTracks
                    }
                }
            }

            tracks.distinctBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

    private fun parseResponsiveItem(item: kotlinx.serialization.json.JsonObject, isAlbumOrPlaylist: Boolean = false): SieloTrack? {
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

            if (!isAlbumOrPlaylist && !isPureMusicTrack(title, artist)) {
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







