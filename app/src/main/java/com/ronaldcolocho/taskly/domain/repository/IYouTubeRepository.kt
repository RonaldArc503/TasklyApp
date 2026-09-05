package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.YouTubeVideo

interface IYouTubeRepository {
    suspend fun searchVideos(query: String): Result<List<YouTubeVideo>>
}
