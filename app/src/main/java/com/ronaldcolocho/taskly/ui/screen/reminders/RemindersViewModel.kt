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
        val completedItems: List<Reminder> = emptyList(),
        val error: String? = null,
        val isRefreshing: Boolean = false
    ) : RemindersUiState
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val getCompletedRemindersUseCase: GetCompletedRemindersUseCase,
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
    private val activeReminders = MutableStateFlow<List<Reminder>>(emptyList())
    private val completedReminders = MutableStateFlow<List<Reminder>>(emptyList())

    init {
        loadReminders()
    }

    private fun loadReminders() {
        if (currentUserId == null) return
        viewModelScope.launch {
            getCompletedRemindersUseCase(currentUserId).collect { completed ->
                completedReminders.value = completed
                val state = _uiState.value as? RemindersUiState.Success ?: return@collect
                _uiState.value = state.copy(completedItems = completed)
            }
        }
        viewModelScope.launch {
            var lastAlarmSignature: List<Triple<String, Long, String>>? = null
            getRemindersUseCase(currentUserId)
                .collect { list ->
                activeReminders.value = list
                val alarmSignature = list.map { Triple(it.id, it.snoozedUntil.takeIf { t -> t > 0L } ?: it.dueDate, "${it.title}:${it.hasTime}") }.sortedBy { it.first }
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
                _uiState.value = RemindersUiState.Success(sorted, completedReminders.value, isRefreshing = false)
            }
        }
    }

    fun refreshAlarms() {
        alarmManager.scheduleAlarms(activeReminders.value)
    }

    fun addReminder(
        title: String,
        dueDate: Long,
        hasTime: Boolean,
        repeatType: com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
    ) {
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
                // Do not wait for the Firestore observer in this screen: the local alarm must
                // exist before the user can leave or close the app.
                val reminder = createReminderUseCase(currentUserId, title.trim(), dueDate, hasTime, repeatType)
                alarmManager.scheduleReminder(reminder)
            } catch (e: Exception) {
                showError("No se pudo crear el recordatorio.")
            }
        }
    }

    fun updateDueDate(id: String, newDate: Long) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                val anchor = if (newDate > 0L) {
                    java.time.Instant.ofEpochMilli(newDate).atZone(java.time.ZoneId.systemDefault()).dayOfMonth
                } else 0
                updateReminderUseCase(currentUserId, id, mapOf("dueDate" to newDate, "anchorDay" to anchor, "snoozedUntil" to 0L))
                activeReminders.value.firstOrNull { it.id == id }?.let { reminder ->
                    alarmManager.scheduleReminder(reminder.copy(dueDate = newDate, anchorDay = anchor, snoozedUntil = 0L))
                }
            } catch (e: Exception) {
                // Ignore or show error
            }
        }
    }

    fun updateReminder(id: String, title: String, dueDate: Long, hasTime: Boolean, repeatType: com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType = com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE) {
        if (currentUserId == null) return
        if (title.isBlank()) {
            showError("Escribe el detalle del aviso.")
            return
        }
        viewModelScope.launch {
            try {
                val anchor = if (dueDate > 0L) {
                    java.time.Instant.ofEpochMilli(dueDate).atZone(java.time.ZoneId.systemDefault()).dayOfMonth
                } else 0
                updateReminderUseCase(
                    currentUserId,
                    id,
                    mapOf(
                        "title" to title.trim(),
                        "dueDate" to dueDate,
                        "hasTime" to hasTime,
                        "repeatType" to repeatType.name,
                        "snoozedUntil" to 0L,
                        "anchorDay" to anchor
                    )
                )
                activeReminders.value.firstOrNull { it.id == id }?.let { reminder ->
                    alarmManager.scheduleReminder(
                        reminder.copy(
                            title = title.trim(),
                            dueDate = dueDate,
                            hasTime = hasTime,
                            repeatType = repeatType,
                            snoozedUntil = 0L,
                            anchorDay = anchor
                        )
                    )
                }
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

    fun completeReminder(reminder: Reminder) {
        if (currentUserId == null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (reminder.repeatType == com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE) {
                alarmManager.cancel(reminder.id)
                val patch = mapOf(
                    "isCompleted" to true,
                    "completedAt" to now,
                    "snoozedUntil" to 0L
                )
                runCatching { updateReminderUseCase(currentUserId, reminder.id, patch) }
                    .onFailure { showError("No se pudo completar el recordatorio.") }
            } else {
                // 1. Archive current period instance into completed history
                val archiveId = "rem-hist-${now.toString(36)}-${java.util.UUID.randomUUID().toString().take(6)}"
                val periodLabel = com.ronaldcolocho.taskly.domain.model.reminder.reminderTimeLabel(reminder.dueDate, false)
                val archiveRecord = reminder.copy(
                    id = archiveId,
                    title = "${reminder.title} ($periodLabel)",
                    isCompleted = true,
                    completedAt = now,
                    repeatType = com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE,
                    snoozedUntil = 0L
                )
                runCatching {
                    createReminderUseCase.repo.addReminder(currentUserId, archiveRecord)
                }

                // 2. Advance active recurring reminder to next cycle and clear receipts
                val nextDue = com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrence(
                    dueDate = reminder.dueDate,
                    repeatType = reminder.repeatType,
                    anchorDay = reminder.effectiveAnchorDay,
                    completedAt = now
                )
                val patch = mapOf(
                    "dueDate" to nextDue,
                    "completedAt" to now,
                    "receipts" to emptyList<Map<String, Any>>(),
                    "snoozedUntil" to 0L
                )
                runCatching { updateReminderUseCase(currentUserId, reminder.id, patch) }
                    .onSuccess {
                        val advanced = reminder.copy(
                            dueDate = nextDue,
                            receipts = emptyList(),
                            snoozedUntil = 0L
                        )
                        alarmManager.scheduleReminder(advanced)
                    }
                    .onFailure { showError("No se pudo completar el recordatorio.") }
            }
        }
    }

    fun restoreReminder(reminder: Reminder) {
        if (currentUserId == null) return
        viewModelScope.launch {
            runCatching { updateReminderUseCase(currentUserId, reminder.id, mapOf("isCompleted" to false, "completedAt" to 0L)) }
                .onFailure { showError("No se pudo restaurar el recordatorio.") }
        }
    }

    fun snoozeReminder(id: String, minutes: Long) {
        if (currentUserId == null) return
        val until = System.currentTimeMillis() + minutes * 60_000L
        viewModelScope.launch {
            runCatching {
                updateReminderUseCase(currentUserId, id, mapOf("snoozedUntil" to until))
                activeReminders.value.firstOrNull { it.id == id }?.let { reminder ->
                    alarmManager.scheduleReminder(reminder.copy(snoozedUntil = until))
                }
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
