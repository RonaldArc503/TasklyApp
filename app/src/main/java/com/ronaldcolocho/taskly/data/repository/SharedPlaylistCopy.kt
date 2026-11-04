package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Publishes only a complete copy; a newer attempt can safely resume an interrupted import. */
internal suspend fun copySharedPlaylist(
    firestore: FirebaseFirestore, target: DocumentReference, source: DocumentReference, uid: String, shareId: String
) {
    fun com.google.firebase.firestore.DocumentSnapshot.complete() = exists() &&
        getBoolean("copyInProgress") != true && getString("sharedOwnerId").isNullOrBlank()
    if (target.get(Source.SERVER).await().complete()) return
    val original = source.get(Source.SERVER).await()
    check(original.exists() && original.getString("shareId") == shareId) { "La lista ya no está disponible." }
    val songs = source.collection("tracks").get(Source.SERVER).await().documents.associate { document ->
        val data = document.data.orEmpty().toMutableMap()
        val id = document.getString("mediaId").orEmpty().ifBlank { document.id }
        data["mediaId"] = id
        trackDocumentId(id) to data
    }
    val attempt = UUID.randomUUID().toString()
    val metadata = mapOf("userId" to uid, "name" to original.getString("name").orEmpty(),
        "coverUrl" to original.getString("coverUrl").orEmpty(), "songCount" to songs.size,
        "createdAt" to System.currentTimeMillis(), "updatedAt" to System.currentTimeMillis(),
        "copiedFromShareId" to shareId)
    val claimed = firestore.runTransaction { tx ->
        val existing = tx.get(target)
        if (existing.complete()) false else {
            tx.set(target, metadata + mapOf("copyInProgress" to true, "copyAttempt" to attempt,
                "sharedOwnerId" to source.parent.parent!!.id, "sharedPlaylistId" to source.id,
                "createdAt" to (existing.getLong("createdAt") ?: metadata.getValue("createdAt"))))
            true
        }
    }.await()
    if (!claimed) return

    suspend fun write(action: (Transaction) -> Unit): Boolean = firestore.runTransaction { tx ->
        if (tx.get(target).getString("copyAttempt") != attempt) false
        else { action(tx); true }
    }.await()

    suspend fun awaitOtherCopy() {
        val completed = withTimeoutOrNull(30_000) {
            callbackFlow {
                val listener = target.addSnapshotListener { snapshot, error ->
                    if (error != null) close(error)
                    else if (snapshot?.exists() == false) close(IllegalStateException("La copia fue eliminada."))
                    else if (snapshot?.complete() == true) trySend(true)
                }
                awaitClose { listener.remove() }
            }.first()
        }
        check(completed == true) { "La copia se está preparando en otro dispositivo. Vuelve a intentarlo." }
    }

    // Only incomplete imports reach this cleanup; existing editable lists are never overwritten.
    val pending = target.collection("tracks").get(Source.SERVER).await().documents
    for (chunk in pending.chunked(400)) {
        if (!write { tx -> chunk.forEach { tx.delete(it.reference) } }) { awaitOtherCopy(); return }
    }
    for (chunk in songs.entries.chunked(400)) {
        if (!write { tx -> chunk.forEach { tx.set(target.collection("tracks").document(it.key), it.value) } }) {
            awaitOtherCopy(); return
        }
    }
    if (!write { tx -> tx.set(target, metadata) }) awaitOtherCopy()
}
