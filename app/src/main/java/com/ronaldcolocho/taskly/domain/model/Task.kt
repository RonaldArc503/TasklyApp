package com.ronaldcolocho.taskly.domain.model

enum class TaskStatus {
    TODO, DOING, DONE
}

data class Task(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val createdAt: Long,
    val updatedAt: Long
)
