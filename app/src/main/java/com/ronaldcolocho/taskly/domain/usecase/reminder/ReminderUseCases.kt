package com.ronaldcolocho.taskly.domain.usecase.reminder

import com.ronaldcolocho.taskly.domain.model.reminder.Receipt
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRemindersUseCase @Inject constructor(private val repo: ReminderRepository) {
    operator fun invoke(uid: String): Flow<List<Reminder>> = repo.getReminders(uid)
}

class CreateReminderUseCase @Inject constructor(private val repo: ReminderRepository) {
    suspend operator fun invoke(uid: String, title: String, dueDate: Long, hasTime: Boolean) {
        val id = "rem-${System.currentTimeMillis().toString(36)}-${java.util.UUID.randomUUID().toString().take(6)}"
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = id,
            title = title,
            dueDate = dueDate,
            hasTime = hasTime,
            receipts = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        repo.addReminder(uid, reminder)
    }
}

class UpdateReminderUseCase @Inject constructor(private val repo: ReminderRepository) {
    suspend operator fun invoke(uid: String, id: String, patch: Map<String, Any?>) {
        repo.updateReminder(uid, id, patch)
    }
}

class DeleteReminderUseCase @Inject constructor(private val repo: ReminderRepository) {
    suspend operator fun invoke(uid: String, id: String) = repo.deleteReminder(uid, id)
}

class AddReceiptUseCase @Inject constructor(private val repo: ReminderRepository) {
    suspend operator fun invoke(uid: String, id: String, receipt: Receipt) = repo.addReceipt(uid, id, receipt)
}

class RemoveReceiptUseCase @Inject constructor(private val repo: ReminderRepository) {
    suspend operator fun invoke(uid: String, id: String, receipt: Receipt) = repo.removeReceipt(uid, id, receipt)
}
