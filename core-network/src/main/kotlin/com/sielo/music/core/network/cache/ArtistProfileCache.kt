package com.sielo.music.core.network.cache

import com.sielo.music.core.network.models.ArtistDetails
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistProfileCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, ArtistDetails>()

    fun get(artistName: String): ArtistDetails? {
        return cache[normalize(artistName)]
    }

    fun put(artistName: String, details: ArtistDetails) {
        cache[normalize(artistName)] = details
    }

    fun clear() {
        cache.clear()
    }

    private fun normalize(name: String): String = name.trim().lowercase()
}
