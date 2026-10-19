package com.sielo.music.core.network.recommendations

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RecommendationApiClient @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Configurable endpoint (live Render deployment)
    var baseUrl: String = "https://sielo.onrender.com"
    var tokenProvider: (suspend () -> String?)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    open suspend fun getRecommendations(request: RecommendationRequestDto): RecommendationResponseDto? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v1/recommendations"
            val bodyString = json.encodeToString(request)
            val requestBody = bodyString.toRequestBody(jsonMediaType)

            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            val token = tokenProvider?.invoke()
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            } else {
                reqBuilder.addHeader("Authorization", "Bearer dev_token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("RecommendationApi", "Recommendation request returned HTTP ${response.code}")
                    return@withContext null
                }
                val responseBody = response.body?.string() ?: return@withContext null
                json.decodeFromString<RecommendationResponseDto>(responseBody)
            }
        } catch (e: Exception) {
            Log.d("RecommendationApi", "Recommendation API unavailable, using local fallback: ${e.message}")
            null
        }
    }

    open suspend fun sendBatchEvents(events: List<ClientListeningEvent>): Boolean = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext true
        try {
            val url = "$baseUrl/v1/events"
            val batchDto = BatchEventsRequestDto(events)
            val requestBody = json.encodeToString(batchDto).toRequestBody(jsonMediaType)

            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            val token = tokenProvider?.invoke()
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            } else {
                reqBuilder.addHeader("Authorization", "Bearer dev_token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.d("RecommendationApi", "Could not send batch events to backend: ${e.message}")
            false
        }
    }

    open suspend fun getTrending(market: String = "IN"): TrendingBatchDto? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v1/trending?market=$market"
            val reqBuilder = Request.Builder()
                .url(url)
                .get()

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("RecommendationApi", "Trending request returned HTTP ${response.code}")
                    return@withContext null
                }
                val responseBody = response.body?.string() ?: return@withContext null
                json.decodeFromString<TrendingBatchDto>(responseBody)
            }
        } catch (e: Exception) {
            Log.d("RecommendationApi", "Trending API unavailable, using local fallback: ${e.message}")
            null
        }
    }
}
