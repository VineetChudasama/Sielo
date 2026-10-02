package com.sielo.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

import okhttp3.OkHttpClient

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
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
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
