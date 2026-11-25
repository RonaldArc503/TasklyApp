package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.ChatSearchHit
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchGlobalMessagesUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class GlobalChatSearchState(
    val query: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatSearchHit> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GlobalChatSearchViewModel @Inject constructor(
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val searchConversationsUseCase: SearchConversationsUseCase,
    private val searchGlobalMessagesUseCase: SearchGlobalMessagesUseCase
) : ViewModel() {
    internal val userId = getCurrentUserIdUseCase().orEmpty()
    private val _state = MutableStateFlow(GlobalChatSearchState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        val normalized = ChatSearchNormalizer.normalize(query)
        val canSearch = normalized.length >= 2 && userId.isNotBlank()
        _state.value = GlobalChatSearchState(query = query, searching = canSearch)
        if (!canSearch) return
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val conversations = searchConversationsUseCase(userId, normalized, 30)
                val messages = searchGlobalMessagesUseCase(userId, normalized, 60)
                _state.value = GlobalChatSearchState(query, conversations, messages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = GlobalChatSearchState(query = query, error = error.message ?: "No se pudo buscar.")
            }
        }
    }
}

private enum class SearchFilter(val label: String) {
    ALL("Todo"), MESSAGES("Mensajes"), FILES("Archivos"), LINKS("Enlaces")
}

private const val SearchPreviewCount = 3
private val searchUrl = Regex("""https?://[^\s<]+""", RegexOption.IGNORE_CASE)
private val marks = Regex("\\p{M}+")

@Composable
fun GlobalChatSearchScreen(
    viewModel: GlobalChatSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenResult: (conversationId: String, messageId: String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.query.isNotBlank()) viewModel.search(state.query)
    }
    GlobalChatSearchContent(state, viewModel.userId, viewModel::search, onBack, onOpenResult)
}

@Composable
internal fun GlobalChatSearchContent(
    state: GlobalChatSearchState,
    userId: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenResult: (String, String?) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val query = state.query
    val normalizedQuery = remember(query) { ChatSearchNormalizer.normalize(query) }
    val results = remember(state.messages, normalizedQuery) {
        SearchResults(
            messages = state.messages.filter { hit ->
                hit.message.text.replace(searchUrl, "").isNotBlank() &&
                    ChatSearchNormalizer.matches(hit.message.text, normalizedQuery)
            },
            files = state.messages.flatMap { hit ->
                val textMatches = ChatSearchNormalizer.matches(hit.message.text, normalizedQuery)
                hit.message.attachments.filter {
                    textMatches || ChatSearchNormalizer.matches(it.name, normalizedQuery)
                }.map { FileResult(it.name, it.size, hit) }
            },
            links = state.messages.flatMap { hit ->
                searchUrl.findAll(hit.message.text).map { it.value.trimEnd('.', ',', ')', ']', '!', '?') }
                    .distinct().filter { ChatSearchNormalizer.matches(it, normalizedQuery) }
                    .map { LinkResult(it, hit) }.toList()
            }
        )
    }
    val matchingChats = remember(state.conversations, state.messages) {
        (state.conversations + state.messages.map(ChatSearchHit::conversation)).distinctBy(ChatConversation::id)
    }
    val showAll = filter == SearchFilter.ALL
    val hasRows = when (filter) {
        SearchFilter.ALL -> results.messages.isNotEmpty() || results.files.isNotEmpty() ||
            results.links.isNotEmpty() || matchingChats.isNotEmpty()
        SearchFilter.MESSAGES -> results.messages.isNotEmpty()
        SearchFilter.FILES -> results.files.isNotEmpty()
        SearchFilter.LINKS -> results.links.isNotEmpty()
    }
    Column(Modifier.fillMaxSize().chatDirectoryBackground()) {
        SearchTopBar(query, onQueryChange, onBack)
        ChatDirectoryFilters(SearchFilter.entries, filter, SearchFilter::label) { filter = it }
        when {
            state.searching -> LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = ChatAccent, trackColor = ChatDivider
            )
            state.error != null -> SearchStatus(state.error, ChatError)
            normalizedQuery.length < 2 -> SearchStatus("Busca en tus chats, mensajes, archivos y enlaces.")
            !hasRows -> SearchStatus("Sin resultados")
            else -> key(filter) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (showAll || filter == SearchFilter.MESSAGES) {
                        searchSection("MENSAJES", results.messages, showAll, { filter = SearchFilter.MESSAGES }) { hit ->
                            SearchMessageRow(hit, userId, normalizedQuery) { onOpenResult(hit.conversation.id, hit.message.id) }
                        }
                    }
                    if (showAll || filter == SearchFilter.FILES) {
                        searchSection("ARCHIVOS", results.files, showAll, { filter = SearchFilter.FILES }) { file ->
                            SearchResourceRow(
                                file.name, resourceSubtitle(file.hit, userId, file.size.takeIf { it > 0L }?.let(::formatBytes)),
                                normalizedQuery, Icons.Outlined.Description, Color(0xFFBAA8FF)
                            ) { onOpenResult(file.hit.conversation.id, file.hit.message.id) }
                        }
                    }
                    if (showAll || filter == SearchFilter.LINKS) {
                        searchSection("ENLACES", results.links, showAll, { filter = SearchFilter.LINKS }) { link ->
                            SearchResourceRow(
                                link.url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), ""),
                                resourceSubtitle(link.hit, userId), normalizedQuery, Icons.Outlined.Link, ChatAccent
                            ) { onOpenResult(link.hit.conversation.id, link.hit.message.id) }
                        }
                    }
                    if (showAll) searchSection("CHATS", matchingChats) { chat ->
                        SearchChatRow(chat, userId, normalizedQuery) { onOpenResult(chat.id, null) }
                    }
                }
            }
        }
    }
}

