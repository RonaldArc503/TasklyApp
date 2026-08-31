package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class MoveTaskUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, taskId: String, status: TaskStatus): Result<Unit> {
        return repository.updateTaskStatus(userId, taskId, status)
    }
}
