package com.ronaldcolocho.taskly.ui.state

import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val user: UserProfile?,
        val pendingTasksCount: Int,
        val recentPendingTasks: List<Task>,
        val recentConversations: List<ChatConversation>,
        val unreadConversationsCount: Int,
        val upcomingReminders: List<Reminder>,
        val isRefreshing: Boolean = false
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
