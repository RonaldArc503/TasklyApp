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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioRepeatMode
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundDark
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundLight
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceLight
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryDark
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryLight
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceLight

// ─────────────────────────────────────────────────────────────
// MusicScreen — REDESIGN VISUAL MAREA
// ─────────────────────────────────────────────────────────────
@Composable
fun MusicScreen(
    audioController: AudioPlayerController,
    onNavigateBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: (String) -> Unit = {},
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

    // Accessibility check
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) > 0f
        } catch (_: Exception) {
            true
        }
    }

    // Marea Theme Tokens
    val darkTheme = isSystemInDarkTheme()
    val background = if (darkTheme) MareaBackgroundDark else MareaBackgroundLight
    val surface    = if (darkTheme) MareaSurfaceDark    else MareaSurfaceLight
    val onSurface  = if (darkTheme) MareaOnSurfaceDark  else MareaOnSurfaceLight
    val secondary  = if (darkTheme) MareaSecondaryDark  else MareaSecondaryLight
    val lilac      = Color(0xFF8E80BA)  // Soft muted velvety lavender
    val peach      = Color(0xFFD49B85)  // Soft warm terracotta-almond
    val mint       = Color(0xFF70A896)  // Soft muted sage/mint
    val onAccent   = Color(0xFF0F2233)  // Deep readable dark navy

    // Continuous monotonic time driven by frame clock for fluid living tide in background
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameTimeNanos - lastNanos) / 1_000_000_000f
                    animTimeSec += dt.coerceIn(0f, 0.05f)
                }
                lastNanos = frameTimeNanos
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Living ambient aura (calm ocean tides)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val phase = animTimeSec * 0.35f
            val ox1 = (kotlin.math.sin(phase) * 50f).toFloat()
            val oy1 = (kotlin.math.cos(phase * 0.7f) * 35f).toFloat()
            val ox2 = (kotlin.math.cos(phase * 0.6f) * 45f).toFloat()
            val oy2 = (kotlin.math.sin(phase * 0.8f) * 40f).toFloat()

            // Lilac glow at top right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lilac.copy(alpha = if (darkTheme) 0.12f else 0.07f), Color.Transparent),
                    center = Offset(w * 0.85f + ox1, h * 0.12f + oy1),
                    radius = w * 0.75f
                )
            )

            // Mint glow at middle left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(mint.copy(alpha = if (darkTheme) 0.10f else 0.05f), Color.Transparent),
                    center = Offset(w * 0.15f + ox2, h * 0.48f + oy2),
                    radius = w * 0.70f
                )
            )

            // Peach glow at bottom right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(peach.copy(alpha = if (darkTheme) 0.09f else 0.04f), Color.Transparent),
                    center = Offset(w * 0.78f - ox1, h * 0.82f - oy1),
                    radius = w * 0.65f
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (playerState.currentTrack != null) 124.dp else 92.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Top Bar ─────────────────────────────────────────
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
                    focusRequester = searchFocusRequester,
                    surface = surface,
                    onSurface = onSurface,
                    secondary = secondary,
                    mint = mint,
                    onAccent = onAccent
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(surface)
                                .border(1.dp, Color(0xFFE57373).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    error.orEmpty(),
                                    color = onSurface,
                                    fontFamily = BricolageGrotesque,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearError) {
                                    Text(
                                        "Cerrar",
                                        color = mint,
                                        fontFamily = BricolageGrotesque,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── "Las más escuchadas" section header ──────────────
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Las más escuchadas",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = onSurface
                    )
                    if (tracks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(mint.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${tracks.size}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = mint
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (tracks.isEmpty()) {
                item {
                    EmptyMostPlayed(
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        mint = mint,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }
            } else {
                item {
                    MostPlayedCarousel(
                        tracks = tracks,
                        currentTrackId = playerState.currentTrack?.id,
                        isPlaying = playerState.playing,
                        animationsEnabled = animationsEnabled,
                        surface = surface,
                        onSurface = onSurface,
                        mint = mint,
                        lilac = lilac,
                        onTrackClick = { track ->
                            playTracks(audioController, tracks, track)
                            onOpenPlayer("Las más escuchadas")
                        }
                    )
                }
            }

            // ── "Mis listas" section header ─────────────────────
            item {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Mis listas",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = BricolageGrotesque,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(secondary.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${playlists.size}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Quick "+ Nueva" pill in header
                    MareaScaleButton(
                        onClick = { showCreate = true },
                        shape = RoundedCornerShape(16.dp),
                        color = mint.copy(alpha = 0.15f),
                        modifier = Modifier
                            .height(34.dp)
                            .border(1.dp, mint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = mint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Nueva",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = mint
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── Sort chips ─────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    items(orderOptions) { option ->
                        val isSelected = playlistOrder == option
                        MareaScaleButton(
                            onClick = { playlistOrder = option },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) mint else surface,
                            modifier = Modifier
                                .height(36.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) mint else secondary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            Text(
                                text = option,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) onAccent else onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // ── Empty playlists state ──────────────────────────
            if (visiblePlaylists.isEmpty()) {
                item {
                    EmptyPlaylistsState(
                        hasQuery = playlistQuery.isNotBlank(),
                        searchQuery = playlistQuery,
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        mint = mint,
                        onAccent = onAccent,
                        onCreatePlaylist = { showCreate = true },
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
                    )
                }
            }

            // ── Playlist grid (2 columns via chunked rows) ─────
            items(playlistRows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            surface = surface,
                            onSurface = onSurface,
                            secondary = secondary,
                            mint = mint,
                            onClick = { onOpenPlaylist(playlist.id) },
                            onRename = { name -> viewModel.renamePlaylist(playlist.id, name) },
                            onDelete = { viewModel.deletePlaylist(playlist.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // ── Floating Action Button (Nueva lista) ─────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 20.dp,
                    bottom = if (playerState.currentTrack != null) 92.dp else 24.dp
                )
        ) {
            MareaScaleButton(
                onClick = { showCreate = true },
                shape = RoundedCornerShape(24.dp),
                color = mint,
                modifier = Modifier
                    .height(50.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Crear lista",
                        tint = onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Nueva lista",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = onAccent
                    )
                }
            }
        }

        // ── Sticky Floating Mini Player at Bottom ───────────
        val currentTrack = playerState.currentTrack
        if (currentTrack != null) {
            val dur = if (playerState.durationMs > 0) playerState.durationMs
            else (currentTrack.durationSeconds.takeIf { it > 0 }?.toLong()?.times(1000L)) ?: 0L
            val progress = if (dur > 0)
                (playerState.currentTimeMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            else 0f

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                MareaFloatingMiniPlayer(
                    track       = currentTrack,
                    isPlaying   = playerState.playing,
                    progress    = progress,
                    surface     = surface,
                    onSurface   = onSurface,
                    secondary   = secondary,
                    lilac       = lilac,
                    peach       = peach,
                    onAccent    = onAccent,
                    darkTheme   = darkTheme,
                    onPlayPause = { audioController.toggleCurrent() },
                    onClick     = { onOpenPlayer("Música") }
                )
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            surface = surface,
            onSurface = onSurface,
            secondary = secondary,
            mint = mint,
            onAccent = onAccent,
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
    focusRequester: FocusRequester,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    onAccent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MareaIconButton(
            onClick = onBack,
            contentDescription = "Volver",
            surface = surface,
            size = 44.dp
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically(),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(surface)
                    .border(1.dp, mint.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = mint,
                        modifier = Modifier.size(18.dp)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "Buscar listas...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontSize = 14.sp
                                ),
                                color = onSurface.copy(alpha = 0.45f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontSize = 14.sp,
                            color = onSurface
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = mint
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { })
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!searchExpanded) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Música",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tus listas y reproducciones",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontSize = 12.sp
                    ),
                    color = onSurface.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }
        }

        MareaIconButton(
            onClick = onSearchToggle,
            contentDescription = if (searchExpanded) "Cerrar búsqueda" else "Buscar",
            surface = surface,
            size = 44.dp
        ) {
            Icon(
                if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                contentDescription = null,
                tint = if (searchExpanded) onSurface else mint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Most Played Horizontal Carousel ───────────────────────────
@Composable
private fun MostPlayedCarousel(
    tracks: List<MusicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    surface: Color,
    onSurface: Color,
    mint: Color,
    lilac: Color,
    onTrackClick: (MusicTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val isActive = track.id == currentTrackId
            MostPlayedCard(
                track = track,
                rank = index + 1,
                isActive = isActive,
                isPlaying = isActive && isPlaying,
                animationsEnabled = animationsEnabled,
                surface = surface,
                onSurface = onSurface,
                mint = mint,
                lilac = lilac,
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
    animationsEnabled: Boolean,
    surface: Color,
    onSurface: Color,
    mint: Color,
    lilac: Color,
    onClick: () -> Unit
) {
    val cardScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "mostPlayedCardScale"
    )

    val coverShape = RoundedCornerShape(topStart = 20.dp, topEnd = 12.dp, bottomEnd = 22.dp, bottomStart = 14.dp)

    Column(
        modifier = Modifier
            .width(124.dp)
            .scale(cardScale)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Cover container with organic asymmetric corners
        Box(
            modifier = Modifier
                .size(124.dp)
                .shadow(
                    elevation = if (isActive) 10.dp else 4.dp,
                    shape = coverShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.40f)
                )
                .clip(coverShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MareaTrackColor(track.id, 0),
                            MareaTrackColor(track.id, 1)
                        )
                    )
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
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                )
            }

            // Active border & aura overlay
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, mint, coverShape)
                        .background(
                            Brush.radialGradient(
                                listOf(mint.copy(alpha = 0.20f), Color.Transparent)
                            )
                        )
                )
            }

            // Rank glass badge at top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (isActive) mint else if (rank <= 3) lilac else Color.White.copy(alpha = 0.9f)
                )
            }

            // Playing indicator equalizer badge (bottom-right)
            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MareaEqualizerBars(isPlaying = true, animationsEnabled = animationsEnabled)
                }
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pausado",
                        tint = mint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = track.name,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = if (isActive) mint else onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(2.dp))

        val playCountText = if (track.playCount == 1) "1 reproducción" else "${track.playCount} reproducciones"
        Text(
            text = playCountText,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontSize = 11.sp
            ),
            color = onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Playlist Grid Card ─────────────────────────────────────────
@Composable
private fun PlaylistGridCard(
    playlist: MusicPlaylist,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "playlistCardScale"
    )

    Column(
        modifier = modifier
            .scale(cardScale)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        // Square cover with rounded corners (16dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MareaTrackColor(playlist.id, 0),
                            MareaTrackColor(playlist.id, 2)
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
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                )
            }

            // Top-right 3-dots options menu button with circular glass container
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.40f))
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(surface)
                        .border(1.dp, secondary, RoundedCornerShape(16.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Renombrar",
                                fontFamily = BricolageGrotesque,
                                color = onSurface
                            )
                        },
                        onClick = {
                            showRenameDialog = true
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = mint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Eliminar",
                                fontFamily = BricolageGrotesque,
                                color = Color(0xFFE57373)
                            )
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Bottom-right song count badge
            val countText = if (playlist.songCount == 1) "1 tema" else "${playlist.songCount} temas"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.50f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = countText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = Color.White
                )
            }
        }

        // Details below cover
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = playlist.name,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Lista personalizada",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontSize = 11.sp
                ),
                color = onSurface.copy(alpha = 0.50f),
                maxLines = 1
            )
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            surface = surface,
            onSurface = onSurface,
            secondary = secondary,
            mint = mint,
            onAccent = Color(0xFF0F2233),
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                onRename(name)
                showRenameDialog = false
            }
        )
    }
}

