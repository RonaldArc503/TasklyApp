package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class ClearDoneTasksUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, doneIds: List<String>): Result<Unit> {
        if (doneIds.isEmpty()) return Result.success(Unit)
        return repository.clearDoneTasks(userId, doneIds)
    }
}
