package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchGlobalMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobalChatSearchState(
    val query: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GlobalChatSearchViewModel @Inject constructor(
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val searchConversationsUseCase: SearchConversationsUseCase,
    private val searchGlobalMessagesUseCase: SearchGlobalMessagesUseCase
) : ViewModel() {
    private val userId = getCurrentUserIdUseCase().orEmpty()
    private val _state = MutableStateFlow(GlobalChatSearchState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        _state.value = GlobalChatSearchState(query = query)
        val normalized = ChatSearchNormalizer.normalize(query)
        if (normalized.length < 2 || userId.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(350)
            _state.value = _state.value.copy(searching = true)
            runCatching {
                val conversations = searchConversationsUseCase(userId, normalized, 30)
                val messages = searchGlobalMessagesUseCase(userId, normalized, 60)
                conversations to messages
            }.onSuccess { (conversations, messages) ->
                _state.value = _state.value.copy(
                    conversations = conversations,
                    messages = messages,
                    searching = false
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(searching = false, error = error.message ?: "No se pudo buscar.")
            }
        }
    }
}

@Composable
fun GlobalChatSearchScreen(
    viewModel: GlobalChatSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenResult: (conversationId: String, messageId: String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar chats, mensajes y archivos") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.search("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar")
                    }
                }
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        val typed = remember(state.messages) { typeMessages(state.messages) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (state.query.length >= 2 && state.conversations.isEmpty() && state.messages.isEmpty() && !state.searching) {
                item { Text("Sin resultados", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            searchSection("Conversaciones", state.conversations) { conversation ->
                SearchRow(
                    title = conversation.name ?: conversation.members.values.firstOrNull()?.displayName ?: "Conversacion",
                    subtitle = conversation.lastMessage,
                    onClick = { onOpenResult(conversation.id, null) }
                )
            }
            typed.forEach { (title, messages) ->
                searchSection(title, messages) { message ->
                    SearchRow(
                        title = message.text.ifBlank { message.attachments.firstOrNull()?.name ?: "Contenido" },
                        subtitle = message.attachments.joinToString { it.name },
                        onClick = { onOpenResult(message.conversationId, message.id) }
                    )
                }
            }
        }
    }
}

private fun typeMessages(messages: List<ChatMessage>): LinkedHashMap<String, List<ChatMessage>> {
    val sections = linkedMapOf<String, MutableList<ChatMessage>>()
    fun add(title: String, message: ChatMessage) { sections.getOrPut(title) { mutableListOf() }.add(message) }
    messages.forEach { message ->
        if (message.text.isNotBlank()) add(if (URL_REGEX.containsMatchIn(message.text)) "Enlaces" else "Mensajes", message)
        if (message.attachments.any { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }) add("Multimedia", message)
        if (message.attachments.any { it.kind == AttachmentKind.AUDIO }) add("Audios", message)
        if (message.attachments.any { it.kind == AttachmentKind.PDF || it.kind == AttachmentKind.DOC || it.kind == AttachmentKind.FILE }) add("Archivos", message)
    }
    return LinkedHashMap(sections.mapValues { it.value.distinctBy(ChatMessage::id) })
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.searchSection(
    title: String,
    values: List<T>,
    row: @Composable (T) -> Unit
) {
    if (values.isEmpty()) return
    item { Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
    items(values.size) { index -> row(values[index]) }
}

@Composable
private fun SearchRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val URL_REGEX = "https?://\\S+".toRegex(RegexOption.IGNORE_CASE)
