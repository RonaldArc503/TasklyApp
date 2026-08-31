package com.ronaldcolocho.taskly.domain.usecase.task

import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repository: ITaskRepository
) {
    suspend operator fun invoke(userId: String, title: String): Result<Unit> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Result.failure(Exception("El título no puede estar vacío"))
        return repository.addTask(userId, cleanTitle)
    }
}
