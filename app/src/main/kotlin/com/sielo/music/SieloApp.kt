package com.sielo.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SieloApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Clean up any residual disk image cache directories to ensure zero permanent storage
        try {
            cacheDir.resolve("sielo_image_cache").deleteRecursively()
            cacheDir.resolve("image_cache").deleteRecursively()
        } catch (_: Exception) {}
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35) // High-capacity in-session RAM cache for smooth navigation
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache(null) // No persistent disk storage: images live purely in session memory
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
