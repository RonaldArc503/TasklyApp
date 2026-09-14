package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun GlobalChatSearchScreen(
    viewModel: GlobalChatSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenResult: (conversationId: String, messageId: String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top search bar ──────────────────────────────────────────────────
        SearchTopBar(
            query = state.query,
            searching = state.searching,
            onQueryChange = viewModel::search,
            onBack = onBack
        )

        // ── Linear progress while searching ────────────────────────────────
        AnimatedVisibility(
            visible = state.searching,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // ── Error state ─────────────────────────────────────────────────────
        state.error?.let { errorMsg ->
            ErrorPanel(message = errorMsg)
            return@Column
        }

        val typed = remember(state.messages) { typeMessages(state.messages) }
        val hasResults = state.conversations.isNotEmpty() || state.messages.isNotEmpty()

        // ── Empty / idle states ─────────────────────────────────────────────
        if (state.query.length >= 2 && !hasResults && !state.searching) {
            EmptyResultsPanel()
            return@Column
        }
        if (state.query.length < 2) {
            IdlePanel()
            return@Column
        }

        // ── Results list ────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            searchSection(
                title = "Conversaciones",
                icon = Icons.Default.ChatBubble,
                values = state.conversations
            ) { conversation ->
                SearchResultCard(
                    icon = Icons.Default.ChatBubble,
                    title = conversation.name
                        ?: conversation.members.values.firstOrNull()?.displayName
                        ?: "Conversacion",
                    subtitle = conversation.lastMessage,
                    onClick = { onOpenResult(conversation.id, null) }
                )
            }
            typed.forEach { (title, messages) ->
                val icon = sectionIcon(title)
                searchSection(title = title, icon = icon, values = messages) { message ->
                    val hasAttachments = message.attachments.isNotEmpty()
                    SearchResultCard(
                        icon = icon,
                        title = message.text.ifBlank {
                            message.attachments.firstOrNull()?.name ?: "Contenido"
                        },
                        subtitle = if (hasAttachments)
                            message.attachments.joinToString { it.name }
                        else "",
                        onClick = { onOpenResult(message.conversationId, message.id) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchTopBar(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    Surface(
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                placeholder = {
                    Text(
                        "Buscar chats, mensajes y archivos",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searching,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AnimatedVisibility(
                        visible = !searching && query.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

private fun <T> LazyListScope.searchSection(
    title: String,
    icon: ImageVector,
    values: List<T>,
    row: @Composable (T) -> Unit
) {
    if (values.isEmpty()) return
    item(key = "header_$title") {
        SectionHeader(title = title, icon = icon)
    }
    items(values.size, key = { "${title}_$it" }) { index ->
        row(values[index])
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Result card
// ---------------------------------------------------------------------------

@Composable
private fun SearchResultCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty / idle / error panels
// ---------------------------------------------------------------------------

@Composable
private fun EmptyResultsPanel() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Sin resultados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Prueba con otros terminos o revisa la ortografia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IdlePanel() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Busca en tus chats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Escribe al menos 2 caracteres para buscar conversaciones, mensajes y archivos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers (logic unchanged)
// ---------------------------------------------------------------------------

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

private fun sectionIcon(title: String): ImageVector = when (title) {
    "Conversaciones" -> Icons.Default.ChatBubble
    "Mensajes"       -> Icons.AutoMirrored.Filled.Message
    "Multimedia"     -> Icons.Default.Image
    "Audios"         -> Icons.Default.AudioFile
    "Archivos"       -> Icons.AutoMirrored.Filled.InsertDriveFile
    "Enlaces"        -> Icons.Default.Link
    else             -> Icons.Default.Search
}

private val URL_REGEX = "https?://\\S+".toRegex(RegexOption.IGNORE_CASE)
