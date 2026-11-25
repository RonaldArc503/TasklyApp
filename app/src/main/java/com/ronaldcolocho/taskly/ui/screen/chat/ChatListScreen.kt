package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.ui.theme.ChatAccent
import com.ronaldcolocho.taskly.ui.theme.ChatActionGradient
import com.ronaldcolocho.taskly.ui.theme.ChatMuted
import com.ronaldcolocho.taskly.ui.theme.ChatText
import kotlinx.coroutines.flow.distinctUntilChanged

private val AvatarPalette = listOf(
    Color(0xFF6366F1), // Indigo-500
    Color(0xFF0EA5E9), // Sky-500
    Color(0xFF10B981), // Emerald-500
    Color(0xFFF59E0B), // Amber-500
    Color(0xFFF43F5E), // Rose-500
    Color(0xFF8B5CF6), // Violet-500
    Color(0xFF14B8A6), // Teal-500
    Color(0xFFD946EF)  // Fuchsia-500
)

private fun avatarColor(uid: String): Color {
    val idx = Math.abs(uid.hashCode()) % AvatarPalette.size
    return AvatarPalette[idx]
}

private fun initialsFromName(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

private enum class ChatListFilter(val label: String) {
    ALL("Todos"), UNREAD("No leídos"), GROUPS("Grupos"), PERSONAL("Personales");

    fun includes(chat: ChatConversation) = when (this) {
        ALL -> true
        UNREAD -> chat.unreadCount > 0
        GROUPS -> chat.isGroup
        PERSONAL -> !chat.isGroup
    }
}

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onNavigateToChat: (String) -> Unit,
    onNavigateToGlobalSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val newConversation by viewModel.newConversation.collectAsStateWithLifecycle()
    val openConversation by viewModel.openConversation.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    var showNewConversation by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(ChatListFilter.ALL) }
    val showingArchived = (uiState as? ChatListUiState.Success)?.showingArchived == true

    LaunchedEffect(openConversation) {
        val conversationId = openConversation ?: return@LaunchedEffect
        showNewConversation = false
        onNavigateToChat(conversationId)
        viewModel.consumeOpenConversation(conversationId)
    }

    Column(modifier = Modifier.fillMaxSize().chatDirectoryBackground()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showingArchived) "Archivados" else "Chats",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = ChatText,
                modifier = Modifier.weight(1f)
            )
            if (showingArchived) {
                IconButton(onClick = { viewModel.showArchived(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a chats")
                }
            } else {
                IconButton(onClick = onNavigateToGlobalSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Búsqueda global", tint = ChatText)
                }
                IconButton(onClick = { viewModel.showArchived(true) }) {
                    Icon(Icons.Outlined.Archive, contentDescription = "Ver archivados", tint = ChatText)
                }
                IconButton(
                    onClick = { showNewConversation = true },
                    modifier = Modifier.padding(start = 4.dp).size(46.dp).clip(CircleShape).background(ChatActionGradient)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Nueva conversación",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        if (!showingArchived) {
            ChatDirectoryFilters(ChatListFilter.entries, filter, ChatListFilter::label) { filter = it }
        }

        when (val state = uiState) {
            is ChatListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ChatAccent)
                }
            }
            is ChatListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ChatListUiState.Success -> {
                val activeFilter = if (showingArchived) ChatListFilter.ALL else filter
                val visible = remember(state.conversations, activeFilter) { state.conversations.filter(activeFilter::includes) }
                val listState = key(activeFilter, showingArchived) { rememberLazyListState() }

                if (visible.isEmpty()) {
                    LaunchedEffect(activeFilter, state.conversations.size, state.isLoadingMore, state.hasMore) {
                        if (state.hasMore && !state.isLoadingMore) viewModel.loadMore()
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.isLoadingMore || state.hasMore) {
                            CircularProgressIndicator(color = ChatAccent)
                        } else {
                            Text(
                                text = when {
                                    state.searchQuery.isNotBlank() -> "Sin resultados"
                                    state.showingArchived -> "Sin conversaciones archivadas"
                                    activeFilter != ChatListFilter.ALL -> "Sin chats en este filtro"
                                    else -> "Sin conversaciones"
                                },
                                color = ChatMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    val conversationsById = remember(state.conversations) {
                        state.conversations.associateBy(ChatConversation::id)
                    }
                    val shouldLoadMore by remember(listState, visible.size) {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= visible.lastIndex - 4
                        }
                    }
                    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoadingMore) {
                        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) viewModel.loadMore()
                    }
                    LaunchedEffect(listState, conversationsById, currentUserId) {
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                                val conversation = conversationsById[item.key as? String] ?: return@mapNotNull null
                                if (conversation.isGroup) return@mapNotNull null
                                conversation.participantIds.firstOrNull { it != currentUserId }
                            }.toSet()
                        }
                            .distinctUntilChanged()
                            .collect(viewModel::setVisiblePresenceUserIds)
                    }
                    DisposableEffect(listState) {
                        onDispose { viewModel.setVisiblePresenceUserIds(emptySet()) }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { conv ->
                            val otherUserId = conv.participantIds.firstOrNull { it != currentUserId }
                            ConversationItem(
                                conv = conv,
                                currentUserId = currentUserId,
                                isOnline = otherUserId != null &&
                                    state.presenceByUserId[otherUserId]?.isOnlineNow() == true,
                                onClick = { onNavigateToChat(conv.id) },
                                onTogglePinned = { viewModel.togglePinned(conv) },
                                onToggleArchived = { viewModel.toggleArchived(conv) }
                            )
                        }
                        if (state.isLoadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No hay conversaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showNewConversation) {
        NewConversationSheet(
            state = newConversation,
            onQueryChange = viewModel::onNewConversationQueryChange,
            onSelect = viewModel::openDirectConversation,
            onDismiss = {
                showNewConversation = false
                viewModel.clearNewConversation()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    state: NewConversationUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nueva conversacion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Busca por telefono o correo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
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
                        "Telefono o correo",
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
                        visible = state.query.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar busqueda",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )


            when {
                state.isSearching || state.isOpening -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                state.query.trim().length >= 2 && state.results.isEmpty() -> Text(
                    "No se encontraron usuarios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(state.results, key = UserProfile::uid) { profile ->
                        ProfileSearchResult(profile = profile, onClick = { onSelect(profile) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSearchResult(profile: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = profile.photoURL.takeIf(String::isNotBlank),
            contentDescription = profile.displayName,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarColor(profile.uid)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f)) {
            Text(profile.displayName.ifBlank { "Usuario" }, fontWeight = FontWeight.SemiBold)
            Text(
                profile.phone.ifBlank { profile.email },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conv: ChatConversation,
    currentUserId: String,
    isOnline: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val unread = conv.unreadCount > 0 && !conv.isMuted
    val attachmentName = conv.lastMessage.takeIf { it.startsWith("📎 ") }?.removePrefix("📎 ")
    val voice = attachmentName?.startsWith("voice_", ignoreCase = true) == true
    val preview = when {
        voice -> "Nota de voz"
        attachmentName != null -> attachmentName
        conv.lastMessage.isNotBlank() -> conv.lastMessage
        conv.isGroup -> "${conv.participantIds.size} miembros"
        conv.isSelfChat(currentUserId) -> "Escríbete a ti mismo"
        else -> ""
    }

    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClickLabel = "Acciones del chat",
                    onLongClick = { showMenu = true })
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChatDirectoryAvatar(conv, currentUserId, 54.dp, online = isOnline)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(conv.chatTitle(currentUserId), Modifier.weight(1f, fill = false), color = ChatText,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (conv.isMuted) Icon(Icons.Outlined.NotificationsOff, "Chat silenciado", Modifier.size(14.dp), tint = ChatMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (attachmentName != null) {
                        Icon(if (voice) Icons.Outlined.Mic else Icons.Outlined.AttachFile, null, Modifier.size(16.dp), tint = ChatMuted)
                    }
                    if (preview.isNotBlank()) {
                        Text(preview, color = ChatMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val time = chatDirectoryTime(conv.lastMessageAt)
                if (time.isNotEmpty()) Text(time, color = if (unread) ChatAccent else ChatMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (unread) {
                    Box(Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).background(ChatAccent, CircleShape)
                        .padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                        Text(if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (time.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (!conv.isArchived) {
                DropdownMenuItem(text = { Text(if (conv.isPinned) "Desfijar" else "Fijar") }, onClick = {
                    showMenu = false
                    onTogglePinned()
                })
            }
            DropdownMenuItem(
                text = { Text(if (conv.isArchived) "Desarchivar" else "Archivar") },
                leadingIcon = {
                    Icon(if (conv.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null)
                },
                onClick = { showMenu = false; onToggleArchived() }
            )
        }
    }
}
