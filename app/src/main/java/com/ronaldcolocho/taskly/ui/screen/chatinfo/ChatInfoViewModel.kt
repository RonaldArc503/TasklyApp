package com.ronaldcolocho.taskly.ui.screen.chatinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatAttachmentRef
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.referenceIn
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import com.ronaldcolocho.taskly.domain.repository.IPresenceRepository
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import com.ronaldcolocho.taskly.domain.usecase.GetDeletedMessageIdsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChatInfoViewModel @Inject constructor(
    private val chatRepository: IChatRepository,
    profileRepository: IProfileRepository,
    presenceRepository: IPresenceRepository,
    private val getDeletedMessageIds: GetDeletedMessageIdsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val currentUserId = profileRepository.getCurrentUserId().orEmpty()
    private val _uiState = MutableStateFlow<ChatInfoUiState>(ChatInfoUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()
    private val _savingFavorite = MutableStateFlow(false)
    val savingFavorite = _savingFavorite.asStateFlow()
    private val _selectedAttachments = MutableStateFlow<Set<ChatAttachmentRef>>(emptySet())
    val selectedAttachments = _selectedAttachments.asStateFlow()
    private val _deletingAttachments = MutableStateFlow(false)
    val deletingAttachments = _deletingAttachments.asStateFlow()
    private val loadedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    private var oldestLoadedTimestamp: Long? = null

    private val peerId = uiState.map { state ->
        (state as? ChatInfoUiState.Success)?.conversation?.takeUnless { it.isGroup }
            ?.participantIds?.firstOrNull { it != currentUserId }
    }.distinctUntilChanged()

    val presence = peerId.flatMapLatest { uid -> uid?.let(presenceRepository::subscribePresence) ?: flowOf(null) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val peerProfile = peerId.flatMapLatest { uid -> uid?.let(profileRepository::getProfile) ?: flowOf(null) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            loadedMessageIds.flatMapLatest { ids -> chatRepository.observeMessagesByIds(convId, currentUserId, ids) }
                .catch { _actionError.value = "No se pudieron sincronizar los adjuntos." }
                .collect { updates ->
                    val state = _uiState.value as? ChatInfoUiState.Success ?: return@collect
                    val remaining = updates.mapValues { (id, message) ->
                        message?.attachments.orEmpty().mapTo(hashSetOf()) { it.referenceIn(id) }
                    }
                    updateAttachments(state) { ref ->
                        !remaining.containsKey(ref.messageId) || ref in remaining.getValue(ref.messageId)
                    }
                }
        }
        if (currentUserId.isBlank()) {
            _uiState.value = ChatInfoUiState.Error("Inicia sesión para ver la conversación.")
        } else viewModelScope.launch {
            chatRepository.getConversation(convId, currentUserId)
                .catch { _uiState.value = ChatInfoUiState.Error("No se pudo abrir el perfil de la conversación.") }
                .collectLatest { conversation ->
                    if (conversation == null) {
                        _uiState.value = ChatInfoUiState.Error("Conversación no encontrada.")
                        return@collectLatest
                    }
                    if (conversation.members.isEmpty() && conversation.createdAt == 0L) return@collectLatest
                    val current = _uiState.value as? ChatInfoUiState.Success
                    if (current != null && current.conversation.historyCutoff == conversation.historyCutoff) {
                        _uiState.value = current.copy(conversation = conversation)
                        return@collectLatest
                    }
                    _uiState.value = ChatInfoUiState.Loading
                    loadedMessageIds.value = emptySet()
                    _selectedAttachments.value = emptySet()
                    oldestLoadedTimestamp = null
                    try {
                        val messages = chatRepository.getChatInfoMessages(convId, currentUserId, PAGE_SIZE)
                        _uiState.value = appendMessages(
                            ChatInfoUiState.Success(currentUserId, conversation, emptyList(), emptyList(), emptyList(), emptyList()),
                            messages
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _uiState.value = ChatInfoUiState.Error("No se pudieron cargar los archivos de la conversación.")
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? ChatInfoUiState.Success ?: return
        val before = oldestLoadedTimestamp ?: return
        if (state.isLoadingMore || !state.hasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val messages = chatRepository.getOlderMessages(convId, currentUserId, before, PAGE_SIZE)
                val latest = _uiState.value as? ChatInfoUiState.Success ?: return@launch
                if (latest.conversation.historyCutoff == state.conversation.historyCutoff) {
                    _uiState.value = appendMessages(latest, messages)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val latest = _uiState.value as? ChatInfoUiState.Success ?: return@launch
                if (latest.conversation.historyCutoff == state.conversation.historyCutoff) {
                    _uiState.value = latest.copy(isLoadingMore = false)
                    _actionError.value = "No se pudo cargar más contenido."
                }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value as? ChatInfoUiState.Success ?: return
        if (_savingFavorite.value) return
        _savingFavorite.value = true
        viewModelScope.launch {
            try {
                chatRepository.setConversationPinned(currentUserId, convId, !state.conversation.isPinned)
                    .onFailure { _actionError.value = "No se pudo actualizar el favorito." }
            } finally {
                _savingFavorite.value = false
            }
        }
    }

    fun clearActionError() { _actionError.value = null }

    fun toggleAttachment(ref: ChatAttachmentRef) {
        if (_deletingAttachments.value) return
        val selection = _selectedAttachments.value
        _selectedAttachments.value = if (ref in selection) selection - ref else selection + ref
    }

    fun clearAttachmentSelection() {
        if (!_deletingAttachments.value) _selectedAttachments.value = emptySet()
    }

    fun deleteSelectedAttachments(forEveryone: Boolean) {
        val selection = _selectedAttachments.value
        if (selection.isEmpty() || _deletingAttachments.value) return
        _deletingAttachments.value = true
        viewModelScope.launch {
            try {
                chatRepository.deleteAttachments(convId, currentUserId, selection, forEveryone)
                    .onSuccess {
                        val state = _uiState.value as? ChatInfoUiState.Success ?: return@onSuccess
                        updateAttachments(state) { it !in selection }
                    }
                    .onFailure { _actionError.value = "No se completó el borrado de los adjuntos." }
            } finally {
                _deletingAttachments.value = false
            }
        }
    }

    private fun updateAttachments(state: ChatInfoUiState.Success, keep: (ChatAttachmentRef) -> Boolean) {
        fun List<Pair<ChatAttachment, String>>.kept() = filter { (attachment, id) -> keep(attachment.referenceIn(id)) }
        _uiState.value = state.copy(
            mediaAttachments = state.mediaAttachments.kept(),
            audioAttachments = state.audioAttachments.kept(),
            files = state.files.kept()
        )
        _selectedAttachments.value = _selectedAttachments.value.filterTo(linkedSetOf(), keep)
    }

    private suspend fun appendMessages(state: ChatInfoUiState.Success, messages: List<ChatMessage>): ChatInfoUiState.Success {
        val hiddenIds = getDeletedMessageIds().first()
        loadedMessageIds.value += messages.map(ChatMessage::id)
        oldestLoadedTimestamp = messages.minOfOrNull(ChatMessage::createdAt) ?: oldestLoadedTimestamp
        val visible = messages.filterNot { it.id in hiddenIds }
        val attachments = visible.flatMap { message -> message.attachments.map { it to message.id } }
        val links = visible.flatMap { message ->
            LINK.findAll(message.text).map { match ->
                val url = match.value.trimEnd('.', ',', ')', ']', '!', '?')
                ChatLink(if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url, message.id, message.senderId, message.createdAt)
            }.toList()
        }
        return state.copy(
            mediaAttachments = (state.mediaAttachments + attachments.filter { it.first.kind in MEDIA }).distinct(),
            mediaMessages = state.mediaMessages + visible.filter { message ->
                message.attachments.any { it.kind in MEDIA }
            }.associateBy(ChatMessage::id),
            links = (state.links + links).distinct(),
            audioAttachments = (state.audioAttachments + attachments.filter { it.first.kind == AttachmentKind.AUDIO }).distinct(),
            files = (state.files + attachments.filter { it.first.kind in FILES }).distinct(),
            isLoadingMore = false,
            hasMore = messages.size == PAGE_SIZE.toInt()
        )
    }

    private companion object {
        const val PAGE_SIZE = 50L
        val LINK = Regex("""(?:https?://|ftp://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)
        val MEDIA = setOf(AttachmentKind.IMAGE, AttachmentKind.VIDEO)
        val FILES = setOf(AttachmentKind.PDF, AttachmentKind.DOC, AttachmentKind.FILE)
    }
}
