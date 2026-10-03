package com.sielo.music.core.network.innertube

import com.sielo.music.core.network.models.SieloAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class VerifiedArtistBio(
    val bio: String? = null,
    val description: String? = null,
    val origin: String? = null,
    val activeYears: String? = null,
    val wikiUrl: String? = null,
    val photoUrl: String? = null
)

data class VerifiedDiscography(
    val studioAlbums: List<SieloAlbum> = emptyList(),
    val singlesAndEPs: List<SieloAlbum> = emptyList(),
    val primaryGenres: List<String> = emptyList(),
    val recordLabel: String? = null
)

object ArtistMetadataResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val bioCache = ConcurrentHashMap<String, VerifiedArtistBio>()
    private val discographyCache = ConcurrentHashMap<String, VerifiedDiscography>()
    private val photoCache = ConcurrentHashMap<String, String>()

    suspend fun fetchWikipediaBio(artistName: String): VerifiedArtistBio? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || TrackMatchValidator.isYouTubeChannelId(cleanName)) return@withContext null

        val cacheKey = cleanName.lowercase()
        bioCache[cacheKey]?.let { return@withContext it }

        fun isValidArtistSummary(extract: String?, desc: String?, type: String?): Boolean {
            if (extract.isNullOrBlank() || extract.length < 35) return false
            if (type.equals("disambiguation", ignoreCase = true)) return false
            val lowerDesc = desc?.lowercase().orEmpty()
            if (lowerDesc.contains("disambiguation") || lowerDesc.contains("name list") || lowerDesc.contains("given name")) return false
            return true
        }

        try {
            // 1. Direct Wikipedia REST summary
            val encodedName = URLEncoder.encode(cleanName.replace(" ", "_"), "UTF-8")
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedName"
            val request = Request.Builder()
                .url(summaryUrl)
                .addHeader("User-Agent", "SieloMusicApp/10.1.2 (https://github.com/sielo; support@sielo.app)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val type = root["type"]?.jsonPrimitive?.content
                    val extract = root["extract"]?.jsonPrimitive?.content
                    val desc = root["description"]?.jsonPrimitive?.content
                    val wikiPageUrl = root["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.content
                    val thumbUrl = root["originalimage"]?.jsonObject?.get("source")?.jsonPrimitive?.content
                        ?: root["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

                    if (isValidArtistSummary(extract, desc, type) && !thumbUrl.isNullOrBlank()) {
                        val result = VerifiedArtistBio(
                            bio = extract,
                            description = desc,
                            wikiUrl = wikiPageUrl,
                            photoUrl = thumbUrl
                        )
                        bioCache[cacheKey] = result
                        return@withContext result
                    }
                }
            }

            // 2. Direct lookup for (singer), (musician), (band)
            val candidateTitles = listOf("${cleanName}_(singer)", "${cleanName}_(musician)", "${cleanName}_(band)")
            for (cand in candidateTitles) {
                val encCand = URLEncoder.encode(cand.replace(" ", "_"), "UTF-8")
                val candReq = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encCand")
                    .addHeader("User-Agent", "SieloMusicApp/10.1.2")
                    .build()
                val candResp = client.newCall(candReq).execute()
                if (candResp.isSuccessful) {
                    val b = candResp.body?.string()
                    if (!b.isNullOrBlank()) {
                        val r = json.parseToJsonElement(b).jsonObject
                        val type = r["type"]?.jsonPrimitive?.content
                        val extract = r["extract"]?.jsonPrimitive?.content
                        val desc = r["description"]?.jsonPrimitive?.content
                        val wikiPageUrl = r["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.content
                        val thumbUrl = r["originalimage"]?.jsonObject?.get("source")?.jsonPrimitive?.content
                            ?: r["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

                        if (isValidArtistSummary(extract, desc, type)) {
                            val result = VerifiedArtistBio(
                                bio = extract,
                                description = desc,
                                wikiUrl = wikiPageUrl,
                                photoUrl = thumbUrl
                            )
                            bioCache[cacheKey] = result
                            return@withContext result
                        }
                    }
                }
            }

            // 3. Wikipedia Search Fallback
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${URLEncoder.encode("$cleanName singer musician", "UTF-8")}&format=json"
            val searchReq = Request.Builder().url(searchUrl).addHeader("User-Agent", "SieloMusicApp/10.1.2").build()
            val searchResp = client.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val sBody = searchResp.body?.string()
                if (!sBody.isNullOrBlank()) {
                    val sRoot = json.parseToJsonElement(sBody).jsonObject
                    val searchItems = sRoot["query"]?.jsonObject?.get("search")?.jsonArray
                    for (item in searchItems.orEmpty().take(3)) {
                        val title = item.jsonObject["title"]?.jsonPrimitive?.content ?: continue
                        val cleanTarget = cleanName.lowercase().trim()
                        val searchTokens = cleanTarget.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
                        val titleClean = title.substringBefore("(").trim().lowercase()
                        val titleTokens = titleClean.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }

                        // Prevent completely unrelated artists from matching (e.g. Kishore Kumar matching Mukesh Kumar)
                        val matchesClean = titleClean == cleanTarget
                        val hasFirstTokenMatch = searchTokens.isNotEmpty() && titleTokens.isNotEmpty() && searchTokens.first() == titleTokens.first()
                        val containsAllTokens = searchTokens.isNotEmpty() && searchTokens.all { titleClean.contains(it) }

                        if (!matchesClean && !hasFirstTokenMatch && !containsAllTokens) {
                            continue
                        }

                        val encFirst = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                        val fallbackReq = Request.Builder()
                            .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encFirst")
                            .addHeader("User-Agent", "SieloMusicApp/10.1.2")
                            .build()
                        val fbResp = client.newCall(fallbackReq).execute()
                        if (fbResp.isSuccessful) {
                            val fbBody = fbResp.body?.string()
                            if (!fbBody.isNullOrBlank()) {
                                val fbRoot = json.parseToJsonElement(fbBody).jsonObject
                                val type = fbRoot["type"]?.jsonPrimitive?.content
                                val extract = fbRoot["extract"]?.jsonPrimitive?.content
                                val desc = fbRoot["description"]?.jsonPrimitive?.content
                                val wikiPageUrl = fbRoot["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.content
                                val thumbUrl = fbRoot["originalimage"]?.jsonObject?.get("source")?.jsonPrimitive?.content
                                    ?: fbRoot["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

                                if (isValidArtistSummary(extract, desc, type)) {
                                    val result = VerifiedArtistBio(
                                        bio = extract,
                                        description = desc,
                                        wikiUrl = wikiPageUrl,
                                        photoUrl = thumbUrl
                                    )
                                    bioCache[cacheKey] = result
                                    return@withContext result
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Resolves verified, genuine discography directly from the Apple iTunes Store Catalog & Deezer.
     * Guaranteed to never contain user playlists, YouTube DJ mixes, or generic compilations.
     */
    suspend fun fetchVerifiedDiscography(artistName: String): VerifiedDiscography = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || TrackMatchValidator.isYouTubeChannelId(cleanName)) {
            return@withContext VerifiedDiscography()
        }

        val cacheKey = cleanName.lowercase()
        discographyCache[cacheKey]?.let { return@withContext it }

        val studioAlbums = mutableListOf<SieloAlbum>()
        val singlesAndEPs = mutableListOf<SieloAlbum>()
        val genres = mutableSetOf<String>()
        var resolvedLabel: String? = null

        // 1. Fetch from Apple iTunes API (Direct verified release catalog)
        try {
            val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encodedQuery&entity=album&limit=100"
            val req = Request.Builder()
                .url(itunesUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val results = root["results"]?.jsonArray
                    results?.forEach { item ->
                        val obj = item.jsonObject
                        val collectionName = obj["collectionName"]?.jsonPrimitive?.content ?: return@forEach
                        val releaseArtist = obj["artistName"]?.jsonPrimitive?.content ?: cleanName
                        val collectionType = obj["collectionType"]?.jsonPrimitive?.content ?: "Album"
                        val trackCount = obj["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                        val rawDate = obj["releaseDate"]?.jsonPrimitive?.content
                        val year = rawDate?.take(4) ?: "2024"
                        val rawArtwork = obj["artworkUrl100"]?.jsonPrimitive?.content
                        val highResArtwork = rawArtwork?.replace("100x100bb.jpg", "600x600bb.jpg")
                            ?.replace("100x100", "600x600")
                        val genre = obj["primaryGenreName"]?.jsonPrimitive?.content
                        val copyright = obj["copyright"]?.jsonPrimitive?.content

                        if (!genre.isNullOrBlank() && genre != "Music") {
                            genres.add(genre)
                        }
                        if (resolvedLabel == null && !copyright.isNullOrBlank()) {
                            resolvedLabel = copyright.replace(Regex("(?i)^[℗©]\\s*\\d*\\s*"), "").trim()
                        }

                        // Strict verification: Reject generic compilations, DJ mixes, spam playlists
                        if (TrackMatchValidator.isCompilationAlbum(collectionName, cleanName)) {
                            return@forEach
                        }
                        if (!TrackMatchValidator.isAlbumMadeByArtist(collectionName, releaseArtist, cleanName)) {
                            return@forEach
                        }

                        val lowerTitle = collectionName.lowercase()
                        val isSingleOrEP = trackCount == 1 ||
                                collectionType.equals("Single", ignoreCase = true) ||
                                lowerTitle.contains(" - single") ||
                                lowerTitle.contains(" (single)") ||
                                lowerTitle.contains(" - ep") ||
                                lowerTitle.contains(" (ep)")

                        val itunesCollId = obj["collectionId"]?.jsonPrimitive?.content
                        val albumId = if (!itunesCollId.isNullOrBlank()) "itunes_$itunesCollId" else "itunes_${kotlin.math.abs((collectionName + cleanName).hashCode())}"

                        if (isSingleOrEP) {
                            singlesAndEPs.add(
                                SieloAlbum(
                                    id = albumId,
                                    title = collectionName.replace(Regex("(?i)\\s*-\\s*(single|ep)$"), "").trim(),
                                    artist = releaseArtist,
                                    year = year,
                                    thumbnailUrl = highResArtwork,
                                    type = if (trackCount in 2..6 || lowerTitle.contains("ep")) "EP" else "Single",
                                    songCount = trackCount
                                )
                            )
                        } else if (!TrackMatchValidator.isSoundtrackRelease(collectionName, collectionType)) {
                            studioAlbums.add(
                                SieloAlbum(
                                    id = albumId,
                                    title = collectionName,
                                    artist = releaseArtist,
                                    year = year,
                                    thumbnailUrl = highResArtwork,
                                    type = "Album",
                                    songCount = trackCount
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ArtistMetadataResolver", "iTunes discography fetch error: ${e.message}")
        }

        // 2. Fetch from Deezer API to supplement albums/singles and high-res cover art
        try {
            val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
            val deezerSearchUrl = "https://api.deezer.com/search/artist?q=$encodedQuery"
            val dzReq = Request.Builder().url(deezerSearchUrl).build()
            val dzResp = client.newCall(dzReq).execute()
            if (dzResp.isSuccessful) {
                val dzBody = dzResp.body?.string()
                if (!dzBody.isNullOrBlank()) {
                    val dzRoot = json.parseToJsonElement(dzBody).jsonObject
                    val dzResults = dzRoot["data"]?.jsonArray
                    val firstArtist = dzResults?.firstOrNull()?.jsonObject
                    val deezerId = firstArtist?.get("id")?.jsonPrimitive?.content
                    val deezerPic = firstArtist?.get("picture_xl")?.jsonPrimitive?.content
                        ?: firstArtist?.get("picture_big")?.jsonPrimitive?.content

                    if (!deezerPic.isNullOrBlank()) {
                        photoCache[cacheKey] = deezerPic
                    }

                    if (!deezerId.isNullOrBlank()) {
                        val albumUrl = "https://api.deezer.com/artist/$deezerId/albums?limit=50"
                        val albReq = Request.Builder().url(albumUrl).build()
                        val albResp = client.newCall(albReq).execute()
                        if (albResp.isSuccessful) {
                            val albBody = albResp.body?.string()
                            if (!albBody.isNullOrBlank()) {
                                val albRoot = json.parseToJsonElement(albBody).jsonObject
                                val albList = albRoot["data"]?.jsonArray
                                albList?.forEach { albItem ->
                                    val aObj = albItem.jsonObject
                                    val title = aObj["title"]?.jsonPrimitive?.content ?: return@forEach
                                    val recordType = aObj["record_type"]?.jsonPrimitive?.content ?: "album"
                                    val relDate = aObj["release_date"]?.jsonPrimitive?.content
                                    val year = relDate?.take(4) ?: "2024"
                                    val cover = aObj["cover_xl"]?.jsonPrimitive?.content
                                        ?: aObj["cover_big"]?.jsonPrimitive?.content

                                    // Filter out compilation albums
                                    if (recordType.equals("compile", ignoreCase = true) ||
                                        TrackMatchValidator.isCompilationAlbum(title, cleanName)
                                    ) {
                                        return@forEach
                                    }

                                    val isSingleOrEP = recordType.equals("single", ignoreCase = true) ||
                                            recordType.equals("ep", ignoreCase = true) ||
                                            title.lowercase().contains("single") ||
                                            title.lowercase().contains("ep")

                                    val dzAlbumId = aObj["id"]?.jsonPrimitive?.content
                                    val albumId = if (!dzAlbumId.isNullOrBlank()) "dz_$dzAlbumId" else "dz_${kotlin.math.abs((title + cleanName).hashCode())}"

                                    if (isSingleOrEP) {
                                        if (singlesAndEPs.none { it.title.equals(title, ignoreCase = true) }) {
                                            singlesAndEPs.add(
                                                SieloAlbum(
                                                    id = albumId,
                                                    title = title,
                                                    artist = cleanName,
                                                    year = year,
                                                    thumbnailUrl = cover,
                                                    type = if (recordType.equals("ep", ignoreCase = true)) "EP" else "Single",
                                                    songCount = 1
                                                )
                                            )
                                        }
                                    } else if (!TrackMatchValidator.isSoundtrackRelease(title, recordType)) {
                                        if (studioAlbums.none { it.title.equals(title, ignoreCase = true) }) {
                                            studioAlbums.add(
                                                SieloAlbum(
                                                    id = albumId,
                                                    title = title,
                                                    artist = cleanName,
                                                    year = year,
                                                    thumbnailUrl = cover,
                                                    type = "Album",
                                                    songCount = 6
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ArtistMetadataResolver", "Deezer fetch error: ${e.message}")
        }

        val deduplicatedStudio = studioAlbums
            .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
            .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

        val deduplicatedSingles = singlesAndEPs
            .distinctBy { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
            .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

        val verified = VerifiedDiscography(
            studioAlbums = deduplicatedStudio,
            singlesAndEPs = deduplicatedSingles,
            primaryGenres = genres.toList(),
            recordLabel = resolvedLabel
        )
        discographyCache[cacheKey] = verified
        verified
    }

    suspend fun resolveArtistPhoto(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || TrackMatchValidator.isYouTubeChannelId(cleanName)) return@withContext null

        val cacheKey = cleanName.lowercase()
        photoCache[cacheKey]?.let { return@withContext it }

        // 1. Wikipedia verified high-res portrait
        fetchWikipediaBio(cleanName)?.photoUrl?.let {
            if (it.isNotBlank()) {
                photoCache[cacheKey] = it
                return@withContext it
            }
        }

        // 2. Deezer high-res artist photo fallback (1000x1000)
        try {
            val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
            val deezerSearchUrl = "https://api.deezer.com/search/artist?q=$encodedQuery"
            val dzReq = Request.Builder().url(deezerSearchUrl).build()
            val dzResp = client.newCall(dzReq).execute()
            if (dzResp.isSuccessful) {
                val dzBody = dzResp.body?.string()
                if (!dzBody.isNullOrBlank()) {
                    val dzRoot = json.parseToJsonElement(dzBody).jsonObject
                    val dzResults = dzRoot["data"]?.jsonArray
                    val firstArtist = dzResults?.firstOrNull()?.jsonObject
                    val artistNameFromDz = firstArtist?.get("name")?.jsonPrimitive?.content ?: ""
                    if (TrackMatchValidator.isStrictArtistMatch(artistNameFromDz, cleanName)) {
                        val pic = firstArtist?.get("picture_xl")?.jsonPrimitive?.content
                            ?: firstArtist?.get("picture_big")?.jsonPrimitive?.content
                        if (!pic.isNullOrBlank() && !pic.contains("default-artist") && !pic.contains("default")) {
                            photoCache[cacheKey] = pic
                            return@withContext pic
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }
}
