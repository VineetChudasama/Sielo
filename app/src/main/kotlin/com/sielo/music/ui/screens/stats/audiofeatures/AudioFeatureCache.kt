package com.sielo.music.ui.screens.stats.audiofeatures

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe persistent cache for TrackAudioFeatures.
 * Stores features on internal disk without altering or touching the existing Room database schema.
 */
class AudioFeatureCache(private val context: Context) {

    private val inMemoryMap = ConcurrentHashMap<String, TrackAudioFeatures>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheFile: File by lazy {
        File(context.filesDir, "track_audio_features_cache.json")
    }

    private var isLoaded = false

    suspend fun get(trackId: String): TrackAudioFeatures? = withContext(Dispatchers.IO) {
        ensureLoaded()
        val entry = inMemoryMap[trackId]
        if (entry != null) {
            return@withContext entry.copy(source = FeatureSource.CACHED)
        }
        null
    }

    suspend fun put(features: TrackAudioFeatures) = withContext(Dispatchers.IO) {
        ensureLoaded()
        inMemoryMap[features.trackId] = features
        persistToDisk()
    }

    suspend fun putAll(list: List<TrackAudioFeatures>) = withContext(Dispatchers.IO) {
        ensureLoaded()
        list.forEach { inMemoryMap[it.trackId] = it }
        persistToDisk()
    }

    private suspend fun ensureLoaded() {
        if (isLoaded) return
        mutex.withLock {
            if (isLoaded) return@withLock
            try {
                if (cacheFile.exists()) {
                    val content = cacheFile.readText()
                    if (content.isNotBlank()) {
                        val parsed = json.decodeFromString<Map<String, TrackAudioFeatures>>(content)
                        inMemoryMap.putAll(parsed)
                    }
                }
            } catch (e: Exception) {
                // Non-fatal if cache read fails
            }
            isLoaded = true
        }
    }

    private suspend fun persistToDisk() {
        mutex.withLock {
            try {
                val copy = HashMap(inMemoryMap)
                val encoded = json.encodeToString(copy)
                cacheFile.writeText(encoded)
            } catch (e: Exception) {
                // Non-fatal if cache write fails
            }
        }
    }
}
