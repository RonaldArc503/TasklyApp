package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.UserProfile
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Color palette (local)
private val Indigo600 = Color(0xFF4F46E5)
private val Emerald500 = Color(0xFF10B981)

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

private fun formatTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
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
    var savedSearchListIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedSearchListOffset by rememberSaveable { mutableIntStateOf(0) }
    val showingArchived = (uiState as? ChatListUiState.Success)?.showingArchived == true

    LaunchedEffect(openConversation) {
        val conversationId = openConversation ?: return@LaunchedEffect
        showNewConversation = false
        onNavigateToChat(conversationId)
        viewModel.consumeOpenConversation(conversationId)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Surface(shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showingArchived) "Archivados" else "Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (showingArchived) {
                    IconButton(onClick = { viewModel.showArchived(false) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver a chats")
                    }
                } else {
                    IconButton(onClick = onNavigateToGlobalSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Busqueda global")
                    }
                    IconButton(onClick = { viewModel.showArchived(true) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Ver archivados")
                    }
                    IconButton(onClick = { showNewConversation = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Nueva conversacion",
                            tint = Indigo600
                        )
                    }
                }
            }
        }

        when (val state = uiState) {
            is ChatListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo600)
                }
            }
            is ChatListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ChatListUiState.Success -> {
                val listState = rememberLazyListState()
                LaunchedEffect(state.searchQuery) {
                    if (state.searchQuery.isBlank() && !state.showingArchived && state.conversations.isNotEmpty()) {
                        listState.scrollToItem(
                            savedSearchListIndex.coerceAtMost(state.conversations.lastIndex),
                            savedSearchListOffset
                        )
                    }
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { query ->
                        if (state.searchQuery.isBlank() && query.isNotBlank()) {
                            savedSearchListIndex = listState.firstVisibleItemIndex
                            savedSearchListOffset = listState.firstVisibleItemScrollOffset
                        }
                        viewModel.onConversationSearchQueryChange(query)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(if (state.showingArchived) "Buscar en archivados" else "Buscar conversaciones") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.isSearching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onConversationSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar busqueda")
                            }
                        }
                    }
                )
                if (state.conversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Indigo600.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Indigo600,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = when {
                                    state.searchQuery.isNotBlank() -> "Sin resultados"
                                    state.showingArchived -> "Sin conversaciones archivadas"
                                    else -> "Sin conversaciones"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    state.searchQuery.isNotBlank() -> "Prueba con otro nombre o telefono."
                                    state.showingArchived -> "Los chats que archives apareceran aqui."
                                    else -> "Toca el lapiz arriba para contactar a alguien."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val conversationsById = remember(state.conversations) {
                        state.conversations.associateBy(ChatConversation::id)
                    }
                    val shouldLoadMore by remember(listState, state.conversations.size) {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= state.conversations.lastIndex - 4
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
                        items(state.conversations, key = { it.id }) { conv ->
                            val otherUserId = conv.participantIds.firstOrNull { it != currentUserId }
                            ConversationItem(
                                conv = conv,
                                currentUserId = currentUserId,
                                isOnline = otherUserId != null &&
                                    state.presenceByUserId[otherUserId]?.isOnlineNow() == true,
                                isSelected = false,
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
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Telefono o correo") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar busqueda")
                        }
                    }
                }
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

@Composable
private fun ConversationItem(
    conv: ChatConversation,
    currentUserId: String,
    isOnline: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isGroup = conv.isGroup
    val isSelf = !isGroup && (conv.participantIds.size == 1 ||
            (conv.participantIds.size == 2 && conv.participantIds.all { it == currentUserId }))

    val otherId = if (isSelf) currentUserId
    else conv.participantIds.firstOrNull { it != currentUserId } ?: currentUserId

    val displayName = when {
        isGroup -> conv.name?.takeIf { it.isNotBlank() } ?: "Grupo"
        isSelf -> "Mensajes guardados"
        else -> conv.members[otherId]?.displayName ?: "Usuario"
    }

    val photoUrl = if (isGroup || isSelf) null
    else conv.members[otherId]?.photoURL?.takeIf { it.isNotBlank() }

    val subtitle = when {
        isGroup -> {
            val memberCount = "${conv.members.size} miembros"
            if (conv.lastMessage.isBlank()) memberCount
            else "$memberCount · ${conv.lastMessage}"
        }
        isSelf && conv.lastMessage.isBlank() -> "Escribete a ti mismo"
        else -> conv.lastMessage
    }

    val rowBg = if (isSelected) Indigo600.copy(alpha = 0.08f) else Color.Transparent
    val timeLabel = remember(conv.lastMessageAt) {
        conv.lastMessageAt.takeIf { it > 0 }?.let(::formatTime)
    }
    val initials = remember(displayName) { initialsFromName(displayName) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar (+11% more: 46dp)
        Box(modifier = Modifier.size(46.dp)) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            } else {
                val bgColor = avatarColor(if (isGroup) conv.id else otherId)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isGroup) (conv.name ?: "G").take(1).uppercase()
                        else initials,
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Online dot (bottom-right)
            if (!isSelf && !isGroup && isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .background(Emerald500)
                )
            }
        }

        // Text column
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Acciones de conversacion")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!conv.isArchived) {
                            DropdownMenuItem(
                                text = { Text(if (conv.isPinned) "Desfijar" else "Fijar") },
                                onClick = { showMenu = false; onTogglePinned() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (conv.isArchived) "Desarchivar" else "Archivar") },
                            leadingIcon = {
                                Icon(
                                    if (conv.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null
                                )
                            },
                            onClick = { showMenu = false; onToggleArchived() }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (conv.unreadCount > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conv.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(Indigo600),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
