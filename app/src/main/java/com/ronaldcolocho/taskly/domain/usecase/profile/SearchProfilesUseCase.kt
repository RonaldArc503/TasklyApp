package com.ronaldcolocho.taskly.domain.usecase.profile

import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import javax.inject.Inject

class SearchProfilesUseCase @Inject constructor(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(term: String): Result<List<UserProfile>> {
        return repository.searchProfiles(term)
    }
}
