package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.SavedItem
import kotlinx.coroutines.flow.Flow

data class SavedCounts(val total: Int, val pinned: Int)

interface ISavedRepository {
    suspend fun saveMessage(uid: String, item: SavedItem): Result<Unit>
    fun getSavedItems(uid: String): Flow<List<SavedItem>>
    suspend fun getSavedCounts(uid: String): SavedCounts
    suspend fun deleteSaved(uid: String, id: String): Result<Unit>
    suspend fun setPinned(uid: String, id: String, pinned: Boolean): Result<Unit>
}
