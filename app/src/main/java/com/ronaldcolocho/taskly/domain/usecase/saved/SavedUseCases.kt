package com.ronaldcolocho.taskly.domain.usecase.saved

import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.repository.ISavedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSavedItemsUseCase @Inject constructor(
    private val savedRepository: ISavedRepository
) {
    operator fun invoke(uid: String): Flow<List<SavedItem>> {
        return savedRepository.getSavedItems(uid)
    }
}

class GetSavedCountsUseCase @Inject constructor(
    private val savedRepository: ISavedRepository
) {
    suspend operator fun invoke(uid: String) = savedRepository.getSavedCounts(uid)
}

class DeleteSavedItemUseCase @Inject constructor(
    private val savedRepository: ISavedRepository
) {
    suspend operator fun invoke(uid: String, id: String): Result<Unit> {
        return savedRepository.deleteSaved(uid, id)
    }
}

class PinSavedItemUseCase @Inject constructor(
    private val savedRepository: ISavedRepository
) {
    suspend operator fun invoke(uid: String, id: String, pinned: Boolean): Result<Unit> {
        return savedRepository.setPinned(uid, id, pinned)
    }
}
