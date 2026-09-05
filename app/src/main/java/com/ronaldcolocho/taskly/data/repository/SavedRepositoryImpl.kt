package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Query
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.repository.SavedCounts
import kotlinx.coroutines.channels.awaitClose
import com.ronaldcolocho.taskly.domain.repository.ISavedRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SavedRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ISavedRepository {

    override suspend fun saveMessage(uid: String, item: SavedItem): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).collection("saved")
            .document(item.id)
            .set(item.toDto())
            .await()
    }

    override fun getSavedItems(uid: String, filter: String): kotlinx.coroutines.flow.Flow<List<SavedItem>> = kotlinx.coroutines.flow.callbackFlow {
        val collection = firestore.collection("users").document(uid).collection("saved")
        val query: Query = when (filter) {
            "messages" -> collection.whereEqualTo("kind", "message")
                .orderBy("pinned", Query.Direction.DESCENDING)
                .orderBy("pinnedAt", Query.Direction.DESCENDING)
                .orderBy("savedAt", Query.Direction.DESCENDING)
            "links" -> collection.whereEqualTo("kind", "link")
                .orderBy("pinned", Query.Direction.DESCENDING)
                .orderBy("pinnedAt", Query.Direction.DESCENDING)
                .orderBy("savedAt", Query.Direction.DESCENDING)
            "pinned" -> collection.whereEqualTo("pinned", true)
                .orderBy("pinnedAt", Query.Direction.DESCENDING)
                .orderBy("savedAt", Query.Direction.DESCENDING)
            else -> collection.orderBy("pinned", Query.Direction.DESCENDING)
                .orderBy("pinnedAt", Query.Direction.DESCENDING)
                .orderBy("savedAt", Query.Direction.DESCENDING)
        }

        // This fallback keeps the screen usable until a newly declared composite
        // index has been deployed and finished building in the Firebase project.
        val fallbackQuery: Query = when (filter) {
            "messages" -> collection.whereEqualTo("kind", "message")
            "links" -> collection.whereEqualTo("kind", "link")
            "pinned" -> collection.whereEqualTo("pinned", true)
            else -> collection
        }
        var listener: ListenerRegistration? = null
        fun listen(activeQuery: Query, sortLocally: Boolean) {
            listener = activeQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION && !sortLocally) {
                        listener?.remove()
                        listen(fallbackQuery, sortLocally = true)
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val mapped = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(com.ronaldcolocho.taskly.data.model.SavedItemDto::class.java)?.toDomain(doc.id)
                    }
                    val items = if (sortLocally) mapped.sortedWith(savedItemComparator) else mapped
                    trySend(items)
                }
            }
        }
        listen(query, sortLocally = false)
        awaitClose { listener?.remove() }
    }

    override suspend fun getSavedCounts(uid: String): SavedCounts {
        val collection = firestore.collection("users").document(uid).collection("saved")
        val total = collection.count().get(AggregateSource.SERVER).await().count
        val pinned = collection.whereEqualTo("pinned", true)
            .count().get(AggregateSource.SERVER).await().count
        return SavedCounts(total = total.toInt(), pinned = pinned.toInt())
    }

    override suspend fun deleteSaved(uid: String, id: String): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).collection("saved")
            .document(id)
            .delete()
            .await()
    }

    override suspend fun setPinned(uid: String, id: String, pinned: Boolean): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).collection("saved")
            .document(id)
            .update(mapOf("pinned" to pinned, "pinnedAt" to if (pinned) System.currentTimeMillis() else 0L))
            .await()
    }

    private companion object {
        val savedItemComparator = compareByDescending<SavedItem> { it.pinned }
            .thenByDescending { if (it.pinned) it.pinnedAt else it.savedAt }
            .thenByDescending { it.savedAt }
    }
}
