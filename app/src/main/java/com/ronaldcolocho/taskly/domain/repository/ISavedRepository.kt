package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.SavedItem

interface ISavedRepository {
    suspend fun saveMessage(uid: String, item: SavedItem): Result<Unit>
}
