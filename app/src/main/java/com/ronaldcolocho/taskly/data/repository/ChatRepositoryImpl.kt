package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.model.ChatConversationDto
import com.ronaldcolocho.taskly.data.model.ChatMessageDto
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatAttachmentRef
import com.ronaldcolocho.taskly.domain.model.referenceIn
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.ChatSearchHit
import com.ronaldcolocho.taskly.domain.model.ConversationUserState
import com.ronaldcolocho.taskly.domain.model.ConversationReceipt
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import com.ronaldcolocho.taskly.domain.repository.IDeletedMessagesRepository
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val deletedMessages: IDeletedMessagesRepository
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
                    trySend(
                        if (trimToLimit) conversations.sortedByDescending { it.lastMessageAt }.take(limit.toInt())
                        else conversations
                    )
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

    override fun getConversation(convId: String, currentUserId: String): Flow<ChatConversation?> =
        combine(observeConversation(convId, currentUserId), observeUserState(convId, currentUserId)) { conversation, state ->
            conversation?.withUserState(state)
        }.mapLatest { conversation ->
            val cutoff = conversation?.deletedThrough
            when {
                conversation == null -> null
                conversation.isDeletedForMe -> conversation.copy(pinnedMessages = emptyList(), unreadCount = 0, lastMessage = "")
                cutoff == null || conversation.pinnedMessages.isEmpty() -> conversation
                else -> coroutineScope {
                    val messages = firestore.collection("conversations").document(convId).collection("messages")
                    val pins = conversation.pinnedMessages.map { pin ->
                        async {
                            val createdAt = runCatching { messages.document(pin.id).get().await().getLong("createdAt") }.getOrNull()
                            pin.takeIf { createdAt != null && createdAt > cutoff }
                        }
                    }.awaitAll().filterNotNull()
                    conversation.copy(pinnedMessages = pins)
                }
            }
        }

    private fun observeConversation(convId: String, currentUserId: String): Flow<ChatConversation?> = callbackFlow {
        var emitted = false

        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching {
                    firestore.collection("conversations").document(convId)
                        .get(Source.CACHE)
                        .await()
                }.getOrNull()
            }
            if (emitted) return@launch
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    if (!emitted) close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    emitted = true
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        val conv = snapshot.toObject(ChatConversationDto::class.java)?.copy(id = snapshot.id)?.toDomain(currentUserId)
                        trySend(conv)
                    }
                } else if (snapshot?.metadata?.isFromCache == false) {
                    emitted = true
                    trySend(null)
                } else if (!emitted) {
                    emitted = true
                    trySend(fallbackConversation(convId, currentUserId))
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getRecentMessages(convId: String, currentUserId: String): Flow<List<ChatMessage>> =
        observeHistoryCutoff(convId, currentUserId)
            .combine(deletedMessages.observeHiddenAttachments(currentUserId, convId)) { cutoff, hidden -> cutoff to hidden }
            .flatMapLatest { (cutoff, hidden) ->
                observeRecentMessages(convId, currentUserId, cutoff, 30L + hidden.size)
                    .map { messages -> messages.mapNotNull { it.withoutHiddenAttachments(hidden) }.take(30) }
            }

    private fun observeRecentMessages(convId: String, currentUserId: String, cutoff: Long?, limit: Long): Flow<List<ChatMessage>> = callbackFlow {
        var emitted = false

        val query = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .afterDeletion(cutoff)
            .limit(limit)

        launch(kotlinx.coroutines.Dispatchers.Default) {
            val cached = withTimeoutOrNull(3000) {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
            val messages = cached?.documents?.mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }.orEmpty()
            if (!emitted) {
                emitted = true
                trySend(messages)
            }
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (!emitted) close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                emitted = true
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

    override fun observeMessagesByIds(convId: String, currentUserId: String, ids: Set<String>): Flow<Map<String, ChatMessage?>> =
        if (ids.isEmpty()) flowOf(emptyMap()) else combine(
            observeChatMessageUpdates(firestore, convId, currentUserId, ids),
            observeHistoryCutoff(convId, currentUserId),
            deletedMessages.observeHiddenAttachments(currentUserId, convId)
        ) { messages, cutoff, hidden ->
            messages.mapValues { (_, message) ->
                message?.takeIf { cutoff == null || it.createdAt > cutoff }?.withoutHiddenAttachments(hidden)
            }
        }

    override suspend fun getChatInfoMessages(convId: String, currentUserId: String, limit: Long): List<ChatMessage> {
        val cutoff = historyCutoff(convId, currentUserId)
        return firestore.collection("conversations").document(convId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .afterDeletion(cutoff)
            .limit(limit + deletedMessages.hiddenAttachments(currentUserId, convId).size)
            .get(Source.DEFAULT)
            .await()
            .documents.mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }
            .visibleAttachments(convId, currentUserId).take(limit.toInt())
    }

    override suspend fun getOlderMessages(convId: String, currentUserId: String, beforeTimestamp: Long, limit: Long): List<ChatMessage> {
        val cutoff = historyCutoff(convId, currentUserId)
        if (cutoff != null && beforeTimestamp <= cutoff) return emptyList()
        val query = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .whereLessThan("createdAt", beforeTimestamp)
            .afterDeletion(cutoff)
            .limit(limit + deletedMessages.hiddenAttachments(currentUserId, convId).size)

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
        }.visibleAttachments(convId, currentUserId).take(limit.toInt())
    }

    override suspend fun getNewerMessages(convId: String, currentUserId: String, afterTimestamp: Long, limit: Long): List<ChatMessage> {
        val query = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .whereGreaterThan("createdAt", maxOf(afterTimestamp, historyCutoff(convId, currentUserId) ?: Long.MIN_VALUE))
            .limit(limit + deletedMessages.hiddenAttachments(currentUserId, convId).size)

        val snapshot = try {
            query.get(Source.DEFAULT).await()
        } catch (e: FirebaseFirestoreException) {
            withTimeoutOrNull(3000) {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
        } ?: return emptyList()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
        }.visibleAttachments(convId, currentUserId).take(limit.toInt())
    }

    override suspend fun getThreadMessages(
        convId: String,
        currentUserId: String,
        threadRootId: String,
        limit: Long
    ): List<ChatMessage> {
        if (threadRootId.isBlank()) return emptyList()
        val messages = firestore.collection("conversations").document(convId).collection("messages")
        val cutoff = historyCutoff(convId, currentUserId)
        if (cutoff != null && (messages.document(threadRootId).get().await().getLong("createdAt") ?: 0L) <= cutoff) {
            return emptyList()
        }

        suspend fun fetch(field: String) = runCatching {
            messages.whereEqualTo(field, threadRootId).limit(limit).get(Source.DEFAULT).await()
        }.getOrElse {
            runCatching { messages.whereEqualTo(field, threadRootId).limit(limit).get(Source.CACHE).await() }
                .getOrNull()
        }?.documents.orEmpty()

        // replyTo.id preserves threads created before threadRootId was introduced.
        return (fetch("threadRootId") + fetch("replyTo.id"))
            .distinctBy { it.id }
            .filter { cutoff == null || (it.getLong("createdAt") ?: 0L) > cutoff }
            .mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }
            .sortedByDescending(ChatMessage::createdAt)
            .visibleAttachments(convId, currentUserId)
    }

    override suspend fun getMessageWindowById(
        convId: String,
        currentUserId: String,
        messageId: String,
        windowSize: Long
    ): List<ChatMessage> {
        if (messageId.isBlank() || windowSize <= 0L) return emptyList()
        val cutoff = historyCutoff(convId, currentUserId)
        val messages = firestore.collection("conversations").document(convId).collection("messages")
        val targetRef = messages.document(messageId)
        val target = runCatching { targetRef.get(Source.DEFAULT).await() }
            .getOrElse { runCatching { targetRef.get(Source.CACHE).await() }.getOrNull() }
            ?.takeIf { it.exists() }
            ?: return emptyList()
        val timestamp = target.getLong("createdAt") ?: return emptyList()
        if (cutoff != null && timestamp <= cutoff) return emptyList()
        val sideLimit = (windowSize / 2L).coerceAtLeast(1L)

        suspend fun fetch(query: Query) = runCatching { query.get(Source.DEFAULT).await() }
            .getOrElse { runCatching { query.get(Source.CACHE).await() }.getOrNull() }
            ?.documents.orEmpty()

        val olderIncludingTarget = fetch(
            messages.orderBy("createdAt", Query.Direction.DESCENDING)
                .whereLessThanOrEqualTo("createdAt", timestamp)
                .afterDeletion(cutoff)
                .limit(sideLimit + 1L)
        )
        val newer = fetch(
            messages.orderBy("createdAt", Query.Direction.ASCENDING)
                .whereGreaterThan("createdAt", timestamp)
                .limit(sideLimit)
        )

        return (olderIncludingTarget + newer + target)
            .distinctBy { it.id }
            .mapNotNull { doc ->
                doc.toObject(ChatMessageDto::class.java)?.copy(id = doc.id)?.toDomain(currentUserId)
            }
            .sortedByDescending { it.createdAt }
            .visibleAttachments(convId, currentUserId)
    }

    override suspend fun findOrCreateDirectConversation(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> = runCatching {
        require(currentUser.uid.isNotBlank() && otherUser.uid.isNotBlank()) { "Usuario no valido." }
        require(currentUser.uid != otherUser.uid) { "Selecciona otro usuario." }

        val conversations = firestore.collection("conversations")
        val participantOrders = listOf(
            listOf(currentUser.uid, otherUser.uid),
            listOf(otherUser.uid, currentUser.uid)
        ).distinct()
        for (participants in participantOrders) {
            val existing = conversations
                .whereEqualTo("participantIds", participants)
                .limit(5L)
                .get(Source.DEFAULT)
                .await()
                .documents
                .firstOrNull { document ->
                    val kind = document.getString("kind") ?: "dm"
                    kind == "dm" && document.getString("name") == null
                }
            if (existing != null) return@runCatching existing.id
        }

        val canonicalParticipants = listOf(currentUser.uid, otherUser.uid).sorted()
        val conversationId = deterministicDirectConversationId(canonicalParticipants)
        val ref = conversations.document(conversationId)
        firestore.runTransaction { transaction ->
            if (!transaction.get(ref).exists()) {
                val members = mapOf(
                    currentUser.uid to mapOf(
                        "displayName" to currentUser.displayName,
                        "photoURL" to currentUser.photoURL,
                        "phone" to currentUser.phone
                    ),
                    otherUser.uid to mapOf(
                        "displayName" to otherUser.displayName,
                        "photoURL" to otherUser.photoURL,
                        "phone" to otherUser.phone
                    )
                )
                val now = System.currentTimeMillis()
                transaction.set(
                    ref,
                    mapOf(
                        "participantIds" to canonicalParticipants,
                        "members" to members,
                        "unread" to canonicalParticipants.associateWith { 0 },
                        "lastMessage" to "",
                        "lastMessageAt" to 0L,
                        "createdAt" to now,
                        "kind" to "dm",
                        "directKey" to canonicalParticipants.joinToString("|")
                    )
                )
                canonicalParticipants.forEach { userId ->
                    val searchableProfile = if (userId == currentUser.uid) otherUser else currentUser
                    transaction.set(
                        firestore.collection(CONVERSATION_SEARCH_COLLECTION)
                            .document(searchIndexId(userId, conversationId)),
                        mapOf(
                            "userId" to userId,
                            "conversationId" to conversationId,
                            "prefixes" to ChatSearchNormalizer.prefixes(
                                listOf(
                                    searchableProfile.displayName,
                                    searchableProfile.phone,
                                    searchableProfile.email
                                )
                            ),
                            "updatedAt" to now
                        )
                    )
                }
            }
            conversationId
        }.await()
    }

    override suspend fun getOrCreateSavedMessagesConversation(
        currentUser: UserProfile
    ): Result<String> = runCatching {
        require(currentUser.uid.isNotBlank()) { "Usuario no valido." }

        val conversations = firestore.collection("conversations")
        val querySnapshot = conversations
            .whereArrayContains("participantIds", currentUser.uid)
            .limit(20L)
            .get(Source.DEFAULT)
            .await()

        val existing = querySnapshot.documents.firstOrNull { doc ->
            val participants = doc.get("participantIds") as? List<*> ?: emptyList<Any>()
            val isGroup = doc.getBoolean("isGroup") ?: false
            !isGroup && (participants.size == 1 || (participants.size == 2 && participants.all { it == currentUser.uid }))
        }
        if (existing != null) return@runCatching existing.id

        val canonicalParticipants = listOf(currentUser.uid)
        val conversationId = deterministicDirectConversationId(canonicalParticipants)
        val ref = conversations.document(conversationId)

        firestore.runTransaction { transaction ->
            if (!transaction.get(ref).exists()) {
                val members = mapOf(
                    currentUser.uid to mapOf(
                        "displayName" to currentUser.displayName,
                        "photoURL" to currentUser.photoURL,
                        "phone" to currentUser.phone
                    )
                )
                val now = System.currentTimeMillis()
                transaction.set(
                    ref,
                    mapOf(
                        "participantIds" to canonicalParticipants,
                        "members" to members,
                        "unread" to mapOf(currentUser.uid to 0),
                        "lastMessage" to "",
                        "lastMessageAt" to 0L,
                        "createdAt" to now,
                        "kind" to "dm",
                        "isGroup" to false,
                        "directKey" to currentUser.uid
                    )
                )
            }
            conversationId
        }.await()
    }

    override fun subscribeConversationStates(
        userId: String,
        conversationIds: Set<String>
    ): Flow<Map<String, ConversationUserState>> {
        val ids = conversationIds.filter(String::isNotBlank).distinct()
        if (userId.isBlank() || ids.isEmpty()) return flowOf(emptyMap())
        return callbackFlow {
            val lock = Any()
            val statesByChunk = mutableMapOf<Int, Map<String, ConversationUserState>>()
            val registrations = ids.chunked(FIRESTORE_IN_LIMIT).mapIndexed { index, chunk ->
                firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
                    .whereIn(FieldPath.documentId(), chunk)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val states = snapshot?.documents.orEmpty().associate { document ->
                            document.id to document.toConversationUserState()
                        }
                        synchronized(lock) {
                            statesByChunk[index] = states
                            trySend(statesByChunk.values.flatMap(Map<String, ConversationUserState>::entries)
                                .associate { it.toPair() })
                        }
                    }
            }
            awaitClose { registrations.forEach { it.remove() } }
        }
    }

    override fun getPinnedConversations(userId: String, limit: Long): Flow<List<ChatConversation>> = callbackFlow {
        val listener = firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
            .whereEqualTo("pinned", true)
            .orderBy("pinnedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    val states = snapshot?.documents.orEmpty().associate { document ->
                        document.id to document.toConversationUserState()
                    }
                    trySend(loadConversationsByIds(states.keys, userId).map { conversation ->
                        conversation.withUserState(states[conversation.id])
                    }.sortedByDescending(ChatConversation::pinnedAt))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getArchivedConversations(
        userId: String,
        beforeArchivedAt: Long?,
        limit: Long
    ): List<ChatConversation> {
        var query: Query = firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
            .whereEqualTo("archived", true)
            .orderBy("archivedAt", Query.Direction.DESCENDING)
            .limit(limit)
        if (beforeArchivedAt != null) query = query.whereLessThan("archivedAt", beforeArchivedAt)
        val states = query.get(Source.DEFAULT).await().documents.associate { document ->
            document.id to document.toConversationUserState()
        }
        return loadConversationsByIds(states.keys, userId)
            .map { conversation -> conversation.withUserState(states[conversation.id]) }
            .sortedByDescending(ChatConversation::archivedAt)
    }

    override suspend fun searchConversations(
        userId: String,
        normalizedPrefix: String,
        limit: Long
    ): List<ChatConversation> {
        if (normalizedPrefix.length < 2) return emptyList()
        val indexDocuments = firestore.collection(CONVERSATION_SEARCH_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereArrayContains("prefixes", normalizedPrefix.take(MAX_SEARCH_PREFIX_LENGTH))
            .limit(limit)
            .get(Source.DEFAULT)
            .await()
        val conversations = loadConversationsByIds(
            indexDocuments.documents.mapNotNull { it.getString("conversationId") },
            userId
        )
        val states = loadConversationStates(userId, conversations.map(ChatConversation::id))
        return conversations.map { it.withUserState(states[it.id]) }.filterNot(ChatConversation::isDeletedForMe)
    }

    override suspend fun ensureConversationSearchIndexes(
        userId: String,
        conversations: List<ChatConversation>
    ): Result<Unit> = runCatching {
        val candidates = conversations.distinctBy(ChatConversation::id).take(MAX_INDEX_BACKFILL_BATCH)
        if (candidates.isEmpty()) return@runCatching
        val refsByConversationId = candidates.associate { conversation ->
            conversation.id to firestore.collection(CONVERSATION_SEARCH_COLLECTION)
                .document(searchIndexId(userId, conversation.id))
        }
        val existingIds = refsByConversationId.values.map { it.id }.chunked(FIRESTORE_IN_LIMIT).flatMap { ids ->
            firestore.collection(CONVERSATION_SEARCH_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereIn(FieldPath.documentId(), ids)
                .get(Source.DEFAULT)
                .await()
                .documents.map { it.id }
        }.toSet()
        val missing = candidates.filter { refsByConversationId.getValue(it.id).id !in existingIds }
        if (missing.isNotEmpty()) {
            val batch = firestore.batch()
            missing.forEach { conversation ->
                batch.set(
                    refsByConversationId.getValue(conversation.id),
                    mapOf(
                        "userId" to userId,
                        "conversationId" to conversation.id,
                        "prefixes" to ChatSearchNormalizer.prefixes(
                            conversationSearchValues(conversation, userId)
                        ),
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            }
            batch.commit().await()
        }
    }

    override suspend fun setConversationPinned(
        userId: String,
        conversationId: String,
        pinned: Boolean
    ): Result<Unit> = runCatching {
        firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
            .document(conversationId)
            .set(
                mapOf(
                    "pinned" to pinned,
                    "pinnedAt" to if (pinned) System.currentTimeMillis() else 0L
                ),
                SetOptions.merge()
            ).await()
    }

    override suspend fun setConversationMuted(userId: String, conversationId: String, muted: Boolean): Result<Unit> =
        runCatching {
            chatStateDocument(conversationId, userId).set(mapOf("muted" to muted), SetOptions.merge()).await()
            Unit
        }

    override suspend fun leaveGroup(userId: String, conversationId: String): Result<Unit> = runCatching {
        val group = firestore.collection("conversations").document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(group)
            require(snapshot.getString("kind") == "group")
            require(userId in (snapshot.get("participantIds") as? List<*>).orEmpty())
            transaction.update(group,
                FieldPath.of("participantIds"), FieldValue.arrayRemove(userId),
                FieldPath.of("members", userId), FieldValue.delete(),
                FieldPath.of("unread", userId), FieldValue.delete()
            )
            transaction.delete(chatStateDocument(conversationId, userId))
        }.await()
        Unit
    }

    override suspend fun setConversationArchived(
        userId: String,
        conversationId: String,
        archived: Boolean
    ): Result<Unit> = runCatching {
        firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
            .document(conversationId)
            .set(
                mapOf(
                    "archived" to archived,
                    "archivedAt" to if (archived) System.currentTimeMillis() else 0L,
                    "pinned" to if (archived) false else FieldValue.delete(),
                    "pinnedAt" to if (archived) 0L else FieldValue.delete()
                ),
                SetOptions.merge()
            ).await()
    }

    override suspend fun deleteConversationForMe(userId: String, conversationId: String): Result<Unit> = runCatching {
        val chat = firestore.collection("conversations").document(conversationId)
        val userState = chatStateDocument(conversationId, userId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(chat)
            require(userId in (snapshot.get("participantIds") as? List<*>).orEmpty())
            val previousCutoff = transaction.get(userState).getLong("deletedThrough") ?: 0L
            val cutoff = maxOf(previousCutoff, snapshot.getLong("lastMessageAt") ?: 0L)
            transaction.set(userState, deletedChatState(cutoff), SetOptions.merge())
            transaction.update(chat, FieldPath.of("unread", userId), 0L)
        }.await()
        Unit
    }

    override suspend fun deleteConversationForEveryone(userId: String, conversationId: String): Result<Unit> = runCatching {
        val chat = firestore.collection("conversations").document(conversationId)
        val (cutoff, participants) = firestore.runTransaction { transaction ->
            val snapshot = transaction.get(chat)
            val participants = (snapshot.get("participantIds") as? List<*>).orEmpty().filterIsInstance<String>().distinct()
            require(userId in participants)
            val cutoff = maxOf(snapshot.getLong("lastMessageAt") ?: 0L, snapshot.getLong("sharedDeletedThrough") ?: 0L)
            val userState = chatStateDocument(conversationId, userId)
            val privateCutoff = transaction.get(userState).getLong("deletedThrough") ?: 0L
            transaction.update(chat, mapOf(
                "sharedDeletedThrough" to cutoff,
                "lastMessage" to "",
                "unread" to participants.associateWith { 0L },
                "pinnedMessages" to emptyList<Any>()
            ))
            transaction.set(userState, deletedChatState(maxOf(privateCutoff, cutoff)), SetOptions.merge())
            cutoff to participants
        }.await()

        // The committed cutoff hides the history while the documents are removed in retryable batches.
        val messages = chat.collection("messages").whereLessThanOrEqualTo("createdAt", cutoff).limit(50)
        while (true) {
            val page = messages.get(Source.SERVER).await().documents
            if (page.isEmpty()) break
            val indexRefs = page.flatMap { message ->
                participants.map { participant ->
                    firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                        .document(globalSearchIndexId(participant, conversationId, message.id))
                }
            }
            indexRefs.chunked(400).forEach { references ->
                val batch = firestore.batch()
                references.forEach { batch.delete(it) }
                batch.commit().await()
            }
            // The rules read each original and its post-transaction state to verify the removal record.
            page.chunked(8).forEach { chunk ->
                firestore.runTransaction { transaction ->
                    val current = chunk.map { transaction.get(it.reference) }
                    current.filter { it.exists() }.forEach { message ->
                        transaction.recordRemovedMedia(message, (message.get("attachments") as? List<*>).orEmpty())
                        transaction.delete(message.reference)
                    }
                }.await()
            }
        }
    }

    private fun deletedChatState(cutoff: Long) = mapOf(
        "deletedThrough" to cutoff,
        "pinned" to false,
        "pinnedAt" to 0L,
        "archived" to false,
        "archivedAt" to 0L
    )

    private fun chatStateDocument(convId: String, userId: String) =
        firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION).document(convId)

    private fun observeUserState(convId: String, userId: String): Flow<ConversationUserState?> =
        subscribeConversationStates(userId, setOf(convId)).map { it[convId] }.distinctUntilChanged()

    private fun observeHistoryCutoff(convId: String, userId: String): Flow<Long?> =
        combine(observeConversation(convId, userId), observeUserState(convId, userId)) { conversation, state ->
            conversation?.withUserState(state)?.historyCutoff
        }.distinctUntilChanged()

    private suspend fun historyCutoff(convId: String, userId: String): Long? = coroutineScope {
        val privateState = async { loadConversationStates(userId, listOf(convId))[convId]?.deletedThrough }
        val sharedState = firestore.collection("conversations").document(convId)
            .get(Source.DEFAULT).await().getLong("sharedDeletedThrough")
        listOfNotNull(privateState.await(), sharedState).maxOrNull()
    }

    private fun Query.afterDeletion(cutoff: Long?): Query =
        if (cutoff == null) this else whereGreaterThan("createdAt", cutoff)

    private fun deterministicDirectConversationId(participantIds: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(participantIds.joinToString("|").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "dm_$digest"
    }

    private fun searchIndexId(userId: String, conversationId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$userId|$conversationId".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "cs_$digest"
    }

    private fun globalSearchData(
        userId: String,
        conversationId: String,
        message: ChatMessage
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "conversationId" to conversationId,
        "messageId" to message.id,
        "senderId" to message.senderId,
        "createdAt" to message.createdAt,
        "searchTerms" to messageSearchTerms(message),
        "contentKinds" to messageContentKinds(message)
    )

    private suspend fun updatePostSendIndexes(
        conversationId: String,
        senderId: String,
        participants: List<String>,
        message: ChatMessage
    ) {
        participants.distinct().chunked(POST_SEND_PARTICIPANT_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { participantId ->
                batch.set(
                    firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                        .document(globalSearchIndexId(participantId, conversationId, message.id)),
                    globalSearchData(participantId, conversationId, message)
                )
                if (participantId != senderId) {
                    batch.set(
                        firestore.collection("users").document(participantId).collection(CHAT_STATES_COLLECTION)
                            .document(conversationId),
                        mapOf("archived" to false, "archivedAt" to 0L),
                        SetOptions.merge()
                    )
                }
            }
            batch.commit().await()
        }
    }

    private suspend fun loadConversationsByIds(
        conversationIds: Collection<String>,
        currentUserId: String
    ): List<ChatConversation> = conversationIds.distinct().chunked(FIRESTORE_IN_LIMIT).flatMap { chunk ->
        if (chunk.isEmpty()) return@flatMap emptyList()
        firestore.collection("conversations")
            .whereArrayContains("participantIds", currentUserId)
            .whereIn(FieldPath.documentId(), chunk)
            .get(Source.DEFAULT)
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(ChatConversationDto::class.java)?.copy(id = document.id)?.toDomain(currentUserId)
            }
    }

    private suspend fun loadConversationStates(
        userId: String,
        conversationIds: Collection<String>
    ): Map<String, ConversationUserState> = conversationIds.distinct().chunked(FIRESTORE_IN_LIMIT).flatMap { chunk ->
        if (chunk.isEmpty()) return@flatMap emptyList()
        firestore.collection("users").document(userId).collection(CHAT_STATES_COLLECTION)
            .whereIn(FieldPath.documentId(), chunk)
            .get(Source.DEFAULT)
            .await()
            .documents.map { document -> document.id to document.toConversationUserState() }
    }.toMap()

    private fun com.google.firebase.firestore.DocumentSnapshot.toConversationUserState() = ConversationUserState(
        conversationId = id,
        pinned = getBoolean("pinned") ?: false,
        pinnedAt = getLong("pinnedAt") ?: 0L,
        archived = getBoolean("archived") ?: false,
        archivedAt = getLong("archivedAt") ?: 0L,
        deletedThrough = getLong("deletedThrough"),
        muted = getBoolean("muted") == true
    )

    private fun ChatConversation.withUserState(state: ConversationUserState?): ChatConversation = copy(
        isPinned = state?.pinned == true,
        pinnedAt = state?.pinnedAt ?: 0L,
        isArchived = state?.archived == true,
        archivedAt = state?.archivedAt ?: 0L,
        deletedThrough = state?.deletedThrough,
        isMuted = state?.muted == true
    )

    private fun conversationSearchValues(conversation: ChatConversation, userId: String): List<String> = buildList {
        conversation.name?.let(::add)
        if (conversation.isGroup) {
            conversation.members.values.forEach { member ->
                add(member.displayName)
                add(member.phone)
            }
        } else {
            val otherId = conversation.participantIds.firstOrNull { it != userId } ?: userId
            conversation.members[otherId]?.let { member ->
                add(member.displayName)
                add(member.phone)
            }
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
        var participantsForPostSend = emptyList<String>()
        var sentAt = message.createdAt

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            val participantIds = snapshot.get("participantIds") as? List<String> ?: emptyList()
            participantsForPostSend = participantIds.drop(POST_SEND_PARTICIPANT_BATCH)
            val existingMessage = transaction.get(messageRef)
            sentAt = existingMessage.getLong("createdAt") ?: maxOf(
                message.createdAt,
                (snapshot.getLong("lastMessageAt") ?: 0L) + 1L,
                (snapshot.getLong("sharedDeletedThrough") ?: 0L) + 1L
            )
            if (existingMessage.exists()) return@runTransaction
            val rootRef = message.threadRootId?.takeIf(String::isNotBlank)
                ?.let { convRef.collection("messages").document(it) }
            val rootSnapshot = rootRef?.let(transaction::get)
            if (snapshot.exists()) {
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
                        "lastMessageAt" to sentAt,
                        "unread" to newUnread
                    )
                )
            }
            if (rootRef != null && rootSnapshot?.exists() == true) {
                transaction.update(rootRef, "replyCount", FieldValue.increment(1L))
            }

            val messageMap = mutableMapOf<String, Any>(
                "id" to message.id,
                "senderId" to message.senderId,
                "text" to message.text,
                "createdAt" to sentAt,
                "conversationId" to convId,
                "searchTerms" to messageSearchTerms(message),
                "contentKinds" to messageContentKinds(message)
            )
            if (message.edited) messageMap["edited"] = true
            message.editedAt?.let { messageMap["editedAt"] = it }
            message.replyTo?.let { messageMap["replyTo"] = it.toDto() }
            message.threadRootId?.let { messageMap["threadRootId"] = it }
            if (message.replyCount > 0) messageMap["replyCount"] = message.replyCount
            if (message.reactions.isNotEmpty()) messageMap["reactions"] = message.reactions
            if (message.attachments.isNotEmpty()) messageMap["attachments"] = message.attachments.map { it.toDto() }
            if (message.mentions.isNotEmpty()) messageMap["mentions"] = message.mentions
            message.forwardedFrom?.let { messageMap["forwardedFrom"] = it.toDto() }

            transaction.set(messageRef, messageMap)
            participantIds.take(POST_SEND_PARTICIPANT_BATCH).forEach { participantId ->
                transaction.set(firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                    .document(globalSearchIndexId(participantId, convId, message.id)),
                    globalSearchData(participantId, convId, message.copy(createdAt = sentAt)))
                if (participantId != currentUserId) transaction.set(
                    firestore.collection("users").document(participantId).collection(CHAT_STATES_COLLECTION).document(convId),
                    mapOf("archived" to false, "archivedAt" to 0L), SetOptions.merge())
            }
        }.await()
        runCatching {
            updatePostSendIndexes(
                conversationId = convId,
                senderId = currentUserId,
                participants = participantsForPostSend,
                message = message.copy(createdAt = sentAt)
            )
        }
        Unit
    }

    override suspend fun searchMessages(
        convId: String,
        currentUserId: String,
        normalizedPrefix: String,
        limit: Long
    ): List<ChatMessage> {
        if (normalizedPrefix.length < 2) return emptyList()
        return firestore.collection("conversations").document(convId).collection("messages")
            .whereArrayContains("searchTerms", normalizedPrefix.take(MAX_SEARCH_PREFIX_LENGTH))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .afterDeletion(historyCutoff(convId, currentUserId))
            .limit(limit)
            .get(Source.DEFAULT)
            .await()
            .documents.mapNotNull { document ->
                document.toObject(ChatMessageDto::class.java)?.copy(id = document.id)?.toDomain(currentUserId)
            }.mapNotNull { visibleSearchMessage(it, currentUserId, convId, normalizedPrefix) }
    }

    override suspend fun ensureMessageSearchIndexes(
        convId: String,
        messages: List<ChatMessage>
    ): Result<Unit> = runCatching {
        val participants = firestore.collection("conversations").document(convId)
            .get(Source.DEFAULT).await().get("participantIds") as? List<String> ?: emptyList()
        val maxMessages = (450 / (participants.size + 1).coerceAtLeast(1)).coerceAtLeast(1)
        val missing = messages.filterNot(ChatMessage::isSearchIndexed)
            .distinctBy(ChatMessage::id)
            .take(minOf(MAX_MESSAGE_INDEX_BACKFILL_BATCH, maxMessages))
        if (missing.isEmpty()) return@runCatching
        val batch = firestore.batch()
        missing.forEach { message ->
            batch.update(
                firestore.collection("conversations").document(convId).collection("messages").document(message.id),
                mapOf(
                    "conversationId" to convId,
                    "searchTerms" to messageSearchTerms(message),
                    "contentKinds" to messageContentKinds(message)
                )
            )
            participants.forEach { participantId ->
                batch.set(
                    firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                        .document(globalSearchIndexId(participantId, convId, message.id)),
                    globalSearchData(participantId, convId, message)
                )
            }
        }
        batch.commit().await()
    }

    override suspend fun searchGlobalMessageHits(
        userId: String,
        normalizedPrefix: String,
        limit: Long
    ): List<ChatSearchHit> {
        if (normalizedPrefix.length < 2) return emptyList()
        val query = firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereArrayContains("searchTerms", normalizedPrefix.take(MAX_SEARCH_PREFIX_LENGTH))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
        val results = mutableListOf<ChatSearchHit>()
        val chatsById = mutableMapOf<String, ChatConversation>()
        var page = query
        while (results.size < limit) {
            val indexes = page.get(Source.DEFAULT).await().documents
            val conversationIds = indexes.mapNotNull { it.getString("conversationId") }.distinct()
                .filterNot(chatsById::containsKey)
            val states = loadConversationStates(userId, conversationIds)
            val conversations = loadConversationsByIds(conversationIds, userId)
            conversations.forEach { chat -> chatsById[chat.id] = chat.withUserState(states[chat.id]) }
            val visible = coroutineScope {
                indexes.mapNotNull { index ->
                    val conversationId = index.getString("conversationId") ?: return@mapNotNull null
                    val messageId = index.getString("messageId") ?: return@mapNotNull null
                    val chat = chatsById[conversationId] ?: return@mapNotNull null
                    val cutoff = chat.historyCutoff
                    if (cutoff != null && (index.getLong("createdAt") ?: 0L) <= cutoff) return@mapNotNull null
                    async {
                        firestore.collection("conversations").document(conversationId)
                            .collection("messages").document(messageId)
                            .get(Source.DEFAULT).await()
                            .takeIf { it.exists() && (cutoff == null || (it.getLong("createdAt") ?: 0L) > cutoff) }
                            ?.toObject(ChatMessageDto::class.java)
                            ?.copy(id = messageId, conversationId = conversationId)
                            ?.toDomain(userId)
                            ?.let { visibleSearchMessage(it, userId, conversationId, normalizedPrefix) }
                            ?.let { ChatSearchHit(it, chat) }
                    }
                }.awaitAll().filterNotNull()
            }
            results += visible
            if (indexes.size < limit) break
            page = query.startAfter(indexes.last())
        }
        return results.take(limit.toInt())
    }

    override fun subscribeConversationReceipts(convId: String): Flow<Map<String, ConversationReceipt>> = callbackFlow {
        val listener = firestore.collection("conversations").document(convId).collection(RECEIPTS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().associate { document ->
                    document.id to ConversationReceipt(
                        userId = document.id,
                        lastDeliveredAt = document.getLong("lastDeliveredAt") ?: 0L,
                        lastDeliveredMessageId = document.getString("lastDeliveredMessageId"),
                        lastReadAt = document.getLong("lastReadAt") ?: 0L,
                        lastReadMessageId = document.getString("lastReadMessageId")
                    )
                })
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateDeliveredCursor(
        convId: String,
        userId: String,
        messageId: String,
        deliveredAt: Long
    ): Result<Unit> = updateReceiptCursor(convId, userId, messageId, deliveredAt, read = false)

    override suspend fun updateReadCursor(
        convId: String,
        userId: String,
        messageId: String,
        readAt: Long,
        clearUnread: Boolean
    ): Result<Unit> = updateReceiptCursor(convId, userId, messageId, readAt, read = true, clearUnread = clearUnread)

    private suspend fun updateReceiptCursor(
        convId: String,
        userId: String,
        messageId: String,
        timestamp: Long,
        read: Boolean,
        clearUnread: Boolean = false
    ): Result<Unit> = runCatching {
        val conversationRef = firestore.collection("conversations").document(convId)
        val receiptRef = conversationRef.collection(RECEIPTS_COLLECTION).document(userId)
        firestore.runTransaction { transaction ->
            val receipt = transaction.get(receiptRef)
            val field = if (read) "lastReadAt" else "lastDeliveredAt"
            val current = receipt.getLong(field) ?: 0L
            if (timestamp > current) {
                val data = mutableMapOf<String, Any>(
                    field to timestamp,
                    (if (read) "lastReadMessageId" else "lastDeliveredMessageId") to messageId,
                    "updatedAt" to System.currentTimeMillis()
                )
                if (read) {
                    val delivered = receipt.getLong("lastDeliveredAt") ?: 0L
                    if (timestamp > delivered) {
                        data["lastDeliveredAt"] = timestamp
                        data["lastDeliveredMessageId"] = messageId
                    }
                    if (clearUnread) {
                        transaction.update(conversationRef, "unread.$userId", 0)
                    }
                }
                transaction.set(receiptRef, data, SetOptions.merge())
            }
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
            val attachmentNames = (msgData?.get("attachments") as? List<Map<String, Any>>)
                .orEmpty().mapNotNull { it["name"] as? String }
            val updatedTerms = ChatSearchNormalizer.prefixes(listOf(clean) + attachmentNames)
            val updatedMessage = checkNotNull(msgSnap.toObject(ChatMessageDto::class.java))
                .copy(id = msgId).toDomain(msgSnap.getString("senderId").orEmpty()).copy(text = clean)

            tx.update(msgRef, mapOf(
                "text" to clean,
                "formatting" to FieldValue.delete(),
                "edited" to true,
                "editedAt" to System.currentTimeMillis(),
                "searchTerms" to updatedTerms
            ))
            val participants = convSnap.get("participantIds") as? List<String> ?: emptyList()
            participants.forEach { participantId ->
                tx.set(
                    firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                        .document(globalSearchIndexId(participantId, convId, msgId)),
                    globalSearchData(participantId, convId, updatedMessage) +
                        mapOf("updatedAt" to System.currentTimeMillis())
                )
            }

            val pinned = (convSnap.get("pinnedMessages") as? List<Map<String, Any>>).orEmpty()
            if (pinned.any { it["id"] == msgId }) tx.update(convRef, "pinnedMessages",
                pinned.map { if (it["id"] == msgId) it + ("text" to clean) else it })

            val lastMessageAt = (convSnap.data?.get("lastMessageAt") as? Number)?.toLong()
            val msgCreatedAt = (msgData?.get("createdAt") as? Number)?.toLong()
            if (lastMessageAt == msgCreatedAt) {
                tx.update(convRef, mapOf("lastMessage" to clean))
            }
        }.await()
    }

    override suspend fun deleteMessage(convId: String, msgId: String): Result<Unit> = runCatching {
        deleteChatMessage(firestore, convId, msgId)
    }

    private fun List<ChatMessage>.visibleAttachments(convId: String, userId: String): List<ChatMessage> {
        val hidden = deletedMessages.hiddenAttachments(userId, convId)
        return if (hidden.isEmpty()) this else mapNotNull { it.withoutHiddenAttachments(hidden) }
    }

    private fun ChatMessage.withoutHiddenAttachments(hidden: Set<String>): ChatMessage? {
        if (hidden.isEmpty() || attachments.isEmpty()) return this
        val visible = attachments.filterNot { it.referenceIn(id).storageKey() in hidden }
        return when {
            visible.size == attachments.size -> this
            visible.isEmpty() && !hasContentApartFromAttachments() -> null
            else -> copy(attachments = visible, isSearchIndexed = true)
        }
    }

    private fun ChatMessage.hasContentApartFromAttachments() =
        text.isNotBlank() || replyCount > 0 || reactions.isNotEmpty()

    private fun visibleSearchMessage(message: ChatMessage, userId: String, convId: String, query: String): ChatMessage? {
        val visible = message.withoutHiddenAttachments(deletedMessages.hiddenAttachments(userId, convId)) ?: return null
        return visible.takeIf { it === message || query.take(MAX_SEARCH_PREFIX_LENGTH) in messageSearchTerms(it) }
    }

    override suspend fun deleteAttachments(
        convId: String,
        userId: String,
        attachments: Set<ChatAttachmentRef>,
        forEveryone: Boolean
    ): Result<Unit> = runCatching {
        if (attachments.isEmpty()) return@runCatching
        if (!forEveryone) {
            deletedMessages.hideAttachments(userId, convId, attachments)
            return@runCatching
        }
        attachments.groupBy { it.messageId }.forEach { (messageId, selected) ->
            val keys = selected.mapTo(hashSetOf()) { it.attachmentKey }
            deleteChatMessage(firestore, convId, messageId) { tx, target, conversation ->
                val raw = (target.get("attachments") as? List<*>).orEmpty()
                val remaining = raw.filterNot { entry ->
                    val file = entry as? Map<*, *> ?: return@filterNot false
                    val url = file["url"] as? String
                    (url?.takeIf(String::isNotBlank) ?: file["publicId"] as? String) in keys
                }
                if (remaining.size == raw.size) return@deleteChatMessage true
                val original = checkNotNull(target.toObject(ChatMessageDto::class.java)).copy(id = messageId).toDomain(userId)
                if (remaining.isEmpty() && !original.hasContentApartFromAttachments()) return@deleteChatMessage false
                tx.recordRemovedMedia(target, raw.filterNot { it in remaining })
                val updated = original.copy(attachments = original.attachments.filterNot { it.referenceIn(messageId).attachmentKey in keys })
                tx.update(target.reference, mapOf(
                    "attachments" to remaining,
                    "searchTerms" to messageSearchTerms(updated),
                    "contentKinds" to messageContentKinds(updated)
                ))
                (conversation.get("participantIds") as? List<*>).orEmpty().filterIsInstance<String>().distinct().forEach { uid ->
                    tx.set(firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION).document(globalSearchIndexId(uid, convId, messageId)),
                        globalSearchData(uid, convId, updated))
                }
                if (conversation.getLong("lastMessageAt") == original.createdAt && original.text.isBlank()) {
                    val files = updated.attachments
                    val suffix = if (files.size > 1) " (+${files.size - 1})" else ""
                    val preview = files.firstOrNull()?.let { "📎 ${it.name}$suffix" }.orEmpty()
                    tx.update(conversation.reference, "lastMessage", preview)
                }
                true
            }
        }
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

    private companion object {
        const val CHAT_STATES_COLLECTION = "chatStates"
        const val CONVERSATION_SEARCH_COLLECTION = "conversationSearch"
        const val GLOBAL_CHAT_SEARCH_COLLECTION = "globalChatSearch"
        const val FIRESTORE_IN_LIMIT = 10
        const val MAX_SEARCH_PREFIX_LENGTH = 32
        const val MAX_INDEX_BACKFILL_BATCH = 50
        const val MAX_MESSAGE_INDEX_BACKFILL_BATCH = 100
        const val POST_SEND_PARTICIPANT_BATCH = 200
        const val RECEIPTS_COLLECTION = "receipts"
        val URL_PATTERN = "https?://\\S+".toRegex(RegexOption.IGNORE_CASE)
    }

    private fun messageSearchTerms(message: ChatMessage): List<String> = ChatSearchNormalizer.prefixes(
        listOf(message.text) + message.attachments.map { it.name }
    )

    private fun messageContentKinds(message: ChatMessage): List<String> = buildSet {
        if (message.text.isNotBlank()) add("message")
        message.attachments.forEach { attachment -> add(attachment.kind.name.lowercase()) }
        if (URL_PATTERN.containsMatchIn(message.text)) add("link")
    }.toList()
}
