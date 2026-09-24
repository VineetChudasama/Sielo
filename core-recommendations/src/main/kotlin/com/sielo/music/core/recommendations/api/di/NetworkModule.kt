package com.sielo.music.core.recommendations.api.di

import com.sielo.music.core.recommendations.api.LastFmApiService
import com.sielo.music.core.recommendations.api.MusicBrainzApiService
import com.sielo.music.core.recommendations.util.RateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFmClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicBrainzClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFmRateLimiter

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicBrainzRateLimiter

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    @LastFmRateLimiter
    fun provideLastFmRateLimiter(): RateLimiter = RateLimiter(requestsPerSecond = 5.0)

    @Provides
    @Singleton
    @MusicBrainzRateLimiter
    fun provideMusicBrainzRateLimiter(): RateLimiter = RateLimiter(requestsPerSecond = 1.0)

    @Provides
    @Singleton
    @LastFmClient
    fun provideLastFmOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @MusicBrainzClient
    fun provideMusicBrainzOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Sielo/8.0.7 (contact@sielo.app; https://github.com/VineetChudasama/Sielo)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideLastFmApiService(
        @LastFmClient okHttpClient: OkHttpClient,
        json: Json
    ): LastFmApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(LastFmApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApiService(
        @MusicBrainzClient okHttpClient: OkHttpClient,
        json: Json
    ): MusicBrainzApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MusicBrainzApiService::class.java)
    }
}
