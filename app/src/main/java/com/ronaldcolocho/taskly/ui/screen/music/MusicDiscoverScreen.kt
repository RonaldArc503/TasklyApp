package com.ronaldcolocho.taskly.ui.screen.music

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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

private val DiscoverBg = Color(0xFF0C0F1D)
private val DiscoverPillBg = Color(0xFF16192E)
private val DiscoverChipBg = Color(0xFF1E2238)
private val DiscoverPink = Color(0xFFE91E63)
private val DiscoverTextSecondary = Color(0xFF9EA3BA)
private val DiscoverEqualizerBg = listOf(Color(0xFF634D25), Color(0xFF382910))

private data class TrendingMusicCard(
    val title: String,
    val artist: String,
    val searchQuery: String,
    val gradient: List<Color>
)

private val TrendingCatalog = listOf(
    TrendingMusicCard(
        title = "Luces del Malecón",
        artist = "Los Marineros",
        searchQuery = "Luces del Malecón Los Marineros",
        gradient = listOf(Color(0xFFFF7A00), Color(0xFFFF0055))
    ),
    TrendingMusicCard(
        title = "Amanecer Tropical",
        artist = "Mary Sol",
        searchQuery = "Amanecer Tropical Mary Sol",
        gradient = listOf(Color(0xFF536DFE), Color(0xFF00B0FF))
    ),
    TrendingMusicCard(
        title = "Cumbia de Medianoche",
        artist = "Trío Aurora",
        searchQuery = "Cumbia de Medianoche Trío Aurora",
        gradient = listOf(Color(0xFF00B09B), Color(0xFF00C9A7))
    ),
    TrendingMusicCard(
        title = "Baile en la Plaza",
        artist = "Mary Sol",
        searchQuery = "Baile en la Plaza Mary Sol",
        gradient = listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
    ),
    TrendingMusicCard(
        title = "Corazón de Vinilo",
        artist = "Valeria Costa",
        searchQuery = "Corazón de Vinilo Valeria Costa",
        gradient = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
    )
)

private val ExploreGenres = listOf(
    "Reggaetón", "Pop", "Rock", "Cumbia", "Electrónica", "Salsa", "Bachata", "Trap", "Baladas"
)

