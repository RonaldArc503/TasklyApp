package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class EditTaskUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, taskId: String, title: String): Result<Unit> {
        return repository.updateTaskTitle(userId, taskId, title)
    }
}
