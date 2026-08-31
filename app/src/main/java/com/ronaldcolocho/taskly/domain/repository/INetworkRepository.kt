package com.ronaldcolocho.taskly.domain.repository

import kotlinx.coroutines.flow.Flow

interface INetworkRepository {
    fun observeNetwork(): Flow<Boolean>
}
