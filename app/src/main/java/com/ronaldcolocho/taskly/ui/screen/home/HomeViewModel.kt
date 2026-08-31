package com.ronaldcolocho.taskly.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationsUseCase
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
    private val getConversationsUseCase: GetConversationsUseCase
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

        combine(
            getProfileUseCase(uid),
            getTasksUseCase(uid),
            getConversationsUseCase(uid)
        ) { profile, tasks, conversations ->
            if (profile == null) return@combine HomeUiState.Loading

            val pendingTasks = tasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.DOING }
            
            val recentTasks = tasks.map { RecentItem.TaskItem(it) }
            val recentChats = conversations.map { RecentItem.ChatItem(it) }
            val recentActivity = (recentTasks + recentChats)
                .sortedByDescending { it.timestamp }
                .take(5)

            HomeUiState.Success(
                user = profile,
                pendingTasks = pendingTasks,
                recentActivity = recentActivity
            )
        }
        .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Error desconocido") }
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)
    }
}
