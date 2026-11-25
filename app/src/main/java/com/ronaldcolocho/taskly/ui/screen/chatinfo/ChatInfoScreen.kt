package com.ronaldcolocho.taskly.ui.screen.chatinfo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatAttachmentRef
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.model.referenceIn
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.domain.util.formatLastSeen
import com.ronaldcolocho.taskly.domain.util.videoThumbnailUrl
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.screen.chat.AttachmentLightbox
import com.ronaldcolocho.taskly.ui.screen.chat.ChatVideoDialog
import com.ronaldcolocho.taskly.ui.screen.chat.AudioCard
import com.ronaldcolocho.taskly.ui.screen.chat.FileCard
import com.ronaldcolocho.taskly.ui.screen.chat.LightboxItem
import com.ronaldcolocho.taskly.ui.util.initialsOf
import com.ronaldcolocho.taskly.util.AttachmentActions
import kotlinx.coroutines.launch

private val ProfileBackground = Color(0xFF14181E)
private val ProfilePanel = Color(0xFF161B23)
private val ProfileCard = Color(0xFF1B212B)
private val ProfileBorder = Color(0xFF2B3444)
private val ProfileText = Color(0xFFF8FAFC)
private val ProfileMuted = Color(0xFFB8C1D0)
private val ProfileAccent = Color(0xFF38D0EE)
private val CardShape = RoundedCornerShape(18.dp)

