package com.ronaldcolocho.taskly.ui.screen.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatConversation
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date
import java.io.File
import java.util.UUID

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
    onNavigateBack: () -> Unit,
    onNavigateToInfo: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val automaticDownloadsEnabled by viewModel.automaticDownloadsEnabled.collectAsStateWithLifecycle()
    val playlistAddState by viewModel.playlistAddState.collectAsStateWithLifecycle()
    val messageNavigation by viewModel.messageNavigation.collectAsStateWithLifecycle()
    val savedFocusMessageId by viewModel.savedFocusMessageId.collectAsStateWithLifecycle()
    val messageSearch by viewModel.messageSearch.collectAsStateWithLifecycle()
    val voiceSendState by viewModel.voiceNoteSendState.collectAsStateWithLifecycle()
    val audioController = rememberAudioPlayerController()
    val mediaManager = rememberMediaDownloadManager()

    var inputValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToDeleteForEveryone by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToDeleteForMe by remember { mutableStateOf<ChatMessage?>(null) }
    var lightboxItems by remember { mutableStateOf<List<LightboxItem>?>(null) }
    var lightboxIndex by remember { mutableStateOf(0) }
    var pinnedIndex by remember { mutableStateOf(0) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    var showMessageSearch by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showAttachmentSheet by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun startVoiceRecording() {
        voiceRecorder.start().onSuccess {
            recordingSeconds = 0
            isRecordingVoice = true
        }.onFailure { viewModel.reportVoiceError(it.message ?: "No se pudo iniciar la grabacion.") }
    }

    val microphonePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecording()
        else viewModel.reportVoiceError("Se necesita permiso de microfono para grabar notas de voz.")
    }

    DisposableEffect(voiceRecorder) {
        onDispose { voiceRecorder.cancel() }
    }
    LaunchedEffect(isRecordingVoice) {
        while (isRecordingVoice) {
            kotlinx.coroutines.delay(1000)
            recordingSeconds++
            if (recordingSeconds >= 600) {
                voiceRecorder.stop()
                    .onSuccess { viewModel.sendVoiceNote(it.file, it.durationSeconds) }
                    .onFailure { viewModel.reportVoiceError("No se pudo guardar la nota de voz.") }
                isRecordingVoice = false
            }
        }
    }

    val mediaPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addDrafts(uris)
    }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { captured ->
        pendingCameraUri?.let { uri ->
            if (captured) viewModel.addDrafts(listOf(uri))
        }
        pendingCameraUri = null
    }
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCameraUri?.let(cameraLauncher::launch)
        else viewModel.reportVoiceError("Se necesita permiso de camara para tomar una foto.")
    }

    fun openCamera() {
        pendingCameraUri = createChatCameraUri(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(pendingCameraUri!!)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistAddState) {
        if (playlistAddState is PlaylistAddState.Success) {
            selectedMessageForActions = null
            viewModel.clearPlaylistAddState()
        }
    }

    fun jumpToMessage(msgId: String) {
        viewModel.navigateToMessage(msgId)
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
            LaunchedEffect(state.messages) {
                viewModel.indexAvailableChatAudio(state.messages)
            }
            LaunchedEffect(messageNavigation?.requestId, state.messages) {
                val request = messageNavigation ?: return@LaunchedEffect
                val index = state.messages.indexOfFirst { it.id == request.messageId }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                    // Place the target near the viewport center after its measured item is available.
                    kotlinx.coroutines.yield()
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { item ->
                        val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                        listState.animateScrollBy((item.offset + item.size / 2 - viewportCenter).toFloat())
                    }
                    if (!request.keepSavedFocus) highlightedId = request.messageId
                    viewModel.consumeMessageNavigation(request.requestId)
                    if (!request.keepSavedFocus) {
                        kotlinx.coroutines.delay(2500)
                        if (highlightedId == request.messageId) highlightedId = null
                    }
                }
            }
            val messagesById = remember(state.messages) { state.messages.associateBy { it.id } }
            LaunchedEffect(listState, messagesById, automaticDownloadsEnabled) {
                if (!automaticDownloadsEnabled) {
                    viewModel.updateAutomaticDownloads(emptyList())
                }
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { it.key as? String }
                        .toSet()
                }.distinctUntilChanged().collect { visibleIds ->
                    if (automaticDownloadsEnabled) {
                        val attachments = visibleIds.flatMap { messagesById[it]?.attachments.orEmpty() }
                        viewModel.updateAutomaticDownloads(attachments)
                    }
                    viewModel.loadNewerHistoryWhenVisible(visibleIds)
                }
            }
            LaunchedEffect(listState, messagesById) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
                }.distinctUntilChanged().collect(viewModel::markVisibleMessages)
            }
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

            val pinnedList = state.conversation.pinnedMessages
            val pinnedIds = remember(pinnedList) { pinnedList.map { it.id }.toSet() }
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

            if (showAttachmentSheet) {
                ChatAttachmentSheet(
                    onDismiss = { showAttachmentSheet = false },
                    onCamera = {
                        showAttachmentSheet = false
                        openCamera()
                    },
                    onGallery = {
                        showAttachmentSheet = false
                        mediaPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                )
            }

            selectedMessageForActions?.let { msg ->
                val musicPlaylists by viewModel.musicPlaylists.collectAsStateWithLifecycle()
                MessageActionsSheet(
                    message = msg,
                    currentUserId = state.currentUserId,
                    isPinned = pinnedIds.contains(msg.id),
                    onDismiss = {
                        selectedMessageForActions = null
                        viewModel.clearPlaylistAddState()
                    },
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
                    playlists = musicPlaylists,
                    isAddingToPlaylist = playlistAddState is PlaylistAddState.Saving,
                    playlistAddError = (playlistAddState as? PlaylistAddState.Error)?.message,
                    onAddAudioToPlaylist = { playlistId -> viewModel.addAudioMessageToPlaylist(msg, playlistId) },
                    onEdit = { viewModel.startEditing(msg) },
                    onDelete = { messageToDeleteForEveryone = msg },
                    onDeleteForMe = { messageToDeleteForMe = msg }
                )
            }

            messageToDeleteForEveryone?.let { msg ->
                AlertDialog(
                    onDismissRequest = { messageToDeleteForEveryone = null },
                    title = { Text("¿Eliminar para todos?") },
                    text = { Text("Este mensaje se eliminará para todos los miembros de este chat.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessage(msg)
                                messageToDeleteForEveryone = null
                            }
                        ) {
                            Text("Eliminar para todos", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { messageToDeleteForEveryone = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            messageToDeleteForMe?.let { msg ->
                AlertDialog(
                    onDismissRequest = { messageToDeleteForMe = null },
                    title = { Text("¿Eliminar para mí?") },
                    text = { Text("Este mensaje solo se eliminará para ti en este dispositivo.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForMe(msg)
                                messageToDeleteForMe = null
                            }
                        ) {
                            Text("Eliminar para mí", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { messageToDeleteForMe = null }) {
                            Text("Cancelar")
                        }
                    }
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
                // HEADER (colecciona typing/presencia por separado: solo el header recompone)
                ChatHeader(
                    conversation = state.conversation,
                    currentUserId = state.currentUserId,
                    isGroup = isGroup,
                    isSelf = isSelf,
                    peerId = peerId,
                    peerName = peerName,
                    peerPhoto = peerPhoto,
                    peerPhone = state.otherParticipant?.phone,
                    onNavigateBack = onNavigateBack,
                    onNavigateToInfo = { onNavigateToInfo(state.conversation.id) },
                    onSearch = { showMessageSearch = true },
                    isMessageSearchActive = showMessageSearch,
                    messageSearch = messageSearch,
                    onMessageSearchQueryChange = viewModel::onMessageSearchQueryChange,
                    onPreviousSearchResult = { viewModel.moveMessageSearchResult(-1) },
                    onNextSearchResult = { viewModel.moveMessageSearchResult(1) },
                    onCloseMessageSearch = {
                        showMessageSearch = false
                        viewModel.clearMessageSearch()
                    },
                    ephemeralState = viewModel.ephemeralState
                )

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

                state.unreadBoundaryMessageId?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::jumpToUnreadBoundary),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            "Mensajes no leidos - tocar para ir al primero",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // MESSAGES
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().chatBackground()) {
                    val maxBubbleWidth = (maxWidth - 24.dp) * 0.8f
                    // Pre-computar lista de imágenes para el lightbox una sola vez (no en cada burbuja)
                    val allLightboxItems = remember(state.messages) {
                        state.messages.flatMap { m ->
                            m.attachments.filter { it.kind == AttachmentKind.IMAGE }
                                .map { LightboxItem(it, m.text) }
                        }.reversed()
                    }
                    
                    val handleImageClick = remember(allLightboxItems) {
                        { att: ChatAttachment ->
                            val idx = allLightboxItems.indexOfFirst { it.attachment.publicId == att.publicId }
                            lightboxIndex = if (idx >= 0) idx else 0
                            lightboxItems = allLightboxItems
                        }
                    }
                    
                    val handleLongPress: (ChatMessage) -> Unit = remember { { msg -> selectedMessageForActions = msg } }
                    val handleRetry: (ChatMessage) -> Unit = remember(viewModel) { { msg -> viewModel.retryMessage(msg) } }
                    val handleReact: (ChatMessage, String) -> Unit = remember(viewModel) { { msg, emoji -> viewModel.reactToMessage(msg, emoji) } }
                    val handleOpenFile: (ChatAttachment, File) -> Unit = remember(context) { { att, file -> AttachmentActions.openLocalFile(context, file, att.mimeType) } }
                    val handleReplyClick: (String) -> Unit = remember { { msgId -> jumpToMessage(msgId) } }
                    val handleLinkClick: (String) -> Unit = remember(context) { { url -> AttachmentActions.openExternal(context, url) } }

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
                                    members = com.ronaldcolocho.taskly.domain.model.MemberDirectory(state.conversation.members),
                                    maxBubbleWidth = maxBubbleWidth,
                                    showAuthor = isGroup && message.senderId != state.currentUserId,
                                    authorName = state.conversation.members[message.senderId]?.displayName,
                                    isPinned = message.id in pinnedIds,
                                    highlighted = highlightedId == message.id,
                                    savedFocused = savedFocusMessageId == message.id,
                                    audioController = audioController,
                                    mediaManager = mediaManager,
                                    onLongPress = handleLongPress,
                                    onTap = { tapped -> viewModel.clearSavedFocus(tapped.id) },
                                    onRetry = handleRetry,
                                    onReact = handleReact,
                                    onOpenFile = handleOpenFile,
                                    onImageClick = handleImageClick,
                                    onReplyClick = handleReplyClick,
                                    onLinkClick = handleLinkClick
                                )

                                if (message.id == state.unreadBoundaryMessageId) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(shape = RoundedCornerShape(14.dp), color = Indigo600) {
                                            Text(
                                                "Mensajes no leidos",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

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

                        item(key = "typing") {
                            TypingIndicatorItem(
                                ephemeralState = viewModel.ephemeralState,
                                isGroup = isGroup,
                                peerId = peerId,
                                currentUserId = state.currentUserId
                            )
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
                val hasComposedContent = inputValue.text.isNotBlank() || drafts.isNotEmpty()
                val canSend = hasComposedContent || isRecordingVoice
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
                        onClick = {
                            if (isRecordingVoice) {
                                voiceRecorder.cancel()
                                isRecordingVoice = false
                                recordingSeconds = 0
                            } else showAttachmentSheet = true
                        },
                        enabled = state.editing == null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isRecordingVoice) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (isRecordingVoice) "Cancelar grabacion" else "Adjuntar",
                            tint = Slate500
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBg)
                            .border(1.dp, inputBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (isRecordingVoice) {
                            Text(
                                "Grabando ${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}",
                                color = Red600,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else BasicTextField(
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
                            if (isRecordingVoice) {
                                voiceRecorder.stop()
                                    .onSuccess { viewModel.sendVoiceNote(it.file, it.durationSeconds) }
                                    .onFailure { viewModel.reportVoiceError("La nota de voz fue demasiado corta o no pudo guardarse.") }
                                isRecordingVoice = false
                                recordingSeconds = 0
                            } else if (hasComposedContent) {
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
                            } else if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startVoiceRecording()
                            } else {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = (!voiceSendState.isUploading && state.editing == null) || hasComposedContent,
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
                            imageVector = when {
                                isRecordingVoice -> Icons.Filled.Stop
                                isEditing -> Icons.Filled.Check
                                hasComposedContent -> Icons.AutoMirrored.Filled.Send
                                else -> Icons.Filled.Mic
                            },
                            contentDescription = when {
                                isRecordingVoice -> "Detener y enviar nota de voz"
                                isEditing -> "Guardar edicion"
                                hasComposedContent -> "Enviar mensaje"
                                else -> "Grabar nota de voz"
                            },
                            tint = if (canSend) Color.White else Slate500,
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

private fun createChatCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "chat_camera").apply { mkdirs() }
    val image = File(directory, "chat_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
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

@Composable
private fun ChatHeader(
    conversation: ChatConversation,
    currentUserId: String,
    isGroup: Boolean,
    isSelf: Boolean,
    peerId: String?,
    peerName: String,
    peerPhoto: String?,
    peerPhone: String?,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onSearch: () -> Unit,
    isMessageSearchActive: Boolean,
    messageSearch: MessageSearchUiState,
    onMessageSearchQueryChange: (String) -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onCloseMessageSearch: () -> Unit,
    ephemeralState: StateFlow<ChatEphemeralState>
) {
    val ephemeral by ephemeralState.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val typingMap = ephemeral.typingMap
    val presence = ephemeral.peerPresence

    val peerTyping = peerId != null && !isSelf && (typingMap[peerId]?.let { now - it < 3500 } == true)
    val typingUids = typingMap.entries
        .filter { it.key != currentUserId && now - it.value < 3500 }
        .map { it.key }
    val groupTypingText = if (isGroup && typingUids.isNotEmpty()) {
        val names = typingUids.take(2).map { conversation.members[it]?.displayName ?: "Alguien" }
        when (typingUids.size) {
            1 -> "${names[0]} está escribiendo…"
            2 -> "${names[0]} y ${names[1]} están escribiendo…"
            else -> "${names[0]} y ${typingUids.size - 1} más están escribiendo…"
        }
    } else null

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        if (isMessageSearchActive) {
            MessageSearchHeader(
                state = messageSearch,
                onQueryChange = onMessageSearchQueryChange,
                onPrevious = onPreviousSearchResult,
                onNext = onNextSearchResult,
                onClose = onCloseMessageSearch
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToInfo)
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
                    val bg = avatarColor(peerId ?: currentUserId)
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initialsOf(if (isGroup) conversation.name ?: "G" else peerName), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!isSelf && !isGroup && presence?.isOnlineNow() == true) {
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
                    else if (isGroup) (conversation.name ?: "Grupo")
                    else peerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isGroup -> groupTypingText ?: "${conversation.members.size} miembros"
                        isSelf -> "Escribirte a ti mismo"
                        peerTyping -> "escribiendo…"
                        presence?.isOnlineNow() == true -> "En línea"
                        presence != null -> formatLastSeen(presence.lastSeen)
                        else -> "Desconectado"
                    },
                    fontSize = 12.sp,
                    color = when {
                        isGroup -> Slate500
                        isSelf -> Slate500
                        peerTyping || presence?.isOnlineNow() == true -> Indigo600
                        else -> Slate500
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar mensajes", tint = Slate500)
                }
            }
        }
    }
}

@Composable
private fun MessageSearchHeader(
    state: MessageSearchUiState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar busqueda", tint = Slate500)
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
            singleLine = true,
            placeholder = { Text("Buscar mensajes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        if (state.query.isNotBlank()) {
            if (state.isSearching) {
                CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = onPrevious, enabled = state.results.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Resultado anterior")
            }
            IconButton(onClick = onNext, enabled = state.results.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Resultado siguiente")
            }
        }
    }
}

@Composable
private fun TypingIndicatorItem(
    ephemeralState: StateFlow<ChatEphemeralState>,
    isGroup: Boolean,
    peerId: String?,
    currentUserId: String
) {
    val ephemeral by ephemeralState.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val typingMap = ephemeral.typingMap
    val peerTyping = peerId != null && !isGroup && (typingMap[peerId]?.let { now - it < 3500 } == true)

    if (!peerTyping) return

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
