package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.domain.model.*

@Composable
internal fun MusicDownloadBadge(
    offline: Boolean, state: MediaDownloadState?, name: String, onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (offline || state is MediaDownloadState.Downloaded) return
    val busy = state is MediaDownloadState.Downloading || state is MediaDownloadState.Queued
    IconButton(onClick = onDownload, enabled = !busy, modifier = modifier.size(48.dp)) {
        Box(Modifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center) {
                when (state) {
                    is MediaDownloadState.Downloading -> {
                        val progress = state.progress.coerceIn(0f, 1f)
                        val progressModifier = Modifier.size(18.dp).semantics {
                            contentDescription = "Descargando $name: ${(progress * 100).toInt()} %"
                        }
                        if (progress > 0f) CircularProgressIndicator(progress = { progress },
                            modifier = progressModifier, color = Color.White, strokeWidth = 2.dp)
                        else CircularProgressIndicator(modifier = progressModifier, color = Color.White, strokeWidth = 2.dp)
                    }
                    MediaDownloadState.Queued -> Icon(Icons.Default.Schedule, "Descarga de $name en cola",
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    is MediaDownloadState.Error -> Icon(Icons.Default.Refresh, "Reintentar descarga de $name: ${state.message}",
                        tint = Color(0xFFFFCA80), modifier = Modifier.size(18.dp))
                    else -> Icon(Icons.Default.Download, "Descargar $name", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddMusicToPlaylistDialog(track: MusicTrack?, actions: MusicTrackActionsViewModel, onDismiss: () -> Unit) {
    track ?: return
    val playlists by actions.playlists.collectAsStateWithLifecycle()
    val busy by actions.busy.collectAsStateWithLifecycle()
    val notice by actions.notice.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Agregar a lista") },
        text = {
            Column {
                if (playlists.isEmpty()) Text("Crea una lista en Mis listas para agregar esta canción.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        TextButton(enabled = !busy, onClick = { actions.add(track, playlist, onDismiss) },
                            modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") } })
}

/** Exactly the same two actions for the current track and the library. */
@Composable
internal fun MusicTrackActionItems(offline: Boolean, download: MediaDownloadState?, onAdd: () -> Unit, onDownload: () -> Unit) {
    DropdownMenuItem(text = { Text("Agregar a lista") }, onClick = onAdd)
    DropdownMenuItem(text = { Text(when {
        offline || download is MediaDownloadState.Downloaded -> "Disponible sin conexión"
        download is MediaDownloadState.Queued -> "En cola de descarga"
        download is MediaDownloadState.Downloading -> "Descargando ${(download.progress * 100).toInt()} %"
        download is MediaDownloadState.Error -> "Reintentar descarga sin conexión"
        else -> "Descargar sin conexión"
    }) }, enabled = !offline && download !is MediaDownloadState.Downloaded &&
        download !is MediaDownloadState.Downloading && download !is MediaDownloadState.Queued,
        onClick = onDownload)
}

@Composable
internal fun MusicPlayerTrackMenu(track: MusicTrack?, actions: MusicTrackActionsViewModel, iconTint: Color) {
    var expanded by remember(track?.id) { mutableStateOf(false) }
    var adding by remember { mutableStateOf<MusicTrack?>(null) }
    val states by actions.downloadStates.collectAsStateWithLifecycle()
    val notice by actions.notice.collectAsStateWithLifecycle()
    LaunchedEffect(track?.id) { track?.let(actions::inspect) }
    Box {
        IconButton(enabled = track != null, onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "Opciones de canción", tint = iconTint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MusicTrackActionItems(false, states[track?.id] ?: track?.let { actions.downloadState(it.id) },
                onAdd = { adding = track; expanded = false },
                onDownload = { track?.let(actions::download); expanded = false })
        }
    }
    AddMusicToPlaylistDialog(adding, actions) { adding = null }
    if (adding == null) MusicActionNotice(notice, actions::clearNotice)
}
