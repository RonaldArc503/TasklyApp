package com.ronaldcolocho.taskly.ui.screen.chatinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val chatRepository: IChatRepository,
    private val profileRepository: IProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val convId: String = checkNotNull(savedStateHandle["convId"])

    private val _uiState = MutableStateFlow<ChatInfoUiState>(ChatInfoUiState.Loading)
    val uiState: StateFlow<ChatInfoUiState> = _uiState.asStateFlow()

    private val currentUserId = profileRepository.getCurrentUserId() ?: ""
    private var oldestLoadedTimestamp: Long? = null
    private var hasMore = true

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // 1. Fetch conversation details (from local cache/flow)
                val conversation = chatRepository.getConversation(convId, currentUserId).firstOrNull()
                if (conversation == null) {
                    _uiState.value = ChatInfoUiState.Error("Conversación no encontrada.")
                    return@launch
                }

                // Solo la primera pÃ¡gina cacheada; el historial se solicita bajo demanda.
                val messages = chatRepository.getChatInfoMessages(convId, currentUserId, PAGE_SIZE)
                oldestLoadedTimestamp = messages.minOfOrNull { it.createdAt }
                hasMore = messages.size == PAGE_SIZE.toInt()
                val parsed = parseMessages(messages)
                _uiState.value = ChatInfoUiState.Success(
                    currentUserId = currentUserId,
                    conversation = conversation,
                    mediaAttachments = parsed.first,
                    links = parsed.second,
                    audioAttachments = parsed.third,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                _uiState.value = ChatInfoUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? ChatInfoUiState.Success ?: return
        val before = oldestLoadedTimestamp ?: return
        if (state.isLoadingMore || !hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            val messages = runCatching {
                chatRepository.getOlderMessages(convId, currentUserId, before, PAGE_SIZE)
            }.getOrDefault(emptyList())
            if (messages.isEmpty()) {
                hasMore = false
                _uiState.value = state.copy(hasMore = false)
                return@launch
            }
            oldestLoadedTimestamp = messages.minOfOrNull { it.createdAt } ?: before
            hasMore = messages.size == PAGE_SIZE.toInt()
            val parsed = parseMessages(messages)
            _uiState.value = state.copy(
                mediaAttachments = (state.mediaAttachments + parsed.first).distinctBy { it.first.publicId to it.second },
                links = (state.links + parsed.second).distinctBy { it.url to it.messageId },
                audioAttachments = (state.audioAttachments + parsed.third).distinctBy { it.first.publicId to it.second },
                isLoadingMore = false,
                hasMore = hasMore
            )
        }
    }

    private suspend fun parseMessages(messages: List<ChatMessage>): Triple<List<Pair<ChatAttachment, String>>, List<ChatLink>, List<Pair<ChatAttachment, String>>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val media = mutableListOf<Pair<ChatAttachment, String>>()
                    val lks = mutableListOf<ChatLink>()
                    val audio = mutableListOf<Pair<ChatAttachment, String>>()

                    // URL Regex pattern
                    val urlPattern = Pattern.compile(
                        "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
                                + "(([\\w\\-]+\\.){1,}?([a-zA-Z]{2,10}|[0-9]{1,3}))"
                                + "(:[0-9]+)?(\\/|\\?|\\#|\\w|\\-|\\.|\\,|\\=|\\&|\\%)*"
                    )

                    messages.forEach { msg ->
                        // Parse text for links
                        if (msg.text.isNotBlank()) {
                            val matcher = urlPattern.matcher(msg.text)
                            while (matcher.find()) {
                                var url = matcher.group(0)?.trim() ?: ""
                                if (url.isNotEmpty()) {
                                    // Add http:// if missing and not starting with http/ftp
                                    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("ftp://")) {
                                        url = "http://$url"
                                    }
                                    lks.add(ChatLink(url, msg.id, msg.senderId, msg.createdAt))
                                }
                            }
                        }

                        // Parse attachments
                        msg.attachments.forEach { attachment ->
                            when (attachment.kind) {
                                AttachmentKind.IMAGE, AttachmentKind.VIDEO -> {
                                    media.add(attachment to msg.id)
                                }
                                AttachmentKind.AUDIO -> {
                                    audio.add(attachment to msg.id)
                                }
                                else -> {} // Ignore DOC, PDF, etc. for now or could add a 'Docs' tab later
                            }
                        }
                    }
                    Triple(media, lks, audio)
            Triple(media, lks, audio)
        }
    }

    private companion object { const val PAGE_SIZE = 50L }
}
