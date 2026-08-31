package com.ronaldcolocho.taskly.domain.usecase.chat

import com.ronaldcolocho.taskly.domain.repository.INetworkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNetworkUseCase @Inject constructor(
    private val repository: INetworkRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.observeNetwork()
}
