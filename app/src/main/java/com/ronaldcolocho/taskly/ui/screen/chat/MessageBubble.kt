package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.ForwardedFrom
import com.ronaldcolocho.taskly.domain.model.MemberSnapshot
import com.ronaldcolocho.taskly.domain.model.MessageStatus
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.model.ReplyInfo
import com.ronaldcolocho.taskly.domain.util.findMentionRanges
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatTime
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.ChatAccent
import com.ronaldcolocho.taskly.ui.theme.ChatAccentContainer
import com.ronaldcolocho.taskly.ui.theme.ChatDivider
import com.ronaldcolocho.taskly.ui.theme.ChatGreen
import com.ronaldcolocho.taskly.ui.theme.ChatMuted
import com.ronaldcolocho.taskly.ui.theme.ChatOutgoing
import com.ronaldcolocho.taskly.ui.theme.ChatOutgoingBorder
import com.ronaldcolocho.taskly.ui.theme.ChatSurfaceRaised
import com.ronaldcolocho.taskly.ui.theme.ChatText
import com.ronaldcolocho.taskly.ui.util.authorTextColor
import com.ronaldcolocho.taskly.ui.util.avatarColor
import com.ronaldcolocho.taskly.ui.util.initialsOf
import java.io.File

import com.ronaldcolocho.taskly.domain.model.MemberDirectory

