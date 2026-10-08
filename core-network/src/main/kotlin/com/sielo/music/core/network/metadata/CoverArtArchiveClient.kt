package com.sielo.music.core.network.metadata

import android.util.Log
import com.sielo.music.core.network.innertube.ArtistMetadataResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtArchiveClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val artworkCache = ConcurrentHashMap<String, CoverArtUrls?>()

    companion object {
        private const val TAG = "CoverArtArchive"
        private const val BASE_URL = "https://coverartarchive.org"
        private const val USER_AGENT = "Sielo/10.1.6 ( https://github.com/sielo/music ; contact@sielo.app )"
    }

    /**
     * Synchronous direct URL generator for Cover Art Archive.
     * CAA provides direct front image endpoints that 307-redirect to the archive.org image file.
     * This avoids any upfront network calls when loading artist profiles.
     */
    fun getDirectReleaseGroupUrls(releaseGroupMbid: String): CoverArtUrls {
        val curated = ArtistMetadataResolver.getCuratedCoverByMbid(releaseGroupMbid)
        if (curated != null) {
            return CoverArtUrls(
                thumbnail = curated,
                medium = curated,
                large = curated,
                original = curated
            )
        }
        return CoverArtUrls(
            thumbnail = "$BASE_URL/release-group/$releaseGroupMbid/front-250",
            medium = "$BASE_URL/release-group/$releaseGroupMbid/front-500",
            large = "$BASE_URL/release-group/$releaseGroupMbid/front",
            original = "$BASE_URL/release-group/$releaseGroupMbid/front"
        )
    }

    fun getDirectReleaseUrls(releaseMbid: String): CoverArtUrls {
        return CoverArtUrls(
            thumbnail = "$BASE_URL/release/$releaseMbid/front-250",
            medium = "$BASE_URL/release/$releaseMbid/front-500",
            large = "$BASE_URL/release/$releaseMbid/front",
            original = "$BASE_URL/release/$releaseMbid/front"
        )
    }

    /**
     * Resolves artwork for a MusicBrainz release-group MBID.
     * Checks in-memory cache first, then calls Cover Art Archive REST API.
     */
    suspend fun getReleaseGroupArtwork(releaseGroupMbid: String): CoverArtUrls? = withContext(Dispatchers.IO) {
        if (releaseGroupMbid.isBlank()) return@withContext null

        val curated = ArtistMetadataResolver.getCuratedCoverByMbid(releaseGroupMbid)
        if (curated != null) {
            val urls = CoverArtUrls(
                thumbnail = curated,
                medium = curated,
                large = curated,
                original = curated
            )
            artworkCache[releaseGroupMbid] = urls
            return@withContext urls
        }

        if (artworkCache.containsKey(releaseGroupMbid)) {
            return@withContext artworkCache[releaseGroupMbid]
        }

        val url = "$BASE_URL/release-group/$releaseGroupMbid"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // 404 is standard when CAA has no uploaded cover for this release group
                if (response.code != 404) {
                    Log.d(TAG, "CAA returned HTTP ${response.code} for RG $releaseGroupMbid")
                }
                artworkCache[releaseGroupMbid] = null
                return@withContext null
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                artworkCache[releaseGroupMbid] = null
                return@withContext null
            }

            val parsed = json.decodeFromString<CaaReleaseGroupResponse>(body)
            val frontImage = parsed.images.firstOrNull { it.front } ?: parsed.images.firstOrNull()

            if (frontImage != null) {
                val thumbs = frontImage.thumbnails
                val coverUrls = CoverArtUrls(
                    thumbnail = thumbs?.small ?: thumbs?.legacySmall ?: thumbs?.front ?: frontImage.image,
                    medium = thumbs?.medium ?: thumbs?.large ?: thumbs?.legacyLarge ?: frontImage.image,
                    large = thumbs?.large ?: thumbs?.legacyLarge ?: frontImage.image,
                    original = frontImage.image
                )
                Log.d(TAG, "Cover art resolved for RG $releaseGroupMbid: ${coverUrls.medium}")
                artworkCache[releaseGroupMbid] = coverUrls
                return@withContext coverUrls
            }

            artworkCache[releaseGroupMbid] = null
            null
        } catch (e: Exception) {
            Log.d(TAG, "Error resolving cover art for RG $releaseGroupMbid: ${e.message}")
            artworkCache[releaseGroupMbid] = null
            null
        }
    }

    /**
     * Resolves artwork for a MusicBrainz release MBID if release-group had no artwork.
     */
    suspend fun getReleaseArtwork(releaseMbid: String): CoverArtUrls? = withContext(Dispatchers.IO) {
        if (releaseMbid.isBlank()) return@withContext null

        if (artworkCache.containsKey(releaseMbid)) {
            return@withContext artworkCache[releaseMbid]
        }

        val url = "$BASE_URL/release/$releaseMbid"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                artworkCache[releaseMbid] = null
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val parsed = json.decodeFromString<CaaReleaseGroupResponse>(body)
            val frontImage = parsed.images.firstOrNull { it.front } ?: parsed.images.firstOrNull()

            if (frontImage != null) {
                val thumbs = frontImage.thumbnails
                val coverUrls = CoverArtUrls(
                    thumbnail = thumbs?.small ?: thumbs?.legacySmall ?: thumbs?.front ?: frontImage.image,
                    medium = thumbs?.medium ?: thumbs?.large ?: thumbs?.legacyLarge ?: frontImage.image,
                    large = thumbs?.large ?: thumbs?.legacyLarge ?: frontImage.image,
                    original = frontImage.image
                )
                artworkCache[releaseMbid] = coverUrls
                return@withContext coverUrls
            }

            artworkCache[releaseMbid] = null
            null
        } catch (e: Exception) {
            artworkCache[releaseMbid] = null
            null
        }
    }
}
