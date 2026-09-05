package com.ronaldcolocho.taskly.data.repository

import com.ronaldcolocho.taskly.BuildConfig
import com.ronaldcolocho.taskly.data.remote.youtube.YouTubeApi
import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.domain.repository.IYouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class YouTubeRepositoryImpl @Inject constructor(
    private val api: YouTubeApi
) : IYouTubeRepository {

    override suspend fun searchVideos(query: String): Result<List<YouTubeVideo>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Falta la clave de YouTube. Configura YOUTUBE_API_KEY en local.properties."))
        }

        try {
            val response = api.searchVideos(query = query, apiKey = apiKey)
            val videos = response.items?.mapNotNull { item ->
                val videoId = item.id?.videoId ?: return@mapNotNull null
                val title = item.snippet?.title ?: return@mapNotNull null
                YouTubeVideo(
                    id = videoId,
                    title = title,
                    channel = item.snippet.channelTitle ?: "",
                    thumbnail = item.snippet.thumbnails?.medium?.url ?: "",
                    url = "https://www.youtube.com/watch?v=\$videoId"
                )
            } ?: emptyList()
            Result.success(videos)
        } catch (e: HttpException) {
            if (e.code() == 403 || e.code() == 429) {
                Result.failure(Exception("Cuota de YouTube agotada. Intenta más tarde."))
            } else {
                Result.failure(Exception("Error al buscar (\${e.code()})."))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Sin conexión. Revisa tu internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
