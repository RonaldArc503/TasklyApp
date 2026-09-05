package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getConversations(userId: String): Flow<List<ChatConversation>>
    fun getRecentConversations(userId: String, limit: Long): Flow<List<ChatConversation>>
    suspend fun getOlderConversations(userId: String, beforeTimestamp: Long, limit: Long): List<ChatConversation>
    fun getConversation(convId: String, currentUserId: String): Flow<ChatConversation?>

    fun getRecentMessages(convId: String, currentUserId: String): Flow<List<ChatMessage>>
    suspend fun getChatInfoMessages(convId: String, currentUserId: String, limit: Long): List<ChatMessage>
    suspend fun getOlderMessages(convId: String, currentUserId: String, beforeTimestamp: Long, limit: Long): List<ChatMessage>

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
