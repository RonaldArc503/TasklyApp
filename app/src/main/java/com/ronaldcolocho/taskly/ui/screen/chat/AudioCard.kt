package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.audio.AudioMode
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.model.CachedAudioReference
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.ChatAccent as Indigo600
import com.ronaldcolocho.taskly.ui.theme.ChatAccentContainer
import com.ronaldcolocho.taskly.ui.theme.ChatActionGradient
import com.ronaldcolocho.taskly.ui.theme.ChatMuted
import com.ronaldcolocho.taskly.ui.theme.ChatOutgoing
import com.ronaldcolocho.taskly.ui.theme.ChatSurfaceRaised
import com.ronaldcolocho.taskly.ui.theme.ChatText

private val Slate100 = ChatSurfaceRaised
private val Slate500 = ChatMuted
private val Slate800 = ChatText

@Composable
fun AudioCard(
    att: ChatAttachment,
    isMe: Boolean,
    controller: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onSelectionClick: (() -> Unit)? = null,
    onSelectionLongClick: (() -> Unit)? = null,
    trailingStatus: (@Composable () -> Unit)? = null
) {
    val mediaId = att.publicId
    // Estado de descarga limitado a este mediaId: otras descargas no re-componen esta tarjeta.
    val downloadState by remember(mediaId) { mediaManager.stateFor(mediaId) }
        .collectAsStateWithLifecycle(initialValue = mediaManager.currentState(mediaId))

    LaunchedEffect(mediaId) {
        mediaManager.resolveLocalFile(mediaId, MediaKind.AUDIO)
    }

    val track = remember(mediaId) {
        AudioTrack(
            id = mediaId,
            url = att.url,
            name = att.name,
            msgId = mediaId,
            durationSeconds = att.duration ?: 0
        )
    }

    // Estado de reproducción acotado: el ticker de posición solo recompone la tarjeta activa.
    val isCurrent by remember(track.id) { controller.isCurrentFlow(track.id) }.collectAsStateWithLifecycle(initialValue = false)
    val isPlaying by remember(track.id) { controller.playingFor(track.id) }.collectAsStateWithLifecycle(initialValue = false)
    val nowMs by remember(track.id) { controller.timeFor(track.id) }.collectAsStateWithLifecycle(initialValue = 0L)
    val playerDurMs by remember(track.id) { controller.durationFor(track.id) }.collectAsStateWithLifecycle(initialValue = 0L)

    var pendingPlay by remember(mediaId) { mutableStateOf(false) }

    val durMs = if (playerDurMs > 0) playerDurMs
        else (att.duration?.toLong() ?: 0L) * 1000L
    val pct = if (durMs > 0) (nowMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f

    LaunchedEffect(downloadState) {
        if (pendingPlay && downloadState is MediaDownloadState.Downloaded) {
            pendingPlay = false
            controller.toggleTrack(track)
        }
        if (downloadState is MediaDownloadState.Downloaded) {
            mediaManager.registerDownloadedAudio(
                CachedAudioReference(
                    mediaId = mediaId,
                    url = att.url,
                    name = att.name,
                    sourceMessageId = mediaId,
                    durationSeconds = att.duration ?: 0,
                    downloadedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun onPlayClick() {
        when (downloadState) {
            is MediaDownloadState.Downloaded -> controller.toggleTrack(track)
            is MediaDownloadState.NotDownloaded,
            is MediaDownloadState.Error -> {
                pendingPlay = true
                mediaManager.ensureDownloaded(mediaId, att.url, MediaKind.AUDIO)
            }
            is MediaDownloadState.Queued -> {
                pendingPlay = true
                mediaManager.ensureDownloaded(mediaId, att.url, MediaKind.AUDIO)
            }
            is MediaDownloadState.Downloading -> Unit
        }
    }

    val downloaded = downloadState is MediaDownloadState.Downloaded

    Column(
        modifier = if (trailingStatus != null) Modifier.fillMaxWidth()
        else Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(16.dp))
            .background(if (isMe) ChatOutgoing else Slate100)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioPlayButton(
                downloadState = downloadState,
                isPlaying = isPlaying,
                onClick = onSelectionClick ?: ::onPlayClick,
                onLongClick = onSelectionLongClick
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = att.name,
                    color = if (isMe) Color.White else Slate800,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AudioProgressBars(
                    pct = pct,
                    enabled = downloaded && isCurrent && durMs > 0,
                    onSeek = { ratio -> controller.seek((ratio * durMs).toLong()) }
                )
            }

            if (downloaded) AudioModeMenu(
                controller = controller,
                track = track,
                isMe = isMe,
                isCurrent = isCurrent
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioStatusLabel(downloadState, isCurrent, nowMs, durMs, att.size, Modifier.weight(1f))
            trailingStatus?.invoke()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioPlayButton(
    downloadState: MediaDownloadState,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val downloaded = downloadState is MediaDownloadState.Downloaded
    val btnTint = if (downloaded) Color(0xFF102B35) else Indigo600
    val label = when (downloadState) {
        is MediaDownloadState.Downloaded -> if (isPlaying) "Pausar audio" else "Reproducir audio"
        is MediaDownloadState.NotDownloaded -> "Descargar audio"
        is MediaDownloadState.Queued -> "Audio en cola"
        is MediaDownloadState.Downloading -> "Descargando audio"
        is MediaDownloadState.Error -> "Reintentar descarga de audio"
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).then(
                if (downloaded) Modifier.background(ChatActionGradient)
                else Modifier.border(1.dp, Indigo600, CircleShape)
            ),
            contentAlignment = Alignment.Center
        ) {
            when (downloadState) {
                is MediaDownloadState.Downloaded -> {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = btnTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is MediaDownloadState.NotDownloaded -> {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = btnTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is MediaDownloadState.Queued -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = btnTint,
                        strokeWidth = 2.dp
                    )
                }
                is MediaDownloadState.Downloading -> {
                    CircularProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.size(18.dp),
                        color = btnTint,
                        strokeWidth = 2.dp
                    )
                }
                is MediaDownloadState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = btnTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioStatusLabel(
    downloadState: MediaDownloadState,
    isCurrent: Boolean,
    nowMs: Long,
    durMs: Long,
    sizeBytes: Long,
    modifier: Modifier
) {
    val text = when (downloadState) {
        is MediaDownloadState.Downloading -> "Descargando ${(downloadState.progress * 100).toInt()}%"
        is MediaDownloadState.Error -> downloadState.message
        is MediaDownloadState.Queued -> "En cola"
        is MediaDownloadState.NotDownloaded -> formatBytes(sizeBytes)
        is MediaDownloadState.Downloaded ->
            if (isCurrent) "${formatDuration(nowMs)} / ${formatDuration(durMs)}"
            else if (durMs > 0) formatDuration(durMs) else formatBytes(sizeBytes)
    }
    Text(
        text = text,
        modifier = modifier,
        color = if (downloadState is MediaDownloadState.Error) MaterialTheme.colorScheme.error else Slate500,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AudioModeMenu(
    controller: AudioPlayerController,
    track: AudioTrack,
    isMe: Boolean,
    isCurrent: Boolean
) {
    val mode by controller.modeState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Opciones de reproducción",
                tint = if (isMe) Color.White.copy(alpha = 0.8f) else Slate500,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "Reproducción",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else Slate500,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ModeRow(
                icon = Icons.Filled.PlayArrow,
                label = "Individual",
                hint = "Se detiene al terminar",
                isMe = isMe,
                active = mode == AudioMode.INDIVIDUAL,
                onClick = {
                    if (isCurrent) controller.updateMode(AudioMode.INDIVIDUAL)
                    else controller.playWithIndividual(track)
                    expanded = false
                }
            )
            ModeRow(
                icon = Icons.Filled.SkipNext,
                label = "Seguir con el siguiente",
                hint = "En orden hasta el último",
                isMe = isMe,
                active = mode == AudioMode.QUEUE,
                onClick = {
                    if (isCurrent) controller.updateMode(AudioMode.QUEUE)
                    else controller.playWithQueue(track)
                    expanded = false
                }
            )
            ModeRow(
                icon = Icons.Filled.Repeat,
                label = "Repetir lista",
                hint = "Ciclo completo de la lista",
                isMe = isMe,
                active = mode == AudioMode.LOOP,
                onClick = {
                    if (isCurrent) controller.updateMode(AudioMode.LOOP)
                    else controller.playWithLoop(track)
                    expanded = false
                }
            )
            ModeRow(
                icon = Icons.Filled.RepeatOne,
                label = "Repetir este audio",
                hint = "En bucle, sin parar",
                isMe = isMe,
                active = mode == AudioMode.REPEAT,
                onClick = {
                    if (isCurrent) controller.updateMode(AudioMode.REPEAT)
                    else controller.playWithRepeat(track)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    label: String,
    hint: String,
    isMe: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = when {
            active && isMe -> ChatAccentContainer
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        label = "modeRowBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isMe) Color.White.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isMe) Color.White else Indigo600,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMe) Color.White else Slate800
            )
            Text(
                text = hint,
                fontSize = 11.sp,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else Slate500
            )
        }
        if (active) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Indigo600,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AudioProgressBars(
    pct: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit
) {
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .then(if (enabled) Modifier.semantics {
                contentDescription = "Progreso del audio"
                progressBarRangeInfo = ProgressBarRangeInfo(pct, 0f..1f)
                setProgress { value ->
                    seek(value.coerceIn(0f, 1f))
                    true
                }
            } else Modifier)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    seek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    seek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
    ) {
        val step = size.width / AudioBarHeights.size
        val stroke = 3.dp.toPx().coerceAtMost(step * 0.65f)
        AudioBarHeights.forEachIndexed { index, height ->
            val x = (index + 0.5f) * step
            val half = height.dp.toPx() / 2
            drawLine(
                color = if (x <= size.width * pct) Indigo600 else Color(0xFF3A4558),
                start = Offset(x, center.y - half),
                end = Offset(x, center.y + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private val AudioBarHeights = intArrayOf(
    5, 16, 6, 8, 10, 17, 13, 7, 5, 6, 18, 6, 13, 5, 12, 8,
    16, 10, 6, 8, 9, 18, 8, 6, 11, 15, 8, 17, 10, 6, 13, 11
)
