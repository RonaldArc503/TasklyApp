package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.di.MediaEntryPoint
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import dagger.hilt.android.EntryPointAccessors

private val Slate200 = Color(0xFF334155)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF94A3B8)
private val Slate600 = Color(0xFF94A3B8)
private val Slate800 = Color(0xFFF1F5F9)

@Composable
fun rememberAudioPlayerController(): AudioPlayerController {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, MediaEntryPoint::class.java)
            .audioPlayerController()
    }
}

@Composable
fun rememberMediaDownloadManager(): MediaDownloadManager {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, MediaEntryPoint::class.java)
            .mediaDownloadManager()
    }
}

@Composable
fun AudioPlayerBar(
    controller: AudioPlayerController,
    onOpenPlayer: (() -> Unit)? = null
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    val pct = if (state.durationMs > 0) (state.currentTimeMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Indigo600.copy(alpha = 0.15f))
                        .clickable { onOpenPlayer?.invoke() ?: controller.toggleCurrent() },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.playing) {
                        EqBars(color = Indigo600)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Indigo600,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPlayer?.invoke() ?: controller.toggleCurrent() }
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDuration(state.currentTimeMs)} / ${formatDuration(state.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller.prev() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Slate600)
                    }
                    IconButton(
                        onClick = { controller.toggleCurrent() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Indigo600)
                    ) {
                        Icon(
                            imageVector = if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.playing) "Pausar" else "Reproducir",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { controller.next() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = Slate600)
                    }
                    IconButton(onClick = { controller.stop() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar reproductor", tint = Slate400)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Slate200)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            controller.seek(((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * state.durationMs).toLong())
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .fillMaxHeight()
                        .background(Indigo600)
                )
            }
        }
    }
}

@Composable
private fun EqBars(color: Color) {
    Row(
        modifier = Modifier.height(14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(0, 200, 400).forEach { delayMs ->
            val transition = rememberInfiniteTransition(label = "eq$delayMs")
            val h by transition.animateFloat(
                initialValue = 4f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eqh"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}
