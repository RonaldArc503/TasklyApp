package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.model.TaskDto
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ITaskRepository {

    private fun tasksCollection(userId: String) =
        firestore.collection("users").document(userId).collection("tasks")

    override fun getTasks(userId: String): Flow<List<Task>> = callbackFlow {
        val listener = tasksCollection(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TaskDto::class.java)?.copy(id = doc.id)?.toDomain()
                    }.sortedBy { it.createdAt }
                    trySend(tasks)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addTask(userId: String, title: String): Result<Unit> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dto = TaskDto(id = id, title = title, status = "todo", createdAt = now, updatedAt = now)
        tasksCollection(userId).document(id).set(dto).await()
    }

    override suspend fun updateTaskStatus(userId: String, taskId: String, status: TaskStatus): Result<Unit> = runCatching {
        val statusString = when(status) {
            TaskStatus.TODO -> "todo"
            TaskStatus.DOING -> "doing"
            TaskStatus.DONE -> "done"
        }
        tasksCollection(userId).document(taskId).update(
            mapOf(
                "status" to statusString,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> = runCatching {
        tasksCollection(userId).document(taskId).delete().await()
    }
    
    override suspend fun restoreTask(userId: String, task: Task): Result<Unit> = runCatching {
        val dto = task.toDto().copy(updatedAt = System.currentTimeMillis())
        tasksCollection(userId).document(task.id).set(dto).await()
    }
    override suspend fun clearDoneTasks(userId: String, doneIds: List<String>): Result<Unit> = runCatching {
        val batch = firestore.batch()
        doneIds.forEach { id ->
            batch.delete(tasksCollection(userId).document(id))
        }
        batch.commit().await()
    }
}
