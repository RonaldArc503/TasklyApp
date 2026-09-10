package com.ronaldcolocho.taskly.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.reminder.GetRemindersUseCase
import com.ronaldcolocho.taskly.domain.usecase.task.GetTasksUseCase
import com.ronaldcolocho.taskly.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getTasksUseCase: GetTasksUseCase,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase,
    private val getRemindersUseCase: GetRemindersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        val uid = getCurrentUserIdUseCase()
        if (uid == null) {
            _uiState.value = HomeUiState.Error("No hay sesión activa")
            return
        }

        val profileFlow = getProfileUseCase(uid)
            .onStart { emit(null as UserProfile?) }
            .catch { emit(null) }
        val tasksFlow = getTasksUseCase(uid)
            .onStart { emit(emptyList()) }
            .catch { emit(emptyList()) }
        val conversationsFlow = getRecentConversationsUseCase(uid, RECENT_ACTIVITY_LIMIT)
            .onStart { emit(emptyList()) }
            .catch { emit(emptyList()) }
        val remindersFlow = getRemindersUseCase(uid)
            .onStart { emit(emptyList()) }
            .catch { emit(emptyList()) }

        combine(
            profileFlow,
            tasksFlow,
            conversationsFlow,
            remindersFlow
        ) { profile, tasks, conversations, reminders ->
            val pendingTasks = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.DOING }
            val recentPendingTasks = pendingTasks.sortedByDescending { it.updatedAt }.take(3)
            
            val upcomingReminders = reminders
                .filter { !it.isCompleted && it.dueDate > System.currentTimeMillis() - 86400000L } // only uncompleted, maybe past due a bit but mostly future
                .sortedBy { it.dueDate }
                .take(3)
                
            val unreadChatsCount = conversations.count { it.unreadCount > 0 }

            HomeUiState.Success(
                user = profile,
                pendingTasksCount = pendingTasks.size,
                recentPendingTasks = recentPendingTasks,
                recentConversations = conversations.take(5),
                unreadConversationsCount = unreadChatsCount,
                upcomingReminders = upcomingReminders,
                isRefreshing = false
            )
        }
        .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Error desconocido") }
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)
    }

    private companion object {
        const val RECENT_ACTIVITY_LIMIT = 5L
    }
}
