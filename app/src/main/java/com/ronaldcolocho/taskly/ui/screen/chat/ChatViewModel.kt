package com.ronaldcolocho.taskly.ui.screen.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ConversationReceipt
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.ForwardedFrom
import com.ronaldcolocho.taskly.domain.model.MessageStatus
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.model.ReplyInfo
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.usecase.chat.ClearTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.DeleteMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.EditMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetMessageWindowByIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetOlderMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.EnsureMessageSearchIndexesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.PinMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SaveMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SendMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SetTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribePresenceUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribeTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribeConversationReceiptsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UpdateDeliveredCursorUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UpdateReadCursorUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.ToggleReactionUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UnpinMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UploadAttachmentUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.repository.ISettingsRepository
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.domain.util.extractMentions
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import com.ronaldcolocho.taskly.domain.util.splitLinks
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.media.AutoDownloadAttachment
import com.ronaldcolocho.taskly.ui.state.ChatUiState
import com.ronaldcolocho.taskly.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import java.io.File
import javax.inject.Inject

data class AttachmentDraft(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val kind: AttachmentKind,
    val status: DraftStatus,
    val progress: Int,
    val previewUri: Uri?,
    val attach: ChatAttachment? = null,
    val error: String? = null
)

enum class DraftStatus { READY, UPLOADING, DONE, ERROR }

internal data class ChatEphemeralState(
    val peerPresence: Presence?,
    val typingMap: Map<String, Long>
)

data class MessageNavigationRequest(
    val messageId: String,
    val requestId: Long
)

data class MessageSearchUiState(
    val query: String = "",
    val results: List<ChatMessage> = emptyList(),
    val currentIndex: Int = 0,
    val isSearching: Boolean = false,
    val error: String? = null
)

private const val MAX_ATTACHMENTS_PER_MESSAGE = 30
private const val TAG = "ChatViewModel"

sealed interface PlaylistAddState {
    data object Idle : PlaylistAddState
    data object Saving : PlaylistAddState
    data class Error(val message: String) : PlaylistAddState
    data class Success(val savedTracks: Int) : PlaylistAddState
}