internal enum class ProfileTab(val label: String, val icon: ImageVector, val emptyText: String) {
    MEDIA("Multimedia", Icons.Outlined.Photo, "No hay fotos ni videos."),
    FILES("Archivos", Icons.AutoMirrored.Outlined.InsertDriveFile, "No hay archivos compartidos."),
    LINKS("Enlaces", Icons.Outlined.Link, "No hay enlaces compartidos."),
    AUDIO("Audios", Icons.Outlined.MusicNote, "No hay audios compartidos.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    onNavigateBack: () -> Unit,
    onChatAction: (String) -> Unit,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    viewModel: ChatInfoViewModel = hiltViewModel(),
    openMedia: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val presence by viewModel.presence.collectAsStateWithLifecycle()
    val peerProfile by viewModel.peerProfile.collectAsStateWithLifecycle()
    val savingFavorite by viewModel.savingFavorite.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val selection by viewModel.selectedAttachments.collectAsStateWithLifecycle()
    val deleting by viewModel.deletingAttachments.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lightboxItems by remember { mutableStateOf<List<LightboxItem>>(emptyList()) }
    var lightboxIndex by remember { mutableIntStateOf(0) }
    var videoItem by remember { mutableStateOf<LightboxItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var confirmSelection by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val cutoff = (uiState as? ChatInfoUiState.Success)?.conversation?.historyCutoff
    val visibleMedia = (uiState as? ChatInfoUiState.Success)?.mediaAttachments

    LaunchedEffect(cutoff) {
        lightboxItems = emptyList()
        videoItem = null
    }
    LaunchedEffect(visibleMedia) {
        if (lightboxItems.any { item -> visibleMedia.orEmpty().none { it.first == item.attachment && it.second == item.message?.id } }) {
            lightboxItems = emptyList()
        }
        if (videoItem?.let { item -> visibleMedia.orEmpty().none { it.first == item.attachment && it.second == item.message?.id } } == true) {
            videoItem = null
        }
    }
    BackHandler(selection.isNotEmpty() || deleting) { viewModel.clearAttachmentSelection() }
    LaunchedEffect(actionError) {
        actionError?.let { snackbar.showSnackbar(it); viewModel.clearActionError() }
    }

    Scaffold(
        containerColor = ProfileBackground,
        contentColor = ProfileText,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (deleting) Text("Eliminando adjuntos…")
                    else if (selection.isNotEmpty()) Text(if (selection.size == 1) "1 seleccionado" else "${selection.size} seleccionados")
                },
                navigationIcon = {
                    IconButton(
                        enabled = !deleting,
                        onClick = if (selection.isEmpty()) onNavigateBack else viewModel::clearAttachmentSelection
                    ) {
                        if (selection.isEmpty() && !deleting) Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        else Icon(Icons.Default.Close, "Salir de la selección")
                    }
                },
                actions = {
                    if (deleting) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(22.dp), color = ProfileAccent, strokeWidth = 2.dp)
                    } else if (selection.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { selectionMenuOpen = true }) {
                                Icon(Icons.Default.Delete, "Eliminar adjuntos seleccionados")
                            }
                            DropdownMenu(expanded = selectionMenuOpen, onDismissRequest = { selectionMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Eliminar para mí") }, onClick = {
                                    selectionMenuOpen = false
                                    confirmSelection = false
                                })
                                DropdownMenuItem(text = { Text("Eliminar para todos") }, onClick = {
                                    selectionMenuOpen = false
                                    confirmSelection = true
                                })
                            }
                        }
                    } else if (uiState is ChatInfoUiState.Success) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, "Opciones del chat")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Vaciar para mí") }, onClick = {
                                    menuOpen = false
                                    onChatAction("deleteForMe")
                                })
                                DropdownMenuItem(text = { Text("Vaciar para todos") }, onClick = {
                                    menuOpen = false
                                    onChatAction("deleteForEveryone")
                                })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ProfileBackground,
                    navigationIconContentColor = ProfileText,
                    actionIconContentColor = ProfileText
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val state = uiState) {
                ChatInfoUiState.Loading -> CircularProgressIndicator(color = ProfileAccent)
                is ChatInfoUiState.Error -> Text(state.message, Modifier.padding(24.dp), textAlign = TextAlign.Center)
                is ChatInfoUiState.Success -> ChatInfoContent(
                    state, peerProfile, presence, savingFavorite, audioController, mediaManager, openMedia,
                    onMessage = onNavigateBack,
                    onSearch = { onChatAction("search") },
                    onFavorite = viewModel::toggleFavorite,
                    onLoadMore = viewModel::loadMore,
                    selection = selection,
                    selectionMode = selection.isNotEmpty() || deleting,
                    onToggleAttachment = viewModel::toggleAttachment,
                    onCallUnavailable = { scope.launch { snackbar.showSnackbar("No hay una app de teléfono disponible.") } },
                    onMediaClick = { index ->
                        fun mediaItem(entry: Pair<ChatAttachment, String>): LightboxItem {
                            val (file, messageId) = entry
                            val message = state.mediaMessages[messageId]
                            val author = message?.let {
                                if (it.senderId == state.currentUserId) "Tú"
                                else state.conversation.members[it.senderId]?.displayName?.takeIf(String::isNotBlank)
                            }
                            return LightboxItem(file, message?.text.orEmpty(), author, message)
                        }
                        val entry = state.mediaAttachments[index]
                        val attachment = entry.first
                        if (attachment.kind == AttachmentKind.VIDEO) {
                            videoItem = mediaItem(entry)
                        } else {
                            lightboxIndex = state.mediaAttachments.take(index).count { it.first.kind == AttachmentKind.IMAGE }
                            lightboxItems = state.mediaAttachments.filter { it.first.kind == AttachmentKind.IMAGE }.map(::mediaItem)
                        }
                    }
                )
            }
        }
    }

    confirmSelection?.let { forEveryone ->
        if (selection.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { confirmSelection = null },
                title = { Text(if (forEveryone) "¿Eliminar para todos?" else "¿Eliminar para mí?") },
                text = {
                    val target = if (forEveryone) "para todos los participantes" else "de tu vista en este dispositivo"
                    Text("Se eliminarán los adjuntos seleccionados $target.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmSelection = null
                        viewModel.deleteSelectedAttachments(forEveryone)
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmSelection = null }) { Text("Cancelar") } }
            )
        }
    }
    LaunchedEffect(selection.isEmpty()) {
        if (selection.isEmpty()) {
            confirmSelection = null
            selectionMenuOpen = false
        }
    }

    if (lightboxItems.isNotEmpty()) {
        AttachmentLightbox(
            items = lightboxItems,
            initialIndex = lightboxIndex,
            mediaManager = mediaManager,
            onClose = { lightboxItems = emptyList() }
        )
    }
    videoItem?.let { ChatVideoDialog(it, mediaManager, onClose = { videoItem = null }) }
}

