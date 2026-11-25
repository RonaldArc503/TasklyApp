package com.ronaldcolocho.taskly.domain.repository

import kotlinx.coroutines.flow.Flow
import com.ronaldcolocho.taskly.domain.model.ChatAttachmentRef

interface IDeletedMessagesRepository {
    fun getDeletedMessageIds(): Flow<Set<String>>
    suspend fun markAsDeletedForMe(messageId: String)
    fun hiddenAttachments(userId: String, conversationId: String): Set<String>
    fun observeHiddenAttachments(userId: String, conversationId: String): Flow<Set<String>>
    suspend fun hideAttachments(userId: String, conversationId: String, attachments: Set<ChatAttachmentRef>)
}
