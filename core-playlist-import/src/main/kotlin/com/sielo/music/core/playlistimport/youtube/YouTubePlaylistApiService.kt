package com.sielo.music.core.playlistimport.youtube

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class YouTubePlaylistResponse(
    val nextPageToken: String? = null,
    val items: List<YouTubePlaylistItem> = emptyList()
)

@Serializable
data class YouTubePlaylistItem(
    val snippet: YouTubeSnippet
)

@Serializable
data class YouTubeSnippet(
    val title: String,
    val videoOwnerChannelTitle: String = ""
)

interface YouTubePlaylistApiService {
    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") key: String
    ): YouTubePlaylistResponse
}