@Composable
internal fun MusicDiscoverScreen(
    controller: AudioPlayerController,
    onBack: () -> Unit,
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

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.searchDirect(spoken)
            }
        }
    }

    val launchVoiceSearch = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Busca canciones o artistas...")
            }
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            viewModel.feedback("Búsqueda por voz no disponible en este dispositivo.")
        }
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let { snackbar.showSnackbar(it); viewModel.clearFeedback() }
    }

    val isResultsState = state.searchedQuery.isNotBlank() || state.tracks.isNotEmpty() ||
            (state.loading && state.query.isNotBlank()) || state.error != null

    val shimmerBrush = rememberShimmerBrush()

    val filteredTracks = remember(state.tracks, state.selectedCategory) {
        when (state.selectedCategory) {
            MusicSearchCategory.ALL -> state.tracks
            MusicSearchCategory.SONGS -> state.tracks
            MusicSearchCategory.ARTISTS -> state.tracks.filter { it.artist.isNotBlank() }
            MusicSearchCategory.ALBUMS -> state.tracks
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = DiscoverBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isResultsState) {
                // ── VISTA 1: ANTES DE BUSCAR ─────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Descubrir",
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                    }
                }

                item {
                    DiscoverSearchBar(
                        query = state.query,
                        onQueryChange = viewModel::queryChanged,
                        onSearch = { search() },
                        onVoiceSearch = launchVoiceSearch,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Explora por género",
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            items(ExploreGenres) { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(DiscoverChipBg)
                                        .clickable { viewModel.searchDirect(genre) }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.recentSearches.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Búsquedas recientes",
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Borrar todo",
                                    fontSize = 13.sp,
                                    color = DiscoverTextSecondary,
                                    modifier = Modifier.clickable(onClick = viewModel::clearRecentSearches)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            state.recentSearches.forEach { recent ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.searchDirect(recent) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = DiscoverTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = recent,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeRecentSearch(recent) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Eliminar de recientes",
                                            tint = DiscoverTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Tendencias",
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            items(TrendingCatalog) { card ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable { viewModel.searchDirect("${card.title} ${card.artist}") },
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Brush.verticalGradient(card.gradient)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                    Text(
                                        text = card.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = card.artist,
                                        fontSize = 12.sp,
                                        color = DiscoverTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ── VISTA 2: RESULTADOS DE BÚSQUEDA ───────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Descubrir", tint = Color.White)
                        }
                        DiscoverSearchBar(
                            query = state.query,
                            onQueryChange = viewModel::queryChanged,
                            onSearch = { search() },
                            onVoiceSearch = launchVoiceSearch,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    CategoryFilterChips(
                        selected = state.selectedCategory,
                        onSelect = viewModel::selectCategory
                    )
                }

                if (state.loading && state.tracks.isEmpty()) {
                    items(5) {
                        MusicDiscoverSkeletonItem(brush = shimmerBrush)
                    }
                }

                if (state.error != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.search(more = state.tracks.isNotEmpty()) },
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoverPink)
                            ) {
                                Text("Reintentar", color = Color.White)
                            }
                        }
                    }
                }

                if (filteredTracks.isEmpty() && !state.loading && state.error == null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = DiscoverPink)
                            Text(
                                text = "No encontramos resultados",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Prueba con otra canción, género o nombre de artista.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DiscoverTextSecondary
                            )
                        }
                    }
                }

                items(filteredTracks, key = MusicTrack::id) { track ->
                    val isPlayingTrack = player.currentTrack?.id == track.id
                    MusicDiscoverRow(
                        track = track,
                        favorite = track.id in favorites,
                        saving = track.id in state.savingIds,
                        downloaded = track.id in state.downloadedIds,
                        download = downloads[track.id],
                        isPlaying = isPlayingTrack && player.playing,
                        isCurrentTrack = isPlayingTrack,
                        onPlay = {
                            if (isPlayingTrack) controller.toggleCurrent()
                            else playTracks(controller, state.tracks, track)
                        },
                        onFavorite = { viewModel.favorite(track, track.id !in favorites) },
                        onPlaylist = { playlistName = ""; playlistTrack = track },
                        onNext = {
                            controller.addToQueueNext(track.toAudioTrack())
                            viewModel.feedback("Agregada para reproducir después")
                        },
                        onEnd = {
                            controller.addToQueueEnd(track.toAudioTrack())
                            viewModel.feedback("Agregada al final de la cola")
                        },
                        onDownload = { viewModel.download(track) }
                    )
                }

                if (state.loading && state.tracks.isNotEmpty()) {
                    items(2) {
                        MusicDiscoverSkeletonItem(brush = shimmerBrush)
                    }
                }

                if (state.hasMore && state.query.trim() == state.searchedQuery && !state.loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { viewModel.search(more = true) },
                                colors = ButtonDefaults.textButtonColors(contentColor = DiscoverPink)
                            ) {
                                Text("Cargar más canciones", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    playlistTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { playlistTrack = null },
            title = { Text("Guardar en una lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TextButton(
                                onClick = { viewModel.addToPlaylist(track, playlist.id); playlistTrack = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Nueva lista") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createPlaylist(track, playlistName); playlistTrack = null },
                    enabled = playlistName.isNotBlank()
                ) { Text("Crear y guardar") }
            },
            dismissButton = {
                TextButton(onClick = { playlistTrack = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DiscoverSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(DiscoverPillBg)
            .border(1.2.dp, DiscoverPink.copy(alpha = 0.65f), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = DiscoverTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Buscar canciones, artistas...",
                    color = DiscoverTextSecondary,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = BricolageGrotesque
                ),
                cursorBrush = SolidColor(DiscoverPink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Borrar búsqueda",
                    tint = DiscoverTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF262A45))
                .clickable(onClick = onVoiceSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Búsqueda por voz",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CategoryFilterChips(
    selected: MusicSearchCategory,
    onSelect: (MusicSearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(MusicSearchCategory.entries.toTypedArray(), key = { it.name }) { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) DiscoverPink else DiscoverChipBg)
                    .clickable { onSelect(category) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MusicDiscoverRow(
    track: MusicTrack,
    favorite: Boolean,
    saving: Boolean,
    downloaded: Boolean,
    download: MediaDownloadState?,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onPlaylist: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    onDownload: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrentTrack) Brush.verticalGradient(DiscoverEqualizerBg)
                        else Brush.verticalGradient(listOf(Color(0xFF1E2238), Color(0xFF16192E)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack) {
                    if (isPlaying) {
                        PlayingEqualizerBars(color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    if (track.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = track.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) DiscoverPink else Color.White,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                val duration = track.durationSeconds.takeIf { it > 0 }?.let {
                    "${it / 60}:${(it % 60).toString().padStart(2, '0')}"
                }
                val subtitle = if (isCurrentTrack) {
                    "${track.artist.ifBlank { "Taskly" }} · Reproduciendo"
                } else {
                    listOfNotNull(track.artist.takeIf { it.isNotBlank() }, duration).joinToString(" · ")
                }
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) DiscoverPink.copy(alpha = 0.85f) else DiscoverTextSecondary,
                    fontSize = 13.sp
                )
                if (downloaded) {
                    Text(
                        text = "Disponible sin conexión",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (download is MediaDownloadState.Error) {
                    Text(
                        text = "Descarga fallida · Reintenta en Opciones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (download is MediaDownloadState.Queued) {
                    Text(
                        text = "Descarga en cola",
                        style = MaterialTheme.typography.labelSmall,
                        color = DiscoverTextSecondary
                    )
                }
            }

            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones de ${track.name}",
                        tint = DiscoverTextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (favorite) "Quitar de favoritas" else "Guardar en favoritas") },
                        enabled = !saving,
                        leadingIcon = {
                            Icon(
                                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (favorite) DiscoverPink else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { menu = false; onFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text("Guardar en una lista") },
                        enabled = !saving,
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = { menu = false; onPlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text("Reproducir después") },
                        leadingIcon = { Icon(Icons.Default.QueuePlayNext, null) },
                        onClick = { menu = false; onNext() }
                    )
                    DropdownMenuItem(
                        text = { Text("Agregar al final de la cola") },
                        leadingIcon = { Icon(Icons.Default.AddToQueue, null) },
                        onClick = { menu = false; onEnd() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (downloaded) "Descargada" else "Descargar sin conexión") },
                        enabled = !downloaded && download !is MediaDownloadState.Downloading && download !is MediaDownloadState.Queued,
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = { menu = false; onDownload() }
                    )
                }
            }
        }
        if (download is MediaDownloadState.Downloading) {
            LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = DiscoverPink
            )
        }
    }
}

@Composable
private fun PlayingEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val transition = rememberInfiniteTransition(label = "eqAnim")
    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight(h1)
                .background(color, RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight(h2)
                .background(color, RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight(h3)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E2238),
            Color(0xFF2D3354),
            Color(0xFF1E2238)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

@Composable
private fun MusicDiscoverSkeletonItem(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}
