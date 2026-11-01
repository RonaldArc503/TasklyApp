package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

@Composable
internal fun MusicDiscoverScreen(
    controller: AudioPlayerController, onBack: () -> Unit,
    viewModel: MusicDiscoverViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadStates.collectAsStateWithLifecycle()
    val player by controller.state.collectAsStateWithLifecycle()
    var playlistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val search = { keyboard?.hide(); viewModel.search() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { snackbar.showSnackbar(it); viewModel.clearFeedback() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                    Column(Modifier.weight(1f)) {
                        Text("Descubre música", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("Busca canciones y artistas en YouTube Music", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.query, onValueChange = viewModel::queryChanged,
                        label = { Text("Canción o artista") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.queryChanged("") }) {
                            Icon(Icons.Default.Close, "Limpiar búsqueda") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = { search() }, enabled = state.query.isNotBlank() && !state.loading,
                        modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Search, "Buscar música") }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) }
            if (state.error != null) item {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.search(more = state.tracks.isNotEmpty()) }) { Text("Reintentar") }
                }
            }
            if (state.tracks.isEmpty() && !state.loading && state.error == null) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (state.searchedQuery.isBlank()) "Tu próxima canción empieza aquí" else "No encontramos canciones",
                        style = MaterialTheme.typography.titleLarge)
                    Text(if (state.searchedQuery.isBlank()) "Escucha al tocar una canción. Guarda tus favoritas o descárgalas para escucharlas sin conexión."
                        else "Prueba con otro título o nombre de artista.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.tracks.isNotEmpty()) item {
                Text("Resultados para “${state.searchedQuery}”", modifier = Modifier.padding(horizontal = 22.dp),
                    style = MaterialTheme.typography.labelLarge)
            }
            items(state.tracks, key = MusicTrack::id) { track ->
                MusicDiscoverRow(track, track.id in favorites, track.id in state.savingIds,
                    track.id in state.downloadedIds, downloads[track.id],
                    player.currentTrack?.id == track.id && player.playing,
                    onPlay = {
                        if (player.currentTrack?.id == track.id) controller.toggleCurrent()
                        else playTracks(controller, state.tracks, track)
                    },
                    onFavorite = { viewModel.favorite(track, track.id !in favorites) },
                    onPlaylist = { playlistName = ""; playlistTrack = track },
                    onNext = { controller.addToQueueNext(track.toAudioTrack()); viewModel.feedback("Agregada para reproducir después") },
                    onEnd = { controller.addToQueueEnd(track.toAudioTrack()); viewModel.feedback("Agregada al final de la cola") },
                    onDownload = { viewModel.download(track) })
            }
            if (state.hasMore && state.query.trim() == state.searchedQuery) item {
                TextButton(onClick = { viewModel.search(more = true) }, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()) { Text("Cargar más canciones") }
            }
        }
    }
    playlistTrack?.let { track ->
        AlertDialog(onDismissRequest = { playlistTrack = null }, title = { Text("Guardar en una lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TextButton(onClick = { viewModel.addToPlaylist(track, playlist.id); playlistTrack = null },
                                modifier = Modifier.fillMaxWidth()) { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    OutlinedTextField(value = playlistName, onValueChange = { playlistName = it },
                        label = { Text("Nueva lista") }, singleLine = true)
                }
            }, confirmButton = {
                TextButton(onClick = { viewModel.createPlaylist(track, playlistName); playlistTrack = null },
                    enabled = playlistName.isNotBlank()) { Text("Crear y guardar") }
            }, dismissButton = { TextButton(onClick = { playlistTrack = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun MusicDiscoverRow(
    track: MusicTrack, favorite: Boolean, saving: Boolean, downloaded: Boolean,
    download: MediaDownloadState?, playing: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit,
    onPlaylist: () -> Unit, onNext: () -> Unit, onEnd: () -> Unit, onDownload: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null)
                    AsyncImage(track.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    if (playing) Icon(Icons.Default.Pause, "Pausar canción", tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    val duration = track.durationSeconds.takeIf { it > 0 }?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
                    Text(listOfNotNull(track.artist.takeIf { it.isNotBlank() }, duration).joinToString(" · "),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    if (downloaded) Text("Disponible sin conexión", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    else if (download is MediaDownloadState.Error) Text("Descarga fallida · Reintenta en Opciones",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    else if (download is MediaDownloadState.Queued) Text("Descarga en cola", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onFavorite, enabled = !saving) { Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (favorite) "Quitar de favoritas" else "Guardar en favoritas",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de ${track.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Guardar en una lista") }, enabled = !saving,
                            onClick = { menu = false; onPlaylist() })
                        DropdownMenuItem(text = { Text("Reproducir después") }, onClick = { menu = false; onNext() })
                        DropdownMenuItem(text = { Text("Agregar al final de la cola") }, onClick = { menu = false; onEnd() })
                        DropdownMenuItem(text = { Text(if (downloaded) "Descargada" else "Descargar sin conexión") },
                            enabled = !downloaded && download !is MediaDownloadState.Downloading && download !is MediaDownloadState.Queued,
                            onClick = { menu = false; onDownload() })
                    }
                }
            }
            if (download is MediaDownloadState.Downloading) LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
