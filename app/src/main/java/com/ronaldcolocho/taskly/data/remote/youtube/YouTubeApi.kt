package com.ronaldcolocho.taskly.data.remote.youtube

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 8,
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): YouTubeSearchResponseDto
}
