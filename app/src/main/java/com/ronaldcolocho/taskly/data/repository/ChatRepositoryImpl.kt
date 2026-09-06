package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
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
import com.ronaldcolocho.taskly.domain.model.ConversationUserState
import com.ronaldcolocho.taskly.domain.model.ConversationReceipt
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
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

    override suspend fun getMessageWindowById(
        convId: String,
        currentUserId: String,
        messageId: String,
        windowSize: Long
    ): List<ChatMessage> {
        if (messageId.isBlank() || windowSize <= 0L) return emptyList()
        val messages = firestore.collection("conversations").document(convId).collection("messages")
        val targetRef = messages.document(messageId)
        val target = runCatching { targetRef.get(Source.DEFAULT).await() }
            .getOrElse { runCatching { targetRef.get(Source.CACHE).await() }.getOrNull() }
            ?.takeIf { it.exists() }
            ?: return emptyList()
        val timestamp = target.getLong("createdAt") ?: return emptyList()
        val sideLimit = (windowSize / 2L).coerceAtLeast(1L)

        suspend fun fetch(query: Query) = runCatching { query.get(Source.DEFAULT).await() }
            .getOrElse { runCatching { query.get(Source.CACHE).await() }.getOrNull() }
            ?.documents.orEmpty()

        val olderIncludingTarget = fetch(
            messages.orderBy("createdAt", Query.Direction.DESCENDING)
                .whereLessThanOrEqualTo("createdAt", timestamp)
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
        return conversations.map { it.withUserState(states[it.id]) }
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

    private fun globalSearchIndexId(userId: String, conversationId: String, messageId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$userId|$conversationId|$messageId".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "gcs_$digest"
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
        archivedAt = getLong("archivedAt") ?: 0L
    )

    private fun ChatConversation.withUserState(state: ConversationUserState?): ChatConversation = copy(
        isPinned = state?.pinned == true,
        pinnedAt = state?.pinnedAt ?: 0L,
        isArchived = state?.archived == true,
        archivedAt = state?.archivedAt ?: 0L
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

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            val participantIds = snapshot.get("participantIds") as? List<String> ?: emptyList()
            participantsForPostSend = participantIds
            if (transaction.get(messageRef).exists()) return@runTransaction
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
                        "lastMessageAt" to message.createdAt,
                        "unread" to newUnread
                    )
                )
            }

            val messageMap = mutableMapOf<String, Any>(
                "id" to message.id,
                "senderId" to message.senderId,
                "text" to message.text,
                "createdAt" to message.createdAt,
                "conversationId" to convId,
                "searchTerms" to messageSearchTerms(message),
                "contentKinds" to messageContentKinds(message)
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
        runCatching {
            updatePostSendIndexes(
                conversationId = convId,
                senderId = currentUserId,
                participants = participantsForPostSend,
                message = message
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
            .limit(limit)
            .get(Source.DEFAULT)
            .await()
            .documents.mapNotNull { document ->
                document.toObject(ChatMessageDto::class.java)?.copy(id = document.id)?.toDomain(currentUserId)
            }
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

    override suspend fun searchGlobalMessages(
        userId: String,
        normalizedPrefix: String,
        limit: Long
    ): List<ChatMessage> {
        if (normalizedPrefix.length < 2) return emptyList()
        val indexes = firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereArrayContains("searchTerms", normalizedPrefix.take(MAX_SEARCH_PREFIX_LENGTH))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get(Source.DEFAULT).await().documents
        return coroutineScope {
            indexes.mapNotNull { index ->
                val conversationId = index.getString("conversationId") ?: return@mapNotNull null
                val messageId = index.getString("messageId") ?: return@mapNotNull null
                async {
                    firestore.collection("conversations").document(conversationId)
                        .collection("messages").document(messageId)
                        .get(Source.DEFAULT).await()
                        .takeIf { it.exists() }
                        ?.toObject(ChatMessageDto::class.java)
                        ?.copy(id = messageId, conversationId = conversationId)
                        ?.toDomain(userId)
                }
            }.awaitAll().filterNotNull()
        }
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

            tx.update(msgRef, mapOf(
                "text" to clean,
                "edited" to true,
                "editedAt" to System.currentTimeMillis(),
                "searchTerms" to updatedTerms
            ))
            val participants = convSnap.get("participantIds") as? List<String> ?: emptyList()
            participants.forEach { participantId ->
                tx.set(
                    firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                        .document(globalSearchIndexId(participantId, convId, msgId)),
                    mapOf("searchTerms" to updatedTerms, "updatedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                )
            }

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
        val convRef = firestore.collection("conversations").document(convId)
        val participants = convRef.get(Source.DEFAULT).await().get("participantIds") as? List<String> ?: emptyList()
        val deleteBatch = firestore.batch().delete(msgRef)
        participants.forEach { participantId ->
            deleteBatch.delete(
                firestore.collection(GLOBAL_CHAT_SEARCH_COLLECTION)
                    .document(globalSearchIndexId(participantId, convId, msgId))
            )
        }
        deleteBatch.commit().await()

        val latest = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

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
