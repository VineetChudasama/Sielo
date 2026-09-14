package com.sielo.music.core.recommendations.repository

import com.sielo.music.core.database.dao.ArtistCountryCacheDao
import com.sielo.music.core.database.dao.SimilarArtistCacheDao
import com.sielo.music.core.database.entity.ArtistCountryCacheEntity
import com.sielo.music.core.database.entity.SimilarArtistCacheEntity
import com.sielo.music.core.recommendations.BuildConfig
import com.sielo.music.core.recommendations.api.LastFmApiService
import com.sielo.music.core.recommendations.api.MusicBrainzApiService
import com.sielo.music.core.recommendations.api.di.LastFmRateLimiter
import com.sielo.music.core.recommendations.api.di.MusicBrainzRateLimiter
import com.sielo.music.core.recommendations.model.LastFmImage
import com.sielo.music.core.recommendations.model.SimilarArtistResult
import com.sielo.music.core.recommendations.util.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class SimilarArtistsException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class SimilarArtistsRepository @Inject constructor(
    private val lastFmApiService: LastFmApiService,
    private val musicBrainzApiService: MusicBrainzApiService,
    private val similarArtistCacheDao: SimilarArtistCacheDao,
    private val artistCountryCacheDao: ArtistCountryCacheDao,
    @LastFmRateLimiter private val lastFmRateLimiter: RateLimiter,
    @MusicBrainzRateLimiter private val musicBrainzRateLimiter: RateLimiter,
    private val json: Json
) {
    companion object {
        const val SIMILAR_ARTISTS_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days
        const val ARTIST_COUNTRY_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days
        const val CANDIDATE_CONCURRENCY = 3
    }

    suspend fun getSimilarArtists(
        seedArtistName: String,
        limit: Int = 15
    ): Result<List<SimilarArtistResult>> = withContext(Dispatchers.IO) {
        val trimmedSeed = seedArtistName.trim()
        if (trimmedSeed.isBlank()) {
            return@withContext Result.failure(SimilarArtistsException("Seed artist name cannot be blank"))
        }

        val normalizedSeedId = trimmedSeed.lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()

        // 1. Check local cache
        try {
            val cached = similarArtistCacheDao.getCache(normalizedSeedId)
            if (cached != null && (now - cached.cachedAt) < SIMILAR_ARTISTS_CACHE_TTL_MS) {
                val parsed = json.decodeFromString<List<SimilarArtistResult>>(cached.resultsJson)
                if (parsed.isNotEmpty()) {
                    return@withContext Result.success(parsed.take(limit))
                }
            }
        } catch (_: Exception) {
            // Proceed to fetch if cache parse fails
        }

        // 2. Fetch fresh recommendations from external APIs
        try {
            val apiKey = BuildConfig.LASTFM_API_KEY

            val (seedTags, seedCountry, rawCandidates) = coroutineScope {
                val seedTagsDeferred = async { fetchArtistTags(trimmedSeed, apiKey) }
                val seedCountryDeferred = async { resolveArtistCountry(trimmedSeed) }
                val similarDeferred = async {
                    lastFmRateLimiter.acquire {
                        try {
                            lastFmApiService.getSimilarArtists(
                                artist = trimmedSeed,
                                apiKey = apiKey,
                                limit = 30
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                Triple(
                    seedTagsDeferred.await(),
                    seedCountryDeferred.await(),
                    similarDeferred.await()?.similarartists?.artist.orEmpty()
                )
            }

            val scoredResults: List<SimilarArtistResult>

            // c. Check if fallback is needed (< 5 candidates)
            if (rawCandidates.size < 5 && seedTags.isNotEmpty()) {
                scoredResults = fetchFallbackRecommendations(
                    seedArtistName = trimmedSeed,
                    seedTags = seedTags,
                    seedCountry = seedCountry,
                    apiKey = apiKey
                )
            } else {
                val semaphore = Semaphore(CANDIDATE_CONCURRENCY)
                scoredResults = coroutineScope {
                    rawCandidates
                        .filter { !it.name.equals(trimmedSeed, ignoreCase = true) }
                        .map { candidateDto ->
                            async {
                                semaphore.withPermit {
                                    enrichAndScoreCandidate(
                                        candidateName = candidateDto.name,
                                        candidateMbid = candidateDto.mbid,
                                        lastFmMatchScore = candidateDto.match?.toDoubleOrNull() ?: 0.0,
                                        images = candidateDto.image,
                                        seedTags = seedTags,
                                        seedCountry = seedCountry,
                                        apiKey = apiKey
                                    )
                                }
                            }
                        }.awaitAll()
                }.sortedByDescending { it.matchScore }
            }

            val finalResult = scoredResults.take(limit)

            // Cache the final results
            if (finalResult.isNotEmpty()) {
                val jsonString = json.encodeToString(finalResult)
                similarArtistCacheDao.insertCache(
                    SimilarArtistCacheEntity(
                        seedArtistId = normalizedSeedId,
                        resultsJson = jsonString,
                        cachedAt = System.currentTimeMillis()
                    )
                )
            }

            Result.success(finalResult)
        } catch (e: Exception) {
            // Stale cache fallback on failure
            val staleCache = try {
                similarArtistCacheDao.getCache(normalizedSeedId)
            } catch (_: Exception) {
                null
            }

            if (staleCache != null) {
                val parsed = try {
                    json.decodeFromString<List<SimilarArtistResult>>(staleCache.resultsJson)
                } catch (_: Exception) {
                    null
                }
                if (!parsed.isNullOrEmpty()) {
                    return@withContext Result.success(parsed.take(limit))
                }
            }

            Result.failure(SimilarArtistsException("Failed to fetch similar artists: ${e.message}", e))
        }
    }

    private suspend fun fetchArtistTags(artistName: String, apiKey: String): List<String> {
        return try {
            val response = lastFmRateLimiter.acquire {
                lastFmApiService.getTopTags(artist = artistName, apiKey = apiKey)
            }
            response.toptags?.tag
                ?.sortedByDescending { it.count ?: 0 }
                ?.map { it.name.trim().lowercase(Locale.ROOT) }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveArtistCountry(artistName: String): String? {
        val normalized = artistName.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null

        val now = System.currentTimeMillis()
        try {
            val cached = artistCountryCacheDao.getCountry(normalized)
            if (cached != null && (now - cached.cachedAt) < ARTIST_COUNTRY_CACHE_TTL_MS) {
                return cached.country
            }
        } catch (_: Exception) {
            // Proceed to network lookup
        }

        return try {
            val query = "artist:\"${artistName.trim()}\""
            val response = musicBrainzRateLimiter.acquire {
                musicBrainzApiService.searchArtist(query = query, limit = 3)
            }

            val country = response.artists.firstOrNull()?.let { artist ->
                artist.country
                    ?: artist.area?.isoCodes?.firstOrNull()
                    ?: artist.area?.name
                    ?: artist.beginArea?.isoCodes?.firstOrNull()
                    ?: artist.beginArea?.name
            }

            artistCountryCacheDao.insertCountry(
                ArtistCountryCacheEntity(
                    artistName = normalized,
                    country = country,
                    cachedAt = System.currentTimeMillis()
                )
            )
            country
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun enrichAndScoreCandidate(
        candidateName: String,
        candidateMbid: String?,
        lastFmMatchScore: Double,
        images: List<LastFmImage>?,
        seedTags: List<String>,
        seedCountry: String?,
        apiKey: String
    ): SimilarArtistResult = coroutineScope {
        val candidateTagsDeferred = async { fetchArtistTags(candidateName, apiKey) }
        val candidateCountryDeferred = async { resolveArtistCountry(candidateName) }

        val candidateTags = candidateTagsDeferred.await()
        val candidateCountry = candidateCountryDeferred.await()

        val sharedTags = seedTags.intersect(candidateTags.toSet()).toList()
        val allUniqueTags = (seedTags + candidateTags).distinct()
        val tagOverlapScore = if (allUniqueTags.isNotEmpty()) {
            sharedTags.size.toDouble() / allUniqueTags.size.toDouble()
        } else {
            0.0
        }

        val originBonus = if (!seedCountry.isNullOrBlank() &&
            !candidateCountry.isNullOrBlank() &&
            candidateCountry.equals(seedCountry, ignoreCase = true)
        ) {
            1.0
        } else {
            0.0
        }

        val finalScore = (0.6 * lastFmMatchScore) + (0.25 * tagOverlapScore) + (0.15 * originBonus)
        val bestImage = extractBestImage(images)

        SimilarArtistResult(
            id = candidateMbid?.takeIf { it.isNotBlank() } ?: candidateName.lowercase(Locale.ROOT),
            name = candidateName,
            country = candidateCountry,
            imageUrl = bestImage,
            matchScore = finalScore,
            lastfmScore = lastFmMatchScore,
            tagOverlapScore = tagOverlapScore,
            originBonus = originBonus,
            sharedTags = sharedTags
        )
    }

    private suspend fun fetchFallbackRecommendations(
        seedArtistName: String,
        seedTags: List<String>,
        seedCountry: String?,
        apiKey: String
    ): List<SimilarArtistResult> {
        val fallbackCandidates = mutableMapOf<String, String?>()

        for (tag in seedTags.take(3)) {
            try {
                val tagArtists = lastFmRateLimiter.acquire {
                    lastFmApiService.getTagTopArtists(tag = tag, apiKey = apiKey, limit = 20)
                }
                tagArtists.topartists?.artist.orEmpty().forEach { artistDto ->
                    if (!artistDto.name.equals(seedArtistName, ignoreCase = true) &&
                        !fallbackCandidates.containsKey(artistDto.name)
                    ) {
                        fallbackCandidates[artistDto.name] = extractBestImage(artistDto.image)
                    }
                }
            } catch (_: Exception) {}
        }

        val semaphore = Semaphore(CANDIDATE_CONCURRENCY)
        return coroutineScope {
            fallbackCandidates.entries.take(30).map { (candidateName, imageUrl) ->
                async {
                    semaphore.withPermit {
                        val candidateTagsDeferred = async { fetchArtistTags(candidateName, apiKey) }
                        val candidateCountryDeferred = async { resolveArtistCountry(candidateName) }

                        val candidateTags = candidateTagsDeferred.await()
                        val candidateCountry = candidateCountryDeferred.await()

                        val sharedTags = seedTags.intersect(candidateTags.toSet()).toList()
                        val allUniqueTags = (seedTags + candidateTags).distinct()
                        val tagOverlapScore = if (allUniqueTags.isNotEmpty()) {
                            sharedTags.size.toDouble() / allUniqueTags.size.toDouble()
                        } else {
                            0.0
                        }

                        val originBonus = if (!seedCountry.isNullOrBlank() &&
                            !candidateCountry.isNullOrBlank() &&
                            candidateCountry.equals(seedCountry, ignoreCase = true)
                        ) {
                            1.0
                        } else {
                            0.0
                        }

                        val finalScore = (0.7 * tagOverlapScore) + (0.3 * originBonus)

                        SimilarArtistResult(
                            id = candidateName.lowercase(Locale.ROOT),
                            name = candidateName,
                            country = candidateCountry,
                            imageUrl = imageUrl,
                            matchScore = finalScore,
                            lastfmScore = 0.0,
                            tagOverlapScore = tagOverlapScore,
                            originBonus = originBonus,
                            sharedTags = sharedTags
                        )
                    }
                }
            }.awaitAll()
        }.sortedByDescending { it.matchScore }
    }

    private fun extractBestImage(images: List<LastFmImage>?): String? {
        if (images.isNullOrEmpty()) return null
        return images.find { it.size == "mega" && !it.url.isNullOrBlank() }?.url
            ?: images.find { it.size == "extralarge" && !it.url.isNullOrBlank() }?.url
            ?: images.find { it.size == "large" && !it.url.isNullOrBlank() }?.url
            ?: images.find { it.size == "medium" && !it.url.isNullOrBlank() }?.url
            ?: images.find { !it.url.isNullOrBlank() }?.url
    }
}
