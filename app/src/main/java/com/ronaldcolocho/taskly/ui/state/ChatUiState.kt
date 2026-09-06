package com.ronaldcolocho.taskly.ui.state

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.UserProfile

sealed interface ChatUiState {
    object Loading : ChatUiState
    data class Success(
        val conversation: ChatConversation,
        val currentUserId: String,
        val messages: List<ChatMessage>,
        val isFetchingOlder: Boolean = false,
        val otherParticipant: UserProfile? = null,
        val replyTo: ChatMessage? = null,
        val editing: ChatMessage? = null,
        val sendError: String? = null,
        val unreadBoundaryMessageId: String? = null
    ) : ChatUiState
    data class Error(val message: String) : ChatUiState
}
