package com.sielo.music.core.lyrics.lrclib

import com.sielo.music.core.lyrics.model.LyricLine
import com.sielo.music.core.lyrics.model.SieloLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("track_name", trackName)
                ?.addQueryParameter("artist_name", artistName)

            if (durationSec > 0) {
                urlBuilder?.addQueryParameter("duration", durationSec.toString())
            }

            val url = urlBuilder?.build() ?: return@withContext null
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Sielo-Music-Android-App/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(body).jsonObject

            val plain = obj["plainLyrics"]?.jsonPrimitive?.content
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.content

            val lines = synced?.let { parseLrc(it) } ?: emptyList()

            SieloLyrics(
                plainLyrics = plain,
                syncedLyrics = synced,
                lines = lines
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
