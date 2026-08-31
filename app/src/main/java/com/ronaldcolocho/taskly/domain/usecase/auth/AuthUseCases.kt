package com.ronaldcolocho.taskly.domain.usecase.auth

import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IAuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: IAuthRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.observeAuthState()
}

class LoginUseCase @Inject constructor(
    private val repository: IAuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<UserProfile> =
        repository.login(email, password)
}

class RegisterUseCase @Inject constructor(
    private val repository: IAuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        phone: String?,
        displayName: String? = null
    ): Result<UserProfile> = repository.register(email, password, phone, displayName)
}

class LoginWithGoogleUseCase @Inject constructor(
    private val repository: IAuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<UserProfile> =
        repository.loginWithGoogle(idToken)
}
