package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.AudioMode
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.Emerald500
import com.ronaldcolocho.taskly.ui.theme.Indigo600

private val Slate100 = Color(0xFF1E293B)
private val Slate300 = Color(0xFF475569)
private val Slate500 = Color(0xFF94A3B8)
private val Slate800 = Color(0xFFF1F5F9)

@Composable
fun AudioCard(
    att: ChatAttachment,
    isMe: Boolean,
    controller: AudioPlayerController,
    mediaManager: MediaDownloadManager
) {
    val mediaId = att.publicId
    val state by controller.state.collectAsState()
    val mediaStates by mediaManager.states.collectAsState()

    val track = remember(mediaId) {
        AudioTrack(id = mediaId, url = att.url, name = att.name, msgId = mediaId)
    }
    val downloadState = mediaStates[mediaId]
        ?: remember(mediaId) {
            if (mediaManager.fileFor(mediaId, MediaKind.AUDIO) != null) MediaDownloadState.Downloaded
            else MediaDownloadState.NotDownloaded
        }

    var pendingPlay by remember(mediaId) { mutableStateOf(false) }

    // Descarga automática al aparecer visible por primera vez (dedup + single-flight)
    LaunchedEffect(mediaId) {
        mediaManager.ensureDownloaded(mediaId, att.url, MediaKind.AUDIO)
    }

    val isCurrent = state.currentTrack?.id == track.id
    val isPlaying = isCurrent && state.playing
    val nowMs = if (isCurrent) state.currentTimeMs else 0L
    val durMs = if (isCurrent && state.durationMs > 0) state.durationMs
        else (att.duration?.toLong() ?: 0L) * 1000L
    val pct = if (durMs > 0) (nowMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f

    LaunchedEffect(downloadState) {
        if (pendingPlay && downloadState is MediaDownloadState.Downloaded) {
            pendingPlay = false
            controller.toggleTrack(track)
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
            is MediaDownloadState.Downloading -> Unit
        }
    }

    val bg = if (isMe) Color.White.copy(alpha = 0.15f) else Slate100
    val btnBg = if (isMe) Color.White else Indigo600
    val btnTint = if (isMe) Indigo600 else Color.White
    val subColor = if (isMe) Color.White.copy(alpha = 0.8f) else Slate500
    val trackColor = if (isMe) Color.White.copy(alpha = 0.25f) else Slate300
    val fillColor = if (isMe) Color.White else Emerald500

    Row(
        modifier = Modifier
            .widthIn(max = 256.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioPlayButton(
            downloadState = downloadState,
            isPlaying = isPlaying,
            btnBg = btnBg,
            btnTint = btnTint,
            onClick = ::onPlayClick
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = att.name,
                color = if (isMe) Color.White else Slate800,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AudioStatusLabel(
                downloadState = downloadState,
                isCurrent = isCurrent,
                nowMs = nowMs,
                durMs = durMs,
                isMe = isMe,
                sizeBytes = att.size
            )
            ThinSeekBar(
                pct = pct,
                enabled = downloadState is MediaDownloadState.Downloaded && isCurrent,
                trackColor = trackColor,
                fillColor = fillColor,
                onSeek = { ratio -> controller.seek((ratio * durMs).toLong()) }
            )
        }

        AudioModeMenu(
            controller = controller,
            track = track,
            isMe = isMe,
            isCurrent = isCurrent,
            enabled = downloadState is MediaDownloadState.Downloaded
        )
    }
}

@Composable
private fun AudioPlayButton(
    downloadState: MediaDownloadState,
    isPlaying: Boolean,
    btnBg: Color,
    btnTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(btnBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (downloadState) {
            is MediaDownloadState.Downloaded -> {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar audio" else "Reproducir audio",
                    tint = btnTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            is MediaDownloadState.NotDownloaded -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Preparando descarga",
                    tint = btnTint,
                    modifier = Modifier.size(18.dp)
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
                    contentDescription = "Reintentar audio",
                    tint = btnTint,
                    modifier = Modifier.size(18.dp)
                )
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
    isMe: Boolean,
    sizeBytes: Long
) {
    val subColor = if (isMe) Color.White.copy(alpha = 0.8f) else Slate500
    val text = when (downloadState) {
        is MediaDownloadState.Downloading -> "Descargando ${(downloadState.progress * 100).toInt()}%"
        is MediaDownloadState.Error -> downloadState.message
        is MediaDownloadState.NotDownloaded -> "Preparando descarga"
        is MediaDownloadState.Downloaded ->
            if (isCurrent) "${formatDuration(nowMs)} / ${formatDuration(durMs)}"
            else formatBytes(sizeBytes)
    }
    Text(
        text = text,
        color = if (downloadState is MediaDownloadState.Error) MaterialTheme.colorScheme.error else subColor,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AudioModeMenu(
    controller: AudioPlayerController,
    track: AudioTrack,
    isMe: Boolean,
    isCurrent: Boolean,
    enabled: Boolean
) {
    val state by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
            enabled = enabled
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
                active = state.mode == AudioMode.INDIVIDUAL,
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
                active = state.mode == AudioMode.QUEUE,
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
                active = state.mode == AudioMode.LOOP,
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
                active = state.mode == AudioMode.REPEAT,
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
            active && isMe -> Color(0xFF312E81)
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
private fun ThinSeekBar(
    pct: Float,
    enabled: Boolean,
    trackColor: Color,
    fillColor: Color,
    onSeek: (Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f)) },
                    onDrag = { change, _ ->
                        onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val maxW = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(pct)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(fillColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxW * pct - 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}
