package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.data.model.ChatMessageDto
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Watch only loaded history; an empty local cache is not evidence of a remote deletion. */
internal fun observeChatMessageUpdates(
    db: FirebaseFirestore, conversationId: String, userId: String, ids: Set<String>
): Flow<Map<String, ChatMessage?>> {
    if (ids.isEmpty()) return flowOf(emptyMap())
    return combine(ids.toList().chunked(10).map { chunk ->
        callbackFlow<Map<String, ChatMessage?>> {
            val listener = db.collection("conversations").document(conversationId).collection("messages")
                .whereIn(FieldPath.documentId(), chunk).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) close(error)
                    else if (snapshot != null) {
                        val updates = mutableMapOf<String, ChatMessage?>()
                        if (!snapshot.metadata.isFromCache) chunk.forEach { updates[it] = null }
                        snapshot.documents.forEach { document ->
                            updates[document.id] = document.toObject(ChatMessageDto::class.java)?.copy(id = document.id)?.toDomain(userId)
                        }
                        trySend(updates)
                    }
                }
            awaitClose { listener.remove() }
        }
    }) { batches -> batches.fold(emptyMap()) { all, updates -> all + updates } }
}
