package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.AudioPlayerState
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

@Composable
internal fun MusicResumeCard(
    state: AudioPlayerState, surface: Color, onSurface: Color, accent: Color,
    onToggle: () -> Unit, onOpenPlayer: () -> Unit
) {
    val track = state.currentTrack ?: return
    val duration = state.durationMs.takeIf { it > 0 } ?: (track.durationSeconds * 1000L)
    Surface(color = surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
        Column(Modifier.clickable(onClick = onOpenPlayer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.playing) "Estás escuchando" else "Continúa donde te quedaste", color = accent,
                fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.name, color = onSurface, fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val seconds = state.currentTimeMs.coerceAtLeast(0) / 1000
                    Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                        color = onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
                }
                FilledIconButton(onClick = onToggle, colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accent, contentColor = Color(0xFF0F2233))) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.playing) "Pausar" else "Continuar reproducción")
                }
            }
            if (duration > 0) LinearProgressIndicator(
                progress = { (state.currentTimeMs.toFloat() / duration).coerceIn(0f, 1f) },
                color = accent, modifier = Modifier.fillMaxWidth())
        }
    }
}
