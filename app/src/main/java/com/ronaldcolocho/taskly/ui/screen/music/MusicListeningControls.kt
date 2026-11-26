package com.ronaldcolocho.taskly.ui.screen.music

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import com.ronaldcolocho.taskly.domain.model.activeLyricsLine
import com.ronaldcolocho.taskly.ui.theme.MareaContextStyle
import com.ronaldcolocho.taskly.ui.theme.MareaQueueSubtitleStyle
import com.ronaldcolocho.taskly.ui.theme.MareaQueueTitleStyle

@Composable
internal fun MusicLyricsPreview(state: MusicLyricsState, positionMs: Long, accent: Color, onOpen: () -> Unit) {
    val lyrics = (state.result as? LyricsLookup.Found)?.lyrics?.takeUnless { it.instrumental } ?: return
    val line = if (lyrics.lines.isEmpty()) lyrics.displayText.lineSequence().firstOrNull { it.isNotBlank() }
        else lyrics.lines.getOrNull(activeLyricsLine(lyrics.lines, positionMs))?.text
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier.fillMaxWidth(0.88f).padding(top = 4.dp).clip(shape)
            .background(accent.copy(alpha = 0.09f)).border(1.dp, accent.copy(alpha = 0.3f), shape)
            .clickable(role = Role.Button, onClickLabel = "Abrir letra", onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp).testTag("music_lyrics_preview"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(line?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Ver letra",
            style = MareaQueueTitleStyle.copy(fontSize = 15.sp), color = accent,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun MusicPlayerFeedback(message: String, color: Color, modifier: Modifier = Modifier) {
    Text(message, color = color, style = MareaContextStyle.copy(fontSize = 12.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth())
}

@Composable
internal fun MusicPlayerToolRow(
    track: AudioTrack?, timerActive: Boolean, secondary: Color, accent: Color,
    onEffects: () -> Unit, onSleepTimer: () -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().testTag("music_player_tools"), horizontalArrangement = Arrangement.SpaceAround) {
        IconButton(onClick = { showMusicOutput(context) }) {
            Icon(Icons.Outlined.DesktopWindows, "Dispositivos de audio", tint = secondary)
        }
        IconButton(onClick = onEffects) {
            Icon(Icons.Default.Tune, "Efectos visuales del reproductor", tint = secondary)
        }
        IconButton(enabled = track != null, onClick = onSleepTimer) {
            Icon(Icons.Default.Alarm, if (timerActive) "Temporizador activo" else "Temporizador",
                tint = if (timerActive) accent else secondary)
        }
        IconButton(enabled = track != null, onClick = { track?.let { shareMusicTrack(context, it) } }) {
            Icon(Icons.Default.IosShare, "Compartir canción", tint = secondary)
        }
    }
}

@Composable
internal fun MusicPlayerUpNext(
    next: AudioTrack, surface: Color, onSurface: Color, secondary: Color,
    primary: Color, tertiary: Color, onOpen: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(surface)
            .border(1.dp, secondary.copy(alpha = 0.18f), shape)
            .clickable(role = Role.Button, onClickLabel = "Abrir cola", onClick = onOpen)
            .heightIn(min = 54.dp).padding(horizontal = 11.dp, vertical = 8.dp).testTag("music_up_next"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(primary, tertiary)))) {
            AsyncImage(model = next.thumbnailUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text("A continuación", style = MareaQueueSubtitleStyle.copy(fontSize = 11.sp), color = secondary)
            val detail = next.artist.ifBlank { next.sourceChannel }
            val title = if (detail.isBlank()) next.name else "${next.name} · $detail"
            Text(title, style = MareaQueueTitleStyle, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = onSurface, modifier = Modifier.size(23.dp))
    }
}

private fun showMusicOutput(context: Context) {
    if (Build.VERSION.SDK_INT >= 34 && MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
}

private fun shareMusicTrack(context: Context, track: AudioTrack) {
    val title = if (track.artist.isBlank()) track.name else "${track.name} · ${track.artist}"
    val text = OnlineMusicSource.youtubeId(track.url)?.let { "$title\n${OnlineMusicSource.reference(it)}" } ?: title
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }, "Compartir canción"))
}