@Composable
private fun ChatInfoContent(
    state: ChatInfoUiState.Success,
    peerProfile: UserProfile?,
    presence: Presence?,
    savingFavorite: Boolean,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    openMedia: Boolean,
    onMessage: () -> Unit,
    onSearch: () -> Unit,
    onFavorite: () -> Unit,
    onLoadMore: () -> Unit,
    selection: Set<ChatAttachmentRef>,
    selectionMode: Boolean,
    onToggleAttachment: (ChatAttachmentRef) -> Unit,
    onCallUnavailable: () -> Unit,
    onMediaClick: (Int) -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ProfileTab.MEDIA) }
    var showAll by rememberSaveable { mutableStateOf(openMedia) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (openMedia) 1 else 0)
    val conversation = state.conversation
    val peerId = conversation.participantIds.firstOrNull { it != state.currentUserId }
    val member = if (conversation.isGroup) null else conversation.members[peerId]
    val title = when {
        conversation.isGroup -> conversation.name ?: "Grupo"
        peerId == null -> "Mensajes guardados"
        else -> peerProfile?.displayName?.takeIf(String::isNotBlank) ?: member?.displayName ?: "Usuario"
    }
    val photo = peerProfile?.photoURL?.takeIf(String::isNotBlank) ?: member?.photoURL
    val phone = peerProfile?.phone?.takeIf(String::isNotBlank) ?: member?.phone.orEmpty()
    val online = presence?.isOnlineNow() == true
    val subtitle = when {
        conversation.isGroup -> "${conversation.participantIds.size} miembros"
        peerId == null -> "Escríbete a ti mismo"
        online -> "En línea"
        presence != null -> formatLastSeen(presence.lastSeen)
        else -> "Desconectado"
    }
    val tabCount = when (tab) {
        ProfileTab.MEDIA -> state.mediaAttachments.size
        ProfileTab.FILES -> state.files.size
        ProfileTab.LINKS -> state.links.size
        ProfileTab.AUDIO -> state.audioAttachments.size
    }
    val visibleCount = if (showAll) tabCount else tabCount.coerceAtMost(9)

    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(106.dp)
                        .border(3.dp, Brush.linearGradient(listOf(ProfileAccent, Color(0xFF7958FF))), CircleShape)
                        .padding(6.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF9C6039), Color(0xFF493024)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initialsOf(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (!photo.isNullOrBlank()) {
                        AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (online) Box(Modifier.size(8.dp).background(Color(0xFF47DC8A), CircleShape))
                    Text(subtitle, color = ProfileMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileAction("Mensaje", Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f), enabled = !selectionMode, onClick = onMessage)
                    if (!conversation.isGroup && peerId != null) {
                        ProfileAction("Llamar", Icons.Outlined.Call, Modifier.weight(1f), enabled = !selectionMode && phone.isNotBlank()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
                            } catch (_: ActivityNotFoundException) {
                                onCallUnavailable()
                            }
                        }
                    }
                    ProfileAction("Buscar", Icons.Outlined.Search, Modifier.weight(1f), enabled = !selectionMode, onClick = onSearch)
                    ProfileAction(
                        "Favorito", if (conversation.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        Modifier.weight(1f), enabled = !selectionMode && !savingFavorite, onClick = onFavorite
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ProfileTabs(tab) { item ->
                    if (tab != item) {
                        tab = item
                        showAll = false
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(ProfilePanel).padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (showAll) tab.label else "Recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (showAll || tabCount > 9 || state.hasMore && tabCount > 0) {
                    TextButton(onClick = { showAll = !showAll }, colors = ButtonDefaults.textButtonColors(contentColor = ProfileAccent)) {
                        Text(if (showAll) "Ver recientes" else "Ver todo")
                    }
                } else Spacer(Modifier.height(48.dp))
            }
        }
        when (tab) {
            ProfileTab.MEDIA -> items((0 until visibleCount).chunked(3)) { row ->
                Row(
                    Modifier.fillMaxWidth().background(ProfilePanel).padding(horizontal = 16.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { index ->
                        val (attachment, messageId) = state.mediaAttachments[index]
                        val ref = attachment.referenceIn(messageId)
                        ProfileAttachmentCard(
                            isSelected = ref in selection,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            onOpen = { if (selectionMode) onToggleAttachment(ref) else onMediaClick(index) },
                            onSelect = { onToggleAttachment(ref) }
                        ) { MediaTile(attachment) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            ProfileTab.FILES -> items(state.files.take(visibleCount)) { (attachment, messageId) ->
                val ref = attachment.referenceIn(messageId)
                val select = { onToggleAttachment(ref) }
                Box(sharedRow()) {
                    ProfileAttachmentCard(ref in selection, onSelect = select, onOpen = { if (selectionMode) select() }) {
                        FileCard(
                            att = attachment, isMe = false, mediaManager = mediaManager,
                            onOpenFile = { att, file -> AttachmentActions.openLocalFile(context, file, att.mimeType) },
                            subtitle = attachment.mimeType, actionLabel = "Abrir",
                            onSelectionClick = select.takeIf { selectionMode },
                            onSelectionLongClick = select
                        )
                    }
                }
            }
            ProfileTab.LINKS -> items(state.links.take(visibleCount)) { link ->
                Box(sharedRow()) {
                    Row(
                        Modifier.fillMaxWidth().clip(CardShape).background(ProfileCard)
                            .clickable(enabled = !selectionMode) { AttachmentActions.openExternal(context, link.url) }.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Link, null, tint = ProfileAccent)
                        Text(link.url, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            ProfileTab.AUDIO -> items(state.audioAttachments.take(visibleCount)) { (attachment, messageId) ->
                val ref = attachment.referenceIn(messageId)
                val select = { onToggleAttachment(ref) }
                Box(sharedRow()) {
                    ProfileAttachmentCard(ref in selection, onSelect = select, onOpen = { if (selectionMode) select() }) {
                        AudioCard(attachment, false, audioController, mediaManager,
                            onSelectionClick = select.takeIf { selectionMode },
                            onSelectionLongClick = select
                        )
                    }
                }
            }
        }
        if (tabCount == 0 || state.hasMore && (showAll || visibleCount == tabCount) || state.isLoadingMore) {
            item {
                Column(sharedRow(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tabCount == 0) Text(tab.emptyText, Modifier.padding(16.dp), color = ProfileMuted)
                    if (state.isLoadingMore) CircularProgressIndicator(Modifier.size(28.dp), color = ProfileAccent)
                    else if (state.hasMore) {
                        TextButton(onClick = onLoadMore, colors = ButtonDefaults.textButtonColors(contentColor = ProfileAccent)) {
                            Text("Cargar más")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileTabs(selectedTab: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gap = 7.dp
    val horizontalPadding = 6.dp
    val minimumWidths = ProfileTab.entries.associateWith { tab ->
        val labelWidth = textMeasurer.measure(tab.label, style = labelStyle, maxLines = 1).size.width
        with(density) { labelWidth.toDp() } + horizontalPadding * 2 + 1.dp
    }

    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val availableWidth = maxWidth
        fun minimumRowWidth(tabs: List<ProfileTab>) = tabs.fold(gap * (tabs.size - 1)) { width, tab ->
            width + minimumWidths.getValue(tab)
        }
        val tabs = ProfileTab.entries
        val pairs = tabs.chunked(2)
        val rows = when {
            minimumRowWidth(tabs) <= availableWidth -> listOf(tabs)
            pairs.all { minimumRowWidth(it) <= availableWidth } -> pairs
            else -> tabs.chunked(1)
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                val equalWidth = (availableWidth - gap * (row.size - 1)) / row.size
                val useEqualWidths = row.size == 1 || row.all { minimumWidths.getValue(it) <= equalWidth }
                val extraWidth = (availableWidth - minimumRowWidth(row)) / row.size
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tab ->
                        val selected = tab == selectedTab
                        val tint = if (selected) ProfileBackground else ProfileMuted
                        val weight = if (useEqualWidths) 1f else (minimumWidths.getValue(tab) + extraWidth).value
                        Column(
                            Modifier.weight(weight).heightIn(min = 64.dp)
                                .clip(CardShape)
                                .background(if (selected) ProfileAccent else ProfileCard)
                                .border(1.dp, if (selected) ProfileAccent else ProfileBorder, CardShape)
                                .selectable(selected, role = Role.Tab) { onSelect(tab) }
                                .padding(vertical = 10.dp, horizontal = horizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                        ) {
                            Icon(tab.icon, null, Modifier.size(21.dp), tint)
                            Text(tab.label, color = tint, style = labelStyle,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun sharedRow(): Modifier =
    Modifier.fillMaxWidth().background(ProfilePanel).padding(horizontal = 16.dp).padding(bottom = 8.dp)

@Composable
private fun ProfileAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(CardShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(52.dp).background(ProfileCard, CardShape).border(1.dp, ProfileBorder, CardShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (enabled) ProfileAccent else ProfileMuted.copy(alpha = .45f))
        }
        Text(label, color = if (enabled) ProfileMuted else ProfileMuted.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MediaTile(attachment: ChatAttachment) {
    val isVideo = attachment.kind == AttachmentKind.VIDEO
    val thumbnail = remember(attachment.url, isVideo) {
        if (isVideo) videoThumbnailUrl(attachment.url) else attachment.url
    }
    Box(
        Modifier.fillMaxSize().background(ProfileCard),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (isVideo) Icons.Default.PlayArrow else Icons.Outlined.Photo, null, tint = ProfileMuted)
        AsyncImage(thumbnail, attachment.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = .72f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
                attachment.duration?.takeIf { it > 0 }?.let {
                    Text(formatDuration(it * 1000L), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileAttachmentCard(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape)
            .semantics { selected = isSelected }
            .combinedClickable(onLongClickLabel = "Seleccionar adjunto", onLongClick = onSelect, onClick = onOpen)
    ) {
        content()
        if (isSelected) {
            Box(Modifier.matchParentSize().border(2.dp, ProfileAccent, shape))
            Icon(Icons.Default.Check, null, Modifier.align(Alignment.TopStart).padding(5.dp)
                .size(22.dp).background(ProfileAccent, CircleShape).padding(3.dp), tint = ProfileBackground)
        }
    }
}
