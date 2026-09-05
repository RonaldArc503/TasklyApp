package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
        return remindersCollection(uid).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val title = doc.getString("title") ?: ""
                val dueDate = doc.getLong("dueDate") ?: 0L
                val hasTime = doc.getBoolean("hasTime") ?: false
                val createdAt = doc.getLong("createdAt") ?: 0L
                val updatedAt = doc.getLong("updatedAt") ?: 0L
                
                val receiptsList = doc.get("receipts") as? List<Map<String, Any>> ?: emptyList()
                val receipts = receiptsList.mapNotNull { rMap ->
                    try {
                        Receipt(
                            publicId = rMap["publicId"] as? String ?: "",
                            url = rMap["url"] as? String ?: "",
                            name = rMap["name"] as? String ?: "",
                            size = (rMap["size"] as? Number)?.toLong() ?: 0L,
                            kind = rMap["kind"] as? String ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                Reminder(
                    id = id,
                    title = title,
                    dueDate = dueDate,
                    hasTime = hasTime,
                    receipts = receipts,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            }
        }
    }

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
        )
        reminderDoc(uid, reminder.id).set(data).await()
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
