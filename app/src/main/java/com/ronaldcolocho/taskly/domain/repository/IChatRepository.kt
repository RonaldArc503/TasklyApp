package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.ConversationUserState
import com.ronaldcolocho.taskly.domain.model.ConversationReceipt
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getConversations(userId: String): Flow<List<ChatConversation>>
    fun getRecentConversations(userId: String, limit: Long): Flow<List<ChatConversation>>
    suspend fun getOlderConversations(userId: String, beforeTimestamp: Long, limit: Long): List<ChatConversation>
    fun getConversation(convId: String, currentUserId: String): Flow<ChatConversation?>

    fun getRecentMessages(convId: String, currentUserId: String): Flow<List<ChatMessage>>
    suspend fun getChatInfoMessages(convId: String, currentUserId: String, limit: Long): List<ChatMessage>
    suspend fun getOlderMessages(convId: String, currentUserId: String, beforeTimestamp: Long, limit: Long): List<ChatMessage>
    suspend fun getMessageWindowById(
        convId: String,
        currentUserId: String,
        messageId: String,
        windowSize: Long
    ): List<ChatMessage>

    suspend fun findOrCreateDirectConversation(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String>

    suspend fun getOrCreateSavedMessagesConversation(
        currentUser: UserProfile
    ): Result<String>

    fun subscribeConversationStates(
        userId: String,
        conversationIds: Set<String>
    ): Flow<Map<String, ConversationUserState>>
    fun getPinnedConversations(userId: String, limit: Long): Flow<List<ChatConversation>>
    suspend fun getArchivedConversations(
        userId: String,
        beforeArchivedAt: Long?,
        limit: Long
    ): List<ChatConversation>
    suspend fun searchConversations(userId: String, normalizedPrefix: String, limit: Long): List<ChatConversation>
    suspend fun ensureConversationSearchIndexes(userId: String, conversations: List<ChatConversation>): Result<Unit>
    suspend fun setConversationPinned(userId: String, conversationId: String, pinned: Boolean): Result<Unit>
    suspend fun setConversationArchived(userId: String, conversationId: String, archived: Boolean): Result<Unit>
    suspend fun searchMessages(convId: String, currentUserId: String, normalizedPrefix: String, limit: Long): List<ChatMessage>
    suspend fun ensureMessageSearchIndexes(convId: String, messages: List<ChatMessage>): Result<Unit>
    fun subscribeConversationReceipts(convId: String): Flow<Map<String, ConversationReceipt>>
    suspend fun updateDeliveredCursor(
        convId: String,
        userId: String,
        messageId: String,
        deliveredAt: Long
    ): Result<Unit>
    suspend fun updateReadCursor(
        convId: String,
        userId: String,
        messageId: String,
        readAt: Long,
        clearUnread: Boolean
    ): Result<Unit>
    suspend fun searchGlobalMessages(userId: String, normalizedPrefix: String, limit: Long): List<ChatMessage>

    suspend fun sendMessage(convId: String, currentUserId: String, message: ChatMessage): Result<Unit>
    suspend fun markAsRead(convId: String, currentUserId: String): Result<Unit>

    suspend fun toggleReaction(convId: String, msgId: String, uid: String, emoji: String): Result<Unit>
    suspend fun editMessage(convId: String, msgId: String, text: String): Result<Unit>
    suspend fun deleteMessage(convId: String, msgId: String): Result<Unit>
    suspend fun pinMessage(convId: String, pinned: PinnedMessage): Result<Unit>
    suspend fun unpinMessage(convId: String, msgId: String): Result<Unit>

    fun setTyping(convId: String, uid: String)
    fun clearTyping(convId: String, uid: String)
    fun subscribeTyping(convId: String): Flow<Map<String, Long>>
}
