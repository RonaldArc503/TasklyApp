package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.*
import java.util.Locale

internal fun MusicTrack.toAudioTrack(playlistId: String? = null) = AudioTrack(
    id, url, name, sourceMessageId, durationSeconds, thumbnailUrl, playlistId, artist
)

internal fun formatMusicBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}

internal fun LazyListScope.musicLibraryItems(
    state: MusicLibraryState,
    visible: List<MusicLibraryItem>,
    filter: MusicLibraryFilter,
    order: MusicLibraryOrder,
    downloadStates: Map<String, MediaDownloadState>,
    onFilter: (MusicLibraryFilter) -> Unit,
    onOrder: (MusicLibraryOrder) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (MusicLibraryItem) -> Unit,
    onFavorite: (MusicLibraryItem) -> Unit,
    onDownload: (MusicLibraryItem) -> Unit,
    onNext: (MusicLibraryItem) -> Unit,
    onEnd: (MusicLibraryItem) -> Unit,
    onRecover: () -> Unit
) {
    item(key = "music_library_controls") {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mi biblioteca", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                var showOrder by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { showOrder = true }) { Text(order.label) }
                    DropdownMenu(expanded = showOrder, onDismissRequest = { showOrder = false }) {
                        MusicLibraryOrder.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { onOrder(option); showOrder = false })
                        }
                    }
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar biblioteca") }
            }
            val offline = state.items.filter { it.availableOffline }.distinctBy { it.localPath }
            Text("${offline.size} archivos disponibles · ${formatMusicBytes(offline.sumOf { it.sizeBytes })}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MusicLibraryFilter.entries) { option ->
                    FilterChip(selected = filter == option, onClick = { onFilter(option) }, label = { Text(option.label) })
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp))
            else if (visible.isEmpty()) Text(
                when (filter) {
                    MusicLibraryFilter.FAVORITES -> "Aquí aparecerán tus favoritas. Marca el corazón de una canción."
                    MusicLibraryFilter.OFFLINE -> "No hay canciones descargadas que coincidan con la búsqueda."
                    MusicLibraryFilter.MISSING -> "No hay archivos pendientes de recuperar que coincidan con la búsqueda."
                    else -> "No hay canciones que coincidan con la búsqueda."
                }, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
    items(visible, key = { "music_library_${it.track.id}" }) { item ->
        MusicLibraryRow(item, downloadStates[item.track.id],
            onPlay = { onPlay(item) }, onFavorite = { onFavorite(item) },
            onDownload = { if (item.canDownload) onDownload(item) else onRecover() },
            onNext = { onNext(item) }, onEnd = { onEnd(item) })
    }
}

@Composable
private fun MusicLibraryRow(
    item: MusicLibraryItem, download: MediaDownloadState?, onPlay: () -> Unit,
    onFavorite: () -> Unit, onDownload: () -> Unit, onNext: () -> Unit, onEnd: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val playable = item.availableOffline || item.canDownload
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(enabled = playable, onClick = onPlay)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.availableOffline) Icons.Default.OfflinePin else if (item.needsDownload)
                    Icons.Default.WarningAmber else Icons.Default.MusicNote,
                    contentDescription = if (item.availableOffline) "Disponible sin conexión" else "Sin descarga local",
                    modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.track.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        download is MediaDownloadState.Queued -> "En cola de descarga"
                        download is MediaDownloadState.Downloading -> "Descargando ${(download.progress * 100).toInt()} %"
                        download is MediaDownloadState.Error -> "Descarga fallida · Reintentar"
                        item.availableOffline -> "Sin conexión · ${formatMusicBytes(item.sizeBytes)}"
                        item.needsDownload -> "Archivo local no encontrado · Volver a descargar"
                        else -> "Disponible en línea"
                    }, maxLines = 2, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFavorite) { Icon(
                    if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (item.favorite) "Quitar de favoritas" else "Guardar en favoritas",
                    tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de ${item.track.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Reproducir después") }, enabled = playable,
                            onClick = { onNext(); menu = false })
                        DropdownMenuItem(text = { Text("Agregar al final") }, enabled = playable,
                            onClick = { onEnd(); menu = false })
                        if (!item.availableOffline) DropdownMenuItem(
                            text = { Text(if (item.canDownload) "Descargar" else "Recuperar desde el conversor") },
                            enabled = download !is MediaDownloadState.Downloading && download !is MediaDownloadState.Queued,
                            onClick = { onDownload(); menu = false })
                    }
                }
            }
            if (download is MediaDownloadState.Downloading) LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
