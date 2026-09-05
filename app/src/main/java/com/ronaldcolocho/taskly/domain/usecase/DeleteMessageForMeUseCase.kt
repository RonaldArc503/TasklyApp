package com.ronaldcolocho.taskly.domain.usecase

import com.ronaldcolocho.taskly.domain.repository.IDeletedMessagesRepository
import javax.inject.Inject

class DeleteMessageForMeUseCase @Inject constructor(
    private val deletedMessagesRepository: IDeletedMessagesRepository
) {
    suspend operator fun invoke(messageId: String) {
        deletedMessagesRepository.markAsDeletedForMe(messageId)
    }
}
