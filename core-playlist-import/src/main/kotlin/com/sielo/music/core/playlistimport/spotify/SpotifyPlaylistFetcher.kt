package com.sielo.music.core.playlistimport.spotify

import android.util.Base64
import com.sielo.music.core.playlistimport.BuildConfig
import com.sielo.music.core.playlistimport.model.ImportedTrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SpotifyPlaylistFetcher {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val jsonParser by lazy {
        Json { ignoreUnknownKeys = true; isLenient = true }
    }

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiryMs: Long = 0L

    private suspend fun getAccessToken(clientId: String, clientSecret: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val currentToken = cachedToken
        if (currentToken != null && now < tokenExpiryMs - 60_000L) {
            return@withContext currentToken
        }

        try {
            val authStr = "$clientId:$clientSecret"
            val basicHeader = "Basic " + Base64.encodeToString(authStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", basicHeader)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val obj = jsonParser.parseToJsonElement(body).jsonObject
                val token = obj["access_token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                if (token != null) {
                    cachedToken = token
                    tokenExpiryMs = now + (expiresIn * 1000L)
                }
                token
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchPlaylistTracksPaginated(
        playlistId: String
    ): List<ImportedTrackCandidate>? = withContext(Dispatchers.IO) {
        // 1. Try official Spotify Web API if client credentials are provided
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID.trim().trim('"', '\'')
        val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET.trim().trim('"', '\'')
        if (clientId.isNotBlank() && clientSecret.isNotBlank() && !clientId.contains("YOUR_") && !clientSecret.contains("YOUR_")) {
            val apiTracks = fetchViaOfficialWebApi(playlistId, clientId, clientSecret)
            if (!apiTracks.isNullOrEmpty()) {
                return@withContext apiTracks
            }
        }

        // 2. Automated GraphQL Persisted Query pagination (fetches ALL 100, 320, 500+ tracks without limits)
        val graphqlTracks = fetchPlaylistViaPartnerGraphql(playlistId)
        if (!graphqlTracks.isNullOrEmpty()) {
            return@withContext graphqlTracks
        }

        null
    }

    private suspend fun fetchViaOfficialWebApi(
        playlistId: String,
        clientId: String,
        clientSecret: String
    ): List<ImportedTrackCandidate>? = withContext(Dispatchers.IO) {
        val token = getAccessToken(clientId, clientSecret) ?: return@withContext null
        val candidates = mutableListOf<ImportedTrackCandidate>()

        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100&offset=0&fields=items(track(id,name,artists(name),duration_ms,is_local)),total,limit,offset,next"
        var retries = 0

        while (!nextUrl.isNullOrBlank()) {
            val request = Request.Builder()
                .url(nextUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.code == 429) {
                    val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 2L
                    response.close()
                    if (++retries <= 3) {
                        delay((retryAfterSeconds * 1000L).coerceIn(1000L, 10000L))
                        continue
                    } else {
                        break
                    }
                }
                if (!response.isSuccessful) {
                    response.close()
                    break
                }

                retries = 0
                val body = response.body?.string()
                response.close()
                if (body.isNullOrBlank()) break

                val root = jsonParser.parseToJsonElement(body).jsonObject
                val items = root["items"]?.jsonArray
                if (items != null) {
                    for (item in items) {
                        val trackObj = item.jsonObject["track"]?.takeIf { it !is JsonNull }?.jsonObject ?: continue
                        val name = trackObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (name.isBlank() || name == "Spotify") continue

                        val artists = trackObj["artists"]?.jsonArray?.mapNotNull {
                            if (it is JsonObject) it["name"]?.jsonPrimitive?.contentOrNull else null
                        }?.joinToString(", ") ?: ""

                        candidates.add(
                            ImportedTrackCandidate(
                                title = unescapeUnicode(name),
                                artistGuess = unescapeUnicode(artists),
                                sourcePlatform = "Spotify"
                            )
                        )
                    }
                }

                nextUrl = root["next"]?.jsonPrimitive?.contentOrNull
            } catch (e: Exception) {
                break
            }
        }

        if (candidates.isNotEmpty()) candidates else null
    }

    private suspend fun fetchPlaylistViaPartnerGraphql(
        playlistId: String
    ): List<ImportedTrackCandidate>? = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch embed page to get live session accessToken
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val embedRequest = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val embedHtml = client.newCall(embedRequest).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string() ?: return@withContext null
            }

            val scriptRegex = Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = scriptRegex.find(embedHtml)?.groupValues?.get(1) ?: return@withContext null
            val nextData = jsonParser.parseToJsonElement(jsonMatch).jsonObject

            val token = nextData["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
                ?.get("state")?.jsonObject
                ?.get("settings")?.jsonObject
                ?.get("session")?.jsonObject
                ?.get("accessToken")?.jsonPrimitive?.contentOrNull

            val candidates = mutableListOf<ImportedTrackCandidate>()

            // 2. If token is found, paginate completely through api-partner.spotify.com GraphQL (100 songs per page)
            if (!token.isNullOrBlank()) {
                var offset = 0
                var totalCount: Int? = null
                val limit = 100
                var retries = 0

                while (true) {
                    val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$limit}"""
                    val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"91d4c2bc3e0cd1bc672281c4f1f59f43ff55ba726ca04a45810d99bd091f3f0e"}}"""

                    val encodedVariables = URLEncoder.encode(variables, "UTF-8")
                    val encodedExtensions = URLEncoder.encode(extensions, "UTF-8")
                    val queryUrl = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=fetchPlaylist&variables=$encodedVariables&extensions=$encodedExtensions"

                    val req = Request.Builder()
                        .url(queryUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    val response = client.newCall(req).execute()
                    if (response.code == 429) {
                        response.close()
                        if (++retries <= 3) {
                            delay(1500L)
                            continue
                        } else {
                            break
                        }
                    }
                    if (!response.isSuccessful) {
                        response.close()
                        break
                    }

                    retries = 0
                    val bodyString = response.body?.string()
                    response.close()
                    if (bodyString.isNullOrBlank()) break

                    val root = jsonParser.parseToJsonElement(bodyString).jsonObject
                    val content = root["data"]?.jsonObject
                        ?.get("playlistV2")?.jsonObject
                        ?.get("content")?.jsonObject

                    if (totalCount == null) {
                        totalCount = content?.get("totalCount")?.jsonPrimitive?.intOrNull
                    }

                    val items = content?.get("items")?.jsonArray
                    if (items.isNullOrEmpty()) break

                    for (item in items) {
                        val trackData = item.jsonObject["itemV2"]?.jsonObject?.get("data")?.jsonObject ?: continue
                        val name = trackData["name"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (name.isBlank() || name == "Spotify") continue

                        val artistItems = trackData["artists"]?.jsonObject?.get("items")?.jsonArray
                        val artists = artistItems?.mapNotNull {
                            if (it is JsonObject) it["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull else null
                        }?.filter { it.isNotBlank() }?.joinToString(", ") ?: ""

                        candidates.add(
                            ImportedTrackCandidate(
                                title = unescapeUnicode(name),
                                artistGuess = unescapeUnicode(artists),
                                sourcePlatform = "Spotify"
                            )
                        )
                    }

                    offset += items.size
                    if (totalCount != null && offset >= totalCount) {
                        break
                    }
                }
            }

            // 3. Fallback: If GraphQL pagination yielded no tracks, extract whatever trackList was embedded in __NEXT_DATA__
            if (candidates.isEmpty()) {
                val entity = nextData["props"]?.jsonObject
                    ?.get("pageProps")?.jsonObject
                    ?.get("state")?.jsonObject
                    ?.get("data")?.jsonObject
                    ?.get("entity")?.jsonObject

                val trackList = entity?.get("trackList")?.jsonArray
                if (trackList != null) {
                    for (item in trackList) {
                        if (item is JsonObject) {
                            val title = item["title"]?.jsonPrimitive?.contentOrNull ?: continue
                            val subtitle = item["subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (title.isNotBlank() && title != "Spotify") {
                                candidates.add(
                                    ImportedTrackCandidate(
                                        title = unescapeUnicode(title),
                                        artistGuess = unescapeUnicode(subtitle),
                                        sourcePlatform = "Spotify"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) candidates else null
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeUnicode(text: String): String {
        return text.replace("\\u0026", "&")
            .replace("\\u0027", "'")
            .replace("\\u0022", "\"")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
