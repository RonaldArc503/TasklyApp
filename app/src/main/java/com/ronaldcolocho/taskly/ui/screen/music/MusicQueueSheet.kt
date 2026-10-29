package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ronaldcolocho.taskly.audio.AudioTrack
import kotlinx.coroutines.launch

@Composable
internal fun EditableMusicQueue(
    tracks: List<AudioTrack>, currentId: String?,
    shuffleEnabled: Boolean,
    onSelect: (String) -> Unit, onReorder: (List<AudioTrack>) -> Unit, onRemove: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf(tracks) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedCenter by remember { mutableFloatStateOf(0f) }
    val latestReorder by rememberUpdatedState(onReorder)
    val latestTracks by rememberUpdatedState(tracks)
    LaunchedEffect(tracks) {
        // External transitions and removals cancel an in-progress edit.
        draggedId = null
        preview = tracks
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text("Cola de reproducción", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
        Text("Mantén presionado el asa para arrastrar una canción.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        if (shuffleEnabled) Text("Al organizar la cola se desactiva el modo aleatorio.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            itemsIndexed(preview, key = { _, track -> track.id }) { index, track ->
                var menu by remember { mutableStateOf(false) }
                val isDragged = draggedId == track.id
                Row(Modifier.fillMaxWidth().zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }
                        translationY = if (isDragged && visibleItem != null)
                            draggedCenter - (visibleItem.offset + visibleItem.size / 2f) else 0f
                    }
                    .background(if (track.id == currentId || isDragged) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(track.id) }.padding(horizontal = 8.dp).heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DragHandle, "Arrastrar ${track.name}",
                        modifier = Modifier.size(48.dp).padding(12.dp).pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }
                                    if (row != null) {
                                        draggedId = track.id
                                        draggedCenter = row.offset + row.size / 2f
                                    }
                                },
                                onDragCancel = { draggedId = null; preview = latestTracks },
                                onDragEnd = {
                                    draggedId = null
                                    if (preview != latestTracks) latestReorder(preview)
                                },
                                onDrag = drag@ { change, amount ->
                                    if (draggedId != track.id) return@drag
                                    change.consume()
                                    draggedCenter += amount.y
                                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        draggedCenter >= it.offset && draggedCenter < it.offset + it.size
                                    }
                                    val from = preview.indexOfFirst { it.id == track.id }
                                    if (target != null && from >= 0 && target.index != from) {
                                        preview = preview.toMutableList().apply {
                                            val moved = removeAt(from)
                                            add(target.index.coerceIn(0, size), moved)
                                        }
                                    }
                                    val layout = listState.layoutInfo
                                    val edge = 56.dp.toPx()
                                    val scroll = when {
                                        draggedCenter < layout.viewportStartOffset + edge -> -20f
                                        draggedCenter > layout.viewportEndOffset - edge -> 20f
                                        else -> 0f
                                    }
                                    if (scroll != 0f) scope.launch { listState.scrollBy(scroll) }
                                }
                            )
                        })
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        if (track.id == currentId) Text("Canción actual", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de ${track.name}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Subir") }, enabled = index > 0, onClick = {
                                val reordered = preview.toMutableList().apply { add(index - 1, removeAt(index)) }
                                preview = reordered; onReorder(reordered); menu = false
                            })
                            DropdownMenuItem(text = { Text("Bajar") }, enabled = index < preview.lastIndex, onClick = {
                                val reordered = preview.toMutableList().apply { add(index + 1, removeAt(index)) }
                                preview = reordered; onReorder(reordered); menu = false
                            })
                            DropdownMenuItem(text = { Text("Quitar de la cola") }, onClick = {
                                onRemove(track.id); menu = false
                            })
                        }
                    }
                }
            }
        }
        if (preview.isEmpty()) Text("La cola está vacía.", modifier = Modifier.padding(22.dp))
    }
}
