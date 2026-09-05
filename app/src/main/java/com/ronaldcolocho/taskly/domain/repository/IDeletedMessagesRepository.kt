package com.ronaldcolocho.taskly.domain.repository

import kotlinx.coroutines.flow.Flow

interface IDeletedMessagesRepository {
    fun getDeletedMessageIds(): Flow<Set<String>>
    suspend fun markAsDeletedForMe(messageId: String)
}
