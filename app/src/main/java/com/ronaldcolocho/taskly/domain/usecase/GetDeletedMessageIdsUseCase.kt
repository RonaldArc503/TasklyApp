package com.ronaldcolocho.taskly.domain.usecase

import com.ronaldcolocho.taskly.domain.repository.IDeletedMessagesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDeletedMessageIdsUseCase @Inject constructor(
    private val deletedMessagesRepository: IDeletedMessagesRepository
) {
    operator fun invoke(): Flow<Set<String>> {
        return deletedMessagesRepository.getDeletedMessageIds()
    }
}
