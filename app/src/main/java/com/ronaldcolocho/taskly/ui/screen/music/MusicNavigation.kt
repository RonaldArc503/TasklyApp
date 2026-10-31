package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.ui.theme.*

enum class MusicSection(val label: String) {
    HOME("Inicio"), DISCOVER("Descubrir"), PLAYLISTS("Mis listas"), LIBRARY("Mi biblioteca")
}

/** Shared by the music sections and playlist details. */
@Composable
fun MusicNavigationChrome(
    controller: AudioPlayerController, selected: MusicSection,
    onSelect: (MusicSection) -> Unit, onOpenPlayer: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val background = if (dark) MareaBackgroundDark else MareaBackgroundLight
    Column(Modifier.fillMaxWidth().background(background).navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicNavigationPlayer(controller, onOpenPlayer)
        MusicSectionMenu(selected, onSelect)
    }
}

@Composable
private fun MusicNavigationPlayer(controller: AudioPlayerController, onOpenPlayer: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    val colors by controller.colorState.collectAsStateWithLifecycle()
    val palette = remember(track.id, colors) { getTrackPalette(track, colors) }
    val dark = isSystemInDarkTheme()
    val duration = state.durationMs.takeIf { it > 0 } ?: (track.durationSeconds * 1000L)
    MareaFloatingMiniPlayer(
        track = track, isPlaying = state.playing,
        progress = if (duration > 0) (state.currentTimeMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
        surface = if (dark) MareaSurfaceDark else MareaSurfaceLight,
        onSurface = if (dark) MareaOnSurfaceDark else MareaOnSurfaceLight,
        secondary = if (dark) MareaSecondaryDark else MareaSecondaryLight,
        lilac = palette.primary, peach = palette.tertiary,
        onAccent = if (palette.primary.luminance() > 0.4f) Color(0xFF0F2233) else Color.White, darkTheme = dark,
        onPlayPause = controller::toggleCurrent, onClick = onOpenPlayer,
        onSwipeLeft = { controller.prev(forceTrackChange = true) }, onSwipeRight = controller::next
    )
}

@Composable
private fun MusicSectionMenu(selected: MusicSection, onSelect: (MusicSection) -> Unit) {
    val dark = isSystemInDarkTheme()
    val surface = if (dark) MareaSurfaceDark else MareaSurfaceLight
    val text = if (dark) MareaOnSurfaceDark else MareaOnSurfaceLight
    val secondary = if (dark) MareaSecondaryDark else MareaSecondaryLight
    val mint = Color(0xFF70A896)
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(26.dp)
    Surface(color = surface, shape = shape, shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, secondary.copy(alpha = 0.2f), shape)) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(6.dp)) {
            MusicSection.entries.forEach { section ->
                val active = section == selected
                val tint by animateColorAsState(if (active) mint else text.copy(alpha = 0.6f), label = "musicTabTint")
                val fill by animateColorAsState(if (active) mint.copy(alpha = 0.14f) else Color.Transparent,
                    label = "musicTabBackground")
                Column(Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(20.dp)).background(fill)
                    .selectable(selected = active, role = Role.Tab, onClick = {
                        focusManager.clearFocus()
                        onSelect(section)
                    })
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    Icon(when (section) {
                        MusicSection.HOME -> Icons.Default.Home
                        MusicSection.DISCOVER -> Icons.Default.Search
                        MusicSection.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                        MusicSection.LIBRARY -> Icons.Default.LibraryMusic
                    }, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(section.label, color = tint, fontFamily = BricolageGrotesque,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
