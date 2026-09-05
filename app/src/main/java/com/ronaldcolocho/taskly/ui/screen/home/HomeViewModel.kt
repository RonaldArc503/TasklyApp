package com.ronaldcolocho.taskly.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.task.GetTasksUseCase
import com.ronaldcolocho.taskly.ui.state.HomeUiState
import com.ronaldcolocho.taskly.ui.state.RecentItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getTasksUseCase: GetTasksUseCase,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(
        HomeUiState.Success(
            user = null,
            pendingTasks = emptyList(),
            recentActivity = emptyList(),
            isRefreshing = true
        )
    )
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

        combine(
            profileFlow,
            tasksFlow,
            conversationsFlow
        ) { profile, tasks, conversations ->
            val pendingTasks = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.DOING }
            
            val recentTasks = tasks.map { RecentItem.TaskItem(it) }
            val recentChats = conversations.map { RecentItem.ChatItem(it) }
            val recentActivity = (recentTasks + recentChats)
                .sortedByDescending { it.timestamp }
                .take(5)

            HomeUiState.Success(
                user = profile,
                pendingTasks = pendingTasks,
                recentActivity = recentActivity,
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
