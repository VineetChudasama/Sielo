package com.sielo.music.core.recommendations.api

import com.sielo.music.core.recommendations.model.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicBrainzApiService {

    @GET("ws/2/artist/")
    suspend fun searchArtist(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 3
    ): MusicBrainzSearchResponse
}
