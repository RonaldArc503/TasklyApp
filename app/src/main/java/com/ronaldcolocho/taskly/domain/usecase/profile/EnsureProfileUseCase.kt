package com.ronaldcolocho.taskly.domain.usecase.profile

import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import javax.inject.Inject

class EnsureProfileUseCase @Inject constructor(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(
        uid: String,
        email: String,
        displayName: String? = null,
        phone: String? = null,
        photoURL: String = ""
    ): Result<UserProfile> {
        return repository.ensureProfile(uid, email, displayName, phone, photoURL)
    }
}
