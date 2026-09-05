package com.ronaldcolocho.taskly.ui.state

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.UserProfile

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val user: UserProfile?,
        val pendingTasks: List<Task>,
        val recentActivity: List<RecentItem>,
        val isRefreshing: Boolean = false
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed class RecentItem {
    abstract val timestamp: Long
    data class TaskItem(val task: Task) : RecentItem() {
        override val timestamp: Long = task.createdAt
    }
    data class ChatItem(val conversation: ChatConversation) : RecentItem() {
        override val timestamp: Long = conversation.lastMessageAt
    }
}
