package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    operator fun invoke(userId: String): Flow<List<Task>> {
        return repository.getTasks(userId)
    }

    operator fun invoke(userId: String, status: TaskStatus): Flow<List<Task>> {
        return repository.getTasksByStatus(userId, status)
    }
}
