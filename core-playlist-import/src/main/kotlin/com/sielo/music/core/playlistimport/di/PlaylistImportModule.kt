package com.sielo.music.core.playlistimport.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sielo.music.core.playlistimport.youtube.YouTubePlaylistApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaylistImportModule {
    
    @Provides
    @Singleton
    fun provideYouTubePlaylistApiService(): YouTubePlaylistApiService {
        val okHttpClient = okhttp3.OkHttpClient.Builder().build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        
        return retrofit.create(YouTubePlaylistApiService::class.java)
    }
}


