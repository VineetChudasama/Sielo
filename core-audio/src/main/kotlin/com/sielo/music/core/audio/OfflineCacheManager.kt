package com.sielo.music.core.audio

import android.content.Context
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object OfflineCacheManager {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cacheScope = CoroutineScope(Dispatchers.IO)

    private val _cacheUpdated = MutableStateFlow(System.currentTimeMillis())
    val cacheUpdated: StateFlow<Long> = _cacheUpdated.asStateFlow()

    fun notifyCacheChanged() {
        _cacheUpdated.value = System.currentTimeMillis()
    }

    fun getDownloadsDir(context: Context): File {
        val dir = File(context.cacheDir, "sielo_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isCached(context: Context, trackId: String): Boolean {
        val dir = getDownloadsDir(context)
        val audioFile = File(dir, "$trackId.m4a")
        val metaFile = File(dir, "$trackId.json")
        return audioFile.exists() && audioFile.length() > 1024L && metaFile.exists()
    }

    fun getLocalTrackUri(context: Context, trackId: String): String? {
        val dir = getDownloadsDir(context)
        val audioFile = File(dir, "$trackId.m4a")
        return if (audioFile.exists() && audioFile.length() > 1024L) {
            audioFile.toURI().toString()
        } else null
    }

    fun autoCacheTrack(context: Context, track: SieloTrack, streamUrl: String) {
        if (streamUrl.isBlank() || streamUrl.startsWith("file:")) return
        if (isCached(context, track.id)) return
        cacheScope.launch {
            try {
                downloadFully(context, track, streamUrl)
            } catch (_: Exception) {}
        }
    }

    suspend fun downloadFully(context: Context, track: SieloTrack, streamUrl: String): String = withContext(Dispatchers.IO) {
        val dir = getDownloadsDir(context)
        val audioFile = File(dir, "${track.id}.m4a")
        val metaFile = File(dir, "${track.id}.json")

        if (audioFile.exists() && audioFile.length() > 1024L && metaFile.exists()) {
            return@withContext audioFile.toURI().toString()
        }

        try {
            val builder = Request.Builder().url(streamUrl)
            if (streamUrl.contains("googlevideo.com") || streamUrl.contains("youtube") || streamUrl.contains("ytimg")) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            val response = client.newCall(builder.build()).execute()
            if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")

            val body = response.body ?: throw Exception("Empty body")
            val inputStream = body.byteStream()
            val tempFile = File(dir, "${track.id}.tmp")
            val outputStream = FileOutputStream(tempFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() > 1024L) {
                if (audioFile.exists()) audioFile.delete()
                tempFile.renameTo(audioFile)
                // Save metadata
                metaFile.writeText(json.encodeToString(track))
                notifyCacheChanged()
                return@withContext audioFile.toURI().toString()
            } else {
                tempFile.delete()
                return@withContext streamUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext streamUrl // fallback to streaming
        }
    }

    fun getCachedTracks(context: Context): List<SieloTrack> {
        val dir = getDownloadsDir(context)
        val tracks = mutableListOf<SieloTrack>()
        if (dir.exists()) {
            dir.listFiles { file -> file.name.endsWith(".json") }?.forEach { file ->
                try {
                    val track = json.decodeFromString<SieloTrack>(file.readText())
                    val audioFile = File(dir, "${track.id}.m4a")
                    if (audioFile.exists() && audioFile.length() > 1024L) {
                        tracks.add(track)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return tracks
    }

    fun removeTrack(context: Context, trackId: String) {
        val dir = getDownloadsDir(context)
        try {
            File(dir, "$trackId.m4a").delete()
            File(dir, "$trackId.json").delete()
            File(dir, "$trackId.tmp").delete()
        } catch (_: Exception) {}
        notifyCacheChanged()
    }

    fun clearCache(context: Context) {
        val dir = getDownloadsDir(context)
        if (dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
        notifyCacheChanged()
    }
}
