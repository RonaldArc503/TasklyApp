package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.repository.SavedCounts
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
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

    override fun getSavedItems(uid: String): kotlinx.coroutines.flow.Flow<List<SavedItem>> = kotlinx.coroutines.flow.callbackFlow {
        val collection = firestore.collection("users").document(uid).collection("saved")
        var emitted = false
        fun map(snapshot: com.google.firebase.firestore.QuerySnapshot): List<SavedItem> = snapshot.documents.mapNotNull { doc ->
            doc.toObject(com.ronaldcolocho.taskly.data.model.SavedItemDto::class.java)?.toDomain(doc.id)
        }

        launch {
            runCatching { collection.get(Source.CACHE).await() }
                .getOrNull()
                ?.let { cached ->
                    emitted = true
                    trySend(map(cached))
                }
        }
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Un fallo de red o del listener no debe ocultar la cache local.
                // La pantalla parte de un estado vacio seguro y la lectura CACHE
                // anterior puede completar despues de este callback.
                return@addSnapshotListener
            }
            snapshot?.let {
                emitted = true
                trySend(map(it))
            }
        }
        awaitClose { listener.remove() }
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

}
