package com.ronaldcolocho.taskly.domain.usecase.chat

import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.repository.ISavedRepository
import javax.inject.Inject

class SaveMessageUseCase @Inject constructor(
    private val repository: ISavedRepository
) {
    suspend operator fun invoke(uid: String, item: SavedItem): Result<Unit> =
        repository.saveMessage(uid, item)
}
