package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.repository.IPresenceRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : IPresenceRepository {

    override fun subscribePresence(uid: String): Flow<Presence?> = callbackFlow {
        val listener = firestore.collection("presence").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    trySend(
                        Presence(
                            online = snap.getBoolean("online") ?: false,
                            lastSeen = snap.getLong("lastSeen") ?: 0L
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun subscribePresences(uids: Set<String>): Flow<Map<String, Presence>> {
        val requested = uids.filter { it.isNotBlank() }.distinct()
        if (requested.isEmpty()) return flowOf(emptyMap())
        return callbackFlow {
            val lock = Any()
            val valuesByChunk = mutableMapOf<Int, Map<String, Presence>>()
            val registrations = mutableListOf<ListenerRegistration>()
            requested.chunked(PRESENCE_QUERY_CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
                val registration = firestore.collection("presence")
                    .whereIn(FieldPath.documentId(), chunk)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val values = snapshot?.documents.orEmpty().associate { document ->
                            document.id to Presence(
                                online = document.getBoolean("online") ?: false,
                                lastSeen = document.getLong("lastSeen") ?: 0L
                            )
                        }
                        synchronized(lock) {
                            valuesByChunk[chunkIndex] = values
                            trySend(valuesByChunk.values.flatMap { it.entries }.associate { it.toPair() })
                        }
                    }
                registrations += registration
            }
            awaitClose { registrations.forEach(ListenerRegistration::remove) }
        }
    }

    override fun setOnline(uid: String) {
        firestore.collection("presence").document(uid)
            .set(mapOf("online" to true, "lastSeen" to System.currentTimeMillis()), SetOptions.merge())
    }

    override fun setOffline(uid: String) {
        firestore.collection("presence").document(uid)
            .set(mapOf("online" to false, "lastSeen" to System.currentTimeMillis()), SetOptions.merge())
    }


    private companion object {
        const val PRESENCE_QUERY_CHUNK_SIZE = 10
    }
}
