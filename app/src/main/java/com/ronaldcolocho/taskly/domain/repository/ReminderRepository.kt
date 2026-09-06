package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.reminder.Receipt
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getReminders(uid: String): Flow<List<Reminder>>
    fun getCompletedReminders(uid: String): Flow<List<Reminder>>
    suspend fun getReminder(uid: String, id: String): Reminder?
    suspend fun addReminder(uid: String, reminder: Reminder)
    suspend fun updateReminder(uid: String, id: String, patch: Map<String, Any?>)
    suspend fun deleteReminder(uid: String, id: String)
    suspend fun addReceipt(uid: String, id: String, receipt: Receipt)
    suspend fun removeReceipt(uid: String, id: String, receipt: Receipt)
}
