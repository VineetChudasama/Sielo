package com.sielo.music.core.network.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Respect MusicBrainz rate limit policy (maximum 1 request per second per IP)
    private val rateLimitMutex = Mutex()
    private var lastRequestTimeMs = 0L
    private val minRequestIntervalMs = 950L

    companion object {
        private const val TAG = "MusicBrainz"
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "Sielo/10.1.6 ( https://github.com/sielo/music ; contact@sielo.app )"
    }

    private suspend fun throttle() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimeMs
            if (elapsed < minRequestIntervalMs) {
                delay(minRequestIntervalMs - elapsed)
            }
            lastRequestTimeMs = System.currentTimeMillis()
        }
    }

    /**
     * Executes an HTTP GET request to MusicBrainz with automatic rate limiting and retry on 503.
     */
    private suspend fun executeGet(url: String, attempt: Int = 1): String? = withContext(Dispatchers.IO) {
        throttle()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string()

            if (response.isSuccessful && !body.isNullOrBlank()) {
                return@withContext body
            }

            if (code == 503 && attempt <= 2) {
                Log.w(TAG, "MusicBrainz returned 503 (rate limited/busy). Retrying in 1.5s...")
                delay(1500L)
                return@withContext executeGet(url, attempt + 1)
            }

            if (code == 404) {
                Log.d(TAG, "MusicBrainz 404 for url: $url")
                return@withContext null
            }

            Log.w(TAG, "MusicBrainz HTTP $code for url: $url")
            null
        } catch (e: Exception) {
            Log.e(TAG, "MusicBrainz network error: ${e.message} (url: $url)")
            null
        }
    }

    /**
     * Searches for an artist by name and returns the best matching MbArtist.
     * Matches exact name, cleans punctuation, and considers match score.
     */
    suspend fun searchArtist(artistName: String): MbArtist? = withContext(Dispatchers.IO) {
        val trimmed = artistName.trim()
        if (trimmed.isBlank()) return@withContext null

        Log.d(TAG, "Searching artist: $trimmed")
        val escapedQuery = trimmed.replace("\"", "")
        val encoded = URLEncoder.encode("artist:\"$escapedQuery\"", "UTF-8")
        val url = "$BASE_URL/artist?query=$encoded&fmt=json&limit=10"

        val body = executeGet(url) ?: return@withContext null
        try {
            val response = json.decodeFromString<MbArtistSearchResponse>(body)
            val artists = response.artists
            if (artists.isEmpty()) {
                Log.d(TAG, "No artists found for: $trimmed")
                return@withContext null
            }

            // 1. Exact case-insensitive match with high score
            val exactMatch = artists.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            if (exactMatch != null) {
                Log.d(TAG, "Found exact match: ${exactMatch.name} (MBID: ${exactMatch.id})")
                return@withContext exactMatch
            }

            // 2. Normalized match (without special characters)
            val normalizedTarget = normalizeName(trimmed)
            val normalizedMatch = artists.firstOrNull { normalizeName(it.name) == normalizedTarget }
            if (normalizedMatch != null) {
                Log.d(TAG, "Found normalized match: ${normalizedMatch.name} (MBID: ${normalizedMatch.id})")
                return@withContext normalizedMatch
            }

            // 3. Highest score match
            val topScore = artists.maxByOrNull { it.score ?: 0 }
            if (topScore != null && (topScore.score ?: 0) >= 80) {
                Log.d(TAG, "Found high-score match: ${topScore.name} (MBID: ${topScore.id}, score: ${topScore.score})")
                return@withContext topScore
            }

            artists.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search artist response: ${e.message}", e)
            null
        }
    }

    /**
     * Retrieves all release groups associated with an artist MBID.
     * Implements pagination to fetch full catalog up to maxLimit.
     */
    suspend fun getArtistReleaseGroups(artistMbid: String, maxLimit: Int = 200): List<MbReleaseGroup> = withContext(Dispatchers.IO) {
        if (artistMbid.isBlank()) return@withContext emptyList()

        val allReleaseGroups = mutableListOf<MbReleaseGroup>()
        var offset = 0
        val pageSize = 100

        while (offset < maxLimit) {
            val url = "$BASE_URL/release-group?artist=$artistMbid&inc=artist-credits&limit=$pageSize&offset=$offset&fmt=json"
            val body = executeGet(url) ?: break

            try {
                val response = json.decodeFromString<MbReleaseGroupListResponse>(body)
                val batch = response.releaseGroups
                if (batch.isEmpty()) break

                allReleaseGroups.addAll(batch)
                offset += batch.size

                if (offset >= response.releaseGroupCount || batch.size < pageSize) {
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing release groups at offset $offset: ${e.message}", e)
                break
            }
        }

        Log.d(TAG, "Retrieved ${allReleaseGroups.size} release groups for MBID: $artistMbid")
        allReleaseGroups
    }

    /**
     * Retrieves releases where the artist is credited.
     * Crucial for finding movie soundtrack releases where the artist sang individual songs.
     */
    suspend fun getArtistReleases(artistMbid: String, limit: Int = 100): List<MbRelease> = withContext(Dispatchers.IO) {
        if (artistMbid.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/release?artist=$artistMbid&inc=release-groups+artist-credits+media+recordings&limit=$limit&fmt=json"
        val body = executeGet(url) ?: return@withContext emptyList()

        try {
            val response = json.decodeFromString<MbReleaseListResponse>(body)
            response.releases
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artist releases: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Retrieves releases and recordings for a specific release-group MBID.
     */
    suspend fun getReleaseGroupReleases(releaseGroupMbid: String): List<MbRelease> = withContext(Dispatchers.IO) {
        if (releaseGroupMbid.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/release?release-group=$releaseGroupMbid&inc=recordings+artist-credits&limit=10&fmt=json"
        val body = executeGet(url) ?: return@withContext emptyList()

        try {
            val response = json.decodeFromString<MbReleaseListResponse>(body)
            response.releases
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing release group releases for $releaseGroupMbid: ${e.message}", e)
            emptyList()
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }
}
