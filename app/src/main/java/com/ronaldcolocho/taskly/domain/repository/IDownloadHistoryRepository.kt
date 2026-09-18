package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import kotlinx.coroutines.flow.Flow

interface IDownloadHistoryRepository {
    fun getDownloadedSongs(): Flow<List<DownloadedSong>>
    suspend fun addDownloadedSong(song: DownloadedSong)
    suspend fun removeDownloadedSong(id: String)
    suspend fun updateSavedToMessages(id: String, isSaved: Boolean, messageId: String?)
    suspend fun clearHistory()
}
