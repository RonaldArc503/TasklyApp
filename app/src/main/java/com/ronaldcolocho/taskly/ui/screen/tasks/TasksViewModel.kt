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
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val restoreTaskUseCase: RestoreTaskUseCase,
    private val clearDoneTasksUseCase: ClearDoneTasksUseCase
) : ViewModel() {

    private val _currentTab = MutableStateFlow(TaskStatus.TODO)
    private val _uiState = MutableStateFlow<TasksUiState>(TasksUiState.Loading)
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

        combine(getTasksUseCase(uid), _currentTab) { tasks, tab ->
            val displayed = tasks.filter { it.status == tab }
            val progress = if (tasks.isEmpty()) 0f else {
                tasks.count { it.status == TaskStatus.DONE }.toFloat() / tasks.size
            }
            TasksUiState.Success(
                allTasks = tasks,
                displayedTasks = displayed,
                currentTab = tab,
                progress = progress
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

    fun moveTaskToNextStatus(task: Task) {
        val uid = getCurrentUserIdUseCase() ?: return
        val nextStatus = when (task.status) {
            TaskStatus.TODO -> TaskStatus.DOING
            TaskStatus.DOING -> TaskStatus.DONE
            TaskStatus.DONE -> TaskStatus.TODO
        }
        viewModelScope.launch {
            moveTaskUseCase(uid, task.id, nextStatus)
        }
    }

    fun deleteTask(task: Task, onShowUndo: () -> Unit) {
        val uid = getCurrentUserIdUseCase() ?: return
        recentlyDeletedTask = task
        viewModelScope.launch {
            deleteTaskUseCase(uid, task.id).onSuccess {
                onShowUndo()
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
