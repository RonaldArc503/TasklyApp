package com.ronaldcolocho.taskly.ui.screen.chatinfo

import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatConversation

data class ChatLink(val url: String, val messageId: String, val senderId: String, val timestamp: Long)

sealed interface ChatInfoUiState {
    data object Loading : ChatInfoUiState
    
    data class Success(
        val currentUserId: String,
        val conversation: ChatConversation,
        val mediaAttachments: List<Pair<ChatAttachment, String>>, // Pair<Attachment, MessageId>
        val links: List<ChatLink>,
        val audioAttachments: List<Pair<ChatAttachment, String>>, // Pair<Attachment, MessageId>
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true
    ) : ChatInfoUiState

    data class Error(val message: String) : ChatInfoUiState
}