private val Slate100 = ChatSurfaceRaised
private val Slate200 = ChatDivider
private val Slate400 = ChatMuted
private val Slate500 = ChatMuted
private val Slate800 = ChatText
private val Indigo200 = Color(0xFFB9EBFA)
private val Indigo700 = ChatAccent
private val Indigo900 = ChatAccentContainer
private val Indigo600 = ChatAccent
private val Emerald500 = ChatGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    currentUserId: String,
    members: MemberDirectory,
    maxBubbleWidth: Dp,
    showAuthor: Boolean,
    authorName: String?,
    isPinned: Boolean,
    highlighted: Boolean,
    savedFocused: Boolean,
    menuSelected: Boolean,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onLongPress: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    onOpenFile: (ChatAttachment, File) -> Unit,
    onReplyClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    threadMode: Boolean = false,
    replyCount: Int = 0,
    lastReplySenderId: String? = null,
    onOpenThread: (String) -> Unit = {}
) {
    val blockInteraction = remember(message.id) { MutableInteractionSource() }
    val blockPressed by blockInteraction.collectIsPressedAsState()
    val blockShape = RoundedCornerShape(10.dp)
    val blockColor by animateColorAsState(
        targetValue = when {
            menuSelected -> ChatAccent.copy(alpha = 0.14f)
            savedFocused -> Color.White.copy(alpha = 0.08f)
            highlighted -> ChatAccent.copy(alpha = 0.09f)
            blockPressed -> ChatAccent.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "messageBlockSelection"
    )
    // Include the side gutters and vertical spacing in this message's touch target.
    val blockModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(blockShape)
        .drawBehind { if (blockColor.alpha > 0.001f) drawRect(blockColor) }
        .then(
            if (menuSelected) Modifier.border(1.dp, ChatAccent.copy(alpha = 0.42f), blockShape)
            else Modifier
        )
        .semantics { selected = menuSelected }
        .combinedClickable(
            interactionSource = blockInteraction,
            indication = null,
            onClick = { if (threadMode) onLongPress(message) },
            onLongClickLabel = "Opciones del mensaje",
            onLongClick = { onLongPress(message) }
        )
        .padding(horizontal = 12.dp, vertical = 4.dp)

    if (threadMode) {
        ThreadMessageBlock(
            message, currentUserId, members, isPinned, audioController, mediaManager,
            onLongPress, onRetry, onReact, onImageClick, onOpenFile, onLinkClick,
            replyCount, lastReplySenderId, onOpenThread,
            modifier = blockModifier
        )
        return
    }
    val hasAudioOnly = message.attachments.isNotEmpty() &&
            message.attachments.all { it.kind == AttachmentKind.AUDIO } &&
            message.text.isBlank() &&
            message.replyTo == null &&
            message.forwardedFrom == null
    val hasImagesOnly = message.attachments.isNotEmpty() &&
            message.attachments.all { it.kind == AttachmentKind.IMAGE } &&
            message.text.isBlank()
    val hasMedia = message.attachments.any { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = blockModifier,
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (showAuthor && !isMe) {
            val mentionedMe = message.mentions.contains(currentUserId)
            Row(
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = authorName ?: "Usuario",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = authorTextColor(message.senderId)
                )
                if (mentionedMe) {
                    Text(
                        text = "  @tí",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        textDecoration = TextDecoration.None
                    )
                }
            }
        }

        val bubbleBg = when {
            isMe -> ChatOutgoing
            hasAudioOnly -> ChatSurfaceRaised
            else -> MaterialTheme.colorScheme.surface
        }
        val bubbleBorder = when {
            isMe -> ChatOutgoingBorder
            hasAudioOnly -> ChatDivider
            else -> Slate200
        }

        Box(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth),
            contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onLongPress(message) },
                        onLongClick = { onLongPress(message) }
                    )
                    .shadow(if (isMe) 2.dp else 1.dp, bubbleShape)
                    .background(
                        color = bubbleBg,
                        shape = bubbleShape
                    )
                    .then(
                        if (bubbleBorder != null) Modifier.border(1.dp, bubbleBorder, bubbleShape)
                        else Modifier
                    )
                    .then(
                        when {
                            savedFocused -> Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), bubbleShape)
                            highlighted -> Modifier.border(2.dp, Indigo600, bubbleShape)
                            menuSelected -> Modifier.border(2.dp, if (isMe) Color.White.copy(alpha = 0.78f) else Indigo600, bubbleShape)
                            else -> Modifier
                        }
                    )
            ) {
                if (hasImagesOnly) {
                    ImageOnlyBubble(message, bubbleShape, isPinned, onImageClick, { onLongPress(message) }, mediaManager)
                } else {
                    ContentBubble(
                        message = message,
                        isMe = isMe,
                        currentUserId = currentUserId,
                        members = members,
                        isPinned = isPinned,
                        audioController = audioController,
                        mediaManager = mediaManager,
                        onImageClick = onImageClick,
                        onOpenFile = onOpenFile,
                        onLongPress = { onLongPress(message) },
                        onReplyClick = onReplyClick,
                        onLinkClick = onLinkClick
                    )
                }
            }
        }

        ReactionsRow(
            message = message,
            isMe = isMe,
            currentUserId = currentUserId,
            onReact = { emoji -> onReact(message, emoji) }
        )

        if (message.status == MessageStatus.FAILED && isMe) {
            Text(
                text = "Toca para reintentar",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onRetry(message) }
            )
        }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadMessageBlock(
    message: ChatMessage,
    currentUserId: String,
    members: MemberDirectory,
    isPinned: Boolean,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onLongPress: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    onOpenFile: (ChatAttachment, File) -> Unit,
    onLinkClick: (String) -> Unit,
    replyCount: Int,
    lastReplySenderId: String?,
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = members.map[message.senderId]
    val name = author?.displayName?.takeIf(String::isNotBlank)
        ?: if (message.senderId == currentUserId) "Tú" else "Contacto"
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(avatarColor(message.senderId)),
            contentAlignment = Alignment.Center
        ) {
            if (!author?.photoURL.isNullOrBlank()) {
                AsyncImage(
                    model = author?.photoURL,
                    contentDescription = "Foto de $name",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(initialsOf(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(formatTime(message.createdAt), color = Slate500, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            if (message.attachments.isNotEmpty() && message.text.isBlank() &&
                message.attachments.all { it.kind == AttachmentKind.IMAGE }
            ) {
                ImageOnlyBubble(message, RoundedCornerShape(12.dp), isPinned, onImageClick,
                    { onLongPress(message) }, mediaManager)
            } else {
                ContentBubble(
                    message = message,
                    isMe = false,
                    currentUserId = currentUserId,
                    members = members,
                    isPinned = isPinned,
                    audioController = audioController,
                    mediaManager = mediaManager,
                    onImageClick = onImageClick,
                    onOpenFile = onOpenFile,
                    onLongPress = { onLongPress(message) },
                    onReplyClick = {},
                    onLinkClick = onLinkClick,
                    showReplyQuote = false,
                    showTime = false
                )
            }
            ReactionsRow(message, false, currentUserId, { emoji -> onReact(message, emoji) })
            if (replyCount > 0) {
                Row(
                    modifier = Modifier.clickable { onOpenThread(message.id) }.padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lastReplySenderId?.let { sender ->
                        val member = members.map[sender]
                        Box(
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                .background(avatarColor(sender)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!member?.photoURL.isNullOrBlank()) {
                                AsyncImage(member?.photoURL, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(initialsOf(member?.displayName ?: "?"), color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        "$replyCount ${if (replyCount == 1) "respuesta" else "respuestas"}",
                        color = Indigo700,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (message.status == MessageStatus.FAILED && message.senderId == currentUserId) {
                Text(
                    "Toca para reintentar",
                    modifier = Modifier.padding(top = 4.dp).clickable { onRetry(message) },
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ContentBubble(
    message: ChatMessage,
    isMe: Boolean,
    currentUserId: String,
    members: MemberDirectory,
    isPinned: Boolean,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onImageClick: (ChatAttachment) -> Unit,
    onOpenFile: (ChatAttachment, File) -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    showReplyQuote: Boolean = true,
    showTime: Boolean = true
) {
    val textColor = if (isMe) Color.White else ChatText
    val subColor = if (isMe) Indigo200 else ChatMuted
    val isAudioOnly = message.attachments.isNotEmpty() &&
            message.attachments.all { it.kind == AttachmentKind.AUDIO } &&
            message.text.isBlank() &&
            message.replyTo == null &&
            message.forwardedFrom == null

    val containerPadding = if (!showTime) {
        PaddingValues(0.dp)
    } else if (isAudioOnly) {
        PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)
    } else {
        PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    }

    Column(
        modifier = Modifier.padding(containerPadding),
        verticalArrangement = if (isAudioOnly) Arrangement.Center else Arrangement.Top
    ) {
        message.replyTo?.takeIf { showReplyQuote }?.let { reply ->
            ReplyQuote(
                reply = reply,
                isMe = isMe,
                currentUserId = currentUserId,
                members = members,
                textColor = textColor,
                onReplyClick = onReplyClick
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        message.forwardedFrom?.let { forwarded ->
            ForwardBanner(
                forwarded = forwarded,
                currentUserId = currentUserId,
                isMe = isMe
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        message.attachments.forEachIndexed { index, att ->
            AttachmentItem(
                att = att,
                isMe = isMe,
                audioController = audioController,
                mediaManager = mediaManager,
                onImageClick = { onImageClick(att) },
                onLongPress = onLongPress,
                onOpenFile = onOpenFile
            )
            if (!isAudioOnly && (index < message.attachments.lastIndex || message.text.isNotBlank())) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (message.text.isNotBlank()) {
            val annotated = remember(message.id, message.text, isMe, members) {
                buildMessageText(message.text, isMe, members, onLinkClick)
            }
            Text(
                text = annotated,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = textColor
            )
        }

        if (showTime) MessageTimeRow(
            message = message,
            isMe = isMe,
            isPinned = isPinned,
            subColor = subColor,
            isAudioOnly = isAudioOnly,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun ReplyQuote(
    reply: ReplyInfo,
    isMe: Boolean,
    currentUserId: String,
    members: MemberDirectory,
    textColor: Color,
    onReplyClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isMe) Color.White.copy(alpha = 0.14f) else ChatSurfaceRaised)
            .clickable { onReplyClick(reply.id) }
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isMe) Indigo200 else Indigo600
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (reply.senderId == currentUserId) "Tú"
                    else members.map[reply.senderId]?.displayName ?: "Contacto",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) Indigo200 else Indigo700
                )
            }
            Text(
                text = reply.text.ifBlank { "Adjunto" },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isMe) Color.White else textColor
            )
        }
    }
}

@Composable
private fun ForwardBanner(
    forwarded: ForwardedFrom,
    currentUserId: String,
    isMe: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = if (isMe) Indigo200 else Indigo600
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (forwarded.uid == currentUserId) "Reenviado de ti"
            else "Reenviado de ${forwarded.name}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isMe) Indigo200 else Indigo600
        )
    }
}

@Composable
private fun MessageTimeRow(
    message: ChatMessage,
    isMe: Boolean,
    isPinned: Boolean,
    subColor: Color,
    isAudioOnly: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(
                top = if (isAudioOnly) 0.dp else 2.dp,
                end = if (isAudioOnly) 4.dp else 0.dp,
                bottom = if (isAudioOnly) 1.dp else 0.dp
            ),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Fijado",
                modifier = Modifier.size(if (isAudioOnly) 10.dp else 12.dp),
                tint = subColor
            )
            Spacer(modifier = Modifier.width(if (isAudioOnly) 2.dp else 4.dp))
        }
        if (message.edited) {
            Text(
                text = "editado",
                fontSize = if (isAudioOnly) 9.5.sp else 11.sp,
                fontStyle = FontStyle.Italic,
                color = subColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(end = if (isAudioOnly) 2.dp else 4.dp)
            )
        }
        Text(
            text = formatTime(message.createdAt),
            fontSize = if (isAudioOnly) 9.5.sp else 11.sp,
            color = subColor
        )
        if (isMe) {
            Spacer(modifier = Modifier.width(if (isAudioOnly) 2.dp else 4.dp))
            when (message.status) {
                MessageStatus.SENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (isAudioOnly) 8.dp else 10.dp),
                        color = subColor,
                        strokeWidth = 1.dp
                    )
                }
                MessageStatus.SENT -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(if (isAudioOnly) 10.dp else 12.dp),
                        tint = subColor
                    )
                }
                MessageStatus.DELIVERED, MessageStatus.READ -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(if (isAudioOnly) 10.dp else 12.dp),
                        tint = if (message.status == MessageStatus.READ) Emerald500 else subColor
                    )
                }
                MessageStatus.FAILED -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(if (isAudioOnly) 10.dp else 12.dp),
                        tint = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageOnlyBubble(
    message: ChatMessage,
    shape: RoundedCornerShape,
    isPinned: Boolean,
    onImageClick: (ChatAttachment) -> Unit,
    onLongPress: () -> Unit,
    mediaManager: MediaDownloadManager
) {
    val att = message.attachments.first()
    Box(modifier = Modifier.clip(shape)) {
        ChatImage(
            att = att,
            mediaManager = mediaManager,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .combinedClickable(
                    onClick = { onImageClick(att) },
                    onLongClick = onLongPress
                ),
            contentScale = ContentScale.Crop,
            shape = shape
        )
        Surface(
            shape = CircleShape,
            color = Color(0xFF0F172A).copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (message.edited) {
                    Text("editado", color = Color.White, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(formatTime(message.createdAt), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentItem(
    att: ChatAttachment,
    isMe: Boolean,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onImageClick: () -> Unit,
    onLongPress: () -> Unit,
    onOpenFile: (ChatAttachment, File) -> Unit
) {
    when (att.kind) {
        AttachmentKind.IMAGE -> {
            ChatImage(
                att = att,
                mediaManager = mediaManager,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .combinedClickable(
                        onClick = onImageClick,
                        onLongClick = onLongPress
                    ),
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(8.dp)
            )
        }
        AttachmentKind.AUDIO -> {
            AudioCard(att = att, isMe = isMe, controller = audioController, mediaManager = mediaManager)
        }
        AttachmentKind.VIDEO -> {
            FileCard(
                att = att,
                isMe = isMe,
                mediaManager = mediaManager,
                onOpenFile = onOpenFile,
                subtitle = formatBytes(att.size),
                actionLabel = "Reproducir"
            )
        }
        else -> {
            FileCard(
                att = att,
                isMe = isMe,
                mediaManager = mediaManager,
                onOpenFile = onOpenFile,
                subtitle = formatBytes(att.size),
                actionLabel = if (att.kind == AttachmentKind.PDF) "Ver" else "Abrir"
            )
        }
    }
}

@Composable
private fun FileCard(
    att: ChatAttachment,
    isMe: Boolean,
    mediaManager: MediaDownloadManager,
    onOpenFile: (ChatAttachment, File) -> Unit,
    subtitle: String,
    actionLabel: String
) {
    val mediaId = att.publicId
    val kind = when (att.kind) {
        AttachmentKind.VIDEO -> MediaKind.VIDEO
        AttachmentKind.PDF, AttachmentKind.DOC, AttachmentKind.FILE -> MediaKind.DOCUMENT
        else -> MediaKind.OTHER
    }
    val state by remember(mediaId) { mediaManager.stateFor(mediaId) }
        .collectAsStateWithLifecycle(initialValue = mediaManager.currentState(mediaId))
    val downloadState = state
    var pendingOpen by remember(mediaId) { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        if (pendingOpen && downloadState is MediaDownloadState.Downloaded) {
            pendingOpen = false
            mediaManager.fileFor(mediaId, kind)?.let { onOpenFile(att, it) }
        }
    }

    fun onAction() {
        when (downloadState) {
            is MediaDownloadState.Downloaded ->
                mediaManager.fileFor(mediaId, kind)?.let { onOpenFile(att, it) }
            is MediaDownloadState.NotDownloaded,
            is MediaDownloadState.Error -> {
                pendingOpen = true
                mediaManager.ensureDownloaded(mediaId, att.url, kind)
            }
            is MediaDownloadState.Queued -> {
                pendingOpen = true
                mediaManager.ensureDownloaded(mediaId, att.url, kind)
            }
            is MediaDownloadState.Downloading -> Unit
        }
    }

    val actionText = when (downloadState) {
        is MediaDownloadState.Downloading -> "${(downloadState.progress * 100).toInt()}%"
        is MediaDownloadState.Queued -> "En cola"
        is MediaDownloadState.Error -> "Reintentar"
        else -> actionLabel
    }
    val actionColor = if (downloadState is MediaDownloadState.Error) {
        MaterialTheme.colorScheme.error
    } else if (isMe) Indigo200 else Indigo600

    val borderColor = if (isMe) Color.White.copy(alpha = 0.15f) else Slate200
    val bg = if (isMe) Color.White.copy(alpha = 0.1f) else Slate100
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (att.kind == AttachmentKind.PDF) Color(0xFF450A0A) else Indigo900),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (att.kind == AttachmentKind.PDF) "PDF" else att.name.substringAfterLast('.', "F").take(3).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (att.kind == AttachmentKind.PDF) Color(0xFFFCA5A5) else Indigo200
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = att.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMe) Color.White else Slate800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (downloadState) {
                    is MediaDownloadState.Downloading -> "Descargando…"
                    is MediaDownloadState.Queued -> "En cola"
                    is MediaDownloadState.Error -> downloadState.message
                    else -> subtitle
                },
                fontSize = 11.sp,
                color = if (downloadState is MediaDownloadState.Error) MaterialTheme.colorScheme.error
                else if (isMe) Color.White.copy(alpha = 0.8f) else Slate400
            )
        }
        Text(
            text = actionText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = actionColor,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onAction() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ReactionsRow(
    message: ChatMessage,
    isMe: Boolean,
    currentUserId: String,
    onReact: (String) -> Unit
) {
    if (message.reactions.isEmpty()) return
    val grouped = message.reactions.values.groupingBy { it }.eachCount()
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        grouped.forEach { (emoji, count) ->
            val mine = message.reactions[currentUserId] == emoji
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (mine) Indigo900 else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (mine) Indigo600.copy(alpha = 0.5f) else Slate200),
                modifier = Modifier.clickable { onReact(emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(count.toString(), fontSize = 13.sp, color = Slate500)
                }
            }
        }
    }
}

private fun buildMessageText(
    text: String,
    isMe: Boolean,
    members: MemberDirectory,
    onLinkClick: (String) -> Unit
): AnnotatedString {
    val mentionRanges = findMentionRanges(text, members.map)
    val linkColor = if (isMe) Indigo200 else Indigo600
    return buildAnnotatedString {
        append(text)
        for (match in linkRegex.findAll(text)) {
            val raw = match.value
            val url = raw.replace(trailingPunctRegex, "")
            if (url.isEmpty()) continue
            val start = match.range.first
            val end = start + url.length
            addLink(androidx.compose.ui.text.LinkAnnotation.Clickable(tag = url, linkInteractionListener = { onLinkClick(url) }), start, end)
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
        }
        for (r in mentionRanges) {
            val s = r.start.coerceAtMost(text.length)
            val e = r.end.coerceAtMost(text.length)
            if (s < e) {
                addStyle(
                    SpanStyle(
                        background = if (isMe) Color.White.copy(alpha = 0.25f) else Indigo900,
                        color = if (isMe) Color.White else Indigo200,
                        fontWeight = FontWeight.SemiBold
                    ),
                    s, e
                )
            }
        }
    }
}

private val linkRegex = Regex("(\\bhttps?://[^\\s<]+)", RegexOption.IGNORE_CASE)
private val trailingPunctRegex = Regex("[.,;:!?)\\]}»\"'`]+$")