package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface IAuthRepository {
    fun observeAuthState(): Flow<Boolean>
    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun register(
        email: String,
        password: String,
        phone: String?,
        displayName: String?
    ): Result<UserProfile>
    suspend fun loginWithGoogle(idToken: String): Result<UserProfile>
}
