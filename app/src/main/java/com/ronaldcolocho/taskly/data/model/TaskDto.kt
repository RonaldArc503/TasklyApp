package com.ronaldcolocho.taskly.data.model

import com.google.firebase.firestore.DocumentId

data class TaskDto(
    @DocumentId val id: String = "",
    val title: String = "",
    val status: String = "todo",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
