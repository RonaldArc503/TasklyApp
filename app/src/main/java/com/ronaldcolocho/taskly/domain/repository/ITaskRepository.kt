package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface ITaskRepository {
    fun getTasks(userId: String): Flow<List<Task>>
    suspend fun addTask(userId: String, title: String): Result<Unit>
    suspend fun updateTaskStatus(userId: String, taskId: String, status: TaskStatus): Result<Unit>
    suspend fun deleteTask(userId: String, taskId: String): Result<Unit>
    suspend fun restoreTask(userId: String, task: Task): Result<Unit>
    suspend fun clearDoneTasks(userId: String, doneIds: List<String>): Result<Unit>
}

