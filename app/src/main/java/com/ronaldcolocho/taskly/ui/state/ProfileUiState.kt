package com.ronaldcolocho.taskly.ui.state

import com.ronaldcolocho.taskly.domain.model.UserProfile

sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Success(val user: UserProfile) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
