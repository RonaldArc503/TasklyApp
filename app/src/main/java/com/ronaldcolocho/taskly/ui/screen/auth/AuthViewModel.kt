package com.ronaldcolocho.taskly.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.ronaldcolocho.taskly.domain.usecase.auth.LoginUseCase
import com.ronaldcolocho.taskly.domain.usecase.auth.LoginWithGoogleUseCase
import com.ronaldcolocho.taskly.domain.usecase.auth.ObserveAuthStateUseCase
import com.ronaldcolocho.taskly.domain.usecase.auth.RegisterUseCase
import com.ronaldcolocho.taskly.domain.util.normalizePhone
import com.ronaldcolocho.taskly.ui.state.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) : ViewModel() {

    val authState: StateFlow<AuthState> = observeAuthStateUseCase()
        .map { loggedIn -> if (loggedIn) AuthState.LoggedIn else AuthState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Uninitialized)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Ingresa tu correo y contraseña."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            loginUseCase(email.trim(), password).onFailure {
                _error.value = authErrorMessage(it)
            }
            _isBusy.value = false
        }
    }

    fun register(email: String, password: String, confirm: String, phone: String) {
        if (password != confirm) {
            _error.value = "Las contraseñas no coinciden."
            return
        }
        if (password.length < 6) {
            _error.value = "La contraseña debe tener al menos 6 caracteres."
            return
        }
        val normalizedPhone = normalizePhone(phone)
        if (normalizedPhone.length != 8) {
            _error.value = "El número de teléfono debe tener 8 dígitos (El Salvador)."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            registerUseCase(email.trim(), password, normalizedPhone).onFailure {
                _error.value = authErrorMessage(it)
            }
            _isBusy.value = false
        }
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _isBusy.value = true
            loginWithGoogleUseCase(idToken).onFailure {
                _error.value = authErrorMessage(it)
            }
            _isBusy.value = false
        }
    }

    fun googleSignInError() {
        _error.value = "No se pudo iniciar sesión con Google. Intenta de nuevo."
    }
}

fun authErrorMessage(e: Throwable): String {
    val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
    return when (code) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo."
        "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
        "ERROR_INVALID_CREDENTIAL" -> "Correo o contraseña incorrectos."
        "ERROR_WRONG_PASSWORD" -> "La contraseña es incorrecta."
        "ERROR_USER_NOT_FOUND" -> "No existe una cuenta con ese correo."
        "ERROR_WEAK_PASSWORD" -> "La contraseña debe tener al menos 6 caracteres."
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Intenta más tarde."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Sin conexión. Revisa tu internet."
        else -> e.message ?: "Ocurrió un error inesperado. Intenta de nuevo."
    }
}