// ── Empty states ───────────────────────────────────────────────
@Composable
private fun EmptyMostPlayed(
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, secondary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(mint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = mint,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "Reproduce audios de tus conversaciones para verlos aquí con Marea.",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontSize = 13.sp
            ),
            color = onSurface.copy(alpha = 0.70f)
        )
    }
}

@Composable
private fun EmptyPlaylistsState(
    hasQuery: Boolean,
    searchQuery: String,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    onAccent: Color,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(mint.copy(alpha = 0.12f))
                .border(1.dp, mint.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = mint,
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = if (hasQuery) "Sin listas coincidentes" else "Comienza tu colección",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = onSurface
        )

        Text(
            text = if (hasQuery) "No encontramos ninguna lista que coincida con \"$searchQuery\"."
            else "Crea tu primera lista para organizar tus audios favoritos y disfrutarlos con el reproductor Marea.",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontSize = 13.sp
            ),
            color = onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (!hasQuery) {
            Spacer(Modifier.height(4.dp))
            MareaScaleButton(
                onClick = onCreatePlaylist,
                shape = RoundedCornerShape(22.dp),
                color = mint,
                modifier = Modifier.height(46.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Crear primera lista",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = onAccent
                    )
                }
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
    onOpenPlayer: (String) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) = MareaPlaylistScreen(
    audioController = audioController,
    onNavigateBack = onNavigateBack,
    onOpenPlayer = onOpenPlayer,
    viewModel = viewModel
)

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
    isPlayingFromThisPlaylist: Boolean,
    onPlayPause: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Cancion anterior")
            }
            Button(
                onClick = onPlayPause,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = if (isPlayingFromThisPlaylist && playerState.playing) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (isPlayingFromThisPlaylist && playerState.playing) "Pausar" else "Reproducir",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isPlayingFromThisPlaylist && playerState.playing) "Pausa" else "Reproducir",
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Cancion siguiente")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onShuffle, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Shuffle, contentDescription = "Mezclar orden", modifier = Modifier.size(20.dp))
            }
            FilledTonalIconButton(onClick = onRepeat, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (playerState.repeatMode == AudioRepeatMode.ONE)
                        Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repetir",
                    tint = if (playerState.repeatMode != AudioRepeatMode.OFF)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    isPlaying: Boolean,
    onClick: () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
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
internal fun SleepTimerDialog(
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
internal fun DownloadedAudioDialog(
    tracks: List<MusicTrack>,
    existingIds: Set<String>,
    onAdd: (MusicTrack) -> Unit,
    onPreview: (MusicTrack) -> Unit,
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
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPreview(track) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            SmallCover(Modifier.fillMaxSize())
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Escuchar vista previa de ${track.name}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(enabled = !exists) { onAdd(track) }
                                .padding(vertical = 6.dp)
                        ) {
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
private fun PlaylistNameDialog(
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    onAccent: Color,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                "Nueva lista",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(
                        "Nombre de la lista",
                        style = androidx.compose.ui.text.TextStyle(fontFamily = BricolageGrotesque),
                        color = onSurface.copy(alpha = 0.65f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    color = onSurface,
                    fontSize = 15.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mint,
                    unfocusedBorderColor = secondary,
                    cursorColor = mint
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            MareaScaleButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(18.dp),
                color = if (name.isNotBlank()) mint else secondary.copy(alpha = 0.4f),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    "Crear",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = if (name.isNotBlank()) onAccent else onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancelar",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = onSurface.copy(alpha = 0.70f)
                )
            }
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    mint: Color,
    onAccent: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                "Renombrar lista",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(
                        "Nuevo nombre",
                        style = androidx.compose.ui.text.TextStyle(fontFamily = BricolageGrotesque),
                        color = onSurface.copy(alpha = 0.65f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    color = onSurface,
                    fontSize = 15.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mint,
                    unfocusedBorderColor = secondary,
                    cursorColor = mint
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            MareaScaleButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                shape = RoundedCornerShape(18.dp),
                color = if (name.isNotBlank()) mint else secondary.copy(alpha = 0.4f),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    "Guardar",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = if (name.isNotBlank()) onAccent else onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancelar",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = onSurface.copy(alpha = 0.70f)
                )
            }
        }
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

internal fun playTracks(
    controller: AudioPlayerController,
    tracks: List<MusicTrack>,
    selected: MusicTrack,
    playlistId: String? = null
) {
    val audioTracks = tracks.map {
        AudioTrack(it.id, it.url, it.name, it.sourceMessageId, it.durationSeconds, it.thumbnailUrl, playlistId)
    }
    controller.updatePlaylist(audioTracks)
    audioTracks.firstOrNull { it.id == selected.id }?.let(controller::playWithQueue)
}

/** Plays a downloaded song by itself, so previewing never changes the playlist being edited. */
internal fun previewTrack(controller: AudioPlayerController, track: MusicTrack) {
    val audioTrack = AudioTrack(
        track.id,
        track.url,
        track.name,
        track.sourceMessageId,
        track.durationSeconds,
        track.thumbnailUrl
    )
    controller.updatePlaylist(listOf(audioTrack))
    controller.toggleTrack(audioTrack)
}