data class VoiceNoteSendState(
    val isUploading: Boolean = false,
    val progress: Int = 0
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getConversationUseCase: GetConversationUseCase,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase,
    private val getRecentMessagesUseCase: GetRecentMessagesUseCase,
    private val getOlderMessagesUseCase: GetOlderMessagesUseCase,
    private val getMessageWindowByIdUseCase: GetMessageWindowByIdUseCase,
    private val searchMessagesUseCase: SearchMessagesUseCase,
    private val ensureMessageSearchIndexesUseCase: EnsureMessageSearchIndexesUseCase,
    private val subscribeConversationReceiptsUseCase: SubscribeConversationReceiptsUseCase,
    private val updateDeliveredCursorUseCase: UpdateDeliveredCursorUseCase,
    private val updateReadCursorUseCase: UpdateReadCursorUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val uploadAttachmentUseCase: UploadAttachmentUseCase,
    private val toggleReactionUseCase: ToggleReactionUseCase,
    private val editMessageUseCase: EditMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val pinMessageUseCase: PinMessageUseCase,
    private val unpinMessageUseCase: UnpinMessageUseCase,
    private val subscribeTypingUseCase: SubscribeTypingUseCase,
    private val setTypingUseCase: SetTypingUseCase,
    private val clearTypingUseCase: ClearTypingUseCase,
    private val subscribePresenceUseCase: SubscribePresenceUseCase,
    private val saveMessageUseCase: SaveMessageUseCase,
    private val mediaDownloadManager: MediaDownloadManager,
    private val settingsRepository: ISettingsRepository,
    private val musicRepository: IMusicRepository,
    private val getDeletedMessageIdsUseCase: com.ronaldcolocho.taskly.domain.usecase.GetDeletedMessageIdsUseCase,
    private val deleteMessageForMeUseCase: com.ronaldcolocho.taskly.domain.usecase.DeleteMessageForMeUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val uid: String? = getCurrentUserIdUseCase()

    private val _historyMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _localPendingMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    private val _editing = MutableStateFlow<ChatMessage?>(null)
    private val _sendError = MutableStateFlow<String?>(null)
    private val _messageNavigation = MutableStateFlow<MessageNavigationRequest?>(null)
    private val _messageSearch = MutableStateFlow(MessageSearchUiState())
    private val _receipts = MutableStateFlow<Map<String, ConversationReceipt>>(emptyMap())
    private val _unreadBoundaryMessageId = MutableStateFlow<String?>(null)
    private val _playlistAddState = MutableStateFlow<PlaylistAddState>(PlaylistAddState.Idle)
    private val _voiceNoteSendState = MutableStateFlow(VoiceNoteSendState())
    private val _typingMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _peerPresence = MutableStateFlow<Presence?>(null)
    private val _otherParticipant = MutableStateFlow<UserProfile?>(null)
    private val _isFetchingOlder = MutableStateFlow(false)
    private val _drafts = MutableStateFlow<List<AttachmentDraft>>(emptyList())
    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    private var hasMoreHistory = true
    private var lastTypingSent = 0L
    private var lastDeliveredAtSent = 0L
    private var lastReadAtSent = 0L
    private var readCursorJob: Job? = null
    private var messageSearchJob: Job? = null
    private val indexedMessageIds = mutableSetOf<String>()
    private val voiceRetryFiles = mutableMapOf<String, Pair<File, Int>>()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val drafts: StateFlow<List<AttachmentDraft>> = _drafts.asStateFlow()
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()
    val playlistAddState: StateFlow<PlaylistAddState> = _playlistAddState.asStateFlow()
    val messageNavigation: StateFlow<MessageNavigationRequest?> = _messageNavigation.asStateFlow()
    val messageSearch: StateFlow<MessageSearchUiState> = _messageSearch.asStateFlow()
    val voiceNoteSendState: StateFlow<VoiceNoteSendState> = _voiceNoteSendState.asStateFlow()
    val automaticDownloadsEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.automaticDownloadsEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val musicPlaylists: StateFlow<List<MusicPlaylist>> = uid?.let { musicRepository.observePlaylists(it) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    /** Estado de alta frecuencia (typing/presencia) separado de la lista de mensajes. */
    internal val ephemeralState: StateFlow<ChatEphemeralState> = combine(
        _peerPresence,
        _typingMap
    ) { presence, typing ->
        ChatEphemeralState(presence, typing)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatEphemeralState(null, emptyMap()))

    init {
        loadData()
        savedStateHandle.get<String>("messageId")?.takeIf(String::isNotBlank)?.let { messageId ->
            viewModelScope.launch {
                delay(100)
                navigateToMessage(messageId)
            }
        }
    }

    private fun loadData() {
        // Red de seguridad: si no hay uid, emitir Error inmediatamente
        val id = uid ?: run {
            _uiState.value = ChatUiState.Error("No hay sesión activa")
            return
        }

        // Estado inicial inmediato (Local-First): permite que la UI no muestre "Loading"
        // y navegue de manera inmediata.
        _uiState.value = ChatUiState.Success(
            conversation = offlineFallbackConversation(id),
            currentUserId = id,
            messages = emptyList()
        )

        viewModelScope.launch {
            getProfileUseCase(id).collect { _myProfile.value = it }
        }
        viewModelScope.launch {
            // Marcar como leído no debe bloquear ni colgar si no hay red.
            // La lectura se confirma cuando un mensaje entrante llega a estar visible.
        }
        viewModelScope.launch {
            getRecentConversationsUseCase(id, FORWARD_CONVERSATION_LIMIT).collect { _conversations.value = it }
        }

        val conversationFlow = getConversationUseCase(convId, id)
            .filterNotNull()
            .onStart { emit(offlineFallbackConversation(id)) }
            // Perfil, presencia y estado de chat consumen la misma conversación.
            // Compartir este Flow evita registrar el mismo listener de Firestore tres veces.
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

        // Perfil + presencia del peer: suscribir una sola vez
        viewModelScope.launch {
            val otherId = conversationFlow.first().participantIds.firstOrNull { it != id } ?: return@launch
            getProfileUseCase(otherId).collect { _otherParticipant.value = it }
        }
        viewModelScope.launch {
            val otherId = conversationFlow.first().participantIds.firstOrNull { it != id } ?: return@launch
            subscribePresenceUseCase(otherId).distinctUntilChanged().collect { _peerPresence.value = it }
        }
        viewModelScope.launch {
            subscribeTypingUseCase(convId).distinctUntilChanged().collect { _typingMap.value = it }
        }
        viewModelScope.launch {
            subscribeConversationReceiptsUseCase(convId)
                .catch { emit(emptyMap()) }
                .collect { receipts ->
                    _receipts.value = receipts
                    lastReadAtSent = maxOf(lastReadAtSent, receipts[id]?.lastReadAt ?: 0L)
                    lastDeliveredAtSent = maxOf(lastDeliveredAtSent, receipts[id]?.lastDeliveredAt ?: 0L)
                }
        }

        val recentMessagesFlow = getRecentMessagesUseCase(convId, id)
            .onStart { emit(emptyList()) }
            .onEach { messages ->
                val newestIncoming = messages.filter { it.senderId != id }.maxByOrNull(ChatMessage::createdAt)
                if (newestIncoming != null && newestIncoming.createdAt > lastDeliveredAtSent) {
                    lastDeliveredAtSent = newestIncoming.createdAt
                    viewModelScope.launch {
                        updateDeliveredCursorUseCase(convId, id, newestIncoming.id, newestIncoming.createdAt)
                    }
                }
            }

        // Solo mensajes: se reordena/reconstruye únicamente cuando cambian los mensajes
        val messagesFlow = combine(
            conversationFlow,
            recentMessagesFlow,
            _historyMessages,
            _localPendingMessages,
            getDeletedMessageIdsUseCase()
        ) { conv, recent, history, pending, deletedIds ->
            conv to mergeVisibleMessages(recent, history, pending, deletedIds)
        }.combine(_receipts) { (conversation, messages), receipts ->
            conversation to applyReceiptStatuses(conversation, messages, receipts, id)
        }.onEach { (conversation, messages) ->
            if (_unreadBoundaryMessageId.value == null && conversation.unreadCount > 0) {
                val ownReadAt = _receipts.value[id]?.lastReadAt ?: 0L
                val unread = if (ownReadAt > 0L) {
                    messages.filter { it.senderId != id && it.createdAt > ownReadAt }
                } else {
                    messages.filter { it.senderId != id }.take(conversation.unreadCount)
                }
                _unreadBoundaryMessageId.value = unread.minByOrNull(ChatMessage::createdAt)?.id
            }
            val missingIndexes = messages.filter {
                !it.isSearchIndexed && it.id !in indexedMessageIds &&
                    it.status != MessageStatus.SENDING && it.status != MessageStatus.FAILED
            }
            if (missingIndexes.isNotEmpty()) {
                indexedMessageIds += missingIndexes.map(ChatMessage::id)
                viewModelScope.launch {
                    ensureMessageSearchIndexesUseCase(convId, missingIndexes).onFailure {
                        indexedMessageIds.removeAll(missingIndexes.map(ChatMessage::id).toSet())
                    }
                }
            }
        }.flowOn(Dispatchers.Default)

        // Estado efímero (typing/presencia) se expone aparte; solo mensajes/conversación
        // y campos de baja frecuencia recomponen el estado principal de la pantalla.
        combine(
            messagesFlow,
            _isFetchingOlder,
            _otherParticipant,
            _replyTo,
            _editing,
            _sendError,
            _unreadBoundaryMessageId
        ) { args ->
            val (conv, messages) = args[0] as Pair<ChatConversation, List<ChatMessage>>
            ChatUiState.Success(
                conversation = conv,
                currentUserId = id,
                messages = messages,
                isFetchingOlder = args[1] as Boolean,
                otherParticipant = args[2] as? UserProfile,
                replyTo = args[3] as? ChatMessage,
                editing = args[4] as? ChatMessage,
                sendError = args[5] as? String,
                unreadBoundaryMessageId = args[6] as? String
            )
        }
        .flowOn(Dispatchers.Default)
        .catch { e -> _uiState.value = ChatUiState.Error(e.message ?: "Error al cargar el chat") }
        .distinctUntilChanged()
        .onEach { _uiState.value = it }
        .launchIn(viewModelScope)

        // Red de seguridad anti-spinner: si el flujo de datos no emitió en un plazo
        // razonable (sin red / caché lenta), mostrar el chat con lo disponible en lugar
        // de quedarse en Loading indefinidamente.
        viewModelScope.launch {
            delay(8000)
            if (_uiState.value is ChatUiState.Loading) {
                val deletedIds = getDeletedMessageIdsUseCase().first()
                val available = (_historyMessages.value + _localPendingMessages.value)
                    .filter { it.id !in deletedIds }
                    .sortedByDescending { it.createdAt }
                _uiState.value = ChatUiState.Success(
                    conversation = offlineFallbackConversation(id),
                    currentUserId = id,
                    messages = available
                )
            }
        }
    }

    private fun offlineFallbackConversation(currentUserId: String): ChatConversation = ChatConversation(
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

    /** Both remote sources are descending by createdAt; avoid sorting the full loaded history. */
    private fun mergeVisibleMessages(
        recent: List<ChatMessage>,
        history: List<ChatMessage>,
        pending: List<ChatMessage>,
        deletedIds: Set<String>
    ): List<ChatMessage> {
        val remoteIds = HashSet<String>(recent.size + history.size)
        val remote = ArrayList<ChatMessage>(recent.size + history.size)
        recent.forEach { message ->
            if (message.id !in deletedIds && remoteIds.add(message.id)) remote += message
        }
        history.forEach { message ->
            if (message.id !in deletedIds && remoteIds.add(message.id)) remote += message
        }
        val local = pending.asReversed().filter { it.id !in deletedIds && it.id !in remoteIds }
        if (local.isEmpty()) return remote

        val merged = ArrayList<ChatMessage>(remote.size + local.size)
        var remoteIndex = 0
        var localIndex = 0
        while (remoteIndex < remote.size || localIndex < local.size) {
            val takeLocal = remoteIndex == remote.size ||
                (localIndex < local.size && local[localIndex].createdAt >= remote[remoteIndex].createdAt)
            if (takeLocal) merged += local[localIndex++] else merged += remote[remoteIndex++]
        }
        return merged
    }

    private fun applyReceiptStatuses(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        receipts: Map<String, ConversationReceipt>,
        currentUserId: String
    ): List<ChatMessage> {
        val recipients = conversation.participantIds.filter { it != currentUserId }.distinct()
        return messages.map { message ->
            if (message.senderId != currentUserId ||
                message.status == MessageStatus.SENDING ||
                message.status == MessageStatus.FAILED
            ) return@map message
            val status = when {
                recipients.isEmpty() -> MessageStatus.READ
                recipients.isNotEmpty() && recipients.all { (receipts[it]?.lastReadAt ?: 0L) >= message.createdAt } -> MessageStatus.READ
                recipients.isNotEmpty() && recipients.all { (receipts[it]?.lastDeliveredAt ?: 0L) >= message.createdAt } -> MessageStatus.DELIVERED
                else -> MessageStatus.SENT
            }
            message.copy(status = status)
        }
    }

    fun onMessageSearchQueryChange(query: String) {
        _messageSearch.value = MessageSearchUiState(query = query)
        messageSearchJob?.cancel()
        val normalized = ChatSearchNormalizer.normalize(query)
        if (normalized.length < 2) return
        messageSearchJob = viewModelScope.launch {
            delay(MESSAGE_SEARCH_DEBOUNCE_MS)
            _messageSearch.value = _messageSearch.value.copy(isSearching = true)
            val loaded = (_uiState.value as? ChatUiState.Success)?.messages.orEmpty().filter { message ->
                ChatSearchNormalizer.matches(message.text, normalized) ||
                    message.attachments.any { ChatSearchNormalizer.matches(it.name, normalized) }
            }
            val remote = runCatching {
                searchMessagesUseCase(convId, uid.orEmpty(), normalized, MESSAGE_SEARCH_LIMIT)
            }.getOrElse { error ->
                _messageSearch.value = _messageSearch.value.copy(error = error.message)
                emptyList()
            }
            val results = (loaded + remote).associateBy(ChatMessage::id).values.sortedByDescending(ChatMessage::createdAt)
            _messageSearch.value = _messageSearch.value.copy(
                results = results,
                currentIndex = 0,
                isSearching = false
            )
            results.firstOrNull()?.let { navigateToMessage(it.id) }
        }
    }

    fun moveMessageSearchResult(delta: Int) {
        val state = _messageSearch.value
        if (state.results.isEmpty()) return
        val next = (state.currentIndex + delta).mod(state.results.size)
        _messageSearch.value = state.copy(currentIndex = next)
        navigateToMessage(state.results[next].id)
    }

    fun clearMessageSearch() {
        messageSearchJob?.cancel()
        _messageSearch.value = MessageSearchUiState()
    }

    fun jumpToUnreadBoundary() {
        _unreadBoundaryMessageId.value?.let(::navigateToMessage)
    }

    fun markVisibleMessages(messageIds: Set<String>) {
        val currentUserId = uid ?: return
        val newestVisibleIncoming = (_uiState.value as? ChatUiState.Success)?.messages.orEmpty()
            .filter { it.id in messageIds && it.senderId != currentUserId }
            .maxByOrNull(ChatMessage::createdAt) ?: return
        val newestLoadedIncoming = (_uiState.value as? ChatUiState.Success)?.messages.orEmpty()
            .filter { it.senderId != currentUserId }
            .maxByOrNull(ChatMessage::createdAt)
        if (newestVisibleIncoming.createdAt <= lastReadAtSent) return
        readCursorJob?.cancel()
        readCursorJob = viewModelScope.launch {
            delay(READ_CURSOR_DEBOUNCE_MS)
            if (newestVisibleIncoming.createdAt <= lastReadAtSent) return@launch
            updateReadCursorUseCase(
                convId,
                currentUserId,
                newestVisibleIncoming.id,
                newestVisibleIncoming.createdAt,
                clearUnread = newestLoadedIncoming?.id == newestVisibleIncoming.id
            ).onSuccess { lastReadAtSent = newestVisibleIncoming.createdAt }
        }
    }

    fun loadMore() {
        val id = uid ?: return
        if (_isFetchingOlder.value || !hasMoreHistory) return
        viewModelScope.launch {
            _isFetchingOlder.value = true
            val currentState = _uiState.value
            val currentMessages = if (currentState is ChatUiState.Success) currentState.messages else emptyList()
            val oldestMessage = currentMessages.lastOrNull {
                it.status != MessageStatus.SENDING && it.status != MessageStatus.FAILED
            }
            if (oldestMessage != null) {
                val older = runCatching {
                    getOlderMessagesUseCase(convId, id, oldestMessage.createdAt, 30L)
                }.getOrDefault(emptyList())
                if (older.isEmpty()) hasMoreHistory = false else _historyMessages.value += older
            }
            _isFetchingOlder.value = false
        }
    }

    fun navigateToMessage(messageId: String) {
        val currentUserId = uid ?: return
        if (messageId.isBlank()) return
        viewModelScope.launch {
            val currentMessages = (_uiState.value as? ChatUiState.Success)?.messages.orEmpty()
            if (currentMessages.none { it.id == messageId }) {
                val window = runCatching {
                    getMessageWindowByIdUseCase(
                        convId = convId,
                        currentUserId = currentUserId,
                        messageId = messageId
                    )
                }.getOrElse { error ->
                    _sendError.value = error.message ?: "No se pudo cargar el mensaje."
                    emptyList()
                }
                if (window.none { it.id == messageId }) {
                    _sendError.value = "El mensaje ya no esta disponible."
                    return@launch
                }
                _historyMessages.update { history ->
                    (history + window)
                        .associateBy { it.id }
                        .values
                        .sortedByDescending { it.createdAt }
                }
            }
            _messageNavigation.value = MessageNavigationRequest(
                messageId = messageId,
                requestId = System.nanoTime()
            )
        }
    }

    fun consumeMessageNavigation(requestId: Long) {
        if (_messageNavigation.value?.requestId == requestId) {
            _messageNavigation.value = null
        }
    }

    // ---- Escritura ----

    fun sendText(text: String) {
        val id = uid ?: return
        clearTyping()
        val clean = text.trim()
        val state = _uiState.value as? ChatUiState.Success ?: return
        val mentions = if (state.conversation.isGroup) extractMentions(clean, state.conversation.members) else emptyList()
        val reply = _replyTo.value?.let { ReplyInfo(it.id, it.text, it.senderId) }
        _replyTo.value = null

        val newMessage = ChatMessage(
            id = generateFirestoreId(),
            senderId = id,
            text = clean,
            status = MessageStatus.SENDING,
            createdAt = System.currentTimeMillis(),
            replyTo = reply,
            mentions = mentions
        )
        _localPendingMessages.value += newMessage
        executeSend(newMessage)
    }

    fun sendWithDrafts(text: String) {
        val id = uid ?: return
        clearTyping()
        val clean = text.trim()
        val state = _uiState.value as? ChatUiState.Success ?: return
        val mentions = if (state.conversation.isGroup) extractMentions(clean, state.conversation.members) else emptyList()
        val reply = _replyTo.value?.let { ReplyInfo(it.id, it.text, it.senderId) }
        _replyTo.value = null

        val newMessage = ChatMessage(
            id = generateFirestoreId(),
            senderId = id,
            text = clean,
            status = MessageStatus.SENDING,
            createdAt = System.currentTimeMillis(),
            replyTo = reply,
            mentions = mentions
        )
        _localPendingMessages.value += newMessage

        viewModelScope.launch {
            val attachments = mutableListOf<ChatAttachment>()
            var failed = false

            _drafts.value.filter { it.status == DraftStatus.DONE && it.attach != null }
                .forEach { attachments += it.attach!! }

            val toUpload = _drafts.value.filter { it.status != DraftStatus.DONE }
            for (d in toUpload) {
                if (d.error != null) {
                    failed = true
                    continue
                }
                _drafts.update { list ->
                    list.map { if (it.id == d.id) it.copy(status = DraftStatus.UPLOADING, progress = 0) else it }
                }
                val file = FileUtil.getFileFromUri(context, d.uri)
                val mime = context.contentResolver.getType(d.uri) ?: "application/octet-stream"
                val result = if (file != null) {
                    uploadAttachmentUseCase(file, mime, id, onProgress = { p ->
                        _drafts.update { list ->
                            list.map { if (it.id == d.id) it.copy(progress = p) else it }
                        }
                    })
                } else {
                    Result.failure(Exception("No se pudo leer el archivo"))
                }
                result.onSuccess { att ->
                    file?.let {
                        mediaDownloadManager.registerLocalFile(
                            mediaId = att.publicId,
                            sourceFile = it,
                            kind = att.kind.toMediaKind()
                        )
                    }
                    attachments += att
                    _drafts.update { list ->
                        list.map { if (it.id == d.id) it.copy(status = DraftStatus.DONE, progress = 100, attach = att) else it }
                    }
                }.onFailure { uploadError ->
                    failed = true
                    _drafts.update { list ->
                        list.map { if (it.id == d.id) it.copy(status = DraftStatus.ERROR, error = uploadError.message) else it }
                    }
                }.also {
                    file?.delete()
                }
            }

            if (failed) {
                _localPendingMessages.update { list ->
                    list.map { if (it.id == newMessage.id) it.copy(status = MessageStatus.FAILED) else it }
                }
                _sendError.value = "Algunos archivos no se subieron. Reintenta o elimínalos."
                return@launch
            }

            val final = newMessage.copy(attachments = attachments)
            _localPendingMessages.update { list ->
                list.map { if (it.id == final.id) final else it }
            }
            _drafts.value = emptyList()
            executeSend(final)
        }
    }

    fun addDrafts(uris: List<Uri>) {
        val current = _drafts.value
        if (current.size >= MAX_ATTACHMENTS_PER_MESSAGE) return
        val toAdd = uris.take(MAX_ATTACHMENTS_PER_MESSAGE - current.size)
        viewModelScope.launch {
            // Queries de contentResolver (binder/IO) fuera del hilo principal.
            val drafts = withContext(Dispatchers.IO) {
                toAdd.map { uri ->
                    val name = FileUtil.getDisplayName(context, uri) ?: "archivo"
                    val size = FileUtil.getSize(context, uri)
                    val mime = context.contentResolver.getType(uri)
                    val kind = FileUtil.classify(mime, name)
                    val tooBig = FileUtil.isTooBig(size)
                    AttachmentDraft(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        fileName = name,
                        kind = kind,
                        status = if (tooBig) DraftStatus.ERROR else DraftStatus.READY,
                        progress = 0,
                        previewUri = if (kind == AttachmentKind.IMAGE) uri else null,
                        error = if (tooBig) "Excede 20 MB" else null
                    )
                }
            }
            _drafts.value += drafts
        }
    }

    fun removeDraft(id: String) {
        _drafts.value = _drafts.value.filterNot { it.id == id }
    }

    fun onTextChanged(text: String) {
        val id = uid ?: return
        if (text.isBlank() || _editing.value != null) return
        val t = System.currentTimeMillis()
        if (t - lastTypingSent < 2000) return
        lastTypingSent = t
        setTypingUseCase(convId, id)
    }

    fun clearTyping() {
        val id = uid ?: return
        clearTypingUseCase(convId, id)
    }

    private fun executeSend(message: ChatMessage) {
        val id = uid ?: return
        viewModelScope.launch {
            sendMessageUseCase(convId, id, message)
                .onSuccess {
                    _localPendingMessages.value = _localPendingMessages.value.filter { it.id != message.id }
                }
                .onFailure { e ->
                    val failed = message.copy(status = MessageStatus.FAILED)
                    _localPendingMessages.value = _localPendingMessages.value.map {
                        if (it.id == failed.id) failed else it
                    }
                    _sendError.value = e.message ?: "No se pudo enviar el mensaje"
                }
        }
    }

    fun retryMessage(message: ChatMessage) {
        val retrying = message.copy(status = MessageStatus.SENDING)
        _localPendingMessages.value = _localPendingMessages.value.map {
            if (it.id == message.id) retrying else it
        }
        val voiceRetry = voiceRetryFiles[message.id]
        if (voiceRetry != null) uploadVoiceNote(retrying, voiceRetry.first, voiceRetry.second)
        else executeSend(retrying)
    }

    fun sendVoiceNote(file: File, durationSeconds: Int) {
        val currentUserId = uid ?: run { file.delete(); return }
        val message = ChatMessage(
            id = generateFirestoreId(),
            senderId = currentUserId,
            text = "",
            status = MessageStatus.SENDING,
            createdAt = System.currentTimeMillis()
        )
        voiceRetryFiles[message.id] = file to durationSeconds
        _localPendingMessages.value += message
        uploadVoiceNote(message, file, durationSeconds)
    }

    private fun uploadVoiceNote(message: ChatMessage, file: File, durationSeconds: Int) {
        val currentUserId = uid ?: return
        viewModelScope.launch {
            _voiceNoteSendState.value = VoiceNoteSendState(isUploading = true)
            uploadAttachmentUseCase(file, "audio/mp4", currentUserId) { progress ->
                _voiceNoteSendState.value = VoiceNoteSendState(isUploading = true, progress = progress)
            }.onSuccess { uploaded ->
                val attachment = uploaded.copy(duration = durationSeconds)
                mediaDownloadManager.registerLocalFile(
                    mediaId = attachment.publicId,
                    sourceFile = file,
                    kind = MediaKind.AUDIO
                )
                val ready = message.copy(attachments = listOf(attachment), status = MessageStatus.SENDING)
                _localPendingMessages.update { messages -> messages.map { if (it.id == ready.id) ready else it } }
                voiceRetryFiles.remove(message.id)
                file.delete()
                _voiceNoteSendState.value = VoiceNoteSendState()
                executeSend(ready)
            }.onFailure { error ->
                _localPendingMessages.update { messages ->
                    messages.map { if (it.id == message.id) it.copy(status = MessageStatus.FAILED) else it }
                }
                _voiceNoteSendState.value = VoiceNoteSendState()
                _sendError.value = error.message ?: "No se pudo subir la nota de voz. Toca para reintentar."
            }
        }
    }

    // ---- Acciones del mensaje ----

    fun setReplyTo(message: ChatMessage?) {
        _editing.value = null
        _replyTo.value = message
    }

    fun clearReplyTo() {
        _replyTo.value = null
    }

    fun startEditing(message: ChatMessage) {
        _replyTo.value = null
        _editing.value = message
    }

    fun cancelEditing() {
        _editing.value = null
    }

    fun saveEdit(text: String) {
        val editing = _editing.value ?: return
        val id = uid ?: return
        viewModelScope.launch {
            editMessageUseCase(convId, editing.id, text)
                .onSuccess { _editing.value = null }
                .onFailure { _sendError.value = it.message ?: "No se pudo editar el mensaje" }
        }
    }

    fun reactToMessage(message: ChatMessage, emoji: String) {
        val id = uid ?: return
        viewModelScope.launch {
            toggleReactionUseCase(convId, message.id, id, emoji)
                .onFailure { _sendError.value = it.message ?: "No se pudo reaccionar" }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        _localPendingMessages.value = _localPendingMessages.value.filter { it.id != message.id }
        _historyMessages.value = _historyMessages.value.filter { it.id != message.id }
        val id = uid ?: return
        viewModelScope.launch {
            deleteMessageForMeUseCase(message.id)
            val pinned = (_uiState.value as? ChatUiState.Success)
                ?.conversation?.pinnedMessages?.any { it.id == message.id } == true
            if (pinned) unpinMessageUseCase(convId, message.id)
            deleteMessageUseCase(convId, message.id)
                .onFailure { _sendError.value = it.message ?: "No se pudo eliminar el mensaje" }
        }
    }

    fun deleteMessageForMe(message: ChatMessage) {
        _localPendingMessages.value = _localPendingMessages.value.filter { it.id != message.id }
        _historyMessages.value = _historyMessages.value.filter { it.id != message.id }
        viewModelScope.launch {
            deleteMessageForMeUseCase(message.id)
        }
    }

    fun togglePin(message: ChatMessage) {
        val id = uid ?: return
        val isPinned = (_uiState.value as? ChatUiState.Success)
            ?.conversation?.pinnedMessages?.any { it.id == message.id } == true
        viewModelScope.launch {
            if (isPinned) {
                unpinMessageUseCase(convId, message.id)
            } else {
                pinMessageUseCase(convId, PinnedMessage(message.id, message.text, message.senderId, System.currentTimeMillis()))
            }
        }
    }

    fun unpinMessage(msgId: String) {
        viewModelScope.launch { unpinMessageUseCase(convId, msgId) }
    }

    fun forwardMessage(message: ChatMessage, targetConvIds: List<String>) {
        val id = uid ?: return
        val forwardedFrom = ForwardedFrom(
            uid = message.senderId,
            name = if (message.senderId == id) {
                _myProfile.value?.displayName ?: "Tú"
            } else {
                (_uiState.value as? ChatUiState.Success)?.conversation?.members?.get(message.senderId)?.displayName
                    ?: (_uiState.value as? ChatUiState.Success)?.otherParticipant?.displayName
                    ?: "Contacto"
            }
        )
        viewModelScope.launch {
            targetConvIds.forEach { target ->
                val targetConv = _conversations.value.find { it.id == target }
                val mentions = if (targetConv?.isGroup == true) {
                    extractMentions(message.text, targetConv.members)
                } else {
                    emptyList()
                }
                val fwd = ChatMessage(
                    id = generateFirestoreId(),
                    senderId = id,
                    text = message.text,
                    status = MessageStatus.SENDING,
                    createdAt = System.currentTimeMillis(),
                    attachments = message.attachments,
                    mentions = mentions,
                    forwardedFrom = forwardedFrom
                )
                sendMessageUseCase(target, id, fwd)
            }
            _sendError.value = null
        }
    }

    fun saveMessage(message: ChatMessage) {
        val id = uid ?: return
        val state = _uiState.value as? ChatUiState.Success ?: return
        val text = message.text.trim()
        if (text.isEmpty() && message.attachments.isEmpty()) return

        val links = splitLinks(text).filter { it.url != null }.map { it.url!! }
        val isGroup = state.conversation.isGroup
        val senderName = when {
            message.senderId == id -> _myProfile.value?.displayName ?: "Tú"
            else -> state.conversation.members[message.senderId]?.displayName
                ?: state.otherParticipant?.displayName ?: "Miembro"
        }
        val convName = if (isGroup) state.conversation.name ?: "Grupo"
        else state.otherParticipant?.displayName ?: "Chat"

        val item = SavedItem(
            id = "saved-${Date().time.toString(36)}-${UUID.randomUUID().toString().take(6)}",
            kind = if (links.isNotEmpty() && message.attachments.isEmpty()) "link" else "message",
            text = text,
            links = links,
            attachments = message.attachments,
            senderId = message.senderId,
            senderName = senderName,
            convId = state.conversation.id,
            convName = convName,
            sourceMessageId = message.id,
            createdAt = message.createdAt,
            savedAt = System.currentTimeMillis(),
            pinned = false,
            pinnedAt = 0L
        )
        viewModelScope.launch {
            saveMessageUseCase(id, item)
                .onFailure { _sendError.value = it.message ?: "No se pudo guardar el mensaje" }
        }
    }

    /** Called by ChatScreen only for messages currently visible in the LazyColumn. */
    fun updateAutomaticDownloads(attachments: List<ChatAttachment>) {
        if (!automaticDownloadsEnabled.value) {
            mediaDownloadManager.updateAutomaticWindow(emptyList())
            return
        }
        val candidates = attachments.asSequence()
            .filter { it.publicId.isNotBlank() && it.url.isNotBlank() }
            .map { attachment ->
                AutoDownloadAttachment(
                    mediaId = attachment.publicId,
                    url = attachment.url,
                    kind = attachment.kind.toMediaKind()
                )
            }
            .toList()
        mediaDownloadManager.updateAutomaticWindow(candidates)
    }

    fun addAudioMessageToPlaylist(message: ChatMessage, playlistId: String) {
        val userId = uid
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "Playlist add rejected: no authenticated user")
            _playlistAddState.value = PlaylistAddState.Error("No hay una sesion activa.")
            return
        }
        if (playlistId.isBlank()) {
            Log.e(TAG, "Playlist add rejected: blank playlistId for message=${message.id}")
            _playlistAddState.value = PlaylistAddState.Error("La lista seleccionada no es valida.")
            return
        }
        val audioAttachments = message.attachments.filter {
            it.kind == AttachmentKind.AUDIO && it.publicId.isNotBlank() && it.url.isNotBlank()
        }
        if (audioAttachments.isEmpty()) {
            Log.e(TAG, "Playlist add rejected: no valid audio for message=${message.id}")
            _playlistAddState.value = PlaylistAddState.Error("Este mensaje no tiene un audio valido para guardar.")
            return
        }
        if (_playlistAddState.value is PlaylistAddState.Saving) return
        _playlistAddState.value = PlaylistAddState.Saving
        viewModelScope.launch {
            Log.d(
                TAG,
                "Playlist add started: uid=$userId playlist=$playlistId message=${message.id} tracks=${audioAttachments.size}"
            )
            var savedTracks = 0
            audioAttachments.forEach { attachment ->
                val track = MusicTrack(
                    id = attachment.publicId,
                    url = attachment.url,
                    name = attachment.name,
                    sourceMessageId = message.id,
                    durationSeconds = attachment.duration ?: 0
                )
                Log.d(TAG, "Saving playlist track=${track.id} sourceMessage=${message.id} playlist=$playlistId")
                val result = musicRepository.addTrackToPlaylist(
                    userId,
                    playlistId,
                    track
                )
                result.onFailure { error ->
                    Log.e(TAG, "Playlist add failed: uid=$userId playlist=$playlistId track=${track.id}", error)
                    _playlistAddState.value = PlaylistAddState.Error(
                        error.message ?: "No se pudo agregar el audio a la lista."
                    )
                    return@launch
                }
                savedTracks++
            }
            Log.d(TAG, "Playlist add succeeded: uid=$userId playlist=$playlistId tracks=$savedTracks")
            _playlistAddState.value = PlaylistAddState.Success(savedTracks)
        }
    }

    fun clearPlaylistAddState() {
        _playlistAddState.value = PlaylistAddState.Idle
    }

    fun clearSendError() {
        _sendError.value = null
    }

    fun reportVoiceError(message: String) {
        _sendError.value = message
    }

    private fun generateFirestoreId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20).map { chars.random() }.joinToString("")
    }

    private companion object {
        const val MESSAGE_SEARCH_DEBOUNCE_MS = 350L
        const val MESSAGE_SEARCH_LIMIT = 100L
        const val READ_CURSOR_DEBOUNCE_MS = 600L
        const val FORWARD_CONVERSATION_LIMIT = 100L
    }

    override fun onCleared() {
        voiceRetryFiles.values.forEach { (file, _) -> file.delete() }
        voiceRetryFiles.clear()
        super.onCleared()
    }
}

private fun AttachmentKind.toMediaKind(): MediaKind = when (this) {
    AttachmentKind.IMAGE -> MediaKind.IMAGE
    AttachmentKind.AUDIO -> MediaKind.AUDIO
    AttachmentKind.VIDEO -> MediaKind.VIDEO
    AttachmentKind.PDF, AttachmentKind.DOC, AttachmentKind.FILE -> MediaKind.DOCUMENT
}
