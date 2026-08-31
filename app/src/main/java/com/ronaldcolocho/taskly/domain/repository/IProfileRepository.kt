package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.UserProfile

import kotlinx.coroutines.flow.Flow

interface IProfileRepository {
    fun getCurrentUserId(): String?
    fun getProfile(uid: String): Flow<UserProfile?>

    /**
     * Crea el perfil si no existe o completa campos faltantes (paridad con ensureProfile de la web).
     */
    suspend fun ensureProfile(
        uid: String,
        email: String,
        displayName: String?,
        phone: String?,
        photoURL: String
    ): Result<UserProfile>

    suspend fun updateProfile(profile: UserProfile): Result<Unit>
    suspend fun searchProfiles(term: String): Result<List<UserProfile>>
    suspend fun logout(): Result<Unit>
}
