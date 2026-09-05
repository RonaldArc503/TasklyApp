package com.ronaldcolocho.taskly.ui.state

import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus

sealed interface TasksUiState {
    object Loading : TasksUiState
    data class Success(
        val allTasks: List<Task>,
        val displayedTasks: List<Task>,
        val currentTab: TaskStatus,
        val progress: Float,
        val isRefreshing: Boolean = false
    ) : TasksUiState
    data class Error(val message: String) : TasksUiState
}
