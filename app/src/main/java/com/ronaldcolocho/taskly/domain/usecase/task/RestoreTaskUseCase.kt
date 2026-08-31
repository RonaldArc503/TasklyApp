package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class RestoreTaskUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, task: Task): Result<Unit> {
        return repository.restoreTask(userId, task)
    }
}
