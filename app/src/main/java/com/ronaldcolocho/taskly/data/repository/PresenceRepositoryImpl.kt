package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.repository.IPresenceRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    override fun setOnline(uid: String) {
        firestore.collection("presence").document(uid)
            .set(mapOf("online" to true, "lastSeen" to System.currentTimeMillis()), SetOptions.merge())
    }

    override fun setOffline(uid: String) {
        firestore.collection("presence").document(uid)
            .set(mapOf("online" to false, "lastSeen" to System.currentTimeMillis()), SetOptions.merge())
    }
}
