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

    suspend fun getAlbumSongs(albumId: String): List<SieloTrack> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&cc=in&albumid=$albumId"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(bodyString).jsonObject
            val songArray = root["songs"]?.jsonArray ?: root["list"]?.jsonArray ?: return@withContext emptyList()

            songArray.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val songTitle = unescapeHtml(obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "Unknown")
                val songArtist = unescapeHtml(obj["primary_artists"]?.jsonPrimitive?.content ?: obj["singers"]?.jsonPrimitive?.content ?: "Artist")
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
             .distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Error fetching album songs for $albumId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getArtistDetails(artistIdOrName: String, artistImageUrl: String? = null): ArtistDetails? = withContext(Dispatchers.IO) {
        // 1. Check in-memory session cache first
        val cached = artistProfileCache.get(artistIdOrName)
        if (cached != null) {
            return@withContext cached
        }

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
                artists.firstOrNull()?.id ?: return@withContext createGuaranteedArtistProfile(artistIdOrName, ytPhoto)
            }

            val url = "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$artistId&n_song=15&n_album=10"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext createGuaranteedArtistProfile(artistIdOrName, ytPhoto)
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

            // Top Albums & Past Albums with full tracks
            val topAlbumsObj = root["topAlbums"]?.jsonObject
            val albumArray = topAlbumsObj?.get("albums")?.jsonArray ?: root["albums"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

            val rawAlbums = albumArray.mapNotNull { item ->
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

            // Concurrently fetch tracks for each album
            val fullAlbums = rawAlbums.map { album ->
                val songs = if (album.id.all { it.isDigit() }) {
                    getAlbumSongs(album.id)
                } else emptyList()

                val resolvedSongs = if (songs.isNotEmpty()) {
                    songs
                } else {
                    topSongs.filter { it.album.equals(album.title, ignoreCase = true) }
                }

                album.copy(
                    tracks = resolvedSongs,
                    songCount = if (resolvedSongs.isNotEmpty()) resolvedSongs.size else 4
                )
            }

            // Fallback: If no albums or all empty, dynamically group top tracks or search songs to ensure profile is never empty
            val pastAlbums = if (fullAlbums.isNotEmpty() && fullAlbums.any { it.tracks.isNotEmpty() }) {
                fullAlbums
            } else {
                val searchFallback = search("$name album hits").take(12)
                val combinedSongs = (topSongs + searchFallback).distinctBy { it.id }
                val grouped = combinedSongs.groupBy { it.album ?: "$name Collection" }
                val fallbackList = mutableListOf<SieloAlbum>()
                for ((albName, trks) in grouped) {
                    fallbackList.add(
                        SieloAlbum(
                            id = "alb_${kotlin.math.abs((name + albName).hashCode())}",
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

            val latestAlbum = pastAlbums.maxByOrNull { it.year?.toIntOrNull() ?: 0 } ?: pastAlbums.firstOrNull()

            val details = ArtistDetails(
                id = artistId,
                name = name,
                imageUrl = finalImage,
                bio = "Official Artist on Sielo",
                latestAlbum = latestAlbum,
                topSongs = topSongs,
                pastAlbums = pastAlbums
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

    private suspend fun createGuaranteedArtistProfile(artistName: String, imageUrl: String?): ArtistDetails {
        val tracks = search("$artistName top songs").take(8)
        val image = imageUrl ?: tracks.firstOrNull()?.thumbnailUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80"
        val album = SieloAlbum(
            id = "alb_fallback",
            title = "$artistName Top Hits",
            artist = artistName,
            year = "2024",
            thumbnailUrl = image,
            tracks = tracks,
            songCount = tracks.size
        )
        return ArtistDetails(
            id = artistName,
            name = artistName,
            imageUrl = image,
            bio = "Official Artist on Sielo",
            latestAlbum = album,
            topSongs = tracks.take(5),
            pastAlbums = listOf(album)
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