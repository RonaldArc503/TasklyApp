package com.ronaldcolocho.taskly.data.mapper

import com.ronaldcolocho.taskly.data.model.TaskDto
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus

fun TaskDto.toDomain(): Task {
    return Task(
        id = this.id,
        title = this.title,
        status = when (this.status) {
            "doing" -> TaskStatus.DOING
            "done" -> TaskStatus.DONE
            else -> TaskStatus.TODO
        },
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun Task.toDto(): TaskDto {
    return TaskDto(
        id = this.id,
        title = this.title,
        status = when (this.status) {
            TaskStatus.TODO -> "todo"
            TaskStatus.DOING -> "doing"
            TaskStatus.DONE -> "done"
        },
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
