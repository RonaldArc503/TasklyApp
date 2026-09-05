package com.ronaldcolocho.taskly.domain.usecase.youtube

import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.domain.repository.IYouTubeRepository
import javax.inject.Inject

class SearchYouTubeVideosUseCase @Inject constructor(
    private val repository: IYouTubeRepository
) {
    suspend operator fun invoke(query: String): Result<List<YouTubeVideo>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchVideos(query.trim())
    }
}
