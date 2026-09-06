package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.snapshots
import com.ronaldcolocho.taskly.domain.model.reminder.Receipt
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReminderRepository {

    private fun remindersCollection(uid: String) =
        firestore.collection("users").document(uid).collection("reminders")

    private fun reminderDoc(uid: String, id: String) =
        remindersCollection(uid).document(id)

    override fun getReminders(uid: String): Flow<List<Reminder>> {
        // Los documentos anteriores a completedAt no tienen isCompleted; se consideran activos.
        return remindersCollection(uid).snapshots()
            .map { it.documents.map(::toReminder).filterNot(Reminder::isCompleted) }
    }

    override fun getCompletedReminders(uid: String): Flow<List<Reminder>> =
        remindersCollection(uid).whereEqualTo("isCompleted", true).snapshots()
            .map { it.documents.map(::toReminder).sortedByDescending(Reminder::completedAt) }

    override suspend fun getReminder(uid: String, id: String): Reminder? =
        reminderDoc(uid, id).get().await().takeIf { it.exists() }?.let(::toReminder)

    override suspend fun addReminder(uid: String, reminder: Reminder) {
        val data = mapOf(
            "title" to reminder.title,
            "dueDate" to reminder.dueDate,
            "hasTime" to reminder.hasTime,
            "receipts" to reminder.receipts.map { r ->
                mapOf(
                    "publicId" to r.publicId,
                    "url" to r.url,
                    "name" to r.name,
                    "size" to r.size,
                    "kind" to r.kind
                )
            },
            "createdAt" to reminder.createdAt,
            "updatedAt" to reminder.updatedAt
            , "isCompleted" to reminder.isCompleted
            , "completedAt" to reminder.completedAt
            , "repeatType" to reminder.repeatType.name
            , "snoozedUntil" to reminder.snoozedUntil
        )
        reminderDoc(uid, reminder.id).set(data).await()
    }

    private fun toReminder(doc: DocumentSnapshot): Reminder {
        val receipts = (doc.get("receipts") as? List<Map<String, Any>>).orEmpty().map {
            Receipt(it["publicId"] as? String ?: "", it["url"] as? String ?: "", it["name"] as? String ?: "", (it["size"] as? Number)?.toLong() ?: 0L, it["kind"] as? String ?: "")
        }
        val repeat = runCatching { com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.valueOf(doc.getString("repeatType") ?: "NONE") }.getOrDefault(com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE)
        return Reminder(doc.id, doc.getString("title") ?: "", doc.getLong("dueDate") ?: 0L, doc.getBoolean("hasTime") ?: false, receipts, doc.getLong("createdAt") ?: 0L, doc.getLong("updatedAt") ?: 0L, doc.getBoolean("isCompleted") ?: false, doc.getLong("completedAt") ?: 0L, repeat, doc.getLong("snoozedUntil") ?: 0L)
    }

    override suspend fun updateReminder(uid: String, id: String, patch: Map<String, Any?>) {
        val data = patch.toMutableMap()
        data["updatedAt"] = System.currentTimeMillis()
        reminderDoc(uid, id).update(data).await()
    }

    override suspend fun deleteReminder(uid: String, id: String) {
        reminderDoc(uid, id).delete().await()
    }

    override suspend fun addReceipt(uid: String, id: String, receipt: Receipt) {
        val map = mapOf(
            "publicId" to receipt.publicId,
            "url" to receipt.url,
            "name" to receipt.name,
            "size" to receipt.size,
            "kind" to receipt.kind
        )
        reminderDoc(uid, id).update(
            "receipts", FieldValue.arrayUnion(map),
            "updatedAt", System.currentTimeMillis()
        ).await()
    }

    override suspend fun removeReceipt(uid: String, id: String, receipt: Receipt) {
        val map = mapOf(
            "publicId" to receipt.publicId,
            "url" to receipt.url,
            "name" to receipt.name,
            "size" to receipt.size,
            "kind" to receipt.kind
        )
        reminderDoc(uid, id).update(
            "receipts", FieldValue.arrayRemove(map),
            "updatedAt", System.currentTimeMillis()
        ).await()
    }
}
