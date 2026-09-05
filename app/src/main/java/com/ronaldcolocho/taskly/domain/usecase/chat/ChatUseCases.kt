package com.ronaldcolocho.taskly.domain.usecase.chat

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConversationsUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(userId: String): Flow<List<ChatConversation>> {
        return repository.getConversations(userId)
    }
}

class GetRecentConversationsUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(userId: String, limit: Long): Flow<List<ChatConversation>> =
        repository.getRecentConversations(userId, limit)
}

class GetOlderConversationsUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(userId: String, beforeTimestamp: Long, limit: Long): List<ChatConversation> =
        repository.getOlderConversations(userId, beforeTimestamp, limit)
}

class GetConversationUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(convId: String, currentUserId: String): Flow<ChatConversation?> {
        return repository.getConversation(convId, currentUserId)
    }
}

class GetRecentMessagesUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(convId: String, currentUserId: String): Flow<List<ChatMessage>> {
        return repository.getRecentMessages(convId, currentUserId)
    }
}

class GetOlderMessagesUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, currentUserId: String, beforeTimestamp: Long, limit: Long): List<ChatMessage> {
        return repository.getOlderMessages(convId, currentUserId, beforeTimestamp, limit)
    }
}

class SendMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, currentUserId: String, message: ChatMessage): Result<Unit> {
        return repository.sendMessage(convId, currentUserId, message)
    }
}

class MarkAsReadUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, currentUserId: String): Result<Unit> {
        return repository.markAsRead(convId, currentUserId)
    }
}

class ToggleReactionUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, msgId: String, uid: String, emoji: String): Result<Unit> =
        repository.toggleReaction(convId, msgId, uid, emoji)
}

class EditMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, msgId: String, text: String): Result<Unit> =
        repository.editMessage(convId, msgId, text)
}

class DeleteMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, msgId: String): Result<Unit> =
        repository.deleteMessage(convId, msgId)
}

class PinMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, pinned: PinnedMessage): Result<Unit> =
        repository.pinMessage(convId, pinned)
}

class UnpinMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    suspend operator fun invoke(convId: String, msgId: String): Result<Unit> =
        repository.unpinMessage(convId, msgId)
}

class SetTypingUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(convId: String, uid: String) = repository.setTyping(convId, uid)
}

class ClearTypingUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(convId: String, uid: String) = repository.clearTyping(convId, uid)
}

class SubscribeTypingUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(convId: String): Flow<Map<String, Long>> = repository.subscribeTyping(convId)
}
