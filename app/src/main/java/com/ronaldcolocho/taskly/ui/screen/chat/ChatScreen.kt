package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.util.extractMentions
import com.ronaldcolocho.taskly.domain.util.formatDayLabel
import com.ronaldcolocho.taskly.domain.util.formatLastSeen
import com.ronaldcolocho.taskly.domain.util.sameDay
import com.ronaldcolocho.taskly.ui.state.ChatUiState
import com.ronaldcolocho.taskly.ui.util.avatarColor
import com.ronaldcolocho.taskly.ui.util.chatBackground
import com.ronaldcolocho.taskly.ui.util.initialsOf
import com.ronaldcolocho.taskly.util.AttachmentActions
import kotlinx.coroutines.launch
import java.util.Date

private val Indigo600 = Color(0xFF4F46E5)
private val Indigo100 = Color(0xFF312E81)
private val Slate50 = Color(0xFF0F172A)
private val Slate200 = Color(0xFF334155)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF94A3B8)
private val Slate800 = Color(0xFFF1F5F9)
private val Emerald500 = Color(0xFF10B981)
private val Amber400 = Color(0xFFFBBF24)
private val Red50 = Color(0xFF450A0A)
private val Red600 = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val audioController = rememberAudioPlayerController()
    val mediaManager = rememberMediaDownloadManager()

    var inputValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var lightboxItems by remember { mutableStateOf<List<LightboxItem>?>(null) }
    var lightboxIndex by remember { mutableStateOf(0) }
    var pinnedIndex by remember { mutableStateOf(0) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addDrafts(uris)
    }

    val scope = rememberCoroutineScope()

    fun jumpToMessage(msgId: String) {
        val state = uiState as? ChatUiState.Success ?: return
        val index = state.messages.indexOfFirst { it.id == msgId }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index) }
            highlightedId = msgId
            scope.launch {
                kotlinx.coroutines.delay(2500)
                highlightedId = null
            }
        }
    }

    when (val state = uiState) {
        is ChatUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ChatUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ChatUiState.Success -> {
            val isGroup = state.conversation.isGroup
            val peerId = state.conversation.participantIds.firstOrNull { it != state.currentUserId }
            val peerName = state.otherParticipant?.displayName
                ?: state.conversation.members[peerId]?.displayName
                ?: state.conversation.name
                ?: "Chat"
            val peerPhoto = state.otherParticipant?.photoURL
                ?: state.conversation.members[peerId]?.photoURL
            val isSelf = !isGroup && (state.conversation.participantIds.size == 1 ||
                    (state.conversation.participantIds.size == 2 && state.conversation.participantIds.all { it == state.currentUserId }))

            val now = System.currentTimeMillis()
            val peerTyping = peerId != null && !isSelf &&
                    (state.typingMap[peerId]?.let { now - it < 3500 } == true)
            val typingUids = state.typingMap.entries
                .filter { it.key != state.currentUserId && now - it.value < 3500 }
                .map { it.key }
            val groupTypingText = if (isGroup && typingUids.isNotEmpty()) {
                val names = typingUids.take(2).map { state.conversation.members[it]?.displayName ?: "Alguien" }
                when (typingUids.size) {
                    1 -> "${names[0]} está escribiendo…"
                    2 -> "${names[0]} y ${names[1]} están escribiendo…"
                    else -> "${names[0]} y ${typingUids.size - 1} más están escribiendo…"
                }
            } else null

            val pinnedList = state.conversation.pinnedMessages
            val safePinnedIndex = if (pinnedList.isEmpty()) 0 else pinnedIndex.coerceIn(0, pinnedList.size - 1)

            val isAtTop by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    lastVisible != null && lastVisible.index >= listState.layoutInfo.totalItemsCount - 5
                }
            }

            val isAtBottom by remember {
                derivedStateOf { listState.firstVisibleItemIndex <= 1 }
            }

            LaunchedEffect(isAtTop) {
                if (isAtTop && !state.isFetchingOlder) viewModel.loadMore()
            }

            val firstMessageId = state.messages.firstOrNull()?.id
            LaunchedEffect(firstMessageId) {
                if (firstMessageId != null) {
                    val msg = state.messages.first()
                    if (isAtBottom || msg.senderId == state.currentUserId) {
                        try {
                            listState.animateScrollToItem(0)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }

            // Playlist de audio global (orden cronológico: más antiguo → más reciente)
            LaunchedEffect(state.messages) {
                val tracks = state.messages.sortedBy { it.createdAt }.flatMap { m ->
                    m.attachments.filter { it.kind == AttachmentKind.AUDIO }.map {
                        AudioTrack(it.publicId, it.url, it.name, m.id)
                    }
                }
                audioController.updatePlaylist(tracks)
            }

            val text = inputValue.text
            val cursor = inputValue.selection.start
            val before = text.take(cursor)
            val atIndex = before.lastIndexOf('@')
            val mentionQuery = if (atIndex != -1 && !before.substring(atIndex).contains('\n')) before.substring(atIndex + 1) else null
            val mentionCandidates = if (mentionQuery != null && isGroup) {
                state.conversation.members.values
                    .filter { it.displayName.isNotBlank() }
                    .map { it.displayName }
                    .filter { it.lowercase().contains(mentionQuery.lowercase()) }
            } else emptyList()
            val mentionOpen = mentionCandidates.isNotEmpty()

            lightboxItems?.let { items ->
                AttachmentLightbox(items = items, initialIndex = lightboxIndex, mediaManager = mediaManager, onClose = { lightboxItems = null })
            }

            selectedMessageForActions?.let { msg ->
                MessageActionsSheet(
                    message = msg,
                    currentUserId = state.currentUserId,
                    isPinned = pinnedList.any { it.id == msg.id },
                    onDismiss = { selectedMessageForActions = null },
                    onReact = { emoji -> viewModel.reactToMessage(msg, emoji) },
                    onReply = { viewModel.setReplyTo(msg) },
                    onForward = { forwardMessage = msg },
                    onSave = { viewModel.saveMessage(msg) },
                    onPin = { viewModel.togglePin(msg) },
                    onCopy = {
                        if (msg.text.isNotBlank()) {
                            AttachmentActions.copyText(context, msg.text)
                        } else {
                            msg.attachments.firstOrNull()?.url?.let { AttachmentActions.copyText(context, it) }
                        }
                    },
                    onDownload = {
                        msg.attachments.forEach { AttachmentActions.download(context, it.url, it.name) }
                    },
                    onEdit = { viewModel.startEditing(msg) },
                    onDelete = { viewModel.deleteMessage(msg) }
                )
            }

            forwardMessage?.let { msg ->
                ForwardSheet(
                    conversations = conversations,
                    currentUserId = state.currentUserId,
                    currentConvId = state.conversation.id,
                    previewText = msg.text,
                    attachmentCount = msg.attachments.size,
                    onDismiss = { forwardMessage = null },
                    onSend = { targetIds ->
                        viewModel.forwardMessage(msg, targetIds)
                        forwardMessage = null
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // HEADER
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp).clip(CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Slate500)
                        }

                        // Header Avatar (+11% more: 46dp)
                        Box(modifier = Modifier.size(46.dp)) {
                            if (!isGroup && peerPhoto != null && peerPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = peerPhoto,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(46.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            } else {
                                val bg = avatarColor(peerId ?: state.currentUserId)
                                Box(
                                    modifier = Modifier.size(46.dp).clip(CircleShape).background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initialsOf(if (isGroup) state.conversation.name ?: "G" else peerName), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (!isSelf && !isGroup && state.peerPresence?.isOnlineNow() == true) {
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

                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSelf) "Mensajes guardados"
                                else if (isGroup) (state.conversation.name ?: "Grupo")
                                else peerName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isGroup -> groupTypingText ?: "${state.conversation.members.size} miembros"
                                    isSelf -> "Escribirte a ti mismo"
                                    peerTyping -> "escribiendo…"
                                    state.peerPresence?.isOnlineNow() == true -> "En línea"
                                    state.peerPresence != null -> formatLastSeen(state.peerPresence!!.lastSeen)
                                    else -> state.otherParticipant?.phone?.ifBlank { peerId } ?: peerId ?: ""
                                },
                                fontSize = 12.sp,
                                color = when {
                                    isGroup -> Slate500
                                    isSelf -> Slate500
                                    peerTyping || state.peerPresence?.isOnlineNow() == true -> Indigo600
                                    else -> Slate500
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // PINNED BAR
                if (pinnedList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Indigo100.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PushPin, contentDescription = null, tint = Indigo600, modifier = Modifier.size(16.dp))
                        }
                        if (pinnedList.size > 1) {
                            IconButton(onClick = { pinnedIndex = (safePinnedIndex - 1 + pinnedList.size) % pinnedList.size }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Fijado anterior", tint = Indigo600, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { pinnedIndex = (safePinnedIndex + 1) % pinnedList.size }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Fijado siguiente", tint = Indigo600, modifier = Modifier.size(16.dp))
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f).clickable { jumpToMessage(pinnedList[safePinnedIndex].id) }
                        ) {
                            val senderName = if (pinnedList[safePinnedIndex].senderId == state.currentUserId) "Tú"
                            else if (isGroup) state.conversation.members[pinnedList[safePinnedIndex].senderId]?.displayName ?: "Miembro"
                            else peerName
                            Text(
                                text = "Mensaje fijado · $senderName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF818CF8),
                                maxLines = 1
                            )
                            Text(
                                text = pinnedList[safePinnedIndex].text.ifBlank { "Adjunto" },
                                fontSize = 12.sp,
                                color = Color(0xFFC7D2FE).copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (pinnedList.size > 1) {
                            Text(
                                text = "${safePinnedIndex + 1}/${pinnedList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Indigo600,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.unpinMessage(pinnedList[safePinnedIndex].id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Desfijar mensaje", tint = Indigo600, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // MESSAGES
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().chatBackground()) {
                    val maxBubbleWidth = (maxWidth - 24.dp) * 0.8f
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        if (state.isFetchingOlder) {
                            item(key = "loading") {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (state.messages.isEmpty()) {
                            item(key = "empty") {
                                Column(
                                    modifier = Modifier.fillParentMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Esta es tu conversación con $peerName.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate500)
                                    Text("Escribe el primer mensaje.", fontSize = 12.sp, color = Slate400)
                                }
                            }
                        } else {
                            itemsIndexed(
                                state.messages,
                                key = { _, it -> it.id },
                                contentType = { index, _ ->
                                    val next = state.messages.getOrNull(index + 1)
                                    if (next == null || !sameDay(state.messages[index].createdAt, next.createdAt)) {
                                        "day"
                                    } else {
                                        "message"
                                    }
                                }
                            ) { index, message ->
                                val next = state.messages.getOrNull(index + 1)
                                val showDay = next == null || !sameDay(message.createdAt, next.createdAt)

                                MessageBubble(
                                    message = message,
                                    isMe = message.senderId == state.currentUserId,
                                    currentUserId = state.currentUserId,
                                    members = state.conversation.members,
                                    maxBubbleWidth = maxBubbleWidth,
                                    showAuthor = isGroup && message.senderId != state.currentUserId,
                                    authorName = state.conversation.members[message.senderId]?.displayName,
                                    isPinned = pinnedList.any { it.id == message.id },
                                    highlighted = highlightedId == message.id,
                                    audioController = audioController,
                                    mediaManager = mediaManager,
                                    onLongPress = { selectedMessageForActions = message },
                                    onRetry = { viewModel.retryMessage(message) },
                                    onReact = { m, emoji -> viewModel.reactToMessage(m, emoji) },
                                    onOpenFile = { att, file -> AttachmentActions.openLocalFile(context, file, att.mimeType) },
                                    onImageClick = { att ->
                                        val items = state.messages.flatMap { m ->
                                            m.attachments.filter { it.kind == AttachmentKind.IMAGE }
                                                .map { LightboxItem(it, m.text) }
                                        }
                                        // Orden cronológico (más antiguas primero): deslizar a la derecha = fotos más antiguas
                                        val ordered = items.reversed()
                                        val idx = ordered.indexOfFirst { it.attachment.publicId == att.publicId }
                                        lightboxIndex = if (idx >= 0) idx else 0
                                        lightboxItems = ordered
                                    },
                                    onReplyClick = { msgId -> jumpToMessage(msgId) },
                                    onLinkClick = { url -> AttachmentActions.openExternal(context, url) }
                                )

                                if (showDay) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shadowElevation = 1.dp) {
                                            Text(
                                                text = formatDayLabel(message.createdAt),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate500,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (peerTyping && !isGroup) {
                            item(key = "typing") {
                                Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, Slate200, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            listOf(0, 150, 300).forEach { delayMs ->
                                                val alpha by animateTypingDot(delayMs)
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Slate400.copy(alpha = alpha))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isAtBottom && state.messages.isNotEmpty()) {
                        Button(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .height(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Último mensaje", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // REPLY / EDIT BAR
                state.replyTo?.let { reply ->
                    PinnedActionBar(
                        color = Indigo100.copy(alpha = 0.7f),
                        labelColor = Color(0xFFA5B4FC),
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(14.dp)) },
                        title = "Respondiendo a ${if (isGroup) (state.conversation.name ?: "Grupo") else peerName}",
                        preview = reply.text,
                        onClose = { viewModel.clearReplyTo() }
                    )
                }

                state.editing?.let { editing ->
                    PinnedActionBar(
                        color = Amber400.copy(alpha = 0.25f),
                        labelColor = Color(0xFFFCD34D),
                        icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFFFCD34D), modifier = Modifier.size(14.dp)) },
                        title = "Editando mensaje",
                        preview = editing.text,
                        onClose = { viewModel.cancelEditing() }
                    )
                }

                LaunchedEffect(state.editing?.id) {
                    if (state.editing != null) {
                        inputValue = androidx.compose.ui.text.input.TextFieldValue(state.editing!!.text)
                    }
                }

                // DRAFTS
                if (drafts.isNotEmpty() && state.editing == null) {
                    DraftsRow(drafts = drafts, onRemove = { viewModel.removeDraft(it) })
                }

                // MENTION SUGGESTIONS
                if (mentionOpen) {
                    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            mentionCandidates.take(6).forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newText = text.substring(0, atIndex) + "@$name " + text.substring(cursor)
                                            inputValue = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(atIndex + name.length + 2))
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("@$name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                }
                                HorizontalDivider(color = Slate200.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // INPUT
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val inputBg = if (isDark) Color(0xFF1E293B) else Slate50
                val inputBorder = if (isDark) Color(0xFF334155) else Slate200
                val inputTextColor = if (isDark) Color(0xFFF1F5F9) else Slate800
                val inputPlaceholderColor = if (isDark) Color(0xFF94A3B8) else Slate400
                val canSend = inputValue.text.isNotBlank() || drafts.isNotEmpty()
                val isEditing = state.editing != null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = state.editing == null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Adjuntar", tint = Slate500)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBg)
                            .border(1.dp, inputBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = inputValue,
                            onValueChange = { newValue ->
                                inputValue = newValue
                                viewModel.onTextChanged(newValue.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 20.dp, max = 128.dp),
                            textStyle = TextStyle(fontSize = 15.sp, color = inputTextColor),
                            minLines = 1,
                            maxLines = 6,
                            decorationBox = { innerTextField ->
                                if (inputValue.text.isEmpty()) {
                                    Text(
                                        text = if (state.editing != null) "Editar mensaje…" else "Escribe un mensaje…",
                                        color = inputPlaceholderColor,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    // Botón de Enviar (FUERA del contenedor de texto)
                    IconButton(
                        onClick = {
                            if (canSend) {
                                when {
                                    isEditing -> {
                                        viewModel.saveEdit(inputValue.text)
                                        inputValue = androidx.compose.ui.text.input.TextFieldValue("")
                                    }
                                    drafts.isNotEmpty() -> {
                                        viewModel.sendWithDrafts(inputValue.text)
                                        inputValue = androidx.compose.ui.text.input.TextFieldValue("")
                                    }
                                    else -> {
                                        viewModel.sendText(inputValue.text)
                                        inputValue = androidx.compose.ui.text.input.TextFieldValue("")
                                    }
                                }
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Indigo600
                                else if (isDark) Color(0xFF334155)
                                else Color(0xFFE2E8F0)
                            )
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isEditing) "Guardar edición" else "Enviar mensaje",
                            tint = if (canSend) Color.White else if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                state.sendError?.let { error ->
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Red50)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedActionBar(
    color: Color,
    labelColor: Color,
    icon: @Composable () -> Unit,
    title: String,
    preview: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1)
                Text(preview, fontSize = 12.sp, color = labelColor.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = labelColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DraftsRow(
    drafts: List<AttachmentDraft>,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        drafts.forEach { draft ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Slate200, RoundedCornerShape(12.dp))
            ) {
                if (draft.previewUri != null) {
                    AsyncImage(
                        model = draft.previewUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (draft.kind == AttachmentKind.PDF) "PDF"
                            else draft.fileName.substringAfterLast('.', "F").take(3).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (draft.kind == AttachmentKind.PDF) Color(0xFFFCA5A5) else Indigo600
                        )
                    }
                }

                when (draft.status) {
                    DraftStatus.UPLOADING -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Text("${draft.progress}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    DraftStatus.ERROR -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Text("!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    DraftStatus.DONE -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Emerald500),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    DraftStatus.READY -> {}
                }

                IconButton(
                    onClick = { onRemove(draft.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

@Composable
private fun animateTypingDot(delayMs: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "dot$delayMs")
    return transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = delayMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha$delayMs"
    )
}
