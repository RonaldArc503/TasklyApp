package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.model.ChatConversationDto
import com.ronaldcolocho.taskly.data.model.ChatMessageDto
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : IChatRepository {

    override fun getConversations(userId: String): Flow<List<ChatConversation>> = callbackFlow {
        var emitted = false

        // Lectura cache-first: entrega al instante lo que haya en disco (si existe),
        // sin depender de que el listener remoto responda. El timeout garantiza que
        // el flujo siempre emite aunque la caché tarde/bloquee (ej. arranque en frío offline).
        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching {
                    firestore.collection("conversations")
                        .whereArrayContains("participantIds", userId)
                        .get(Source.CACHE)
                        .await()
                }.getOrNull()
            }

            val convs = cached?.documents?.mapNotNull { doc ->
                doc.toObject(ChatConversationDto::class.java)?.copy(id = doc.id)?.toDomain(userId)
            }.orEmpty().sortedWith(
                compareByDescending<ChatConversation> { it.participantIds.size == 1 }
                    .thenByDescending { it.lastMessageAt }
            )
            emitted = true
            trySend(convs)
        }

        val listener = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Si ya hay datos mostrados, un error de red no debe reemplazarlos.
                    if (!emitted) close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        val convs = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ChatConversationDto::class.java)?.copy(id = doc.id)?.toDomain(userId)
                        }.sortedWith(
                            compareByDescending<ChatConversation> { it.participantIds.size == 1 }
                                .thenByDescending { it.lastMessageAt }
                        )
                        emitted = true
                        trySend(convs)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getRecentConversations(userId: String, limit: Long): Flow<List<ChatConversation>> = callbackFlow {
        var emitted = false
        val orderedQuery = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(limit)

        fun mapConversations(documents: List<com.google.firebase.firestore.DocumentSnapshot>) =
            documents.mapNotNull { doc ->
                doc.toObject(ChatConversationDto::class.java)?.copy(id = doc.id)?.toDomain(userId)
            }.sortedWith(
                compareByDescending<ChatConversation> { it.participantIds.size == 1 }
                    .thenByDescending { it.lastMessageAt }
            )

        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching { orderedQuery.get(Source.CACHE).await() }.getOrNull()
            }
            if (cached != null) {
                emitted = true
                trySend(mapConversations(cached.documents))
            }
        }

        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        fun listenTo(query: Query, trimToLimit: Boolean) {
            listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION && !trimToLimit) {
                        listener?.remove()
                        listenTo(
                            firestore.collection("conversations").whereArrayContains("participantIds", userId),
                            trimToLimit = true
                        )
                    } else if (!emitted) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    val conversations = mapConversations(snapshot.documents)
                    emitted = true
                    trySend(if (trimToLimit) conversations.take(limit.toInt()) else conversations)
                }
            }
        }
        listenTo(orderedQuery, trimToLimit = false)
        awaitClose { listener?.remove() }
    }

    override suspend fun getOlderConversations(
        userId: String,
        beforeTimestamp: Long,
        limit: Long
    ): List<ChatConversation> {
        if (beforeTimestamp <= 0L) return emptyList()
        val query = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .whereLessThan("lastMessageAt", beforeTimestamp)
            .limit(limit)
        val snapshot = try {
            query.get(Source.DEFAULT).await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code != FirebaseFirestoreException.Code.FAILED_PRECONDITION) return emptyList()
            firestore.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .get(Source.DEFAULT)
                .await()
        }
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(ChatConversationDto::class.java)?.copy(id = doc.id)?.toDomain(userId)
        }.filter { it.lastMessageAt < beforeTimestamp }
            .sortedWith(compareByDescending<ChatConversation> { it.participantIds.size == 1 }.thenByDescending { it.lastMessageAt })
            .take(limit.toInt())
    }

    override fun getConversation(convId: String, currentUserId: String): Flow<ChatConversation?> = callbackFlow {
        var emitted = false

        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching {
                    firestore.collection("conversations").document(convId)
                        .get(Source.CACHE)
                        .await()
                }.getOrNull()
            }
            if (cached != null && cached.exists()) {
                val conv = cached.toObject(ChatConversationDto::class.java)?.copy(id = cached.id)?.toDomain(currentUserId)
                emitted = true
                trySend(conv)
            } else {
                // Sin datos en caché (chat nunca visitado / sin red): no colgar la pantalla.
                emitted = true
                trySend(fallbackConversation(convId, currentUserId))
            }
        }

        val listener = firestore.collection("conversations").document(convId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (!emitted) close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        val conv = snapshot.toObject(ChatConversationDto::class.java)?.copy(id = snapshot.id)?.toDomain(currentUserId)
                        emitted = true
                        trySend(conv)
                    }
                } else {
                    emitted = true
                    trySend(fallbackConversation(convId, currentUserId))
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getRecentMessages(convId: String, currentUserId: String): Flow<List<ChatMessage>> = callbackFlow {
        var emitted = false

        val query = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)

        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
            val messages = cached?.documents?.mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }.orEmpty()
            emitted = true
            trySend(messages)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (!emitted) close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
                    }
                    emitted = true
                    trySend(messages)
                }
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun getChatInfoMessages(convId: String, currentUserId: String, limit: Long): List<ChatMessage> {
        return try {
            val snapshot = firestore.collection("conversations")
                .document(convId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get(Source.CACHE)
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getOlderMessages(convId: String, currentUserId: String, beforeTimestamp: Long, limit: Long): List<ChatMessage> {
        val query = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .whereLessThan("createdAt", beforeTimestamp)
            .limit(limit)

        val snapshot = try {
            query.get(Source.DEFAULT).await()
        } catch (e: FirebaseFirestoreException) {
            // Sin red: usar el historial previamente sincronizado desde la caché local.
            withTimeoutOrNull(3000) {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
        } ?: return emptyList()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
        }
    }

    private fun fallbackConversation(convId: String, currentUserId: String): ChatConversation = ChatConversation(
        id = convId,
        participantIds = listOf(currentUserId),
        members = emptyMap(),
        unreadCount = 0,
        lastMessage = "",
        lastMessageAt = 0L,
        createdAt = 0L,
        isGroup = false,
        name = null
    )

    override suspend fun sendMessage(convId: String, currentUserId: String, message: ChatMessage): Result<Unit> = runCatching {
        val convRef = firestore.collection("conversations").document(convId)
        val messageRef = convRef.collection("messages").document(message.id)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            if (snapshot.exists()) {
                val participantIds = snapshot.get("participantIds") as? List<String> ?: emptyList()
                val unread = snapshot.get("unread") as? Map<String, Any> ?: emptyMap()

                val newUnread = unread.mapValues { (it.value as? Number)?.toInt() ?: 0 }.toMutableMap()
                participantIds.forEach { pid ->
                    if (pid != currentUserId) {
                        val mentioned = message.mentions.contains(pid)
                        newUnread[pid] = (newUnread[pid] ?: 0) + if (mentioned) 2 else 1
                    }
                }
                newUnread[currentUserId] = 0

                val preview = if (message.text.isBlank() && message.attachments.isNotEmpty()) {
                    val name = message.attachments.first().name
                    val extra = if (message.attachments.size > 1) " (+${message.attachments.size - 1})" else ""
                    "📎 $name$extra"
                } else {
                    message.text
                }

                transaction.update(
                    convRef,
                    mapOf(
                        "lastMessage" to preview,
                        "lastMessageAt" to message.createdAt,
                        "unread" to newUnread
                    )
                )
            }

            val messageMap = mutableMapOf<String, Any>(
                "id" to message.id,
                "senderId" to message.senderId,
                "text" to message.text,
                "createdAt" to message.createdAt
            )
            if (message.edited) messageMap["edited"] = true
            message.editedAt?.let { messageMap["editedAt"] = it }
            message.replyTo?.let { messageMap["replyTo"] = it.toDto() }
            if (message.reactions.isNotEmpty()) messageMap["reactions"] = message.reactions
            if (message.attachments.isNotEmpty()) messageMap["attachments"] = message.attachments.map { it.toDto() }
            if (message.mentions.isNotEmpty()) messageMap["mentions"] = message.mentions
            message.forwardedFrom?.let { messageMap["forwardedFrom"] = it.toDto() }

            transaction.set(messageRef, messageMap)
        }.await()
    }

    override suspend fun markAsRead(convId: String, currentUserId: String): Result<Unit> = runCatching {
        firestore.collection("conversations").document(convId)
            .update("unread.$currentUserId", 0)
            .await()
    }

    override suspend fun toggleReaction(convId: String, msgId: String, uid: String, emoji: String): Result<Unit> = runCatching {
        val msgRef = firestore.collection("conversations").document(convId).collection("messages").document(msgId)
        firestore.runTransaction { tx ->
            val snap = tx.get(msgRef)
            if (!snap.exists()) throw Exception("El mensaje ya no existe.")
            val reactions = (snap.get("reactions") as? Map<String, Any>)
                ?.mapValues { it.value as? String }
                ?.filterValues { !it.isNullOrEmpty() }
                ?.mapValues { it.value!! }
                ?.toMutableMap()
                ?: mutableMapOf()

            if (reactions[uid] == emoji) reactions.remove(uid) else reactions[uid] = emoji

            if (reactions.isEmpty()) {
                tx.update(msgRef, mapOf("reactions" to FieldValue.delete()))
            } else {
                tx.update(msgRef, mapOf("reactions" to reactions))
            }
        }.await()
    }

    override suspend fun editMessage(convId: String, msgId: String, text: String): Result<Unit> = runCatching {
        val clean = text.trim()
        if (clean.isEmpty()) throw Exception("El mensaje no puede estar vacío")

        val msgRef = firestore.collection("conversations").document(convId).collection("messages").document(msgId)
        val convRef = firestore.collection("conversations").document(convId)

        firestore.runTransaction { tx ->
            val msgSnap = tx.get(msgRef)
            if (!msgSnap.exists()) throw Exception("El mensaje ya no existe.")
            val convSnap = tx.get(convRef)
            val msgData = msgSnap.data

            tx.update(msgRef, mapOf("text" to clean, "edited" to true, "editedAt" to System.currentTimeMillis()))

            val lastMessage = convSnap.data?.get("lastMessage") as? String
            val lastMessageAt = (convSnap.data?.get("lastMessageAt") as? Number)?.toLong()
            val msgText = msgData?.get("text") as? String
            val msgCreatedAt = (msgData?.get("createdAt") as? Number)?.toLong()
            if (lastMessage != null && lastMessageAt == msgCreatedAt && lastMessage == msgText) {
                tx.update(convRef, mapOf("lastMessage" to clean))
            }
        }.await()
    }

    override suspend fun deleteMessage(convId: String, msgId: String): Result<Unit> = runCatching {
        val msgRef = firestore.collection("conversations").document(convId).collection("messages").document(msgId)
        val target = msgRef.get().await()
        if (!target.exists()) return@runCatching
        msgRef.delete().await()

        val latest = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        val convRef = firestore.collection("conversations").document(convId)
        if (latest.isEmpty) {
            convRef.update(mapOf("lastMessage" to "", "lastMessageAt" to 0L)).await()
            return@runCatching
        }

        val m = latest.documents.first().data
        val text = m?.get("text") as? String ?: ""
        val attachments = m?.get("attachments") as? List<Map<String, Any>> ?: emptyList()
        val preview = if (text.trim().isEmpty() && attachments.isNotEmpty()) {
            val name = (attachments.first()["name"] as? String) ?: "Adjunto"
            val extra = if (attachments.size > 1) " (+${attachments.size - 1})" else ""
            "📎 $name$extra"
        } else {
            text
        }
        convRef.update(
            mapOf(
                "lastMessage" to preview,
                "lastMessageAt" to ((m?.get("createdAt") as? Number)?.toLong() ?: 0L)
            )
        ).await()
    }

    override suspend fun pinMessage(convId: String, pinned: PinnedMessage): Result<Unit> = runCatching {
        val convRef = firestore.collection("conversations").document(convId)
        firestore.runTransaction { tx ->
            val snap = tx.get(convRef)
            val existing = (snap.get("pinnedMessages") as? List<*>)?.mapNotNull { it as? Map<String, Any> } ?: emptyList()
            val next = listOf(pinned.toDto()) + existing.filter { (it["id"] as? String) != pinned.id }
            tx.update(convRef, mapOf("pinnedMessages" to next))
        }.await()
    }

    override suspend fun unpinMessage(convId: String, msgId: String): Result<Unit> = runCatching {
        val convRef = firestore.collection("conversations").document(convId)
        firestore.runTransaction { tx ->
            val snap = tx.get(convRef)
            val existing = (snap.get("pinnedMessages") as? List<*>)?.mapNotNull { it as? Map<String, Any> } ?: emptyList()
            val next = existing.filter { (it["id"] as? String) != msgId }
            tx.update(convRef, mapOf("pinnedMessages" to next))
        }.await()
    }

    override fun setTyping(convId: String, uid: String) {
        firestore.collection("typing").document(convId)
            .set(mapOf(uid to System.currentTimeMillis()), SetOptions.merge())
    }

    override fun clearTyping(convId: String, uid: String) {
        firestore.collection("typing").document(convId)
            .update(mapOf(uid to FieldValue.delete()))
    }

    override fun subscribeTyping(convId: String): Flow<Map<String, Long>> = callbackFlow {
        val listener = firestore.collection("typing").document(convId)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snap != null && snap.exists()) {
                    val out = mutableMapOf<String, Long>()
                    snap.data?.forEach { (k, v) -> if (v is Number) out[k] = v.toLong() }
                    trySend(out)
                } else {
                    trySend(emptyMap())
                }
            }
        awaitClose { listener.remove() }
    }
}
