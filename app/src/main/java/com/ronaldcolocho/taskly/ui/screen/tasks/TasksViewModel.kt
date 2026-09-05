package com.ronaldcolocho.taskly.ui.screen.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.task.*
import com.ronaldcolocho.taskly.ui.state.TasksUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getTasksUseCase: GetTasksUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val moveTaskUseCase: MoveTaskUseCase,
    private val editTaskUseCase: EditTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val restoreTaskUseCase: RestoreTaskUseCase,
    private val clearDoneTasksUseCase: ClearDoneTasksUseCase
) : ViewModel() {

    private val _currentTab = MutableStateFlow(TaskStatus.TODO)
    private val _uiState = MutableStateFlow<TasksUiState>(
        TasksUiState.Success(
            allTasks = emptyList(),
            displayedTasks = emptyList(),
            currentTab = TaskStatus.TODO,
            progress = 0f,
            isRefreshing = true
        )
    )
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private var recentlyDeletedTask: Task? = null

    init {
        loadTasks()
    }

    private fun loadTasks() {
        val uid = getCurrentUserIdUseCase()
        if (uid == null) {
            _uiState.value = TasksUiState.Error("No hay sesión activa")
            return
        }

        combine(
            getTasksUseCase(uid, TaskStatus.TODO).onStart { emit(emptyList()) },
            getTasksUseCase(uid, TaskStatus.DOING).onStart { emit(emptyList()) },
            getTasksUseCase(uid, TaskStatus.DONE).onStart { emit(emptyList()) },
            _currentTab
        ) { todo, doing, done, tab ->
            val tasks = todo + doing + done
            val displayed = tasks.filter { it.status == tab }
            val progress = if (tasks.isEmpty()) 0f else {
                tasks.count { it.status == TaskStatus.DONE }.toFloat() / tasks.size
            }
            TasksUiState.Success(
                allTasks = tasks,
                displayedTasks = displayed,
                currentTab = tab,
                progress = progress,
                isRefreshing = false
            )
        }
        .catch { e -> _uiState.value = TasksUiState.Error(e.message ?: "Error al cargar tareas") }
        .onEach { _uiState.value = it }
        .launchIn(viewModelScope)
    }

    fun setTab(status: TaskStatus) {
        _currentTab.value = status
    }

    fun addTask(title: String, onSuccess: () -> Unit) {
        val uid = getCurrentUserIdUseCase() ?: return
        viewModelScope.launch {
            addTaskUseCase(uid, title).onSuccess { onSuccess() }
        }
    }

    fun moveTaskForward(task: Task) {
        val nextStatus = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DOING
            TaskStatus.DOING -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
        }
        moveTaskToStatus(task, nextStatus)
    }

    fun moveTaskBackward(task: Task) {
        val prevStatus = when (task.status) {
            TaskStatus.TODO -> return // No-op
            TaskStatus.DOING -> TaskStatus.TODO
            TaskStatus.DONE -> TaskStatus.DOING
        }
        moveTaskToStatus(task, prevStatus)
    }

    fun moveTaskToNextStatus(task: Task) {
        moveTaskForward(task)
    }

    fun moveTaskToStatus(task: Task, targetStatus: TaskStatus) {
        val uid = getCurrentUserIdUseCase() ?: return
        
        // Optimistic UI update
        val currentState = _uiState.value
        if (currentState is TasksUiState.Success) {
            val updatedAll = currentState.allTasks.map {
                if (it.id == task.id) it.copy(status = targetStatus, updatedAt = System.currentTimeMillis()) else it
            }
            val displayed = updatedAll.filter { it.status == currentState.currentTab }
            val progress = if (updatedAll.isEmpty()) 0f else {
                updatedAll.count { it.status == TaskStatus.DONE }.toFloat() / updatedAll.size
            }
            _uiState.value = currentState.copy(
                allTasks = updatedAll,
                displayedTasks = displayed,
                progress = progress
            )
        }

        viewModelScope.launch {
            moveTaskUseCase(uid, task.id, targetStatus)
        }
    }

    fun editTask(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank() || trimmed == task.title) return
        val uid = getCurrentUserIdUseCase() ?: return

        // Optimistic UI update
        val currentState = _uiState.value
        if (currentState is TasksUiState.Success) {
            val updatedAll = currentState.allTasks.map {
                if (it.id == task.id) it.copy(title = trimmed, updatedAt = System.currentTimeMillis()) else it
            }
            val displayed = updatedAll.filter { it.status == currentState.currentTab }
            _uiState.value = currentState.copy(
                allTasks = updatedAll,
                displayedTasks = displayed
            )
        }

        viewModelScope.launch {
            editTaskUseCase(uid, task.id, trimmed)
        }
    }

    fun deleteTask(task: Task, onShowUndo: (() -> Unit)? = null) {
        val uid = getCurrentUserIdUseCase() ?: return
        recentlyDeletedTask = task

        // Optimistic UI update
        val currentState = _uiState.value
        if (currentState is TasksUiState.Success) {
            val updatedAll = currentState.allTasks.filter { it.id != task.id }
            val displayed = updatedAll.filter { it.status == currentState.currentTab }
            val progress = if (updatedAll.isEmpty()) 0f else {
                updatedAll.count { it.status == TaskStatus.DONE }.toFloat() / updatedAll.size
            }
            _uiState.value = currentState.copy(
                allTasks = updatedAll,
                displayedTasks = displayed,
                progress = progress
            )
        }

        viewModelScope.launch {
            deleteTaskUseCase(uid, task.id).onSuccess {
                onShowUndo?.invoke()
            }
        }
    }

    fun restoreTask() {
        val uid = getCurrentUserIdUseCase() ?: return
        val taskToRestore = recentlyDeletedTask ?: return
        viewModelScope.launch {
            restoreTaskUseCase(uid, taskToRestore)
            recentlyDeletedTask = null
        }
    }

    fun clearDoneTasks() {
        val uid = getCurrentUserIdUseCase() ?: return
        val doneIds = (_uiState.value as? TasksUiState.Success)
            ?.allTasks?.filter { it.status == TaskStatus.DONE }?.map { it.id } ?: return
            
        viewModelScope.launch {
            clearDoneTasksUseCase(uid, doneIds)
        }
    }
}
