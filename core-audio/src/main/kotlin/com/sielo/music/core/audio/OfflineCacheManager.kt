package com.sielo.music.core.audio

import android.content.Context
import com.sielo.music.core.network.models.SieloTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object OfflineCacheManager {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().build()

    fun getDownloadsDir(context: Context): File {
        val dir = File(context.cacheDir, "sielo_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadFully(context: Context, track: SieloTrack, streamUrl: String): String = withContext(Dispatchers.IO) {
        val dir = getDownloadsDir(context)
        val audioFile = File(dir, "${track.id}.m4a")
        val metaFile = File(dir, "${track.id}.json")

        if (audioFile.exists() && metaFile.exists()) {
            return@withContext audioFile.toURI().toString()
        }

        try {
            val request = Request.Builder().url(streamUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to download")

            val inputStream = response.body?.byteStream() ?: throw Exception("Empty body")
            val tempFile = File(dir, "${track.id}.tmp")
            val outputStream = FileOutputStream(tempFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.renameTo(audioFile)

            // Save metadata
            metaFile.writeText(json.encodeToString(track))

            return@withContext audioFile.toURI().toString()
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
                    if (audioFile.exists()) {
                        tracks.add(track)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return tracks
    }

    fun clearCache(context: Context) {
        val dir = getDownloadsDir(context)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}

