package com.ronaldcolocho.taskly.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.LogoutUseCase
import com.ronaldcolocho.taskly.ui.state.ProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = getCurrentUserIdUseCase()
        if (uid == null) {
            _uiState.value = ProfileUiState.Error("No hay sesión activa")
            return
        }

        getProfileUseCase(uid)
            .onEach { profile ->
                if (profile != null) {
                    _uiState.value = ProfileUiState.Success(profile)
                } else {
                    _uiState.value = ProfileUiState.Error("Perfil no encontrado")
                }
            }
            .catch { e -> _uiState.value = ProfileUiState.Error(e.message ?: "Error desconocido") }
            .launchIn(viewModelScope)
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase().onSuccess {
                onSuccess()
            }
        }
    }
}
