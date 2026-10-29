package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.ronaldcolocho.taskly.domain.model.ClipboardEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase-backed shelf for permanent personal text blocks. */
@Singleton
class ClipboardShelfRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun observePermanentEntries(userId: String): Flow<List<ClipboardEntry>> = callbackFlow {
        val collection = entriesCollection(userId)

        launch {
            runCatching { collection.get(Source.CACHE).await() }
                .getOrNull()
                ?.let { cached -> trySend(mapEntries(cached.documents)) }
        }

        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                trySend(mapEntries(snapshot.documents))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun addPermanentEntry(userId: String, entry: ClipboardEntry): Result<Unit> = runCatching {
        entriesCollection(userId).document(entry.id).set(
            mapOf(
                "text" to entry.text,
                "createdAt" to entry.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun deletePermanentEntry(userId: String, entryId: String): Result<Unit> = runCatching {
        entriesCollection(userId).document(entryId).delete().await()
    }

    private fun entriesCollection(userId: String) =
        firestore.collection("users").document(userId).collection("clipboardBlocks")

    private fun mapEntries(documents: List<com.google.firebase.firestore.DocumentSnapshot>) =
        documents.mapNotNull { document ->
            val text = document.getString("text")?.trim().orEmpty()
            if (text.isBlank()) null else ClipboardEntry(
                id = document.id,
                text = text,
                createdAt = document.getLong("createdAt") ?: 0L,
                isPermanent = true
            )
        }.sortedByDescending(ClipboardEntry::createdAt)
}
