package com.ronaldcolocho.taskly.domain.usecase.profile

import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentUserIdUseCase @Inject constructor(
    private val repository: IProfileRepository
) {
    operator fun invoke(): String? = repository.getCurrentUserId()
}

class GetProfileUseCase @Inject constructor(
    private val repository: IProfileRepository
) {
    operator fun invoke(uid: String): Flow<UserProfile?> = repository.getProfile(uid)
}

class LogoutUseCase @Inject constructor(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.logout()
}
