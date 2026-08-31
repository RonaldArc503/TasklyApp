package com.ronaldcolocho.taskly.ui.screen.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.ForwardedFrom
import com.ronaldcolocho.taskly.domain.model.MessageStatus
import com.ronaldcolocho.taskly.domain.model.PinnedMessage
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.model.ReplyInfo
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.usecase.chat.ClearTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.DeleteMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.EditMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetOlderMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.MarkAsReadUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.PinMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SaveMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SendMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SetTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribePresenceUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribeTypingUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.ToggleReactionUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UnpinMessageUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.UploadAttachmentUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.util.extractMentions
import com.ronaldcolocho.taskly.domain.util.splitLinks
import com.ronaldcolocho.taskly.ui.state.ChatUiState
import com.ronaldcolocho.taskly.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
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

private data class ChatEphemeralState(
    val isFetching: Boolean,
    val otherParticipant: UserProfile?,
    val peerPresence: Presence?,
    val typingMap: Map<String, Long>,
    val replyTo: ChatMessage?,
    val editing: ChatMessage?,
    val sendError: String?
)

private const val MAX_ATTACHMENTS_PER_MESSAGE = 30

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getConversationUseCase: GetConversationUseCase,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val getRecentMessagesUseCase: GetRecentMessagesUseCase,
    private val getOlderMessagesUseCase: GetOlderMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markAsReadUseCase: MarkAsReadUseCase,
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val uid: String? = getCurrentUserIdUseCase()

    private val _historyMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _localPendingMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    private val _editing = MutableStateFlow<ChatMessage?>(null)
    private val _sendError = MutableStateFlow<String?>(null)
    private val _typingMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _peerPresence = MutableStateFlow<Presence?>(null)
    private val _otherParticipant = MutableStateFlow<UserProfile?>(null)
    private val _isFetchingOlder = MutableStateFlow(false)
    private val _drafts = MutableStateFlow<List<AttachmentDraft>>(emptyList())
    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    private var hasMoreHistory = true
    private var lastTypingSent = 0L

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val drafts: StateFlow<List<AttachmentDraft>> = _drafts.asStateFlow()
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val id = uid ?: run {
            _uiState.value = ChatUiState.Error("No hay sesión activa")
            return
        }

        viewModelScope.launch {
            getProfileUseCase(id).collect { _myProfile.value = it }
            markAsReadUseCase(convId, id)
            getConversationsUseCase(id).collect { _conversations.value = it }
        }

        val conversationFlow = getConversationUseCase(convId, id).filterNotNull()

        // Perfil + presencia del peer: suscribir una sola vez (sin flatMapLatest)
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

        val recentMessagesFlow = getRecentMessagesUseCase(convId, id).onEach {
            markAsReadUseCase(convId, id)
        }

        // Solo mensajes: se reordena/reconstruye únicamente cuando cambian los mensajes
        val messagesFlow = combine(
            conversationFlow,
            recentMessagesFlow,
            _historyMessages,
            _localPendingMessages
        ) { conv, recent, history, pending ->
            val remoteIds = (recent + history).map { it.id }.toSet()
            val activePending = pending.filter { it.id !in remoteIds }
            if (activePending.size != pending.size) {
                _localPendingMessages.value = activePending
            }
            val allRemoteMap = (history + recent).associateBy { it.id }
            val allMessages = (activePending + allRemoteMap.values)
                .sortedByDescending { it.createdAt }
            conv to allMessages
        }

        // Estado efímero: typing, presencia, reply, edición, error, perfil
        val ephemeralFlow = combine(
            _isFetchingOlder,
            _otherParticipant,
            _peerPresence,
            _typingMap,
            _replyTo,
            _editing,
            _sendError
        ) { args ->
            ChatEphemeralState(
                isFetching = args[0] as Boolean,
                otherParticipant = args[1] as? UserProfile,
                peerPresence = args[2] as? Presence,
                typingMap = args[3] as Map<String, Long>,
                replyTo = args[4] as? ChatMessage,
                editing = args[5] as? ChatMessage,
                sendError = args[6] as? String
            )
        }

        combine(messagesFlow, ephemeralFlow) { (conv, messages), e ->
            ChatUiState.Success(
                conversation = conv,
                currentUserId = id,
                messages = messages,
                isFetchingOlder = e.isFetching,
                otherParticipant = e.otherParticipant,
                peerPresence = e.peerPresence,
                typingMap = e.typingMap,
                replyTo = e.replyTo,
                editing = e.editing,
                sendError = e.sendError
            )
        }
        .catch { e -> _uiState.value = ChatUiState.Error(e.message ?: "Error al cargar el chat") }
        .distinctUntilChanged()
        .onEach { _uiState.value = it }
        .launchIn(viewModelScope)
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
                val older = getOlderMessagesUseCase(convId, id, oldestMessage.createdAt, 30L)
                if (older.isEmpty()) hasMoreHistory = false else _historyMessages.value += older
            }
            _isFetchingOlder.value = false
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
                file?.delete()
                result.onSuccess { att ->
                    attachments += att
                    _drafts.update { list ->
                        list.map { if (it.id == d.id) it.copy(status = DraftStatus.DONE, progress = 100, attach = att) else it }
                    }
                }.onFailure { uploadError ->
                    failed = true
                    _drafts.update { list ->
                        list.map { if (it.id == d.id) it.copy(status = DraftStatus.ERROR, error = uploadError.message) else it }
                    }
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
        uris.take(MAX_ATTACHMENTS_PER_MESSAGE - current.size).forEach { uri ->
            val name = FileUtil.getDisplayName(context, uri) ?: "archivo"
            val size = FileUtil.getSize(context, uri)
            val mime = context.contentResolver.getType(uri)
            val kind = FileUtil.classify(mime, name)
            val tooBig = FileUtil.isTooBig(size)
            _drafts.value += AttachmentDraft(
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
            sendMessageUseCase(convId, id, message).onFailure { e ->
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
        executeSend(retrying)
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
        val id = uid ?: return
        viewModelScope.launch {
            val pinned = (_uiState.value as? ChatUiState.Success)
                ?.conversation?.pinnedMessages?.any { it.id == message.id } == true
            if (pinned) unpinMessageUseCase(convId, message.id)
            deleteMessageUseCase(convId, message.id)
                .onFailure { _sendError.value = it.message ?: "No se pudo eliminar el mensaje" }
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

    fun clearSendError() {
        _sendError.value = null
    }

    private fun generateFirestoreId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20).map { chars.random() }.joinToString("")
    }
}
