package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.SongLyrics
import com.ronaldcolocho.taskly.domain.model.activeLyricsLine
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

internal fun lyricsTextAccent(accent: Color, background: Color, onSurface: Color): Color {
    var color = accent
    repeat(12) {
        val foreground = color.luminance()
        val behind = background.luminance()
        val contrast = (maxOf(foreground, behind) + 0.05f) / (minOf(foreground, behind) + 0.05f)
        if (contrast >= 5f) return color
        color = lerp(color, onSurface, 0.2f)
    }
    return onSurface
}

@Composable
internal fun MusicLyricsPane(
    state: MusicLyricsState, positionMs: Long, onSurface: Color, secondary: Color, accent: Color,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize().testTag("music_lyrics_pane"), contentAlignment = Alignment.Center) {
        when {
            state.loading || state.result == null -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = accent)
                Text("Buscando letra…", color = secondary)
            }
            state.result is LyricsLookup.Found -> {
                val lyrics = state.result.lyrics
                when {
                    lyrics.instrumental -> LyricsMessage("Canción instrumental", "Esta canción no tiene letra.", secondary, lyrics.provider)
                    lyrics.lines.isNotEmpty() -> SyncedMusicLyrics(lyrics, positionMs, onSurface, secondary, accent)
                    else -> Column(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 24.dp)) {
                            item { Text(lyrics.displayText, color = onSurface, fontFamily = BricolageGrotesque,
                                fontSize = 22.sp, lineHeight = 34.sp) }
                        }
                        Text("Letra sin sincronización · ${lyrics.provider}", color = secondary, fontSize = 11.sp)
                    }
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.result is LyricsLookup.Failed) state.result.message else "Letra no disponible",
                    color = secondary, fontFamily = BricolageGrotesque, fontSize = 18.sp)
                if (state.result is LyricsLookup.NotFound) Text("No encontramos una letra que coincida con esta versión.",
                    color = secondary, fontSize = 13.sp)
                if (state.retrySeconds > 0) Text(
                    if (state.autoRetry) "Reintentando automáticamente en ${state.retrySeconds} s…"
                    else "Puedes reintentar en ${state.retrySeconds} s",
                    color = secondary, fontSize = 13.sp)
                TextButton(onClick = onRetry, enabled = !state.loading && state.retrySeconds == 0) {
                    Text("Reintentar", color = if (state.retrySeconds == 0) accent else secondary)
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, message: String, color: Color, provider: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = color, fontFamily = BricolageGrotesque, fontSize = 22.sp)
        Text(message, color = color, fontSize = 14.sp)
        Text(provider, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun SyncedMusicLyrics(lyrics: SongLyrics, positionMs: Long, onSurface: Color, secondary: Color, accent: Color) {
    val listState = rememberLazyListState()
    var following by remember(lyrics) { mutableStateOf(true) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val active = remember(lyrics, positionMs) { activeLyricsLine(lyrics.lines, positionMs) }
    LaunchedEffect(dragging) { if (dragging) following = false }
    LaunchedEffect(active, following, lyrics) {
        if (following && active >= 0) listState.animateScrollToItem(active, -listState.layoutInfo.viewportEndOffset / 3)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("synced_lyrics_list"), state = listState,
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(lyrics.lines, key = { _, line -> line.timeMs }) { index, line ->
                Text(line.text.ifBlank { "♪" }, color = if (index == active) accent else onSurface.copy(alpha = 0.65f),
                    fontFamily = BricolageGrotesque, fontSize = 24.sp, lineHeight = 33.sp,
                    fontWeight = if (index == active) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().semantics { selected = index == active })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Letra sincronizada · ${lyrics.provider}", color = secondary, fontSize = 11.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!following) TextButton(onClick = { following = true }) { Text("Seguir canción", color = accent, fontSize = 12.sp) }
        }
    }
}
