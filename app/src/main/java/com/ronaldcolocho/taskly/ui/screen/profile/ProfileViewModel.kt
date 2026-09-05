package com.ronaldcolocho.taskly.ui.screen.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.LogoutUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.UpdateProfilePhotoUseCase
import com.ronaldcolocho.taskly.ui.state.ProfileUiState
import com.ronaldcolocho.taskly.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val updateProfilePhotoUseCase: UpdateProfilePhotoUseCase,
    @ApplicationContext private val context: Context
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
                    val current = _uiState.value as? ProfileUiState.Success
                    _uiState.value = ProfileUiState.Success(
                        user = profile,
                        isPhotoUploading = current?.isPhotoUploading ?: false,
                        photoError = current?.photoError
                    )
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

    fun changeProfilePhoto(uri: Uri) {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        if (current.isPhotoUploading) return

        _uiState.value = current.copy(isPhotoUploading = true, photoError = null)
        viewModelScope.launch {
            var temporaryFile: File? = null
            val result = runCatching {
                val mimeType = withContext(Dispatchers.IO) {
                    context.contentResolver.getType(uri)
                } ?: throw IllegalArgumentException("No se pudo identificar la imagen.")

                require(mimeType.startsWith("image/")) { "Selecciona una imagen vÃ¡lida." }

                temporaryFile = withContext(Dispatchers.IO) {
                    val size = FileUtil.getSize(context, uri)
                    require(!FileUtil.isTooBig(size)) { "La imagen no puede superar 20 MB." }
                    FileUtil.getFileFromUri(context, uri)
                        ?: throw IllegalArgumentException("No se pudo leer la imagen.")
                }
                updateProfilePhotoUseCase(current.user, temporaryFile!!, mimeType).getOrThrow()
            }

            withContext(Dispatchers.IO) {
                temporaryFile?.delete()
            }

            val latest = _uiState.value as? ProfileUiState.Success ?: return@launch
            val uploadedUrl = result.getOrNull()
            _uiState.value = latest.copy(
                user = uploadedUrl?.let { latest.user.copy(photoURL = it) } ?: latest.user,
                isPhotoUploading = false,
                photoError = result.exceptionOrNull()?.message?.let { "No se pudo actualizar la foto: $it" }
            )
        }
    }

    fun clearPhotoError() {
        val current = _uiState.value as? ProfileUiState.Success ?: return
        _uiState.value = current.copy(photoError = null)
    }
}
