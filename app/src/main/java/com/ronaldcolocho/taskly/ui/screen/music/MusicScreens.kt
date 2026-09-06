package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioRepeatMode
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack

// ─────────────────────────────────────────────────────────────
// MusicScreen — REDESIGN VISUAL
// ─────────────────────────────────────────────────────────────
@Composable
fun MusicScreen(
    audioController: AudioPlayerController,
    onNavigateBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val tracks by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val playerState by audioController.state.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var playlistQuery by remember { mutableStateOf("") }
    var playlistOrder by remember { mutableStateOf("Recientes") }
    var searchExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    val orderOptions = listOf("Recientes", "Antiguas", "A-Z", "Z-A")

    val visiblePlaylists = remember(playlists, playlistQuery, playlistOrder) {
        playlists
            .filter { it.name.contains(playlistQuery, ignoreCase = true) }
            .sortedWith(
                when (playlistOrder) {
                    "Antiguas" -> compareBy<MusicPlaylist> { it.createdAt }
                    "A-Z" -> compareBy { it.name.lowercase() }
                    "Z-A" -> compareByDescending<MusicPlaylist> { it.name.lowercase() }
                    else -> compareByDescending<MusicPlaylist> { it.updatedAt }
                }
            )
    }

    // Chunk playlists into rows of 2 for the grid
    val playlistRows = remember(visiblePlaylists) { visiblePlaylists.chunked(2) }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear lista")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Top bar ────────────────────────────────────────
            item {
                MusicTopBar(
                    onBack = onNavigateBack,
                    searchExpanded = searchExpanded,
                    searchQuery = playlistQuery,
                    onSearchQueryChange = { playlistQuery = it },
                    onSearchToggle = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) {
                            playlistQuery = ""
                            focusManager.clearFocus()
                        }
                    },
                    focusRequester = searchFocusRequester
                )
            }

            // ── Error banner ────────────────────────────────────
            if (error != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    error.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearError) {
                                    Text("Cerrar", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }

            // ── "Las más escuchadas" section ────────────────────
            item {
                MusicSectionLabel(
                    text = "Las más escuchadas",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                )
            }

            if (tracks.isEmpty()) {
                item {
                    EmptyMostPlayed(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    MostPlayedCarousel(
                        tracks = tracks,
                        currentTrackId = playerState.currentTrack?.id,
                        isPlaying = playerState.playing,
                        onTrackClick = { track -> playTracks(audioController, tracks, track) }
                    )
                }
            }

            // ── "Mis listas" section header ─────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mis listas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${playlists.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Sort chips ─────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    items(orderOptions) { option ->
                        FilterChip(
                            selected = playlistOrder == option,
                            onClick = { playlistOrder = option },
                            label = {
                                Text(
                                    option,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // ── Empty playlists state ──────────────────────────
            if (visiblePlaylists.isEmpty()) {
                item {
                    EmptyPlaylistsState(
                        hasQuery = playlistQuery.isNotBlank(),
                        onCreatePlaylist = { showCreate = true },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            }

            // ── Playlist grid (2 columns via chunked rows) ─────
            items(playlistRows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            onRename = { name -> viewModel.renamePlaylist(playlist.id, name) },
                            onDelete = { viewModel.deletePlaylist(playlist.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill second slot if odd count
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createPlaylist(name) { id ->
                    showCreate = false
                    onOpenPlaylist(id)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// MusicScreen sub-composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun MusicTopBar(
    onBack: () -> Unit,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically(),
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar playlists", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
        }

        if (!searchExpanded) {
            Text(
                "Mis Músicas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onSearchToggle) {
            Icon(
                if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchExpanded) "Cerrar búsqueda" else "Buscar",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun MusicSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

// ── Most Played Horizontal Carousel ───────────────────────────
@Composable
private fun MostPlayedCarousel(
    tracks: List<MusicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val isActive = track.id == currentTrackId
            MostPlayedCard(
                track = track,
                rank = index + 1,
                isActive = isActive,
                isPlaying = isActive && isPlaying,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

@Composable
private fun MostPlayedCard(
    track: MusicTrack,
    rank: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = tween(300),
        label = "cardBorder"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .scale(cardScale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Cover art with active border highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                )
            }

            // Active accent border overlay
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Rank badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Playing indicator dots (bottom-right)
            if (isPlaying) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height((6 + i * 3).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            track.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        if (track.playCount > 0) {
            Text(
                "${track.playCount} repr.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp),
                maxLines = 1
            )
        }
    }
}

// ── Playlist Grid Card ─────────────────────────────────────────
@Composable
private fun PlaylistGridCard(
    playlist: MusicPlaylist,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover art (square, 1:1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
            }

            // Gradient overlay at bottom for readability if there's a real image
            if (playlist.coverUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
            }

            // Options button (top-right, non-interfering with click)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = if (playlist.coverUrl.isNotBlank())
                            Color.White.copy(alpha = 0.9f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        onClick = { showRenameDialog = true; showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }

        // Name and count
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songCount} canciones",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { name -> onRename(name); showRenameDialog = false }
        )
    }
}

// ── Empty states ───────────────────────────────────────────────
@Composable
private fun EmptyMostPlayed(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp)
        )
        Text(
            "Reproduce audios de tus chats para verlos aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyPlaylistsState(
    hasQuery: Boolean,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            if (hasQuery) "Sin resultados" else "Todavía no tienes listas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (hasQuery) "Prueba con otro nombre."
            else "Crea una lista para organizar tu música.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (!hasQuery) {
            TextButton(onClick = onCreatePlaylist) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nueva lista")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PlaylistDetailScreen — visual redesign (unchanged from previous)
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistDetailScreen(
    audioController: AudioPlayerController,
    onNavigateBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val playerState by audioController.state.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == viewModel.selectedPlaylistId }

    var showAdd by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    LaunchedEffect(showAdd) { if (showAdd) viewModel.loadDownloadedTracks() }

    val progress = if (playerState.durationMs > 0)
        playerState.currentTimeMs.toFloat() / playerState.durationMs.toFloat()
    else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            PlaylistTopBar(
                title = playlist?.name ?: "Lista",
                onBack = onNavigateBack
            )
        }

        if (error != null) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::clearError) {
                                Text("Cerrar", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }

        item {
            PlaylistHero(
                playlist = playlist,
                trackCount = tracks.size,
                playerState = playerState,
                onPlayAll = { if (tracks.isNotEmpty()) playTracks(audioController, tracks, tracks.first()) },
                onShuffle = {
                    val shuffled = tracks.shuffled()
                    if (shuffled.isNotEmpty()) playTracks(audioController, shuffled, shuffled.first())
                },
                onRepeat = audioController::cycleRepeatMode,
                onTimer = { showSleepTimer = true },
                onAdd = { showAdd = true }
            )
        }

        val nowPlaying = playerState.currentTrack
        val isPlayingFromThisList = nowPlaying != null && tracks.any { it.id == nowPlaying.id }
        if (isPlayingFromThisList && playerState.durationMs > 0) {
            item {
                NowPlayingBar(
                    track = nowPlaying!!,
                    progress = progress,
                    isPlaying = playerState.playing
                )
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Canciones",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${tracks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (tracks.isEmpty()) {
            item { EmptyPlaylistState(onAdd = { showAdd = true }) }
        }

        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val isActive = playerState.currentTrack?.id == track.id
            PlaylistTrackItem(
                track = track,
                index = index + 1,
                isActive = isActive,
                isPlaying = isActive && playerState.playing,
                onClick = { playTracks(audioController, tracks, track) },
                onRemove = { viewModel.removeTrack(track) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showAdd) {
        DownloadedAudioDialog(
            tracks = downloadedTracks,
            existingIds = tracks.mapTo(mutableSetOf()) { it.id },
            onAdd = viewModel::addTrack
        ) { showAdd = false }
    }
    if (showSleepTimer) {
        SleepTimerDialog(
            active = playerState.sleepTimerEndAt != null,
            onSet = { minutes -> audioController.startSleepTimer(minutes * 60_000L); showSleepTimer = false },
            onCancel = { audioController.cancelSleepTimer(); showSleepTimer = false }
        ) { showSleepTimer = false }
    }
}

// ─────────────────────────────────────────────────────────────
// PlaylistDetailScreen sub-composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlaylistHero(
    playlist: MusicPlaylist?,
    trackCount: Int,
    playerState: com.ronaldcolocho.taskly.audio.AudioPlayerState,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onTimer: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = playlist?.coverUrl ?: ""
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            playlist?.name ?: "Lista",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$trackCount canciones",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Shuffle, contentDescription = "Aleatorio")
            }
            Button(
                onClick = onPlayAll,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reproducir", fontWeight = FontWeight.SemiBold)
            }
            FilledTonalIconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (playerState.repeatMode == AudioRepeatMode.ONE)
                        Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repetir",
                    tint = if (playerState.repeatMode != AudioRepeatMode.OFF)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onTimer, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Temporizador",
                    tint = if (playerState.sleepTimerEndAt != null)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Agregar cancion", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    track: AudioTrack,
    progress: Float,
    isPlaying: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Reproduciendo ahora",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + i * 0.3f))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun PlaylistTrackItem(
    track: MusicTrack,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else Color.Transparent,
        animationSpec = tween(300),
        label = "trackBg"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (isActive) 1.01f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "trackScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (isActive) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                track.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (track.playCount > 0) "${track.playCount} repr." else "Audio guardado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (track.durationSeconds > 0) {
                    val min = track.durationSeconds / 60
                    val sec = track.durationSeconds % 60
                    Text(
                        "%d:%02d".format(min, sec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Quitar de la lista") },
                    onClick = { onRemove(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            "Sin canciones aún",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Agrega canciones desde Las más escuchadas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Agregar canciones")
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared dialogs
// ─────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerDialog(
    active: Boolean,
    onSet: (Long) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Apagado temporal") },
    text = {
        Column {
            listOf(15L to "15 min", 30L to "30 min", 60L to "1 hora", 120L to "2 horas").forEach { (minutes, label) ->
                TextButton(onClick = { onSet(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    },
    confirmButton = { if (active) TextButton(onClick = onCancel) { Text("Cancelar temporizador") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
)

@Composable
private fun DownloadedAudioDialog(
    tracks: List<MusicTrack>,
    existingIds: Set<String>,
    onAdd: (MusicTrack) -> Unit,
    onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Agregar canciones") },
    text = {
        if (tracks.isEmpty()) {
            Text("No tienes audios descargados. Los audios que descargues desde tus chats apareceran aqui.")
        } else {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(260.dp)) {
                items(tracks, key = { it.id }) { track ->
                    val exists = track.id in existingIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !exists) { onAdd(track) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallCover(Modifier.size(42.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Audio descargado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exists) {
                            Icon(Icons.Default.Check, contentDescription = "Agregado", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
)

@Composable
private fun PlaylistNameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva lista") },
        text = {
            OutlinedTextField(
                name, { name = it },
                label = { Text("Nombre de la lista") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar lista") },
        text = {
            OutlinedTextField(
                name, { name = it },
                label = { Text("Nuevo nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ─────────────────────────────────────────────────────────────
// Utility composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun SmallCover(modifier: Modifier, url: String = "") = Box(
    modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    contentAlignment = Alignment.Center
) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private fun playTracks(controller: AudioPlayerController, tracks: List<MusicTrack>, selected: MusicTrack) {
    val audioTracks = tracks.map {
        AudioTrack(it.id, it.url, it.name, it.sourceMessageId, it.durationSeconds, it.thumbnailUrl)
    }
    controller.updatePlaylist(audioTracks)
    audioTracks.firstOrNull { it.id == selected.id }?.let(controller::playWithQueue)
}
