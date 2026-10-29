package com.ronaldcolocho.taskly.ui.screen.clipboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.data.repository.ClipboardShelfRepository
import com.ronaldcolocho.taskly.domain.model.ClipboardEntry
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ClipboardShelfUiState(
    val draft: String = "",
    val savePermanently: Boolean = true,
    val entries: List<ClipboardEntry> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ClipboardShelfViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val repository: ClipboardShelfRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClipboardShelfUiState())
    val uiState: StateFlow<ClipboardShelfUiState> = _uiState.asStateFlow()
    private val ownerId = getCurrentUserIdUseCase().orEmpty()
    private val permanentEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val temporaryEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    init {
        if (ownerId.isNotBlank()) {
            viewModelScope.launch {
                repository.observePermanentEntries(ownerId).collect { entries ->
                    permanentEntries.value = entries
                }
            }
            viewModelScope.launch {
                combine(permanentEntries, temporaryEntries) { permanent, temporary ->
                    (permanent + temporary).sortedByDescending(ClipboardEntry::createdAt)
                }.collect { entries ->
                    _uiState.value = _uiState.value.copy(entries = entries)
                }
            }
        }
    }

    fun setDraft(value: String) {
        _uiState.value = _uiState.value.copy(draft = value, message = null)
    }

    fun setSavePermanently(value: Boolean) {
        _uiState.value = _uiState.value.copy(savePermanently = value)
    }

    fun save() {
        val current = _uiState.value
        val text = current.draft.trim()
        if (text.isBlank()) {
            _uiState.value = current.copy(message = "Pega o escribe un texto antes de guardarlo.")
            return
        }
        if (ownerId.isBlank()) {
            _uiState.value = current.copy(message = "No hay una sesión activa para guardar el bloque.")
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, message = null)
            val entry = ClipboardEntry(
                id = UUID.randomUUID().toString(),
                text = text,
                createdAt = System.currentTimeMillis(),
                isPermanent = current.savePermanently
            )
            if (entry.isPermanent) {
                repository.addPermanentEntry(ownerId, entry)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            draft = "",
                            isSaving = false,
                            message = "Bloque guardado en Firebase."
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            message = "No se pudo guardar en Firebase: ${error.message ?: "intenta nuevamente."}"
                        )
                    }
            } else {
                temporaryEntries.value = listOf(entry) + temporaryEntries.value
                _uiState.value = _uiState.value.copy(
                    draft = "",
                    isSaving = false,
                    message = "Bloque temporal guardado para esta sesión."
                )
            }
        }
    }

    fun delete(entry: ClipboardEntry) {
        if (ownerId.isBlank()) return
        viewModelScope.launch {
            if (entry.isPermanent) {
                repository.deletePermanentEntry(ownerId, entry.id)
                    .onSuccess { _uiState.value = _uiState.value.copy(message = "Bloque eliminado de Firebase.") }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            message = "No se pudo eliminar en Firebase: ${error.message ?: "intenta nuevamente."}"
                        )
                    }
            } else {
                temporaryEntries.value = temporaryEntries.value.filterNot { it.id == entry.id }
                _uiState.value = _uiState.value.copy(message = "Bloque eliminado.")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
