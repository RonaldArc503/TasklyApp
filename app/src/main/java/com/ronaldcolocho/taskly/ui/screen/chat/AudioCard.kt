package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.AudioMode
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDuration
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
    controller: AudioPlayerController
) {
    val state by controller.state.collectAsState()
    val track = remember(att.publicId) {
        AudioTrack(id = att.publicId, url = att.url, name = att.name, msgId = att.publicId)
    }
    val isCurrent = state.currentTrack?.id == track.id
    val isPlaying = isCurrent && state.playing
    val nowMs = if (isCurrent) state.currentTimeMs else 0L
    val durMs = if (isCurrent && state.durationMs > 0) state.durationMs
        else (att.duration?.toLong() ?: 0L) * 1000L
    val pct = if (durMs > 0) (nowMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (isMe) Color.White.copy(alpha = 0.15f) else if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9)
    val btnBg = if (isMe) Color.White else Indigo600
    val btnTint = if (isMe) Indigo600 else Color.White
    val textColor = if (isMe) Color.White else if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val subColor = if (isMe) Color.White.copy(alpha = 0.8f) else if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val trackColor = if (isMe) Color.White.copy(alpha = 0.25f) else if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
    val fillColor = if (isMe) Color.White else Emerald500
    var modeMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(btnBg)
                .clickable { controller.toggleTrack(track) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar audio" else "Reproducir audio",
                tint = btnTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = att.name,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isCurrent) "${formatDuration(nowMs)} / ${formatDuration(durMs)}"
                else formatBytes(att.size),
                color = subColor,
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            ThinSeekBar(
                pct = pct,
                enabled = isCurrent,
                trackColor = trackColor,
                fillColor = fillColor,
                onSeek = { ratio -> controller.seek((ratio * durMs).toLong()) }
            )
        }

        Box {
            IconButton(
                onClick = { modeMenuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones de reproducción",
                    tint = if (isMe) Color.White.copy(alpha = 0.8f) else subColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                AudioModeItem(
                    label = "Seguir con el siguiente",
                    hint = "Reproduce uno tras otro",
                    isMe = isMe,
                    active = state.mode == AudioMode.QUEUE
                ) {
                    if (isCurrent) controller.updateMode(AudioMode.QUEUE) else controller.playWithQueue(track)
                    modeMenuOpen = false
                }
                AudioModeItem(
                    label = "Repetir lista",
                    hint = "Al terminar, vuelve a empezar",
                    isMe = isMe,
                    active = state.mode == AudioMode.LOOP
                ) {
                    if (isCurrent) controller.updateMode(AudioMode.LOOP) else controller.playWithLoop(track)
                    modeMenuOpen = false
                }
                AudioModeItem(
                    label = "Repetir este audio",
                    hint = "En bucle, sin parar",
                    isMe = isMe,
                    active = state.mode == AudioMode.REPEAT
                ) {
                    if (isCurrent) controller.updateMode(AudioMode.REPEAT) else controller.playWithRepeat(track)
                    modeMenuOpen = false
                }
            }
        }
    }
}

@Composable
private fun AudioModeItem(
    label: String,
    hint: String,
    isMe: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, color = if (isMe) Color.White else Slate800)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White.copy(alpha = 0.7f) else Slate500)
            }
        },
        onClick = onClick,
        leadingIcon = if (active) {
            { androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, tint = Indigo600) }
        } else {
            null
        }
    )
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
            .height(14.dp)
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
