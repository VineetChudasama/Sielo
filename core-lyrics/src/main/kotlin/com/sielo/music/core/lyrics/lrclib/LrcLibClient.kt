package com.sielo.music.core.lyrics.lrclib

import com.sielo.music.core.lyrics.model.LyricLine
import com.sielo.music.core.lyrics.model.SieloLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLyrics(trackName: String, artistName: String, durationSec: Long = 0): SieloLyrics? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = cleanMetadata(trackName)
            val cleanArtist = cleanMetadata(artistName)

            // 1. Try search with cleanTitle + cleanArtist together (ensures artist accuracy)
            var result = fetchSearch("$cleanTitle $cleanArtist", cleanTitle, cleanArtist)

            // 2. Try exact match with cleaned title & artist
            if (result == null || result.lines.isEmpty()) {
                val exactClean = fetchExact(cleanTitle, cleanArtist, durationSec)
                if (exactClean != null) result = exactClean
            }

            // 3. Try search with raw trackName + artistName
            if (result == null || result.lines.isEmpty()) {
                val searchRaw = fetchSearch("$trackName $artistName", cleanTitle, cleanArtist)
                if (searchRaw != null) result = searchRaw
            }

            // 4. Try exact match with raw trackName & artistName
            if (result == null || result.lines.isEmpty()) {
                val exactRaw = fetchExact(trackName, artistName, durationSec)
                if (exactRaw != null) result = exactRaw
            }

            // 5. Try search with cleanTitle alone (strictly verified against artist)
            if (result == null || result.lines.isEmpty()) {
                val searchTitle = fetchSearch(cleanTitle, cleanTitle, cleanArtist)
                if (searchTitle != null) result = searchTitle
            }

            if (result == null) return@withContext null

            // Apply homophonic English transliteration if lyrics contain non-Latin script
            val transliteratedLines = result.lines.map { line ->
                val homophonicText = HomophonicTransliterator.transliterate(line.text)
                line.copy(text = homophonicText)
            }

            val transliteratedPlain = result.plainLyrics?.let { HomophonicTransliterator.transliterate(it) }

            return@withContext result.copy(
                plainLyrics = transliteratedPlain,
                lines = transliteratedLines
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isLatinOnly(synced: String?): Boolean {
        if (synced.isNullOrBlank()) return false
        return !HomophonicTransliterator.isNonLatin(synced)
    }

    private fun fetchExact(trackName: String, artistName: String, durationSec: Long = 0): SieloLyrics? {
        return try {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("track_name", trackName)
                ?.addQueryParameter("artist_name", artistName)

            if (durationSec > 0) {
                urlBuilder?.addQueryParameter("duration", durationSec.toString())
            }

            val url = urlBuilder?.build() ?: return null
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Sielo-Music-Android-App/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val obj = json.parseToJsonElement(body).jsonObject

            val itemTitle = obj["trackName"]?.jsonPrimitive?.content ?: ""
            val itemArtist = obj["artistName"]?.jsonPrimitive?.content ?: ""

            // Strict verification
            if (!isTrackAndArtistMatch(trackName, artistName, itemTitle, itemArtist)) {
                return null
            }

            val plain = obj["plainLyrics"]?.jsonPrimitive?.content
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.content
            val lines = synced?.let { parseLrc(it) } ?: emptyList()

            if (plain == null && synced == null) null
            else SieloLyrics(plainLyrics = plain, syncedLyrics = synced, lines = lines)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchSearch(query: String, expectedTitle: String, expectedArtist: String): SieloLyrics? {
        return try {
            val url = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", query)
                ?.build() ?: return null

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Sielo-Music-Android-App/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val array = json.parseToJsonElement(body).jsonArray
            if (array.isEmpty()) return null

            // Prioritize items that match BOTH the track name and the artist
            var bestCandidate: SieloLyrics? = null

            for (element in array) {
                val item = element.jsonObject
                val itemTitle = item["trackName"]?.jsonPrimitive?.content ?: ""
                val itemArtist = item["artistName"]?.jsonPrimitive?.content ?: ""
                val synced = item["syncedLyrics"]?.jsonPrimitive?.content
                val plain = item["plainLyrics"]?.jsonPrimitive?.content

                // Strict check: candidate must match BOTH the song title and artist
                val matches = isTrackAndArtistMatch(expectedTitle, expectedArtist, itemTitle, itemArtist)

                if (matches && !synced.isNullOrBlank()) {
                    val parsed = parseLrc(synced)
                    val cand = SieloLyrics(plainLyrics = plain, syncedLyrics = synced, lines = parsed)
                    val hasLatin = isLatinOnly(synced)

                    if (hasLatin) {
                        return cand // Direct perfect Romanized match
                    } else if (bestCandidate == null) {
                        bestCandidate = cand
                    }
                } else if (bestCandidate == null && matches && !plain.isNullOrBlank()) {
                    bestCandidate = SieloLyrics(plainLyrics = plain, syncedLyrics = null, lines = emptyList())
                }
            }

            // No unsafe fallback to unrelated songs
            bestCandidate
        } catch (e: Exception) {
            null
        }
    }

    private fun isTrackAndArtistMatch(
        reqTitle: String,
        reqArtist: String,
        candTitle: String,
        candArtist: String
    ): Boolean {
        val reqTitleNorm = normalizeForMatch(cleanMetadata(reqTitle))
        val candTitleNorm = normalizeForMatch(cleanMetadata(candTitle))

        val reqArtistNorm = normalizeForMatch(cleanMetadata(reqArtist))
        val candArtistNorm = normalizeForMatch(cleanMetadata(candArtist))

        if (reqTitleNorm.isBlank() || candTitleNorm.isBlank()) return false

        // 1. Title Match Verification
        val titleMatch = reqTitleNorm == candTitleNorm ||
                reqTitleNorm.contains(candTitleNorm) ||
                candTitleNorm.contains(reqTitleNorm) ||
                hasSignificantTokenOverlap(reqTitleNorm, candTitleNorm, 0.5f)

        if (!titleMatch) return false

        // 2. Artist Match Verification (if artist is known)
        if (reqArtistNorm.isNotBlank() && candArtistNorm.isNotBlank()) {
            val reqArtists = splitArtists(reqArtistNorm)
            val candArtists = splitArtists(candArtistNorm)

            val artistMatch = reqArtists.any { reqA ->
                candArtists.any { candA ->
                    reqA.contains(candA) || candA.contains(reqA) || hasSignificantTokenOverlap(reqA, candA, 0.4f)
                }
            } || reqArtistNorm.contains(candArtistNorm) || candArtistNorm.contains(reqArtistNorm)

            if (!artistMatch) return false
        }

        return true
    }

    private fun splitArtists(artistString: String): List<String> {
        return artistString
            .split(Regex("""[,/&;+]|\bfeat\.?\b|\bft\.?\b|\bwith\b|\bx\b""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun hasSignificantTokenOverlap(s1: String, s2: String, threshold: Float): Boolean {
        val tokens1 = s1.split(" ").filter { it.length > 2 }.toSet()
        val tokens2 = s2.split(" ").filter { it.length > 2 }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return false
        val intersection = tokens1.intersect(tokens2).size
        val minSize = minOf(tokens1.size, tokens2.size)
        return (intersection.toFloat() / minSize) >= threshold
    }

    private fun cleanMetadata(text: String): String {
        return text
            .replace(Regex("""\s*\(Official(\s+Music)?\s+Video\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[Official(\s+Music)?\s+Video\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Official\s+Audio\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[Official\s+Audio\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Lyric(\s+Video)?\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[Lyric(\s+Video)?\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Lyrical\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(From\s+["'][^"']+["']\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*From\s+["'][^"']+["']\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(feat\..*?\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[feat\..*?\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(ft\..*?\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Remastered.*?\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*Single\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*Remastered\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*Topic\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        lrcContent.lines().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msPart = match.groupValues[3]
                val ms = if (msPart.length == 2) (msPart.toLongOrNull() ?: 0L) * 10 else (msPart.toLongOrNull() ?: 0L)
                val text = match.groupValues[4].trim()

                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                list.add(LyricLine(totalMs, text))
            }
        }
        return list.sortedBy { it.timestampMs }
    }
}

