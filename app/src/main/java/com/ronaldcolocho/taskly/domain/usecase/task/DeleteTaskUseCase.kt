package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class DeleteTaskUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, taskId: String): Result<Unit> {
        return repository.deleteTask(userId, taskId)
    }
}
