package com.sielo.music.core.recommendations.api

import com.sielo.music.core.recommendations.model.LastFmSimilarArtistsResponse
import com.sielo.music.core.recommendations.model.LastFmTagTopArtistsResponse
import com.sielo.music.core.recommendations.model.LastFmTopTagsResponse
import com.sielo.music.core.recommendations.model.LastFmTopTracksResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApiService {

    @GET("2.0/?method=artist.getsimilar&format=json")
    suspend fun getSimilarArtists(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 30,
        @Query("autocorrect") autocorrect: Int = 1
    ): LastFmSimilarArtistsResponse

    @GET("2.0/?method=artist.gettoptags&format=json")
    suspend fun getTopTags(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("autocorrect") autocorrect: Int = 1
    ): LastFmTopTagsResponse

    @GET("2.0/?method=tag.gettopartists&format=json")
    suspend fun getTagTopArtists(
        @Query("tag") tag: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 30
    ): LastFmTagTopArtistsResponse

    @GET("2.0/?method=artist.gettoptracks&format=json")
    suspend fun getTopTracks(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10,
        @Query("autocorrect") autocorrect: Int = 1
    ): LastFmTopTracksResponse
}
