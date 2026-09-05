package com.ronaldcolocho.taskly.ui.screen.reminders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.model.reminder.Receipt
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.repository.ICloudinaryRepository
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.reminder.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface RemindersUiState {
    object Loading : RemindersUiState
    data class Success(
        val items: List<Reminder>,
        val error: String? = null,
        val isRefreshing: Boolean = false
    ) : RemindersUiState
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val createReminderUseCase: CreateReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val addReceiptUseCase: AddReceiptUseCase,
    private val removeReceiptUseCase: RemoveReceiptUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val cloudinaryRepository: ICloudinaryRepository,
    private val alarmManager: ReminderAlarmManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RemindersUiState>(
        RemindersUiState.Success(emptyList(), isRefreshing = true)
    )
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    private val currentUserId = getCurrentUserIdUseCase()

    init {
        loadReminders()
    }

    private fun loadReminders() {
        if (currentUserId == null) return
        viewModelScope.launch {
            var lastAlarmSignature: List<Triple<String, Long, String>>? = null
            getRemindersUseCase(currentUserId)
                .onStart { emit(emptyList()) }
                .collect { list ->
                val alarmSignature = list.map { Triple(it.id, it.dueDate, "${it.title}:${it.hasTime}") }.sortedBy { it.first }
                if (alarmSignature != lastAlarmSignature) {
                    lastAlarmSignature = alarmSignature
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        alarmManager.scheduleAlarms(list)
                    }
                }

                // Sort by urgency, then dueDate, then createdAt
                val sorted = list.sortedWith { a, b ->
                    val ua = a.urgency.ordinal
                    val ub = b.urgency.ordinal
                    if (ua != ub) return@sortedWith ua.compareTo(ub)
                    if (a.dueDate > 0 && b.dueDate > 0) return@sortedWith a.dueDate.compareTo(b.dueDate)
                    b.createdAt.compareTo(a.createdAt)
                }
                _uiState.value = RemindersUiState.Success(sorted, isRefreshing = false)
            }
        }
    }

    fun addReminder(title: String, dueDate: Long, hasTime: Boolean) {
        if (currentUserId == null) return
        if (title.isBlank()) {
            showError("Escribe el detalle del aviso.")
            return
        }
        if (dueDate <= 0L) {
            showError("Selecciona una fecha para el recordatorio.")
            return
        }
        viewModelScope.launch {
            try {
                createReminderUseCase(currentUserId, title.trim(), dueDate, hasTime)
            } catch (e: Exception) {
                showError("No se pudo crear el recordatorio.")
            }
        }
    }

    fun updateDueDate(id: String, newDate: Long) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                updateReminderUseCase(currentUserId, id, mapOf("dueDate" to newDate))
            } catch (e: Exception) {
                // Ignore or show error
            }
        }
    }

    fun updateReminder(id: String, title: String, dueDate: Long, hasTime: Boolean) {
        if (currentUserId == null) return
        if (title.isBlank()) {
            showError("Escribe el detalle del aviso.")
            return
        }
        viewModelScope.launch {
            try {
                updateReminderUseCase(
                    currentUserId,
                    id,
                    mapOf("title" to title.trim(), "dueDate" to dueDate, "hasTime" to hasTime)
                )
            } catch (e: Exception) {
                showError("No se pudo actualizar el recordatorio.")
            }
        }
    }

    fun deleteReminder(id: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                alarmManager.cancel(id)
                deleteReminderUseCase(currentUserId, id)
            } catch (e: Exception) {
                showError("Error al eliminar.")
            }
        }
    }

    fun uploadReceipt(reminderId: String, file: File, kind: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                val mimeType = if (kind == "pdf") "application/pdf" else "image/*"
                val result = cloudinaryRepository.uploadFile(file, mimeType, currentUserId)
                result.onSuccess { attachment ->
                    val receipt = Receipt(
                        publicId = attachment.publicId,
                        url = attachment.url,
                        name = file.name,
                        size = attachment.size,
                        kind = kind
                    )
                    addReceiptUseCase(currentUserId, reminderId, receipt)
                }.onFailure {
                    showError("Error al subir archivo.")
                }
            } catch (e: Exception) {
                showError("Error al subir archivo.")
            }
        }
    }

    fun removeReceipt(reminderId: String, receipt: Receipt) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                removeReceiptUseCase(currentUserId, reminderId, receipt)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun showError(msg: String) {
        val curr = _uiState.value
        if (curr is RemindersUiState.Success) {
            _uiState.value = curr.copy(error = msg)
        }
    }

    fun clearError() {
        val curr = _uiState.value
        if (curr is RemindersUiState.Success) {
            _uiState.value = curr.copy(error = null)
        }
    }
}