private data class SearchResults(val messages: List<ChatSearchHit>, val files: List<FileResult>, val links: List<LinkResult>)
private data class FileResult(val name: String, val size: Long, val hit: ChatSearchHit)
private data class LinkResult(val url: String, val hit: ChatSearchHit)

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Volver", tint = ChatText) }
        val shape = RoundedCornerShape(28.dp)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            textStyle = TextStyle(color = ChatText, fontSize = 16.sp, fontFamily = ChatFont),
            cursorBrush = SolidColor(ChatAccent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).background(ChatSurfaceRaised, shape)
                        .border(1.dp, ChatAccent, shape).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(22.dp), tint = ChatAccent)
                    Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        if (query.isEmpty()) Text("Buscar en chats", color = ChatMuted, fontSize = 16.sp)
                        field()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange(""); focus.requestFocus() },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(Modifier.size(36.dp).background(ChatDivider, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Close, "Limpiar búsqueda", Modifier.size(18.dp), tint = ChatText)
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun <T> LazyListScope.searchSection(
    title: String,
    values: List<T>,
    preview: Boolean = false,
    onShowAll: (() -> Unit)? = null,
    row: @Composable (T) -> Unit
) {
    if (values.isEmpty()) return
    item(key = "section-$title") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = 20.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (title == "CHATS") title else "$title · ${values.size}", Modifier.weight(1f),
                color = ChatMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (preview && onShowAll != null) {
                TextButton(onClick = onShowAll) { Text("Ver todo", color = ChatAccent, fontSize = 13.sp) }
            }
        }
    }
    items(if (preview) minOf(values.size, SearchPreviewCount) else values.size, key = { "$title-$it" }) { row(values[it]) }
}

@Composable
private fun SearchMessageRow(hit: ChatSearchHit, userId: String, query: String, onClick: () -> Unit) {
    val message = hit.message
    val sender = if (message.senderId == userId) "Tú"
        else hit.conversation.members[message.senderId]?.displayName?.takeIf(String::isNotBlank)
    val prefix = sender?.let { "$it: " }.orEmpty()
    val snippet = message.text.trim().replace(Regex("https?://", RegexOption.IGNORE_CASE), "")
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
    ) {
        ChatDirectoryAvatar(hit.conversation, userId, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SearchRowTitle(hit.conversation.chatTitle(userId), chatDirectoryTime(message.createdAt), query)
            Text(remember(prefix, snippet, query) { highlightSearchText(prefix + snippet, query) },
                color = ChatText.copy(alpha = .82f), fontSize = 14.sp, lineHeight = 19.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchChatRow(chat: ChatConversation, userId: String, query: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        ChatDirectoryAvatar(chat, userId, 54.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SearchRowTitle(chat.chatTitle(userId), chatDirectoryTime(chat.lastMessageAt), query)
            val description = if (chat.isGroup) "${chat.participantIds.size} miembros" else chat.lastMessage.removePrefix("📎 ")
            if (description.isNotBlank()) Text(description, color = ChatMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchRowTitle(title: String, time: String, query: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(remember(title, query) { highlightSearchText(title, query) }, Modifier.weight(1f), color = ChatText,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (time.isNotBlank()) Text(time, color = ChatMuted, fontSize = 12.sp)
    }
}

private fun resourceSubtitle(hit: ChatSearchHit, uid: String, extra: String? = null) =
    listOfNotNull(extra, hit.conversation.chatTitle(uid), chatDirectoryTime(hit.message.createdAt).ifBlank { null }).joinToString(" · ")

@Composable
private fun SearchResourceRow(title: String, subtitle: String, query: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(shape)
            .background(ChatSurfaceRaised).border(1.dp, ChatDivider, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(tint.copy(alpha = .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(remember(title, query) { highlightSearchText(title, query) }, color = ChatText, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = ChatMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchStatus(message: String, color: Color = ChatMuted) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = color, fontSize = 14.sp)
    }
}

private fun highlightSearchText(text: String, normalizedQuery: String): AnnotatedString {
    val offsets = mutableListOf<Int>()
    val folded = buildString {
        text.forEachIndexed { index, char ->
            val value = Normalizer.normalize(char.toString().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(marks, "")
            append(value)
            repeat(value.length) { offsets += index }
        }
    }
    val style = SpanStyle(color = Color(0xFF80DEED), background = ChatAccentContainer)
    return buildAnnotatedString {
        append(text)
        normalizedQuery.split(' ').filter { it.isNotEmpty() }.distinct().forEach { term ->
            var start = folded.indexOf(term)
            while (start >= 0) {
                val end = start + term.length
                addStyle(style, offsets[start], offsets.getOrElse(end) { text.length })
                start = folded.indexOf(term, end)
            }
        }
    }
}
