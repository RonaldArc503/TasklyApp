package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioRepeatMode
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.audio.QueueResult
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque
import com.ronaldcolocho.taskly.ui.theme.MareaAccentMint
import com.ronaldcolocho.taskly.ui.theme.MareaAccentPurple
import com.ronaldcolocho.taskly.ui.theme.MareaAccentWarm
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundDark
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundLight
import com.ronaldcolocho.taskly.ui.theme.MareaOnPlayButton
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceLight
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryDark
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryLight
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  MareaPlaylistScreen — Pantalla de Playlist con diseño "Marea"
// ─────────────────────────────────────────────────────────────────────────────

enum class MareaSortOption(val label: String) {
    ORIGINAL("Como las agregaste"),
    MOST_PLAYED("Más intensas primero"),
    SHORTEST("Más cortas primero"),
    LONGEST("Más largas primero")
}

@Composable
fun MareaPlaylistScreen(
    audioController: AudioPlayerController,
    onNavigateBack: () -> Unit,
    onOpenPlayer: (String) -> Unit = {},
    viewModel: PlaylistDetailViewModel
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val playerState by audioController.state.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == viewModel.selectedPlaylistId }

    val isPlayingFromThisPlaylist = playerState.currentTrack?.let { current ->
        tracks.any { it.id == current.id }
    } == true

    // Detect system accessibility animation settings
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

    // Theme tokens — Soft, muted, calm aesthetic colors (avoids harsh bright/neon tones)
    val darkTheme = isSystemInDarkTheme()
    val background = if (darkTheme) MareaBackgroundDark else MareaBackgroundLight
    val surface    = if (darkTheme) MareaSurfaceDark    else MareaSurfaceLight
    val onSurface  = if (darkTheme) MareaOnSurfaceDark  else MareaOnSurfaceLight
    val secondary  = if (darkTheme) MareaSecondaryDark  else MareaSecondaryLight
    val lilac      = Color(0xFF8E80BA)  // Soft muted velvety lavender (replaces bright #B9A7FF)
    val peach      = Color(0xFFD49B85)  // Soft warm terracotta-almond (replaces neon #FFC2A1)
    val mint       = Color(0xFF70A896)  // Soft muted sage/mint (replaces electric #8FF0CF)
    val onAccent   = Color(0xFF0F2233)  // Deep readable dark navy

    // Continuous monotonic time driven by frame clock for fluid living tide
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

    // Dialog & Feedback state
    var showAdd by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackColor by remember { mutableStateOf(mint) }

    // In-memory Favorites tracking
    var favoriteTrackIds by remember { mutableStateOf(setOf<String>()) }

    // Sorting state
    var selectedSort by remember { mutableStateOf(MareaSortOption.ORIGINAL) }
    val sortedTracks = remember(tracks, selectedSort) {
        when (selectedSort) {
            MareaSortOption.ORIGINAL    -> tracks
            MareaSortOption.MOST_PLAYED -> tracks.sortedByDescending { it.playCount }
            MareaSortOption.SHORTEST    -> tracks.sortedBy { it.durationSeconds }
            MareaSortOption.LONGEST     -> tracks.sortedByDescending { it.durationSeconds }
        }
    }

    // Calculate total duration in minutes
    val totalDurationMin = remember(tracks) {
        tracks.sumOf { it.durationSeconds } / 60
    }

    LaunchedEffect(showAdd) {
        if (showAdd) viewModel.loadDownloadedTracks()
    }

    // Floating feedback auto-dismiss
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2200)
            feedbackMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start  = 22.dp,
                end    = 22.dp,
                top    = 12.dp,
                bottom = if (playerState.currentTrack != null) 108.dp else 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Top Bar & Back Button ────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MareaIconButton(
                        onClick = onNavigateBack,
                        contentDescription = "Volver",
                        surface = surface,
                        size = 48.dp
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ── Error Banner ─────────────────────────────────────
            if (error != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error.orEmpty(),
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

            // ── 1. Header (Marea viva cover + Título + Info) ──
            item {
                MareaPlaylistHeader(
                    playlist           = playlist,
                    trackCount         = tracks.size,
                    totalMinutes       = totalDurationMin,
                    background         = background,
                    onSurface          = onSurface,
                    secondary          = secondary,
                    lilac              = lilac,
                    peach              = peach,
                    mint               = mint,
                    animTimeSec        = animTimeSec,
                    animationsEnabled  = animationsEnabled
                )
            }

            // ── 2. Fila de Acciones Principales ──────────────────
            item {
                Spacer(Modifier.height(16.dp))
                MareaPrimaryActionsRow(
                    isPlayingFromPlaylist = isPlayingFromThisPlaylist,
                    isPlaying             = playerState.playing,
                    repeatMode            = playerState.repeatMode,
                    lilac                 = lilac,
                    peach                 = peach,
                    surface               = surface,
                    onSurface             = onSurface,
                    secondary             = secondary,
                    onAccent              = onAccent,
                    onPlayPause = {
                        if (isPlayingFromThisPlaylist) {
                            audioController.toggleCurrent()
                        } else if (sortedTracks.isNotEmpty()) {
                            playTracks(audioController, sortedTracks, sortedTracks.first(), viewModel.selectedPlaylistId)
                        }
                    },
                    onShuffle = {
                        val shuffled = tracks.shuffled().let { s ->
                            if (tracks.size > 1 && s == tracks) tracks.drop(1) + tracks.first() else s
                        }
                        if (shuffled.isNotEmpty()) {
                            viewModel.reorderTracks(shuffled)
                            playTracks(audioController, shuffled, shuffled.first(), viewModel.selectedPlaylistId)
                        }
                    },
                    onRepeat = audioController::cycleRepeatMode
                )
            }

            // ── 3. Fila de Herramientas (Agregar + Reloj Interactivo) ──
            item {
                Spacer(Modifier.height(4.dp))
                MareaToolsRow(
                    sleepTimerEndAt = playerState.sleepTimerEndAt,
                    surface         = surface,
                    onSurface       = onSurface,
                    secondary       = secondary,
                    peach           = peach,
                    onAccent        = onAccent,
                    onAdd           = { showAdd = true },
                    onTimer         = {
                        val presets = listOf(15, 30, 45, 60, 90, 120)
                        val currentEndAt = playerState.sleepTimerEndAt
                        val nextPreset = when {
                            currentEndAt == null -> 15
                            else -> {
                                val remainingMin = ((currentEndAt - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L).toInt()
                                presets.firstOrNull { it > remainingMin + 1 }
                            }
                        }
                        if (nextPreset != null) {
                            audioController.startSleepTimer(nextPreset * 60_000L)
                            val label = when (nextPreset) {
                                60 -> "1 hora"
                                90 -> "1h 30m"
                                120 -> "2 horas"
                                else -> "$nextPreset min"
                            }
                            feedbackMessage = "Temporizador: $label"
                            feedbackColor = peach
                        } else {
                            audioController.cancelSleepTimer()
                            feedbackMessage = "Temporizador apagado"
                            feedbackColor = secondary
                        }
                    }
                )
            }

            // ── 4. Chips de Orden ─────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                MareaSortChipsRow(
                    selectedSort = selectedSort,
                    onSelectSort = { selectedSort = it },
                    secondary    = secondary,
                    mint         = mint,
                    onAccent     = onAccent
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── 5. Lista de Canciones ─────────────────────────────
            if (sortedTracks.isEmpty()) {
                item {
                    MareaEmptyPlaylist(
                        surface   = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        lilac     = lilac,
                        onAdd     = { showAdd = true }
                    )
                }
            } else {
                items(sortedTracks, key = { it.id }) { track ->
                    val isActive = playerState.currentTrack?.id == track.id
                    val isFavorite = favoriteTrackIds.contains(track.id)

                    MareaTrackRowItem(
                        track              = track,
                        isActive           = isActive,
                        isPlaying          = isActive && playerState.playing,
                        isFavorite         = isFavorite,
                        surface            = surface,
                        background         = background,
                        onSurface          = onSurface,
                        secondary          = secondary,
                        lilac              = lilac,
                        peach              = peach,
                        mint               = mint,
                        animationsEnabled  = animationsEnabled,
                        onClick = {
                            playTracks(audioController, sortedTracks, track, viewModel.selectedPlaylistId)
                            onOpenPlayer(playlist?.name ?: "Mi lista")
                        },
                        onSwipeFavorite = {
                            if (isFavorite) {
                                favoriteTrackIds = favoriteTrackIds - track.id
                                feedbackMessage = "Quitada de favoritos"
                                feedbackColor = peach
                            } else {
                                favoriteTrackIds = favoriteTrackIds + track.id
                                feedbackMessage = "Guardada en favoritos"
                                feedbackColor = mint
                            }
                        },
                        onSwipeQueue = {
                            val audioTrack = AudioTrack(
                                id              = track.id,
                                url             = track.url,
                                name            = track.name,
                                msgId           = track.sourceMessageId,
                                durationSeconds = track.durationSeconds,
                                thumbnailUrl    = track.thumbnailUrl,
                                playlistId      = viewModel.selectedPlaylistId
                            )
                            val allAudioTracks = sortedTracks.map {
                                AudioTrack(
                                    id              = it.id,
                                    url             = it.url,
                                    name            = it.name,
                                    msgId           = it.sourceMessageId,
                                    durationSeconds = it.durationSeconds,
                                    thumbnailUrl    = it.thumbnailUrl,
                                    playlistId      = viewModel.selectedPlaylistId
                                )
                            }
                            when (audioController.addToQueueNext(audioTrack, playlistFallback = allAudioTracks)) {
                                QueueResult.ALREADY_PLAYING -> {
                                    feedbackMessage = "Ya se está reproduciendo: ${track.name}"
                                    feedbackColor = peach
                                }
                                QueueResult.PLAYING_NOW -> {
                                    feedbackMessage = "Reproduciendo ahora: ${track.name}"
                                    feedbackColor = mint
                                }
                                QueueResult.ADDED_AS_NEXT -> {
                                    feedbackMessage = "Se reproducirá a continuación: ${track.name}"
                                    feedbackColor = mint
                                }
                                QueueResult.ADDED_TO_QUEUE -> {
                                    feedbackMessage = "Agregada a la cola tras la anterior: ${track.name}"
                                    feedbackColor = peach
                                }
                            }
                        },
                        onRemove = { viewModel.removeTrack(track) }
                    )
                }
            }
        }

        // ── 6. Mini Reproductor Flotante Abajo ─────────────────────
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
                    onClick     = { onOpenPlayer(playlist?.name ?: "Mi lista") }
                )
            }
        }

        // ── 7. Floating Toast / Snackbar de confirmación ───────────
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 22.dp, end = 22.dp)
                .zIndex(99f)
        ) {
            Box(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(surface)
                    .border(1.5.dp, feedbackColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(feedbackColor)
                    )
                    Text(
                        text     = feedbackMessage.orEmpty(),
                        style    = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp
                        ),
                        color    = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAdd) {
        DownloadedAudioDialog(
            tracks      = downloadedTracks,
            existingIds = tracks.mapTo(mutableSetOf()) { it.id },
            onAdd       = viewModel::addTrack,
            onPreview   = { track -> previewTrack(audioController, track) }
        ) { showAdd = false }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Cabecera (Mancha orgánica cover de 132dp + Título + Info)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaPlaylistHeader(
    playlist          : MusicPlaylist?,
    trackCount        : Int,
    totalMinutes      : Int,
    background        : Color,
    onSurface         : Color,
    secondary         : Color,
    lilac             : Color,
    peach             : Color,
    mint              : Color,
    animTimeSec       : Float,
    animationsEnabled : Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mareaCoverTilt")

    // Gentle organic tilt up to 6 degrees
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue  = -6f,
        targetValue   = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiltAngle"
    )

    val currentTilt = if (animationsEnabled) tiltAngle else 0f

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Left: 132dp living "Marea" cover disc (Crisp, fluid, multi-harmonic liquid tide)
        Box(
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer {
                    rotationZ = currentTilt
                }
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.50f)
                )
                .drawBehind {
                    drawLivingMarea(
                        timeSec = if (animationsEnabled) animTimeSec else 0f,
                        g1      = lilac,
                        g2      = peach,
                        g3      = mint
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = playlist?.coverUrl.orEmpty()
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model              = coverUrl,
                    contentDescription = "Carátula de playlist",
                    modifier           = Modifier
                        .fillMaxSize(0.78f)
                        .clip(CircleShape)
                        .alpha(0.85f),
                    contentScale       = ContentScale.Crop
                )
            } else {
                // Subtle vinyl grooves if no custom image
                Icon(
                    imageVector        = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = 0.22f),
                    modifier           = Modifier.size(34.dp)
                )
            }

            // Vinyl center hole: 32% of size (~42dp) with bevel depth, inner ring, and spindle dot
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(background)
                    .drawBehind {
                        // Inset shadow into the hole
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    background,
                                    background,
                                    Color.Black.copy(alpha = 0.38f)
                                ),
                                center = Offset(this.size.width * 0.45f, this.size.height * 0.45f),
                                radius = this.size.width * 0.50f
                            )
                        )
                        // Inner white metallic border
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Center spindle dot
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.40f),
                            radius = 2.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
            )
        }

        // Right: Title & Stats
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text          = playlist?.name ?: "Mi lista",
                style         = androidx.compose.ui.text.TextStyle(
                    fontFamily    = BricolageGrotesque,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 34.sp,
                    lineHeight    = 34.sp,
                    letterSpacing = (-0.68).sp
                ),
                color         = onSurface,
                maxLines      = 2,
                overflow      = TextOverflow.Ellipsis
            )

            Text(
                text          = "$trackCount canciones · $totalMinutes min",
                style         = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 14.sp,
                    lineHeight = 18.sp
                ),
                color         = secondary,
                maxLines      = 1,
                overflow      = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. Fila de Acciones Principales (Reproducir todo + Aleatorio + Repetir)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaPrimaryActionsRow(
    isPlayingFromPlaylist : Boolean,
    isPlaying             : Boolean,
    repeatMode            : AudioRepeatMode,
    lilac                 : Color,
    peach                 : Color,
    surface               : Color,
    onSurface             : Color,
    secondary             : Color,
    onAccent              : Color,
    onPlayPause           : () -> Unit,
    onShuffle             : () -> Unit,
    onRepeat              : () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Reproducir todo" — Flexible width, 56dp high, 28dp corners, lilac background
        MareaScaleButton(
            onClick   = onPlayPause,
            modifier  = Modifier
                .weight(1f)
                .height(56.dp),
            shape     = RoundedCornerShape(28.dp),
            color     = lilac
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector        = if (isPlayingFromPlaylist && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint               = onAccent,
                    modifier           = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (isPlayingFromPlaylist && isPlaying) "Pausa" else "Reproducir todo",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 17.sp
                    ),
                    color = onAccent
                )
            }
        }

        // "Aleatorio" (Shuffle) — 56x56dp, 20dp corners, surface background
        MareaScaleButton(
            onClick   = onShuffle,
            modifier  = Modifier.size(56.dp),
            shape     = RoundedCornerShape(20.dp),
            color     = surface,
            contentDescription = "Reproducción aleatoria"
        ) {
            Icon(
                imageVector        = Icons.Default.Shuffle,
                contentDescription = null,
                tint               = onSurface,
                modifier           = Modifier.size(24.dp)
            )
        }

        // "Repetir" — 56x56dp, 20dp corners, 3 states
        val repeatDesc = when (repeatMode) {
            AudioRepeatMode.OFF -> "Repetir: desactivado"
            AudioRepeatMode.ALL -> "Repetir: toda la lista"
            AudioRepeatMode.ONE -> "Repetir: una canción"
        }
        val repeatBg = when (repeatMode) {
            AudioRepeatMode.OFF -> surface
            AudioRepeatMode.ALL, AudioRepeatMode.ONE -> peach
        }
        val repeatTint = when (repeatMode) {
            AudioRepeatMode.OFF -> secondary
            AudioRepeatMode.ALL, AudioRepeatMode.ONE -> onAccent
        }

        MareaScaleButton(
            onClick   = onRepeat,
            modifier  = Modifier.size(56.dp),
            shape     = RoundedCornerShape(20.dp),
            color     = repeatBg,
            contentDescription = repeatDesc
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = if (repeatMode == AudioRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint               = repeatTint,
                    modifier           = Modifier.size(24.dp)
                )
                if (repeatMode == AudioRepeatMode.ONE) {
                    Text(
                        text     = "1",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color    = onAccent,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 2.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Fila de Herramientas (Agregar canciones + Reloj temporizador)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaToolsRow(
    sleepTimerEndAt : Long?,
    surface         : Color,
    onSurface       : Color,
    secondary       : Color,
    peach           : Color,
    onAccent        : Color,
    onAdd           : () -> Unit,
    onTimer         : () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Agregar canciones" — Flexible width, 56dp high, 20dp corners, no fill, 1.5dp border at 35%
        MareaScaleButton(
            onClick   = onAdd,
            modifier  = Modifier
                .weight(1f)
                .height(56.dp)
                .border(1.5.dp, secondary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            shape     = RoundedCornerShape(20.dp),
            color     = Color.Transparent
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = null,
                    tint               = onSurface,
                    modifier           = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = "Agregar canciones",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 16.sp
                    ),
                    color = onSurface
                )
            }
        }

        // Reloj (Temporizador de sueño) — 56x56dp, 20dp corners
        val isTimerActive = sleepTimerEndAt != null
        val timerBg   = if (isTimerActive) peach else surface
        val timerTint = if (isTimerActive) onAccent else secondary
        val remMinutes = remember(sleepTimerEndAt) {
            if (sleepTimerEndAt != null) {
                ((sleepTimerEndAt - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L + 1).coerceAtLeast(1L)
            } else 0L
        }
        val badgeText = when {
            remMinutes >= 115 -> "2h"
            remMinutes >= 85  -> "90m"
            remMinutes >= 55  -> "1h"
            else              -> "${remMinutes}m"
        }

        MareaScaleButton(
            onClick   = onTimer,
            modifier  = Modifier.size(56.dp),
            shape     = RoundedCornerShape(20.dp),
            color     = timerBg,
            contentDescription = if (isTimerActive) "Temporizador: $badgeText. Toca para cambiar o apagar." else "Activar temporizador (15 min)"
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MareaClockIcon(
                    isActive = isTimerActive,
                    tint     = timerTint,
                    modifier = Modifier.size(24.dp)
                )
                if (isTimerActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-3).dp, y = (-3).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(onAccent)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text       = badgeText,
                            fontSize   = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = peach,
                            fontFamily = BricolageGrotesque
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern analog watch/clock icon with outer bezel ring, cardinal tick marks,
 * 10:10 hands, and center spindle.
 */
@Composable
private fun MareaClockIcon(
    isActive : Boolean,
    tint     : Color,
    modifier : Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = minOf(w, h) / 2f
        val center = Offset(w / 2f, h / 2f)

        // 1. Clock bezel / outer circle
        drawCircle(
            color  = tint,
            radius = r * 0.88f,
            style  = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 2. Cardinal tick marks (12, 3, 6, 9 o'clock)
        val tickLength = r * 0.14f
        val ticks = listOf(
            Offset(center.x, center.y - r * 0.88f) to Offset(center.x, center.y - r * 0.88f + tickLength),
            Offset(center.x + r * 0.88f, center.y) to Offset(center.x + r * 0.88f - tickLength, center.y),
            Offset(center.x, center.y + r * 0.88f) to Offset(center.x, center.y + r * 0.88f - tickLength),
            Offset(center.x - r * 0.88f, center.y) to Offset(center.x - r * 0.88f + tickLength, center.y)
        )
        ticks.forEach { (start, end) ->
            drawLine(
                color       = tint.copy(alpha = 0.55f),
                start       = start,
                end         = end,
                strokeWidth = 1.5.dp.toPx(),
                cap         = StrokeCap.Round
            )
        }

        // 3. Hands (10:10 aesthetic luxury watch position)
        val hourAngleRad   = Math.toRadians(-120.0).toFloat()
        val minuteAngleRad = Math.toRadians(25.0).toFloat()

        // Hour hand
        val hourLength = r * 0.44f
        drawLine(
            color       = tint,
            start       = center,
            end         = Offset(
                center.x + hourLength * kotlin.math.cos(hourAngleRad),
                center.y + hourLength * kotlin.math.sin(hourAngleRad)
            ),
            strokeWidth = 2.2.dp.toPx(),
            cap         = StrokeCap.Round
        )

        // Minute hand
        val minuteLength = r * 0.62f
        drawLine(
            color       = tint,
            start       = center,
            end         = Offset(
                center.x + minuteLength * kotlin.math.cos(minuteAngleRad),
                center.y + minuteLength * kotlin.math.sin(minuteAngleRad)
            ),
            strokeWidth = 1.8.dp.toPx(),
            cap         = StrokeCap.Round
        )

        // 4. Center spindle pivot
        drawCircle(
            color  = tint,
            radius = 2.2.dp.toPx()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. Chips de Orden (Fila con scroll horizontal)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaSortChipsRow(
    selectedSort : MareaSortOption,
    onSelectSort : (MareaSortOption) -> Unit,
    secondary    : Color,
    mint         : Color,
    onAccent     : Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MareaSortOption.entries.forEach { option ->
            val isSelected = option == selectedSort
            val shape = RoundedCornerShape(14.dp)
            val bg = if (isSelected) mint else Color(0xFF142738)
            val borderModifier = if (isSelected)
                Modifier.border(1.dp, mint.copy(alpha = 0.50f), shape)
            else
                Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            val textColor = if (isSelected) onAccent else secondary
            val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bg)
                    .then(borderModifier)
                    .clickable { onSelectSort(option) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = option.label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = textWeight,
                        fontSize   = 14.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  5. Fila de Canción (Miniatura asimétrica + Ecualizador + Forma de onda 9 barras + Swipe)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaTrackRowItem(
    track             : MusicTrack,
    isActive          : Boolean,
    isPlaying         : Boolean,
    isFavorite        : Boolean,
    surface           : Color,
    background        : Color,
    onSurface         : Color,
    secondary         : Color,
    lilac             : Color,
    peach             : Color,
    mint              : Color,
    animationsEnabled : Boolean,
    onClick           : () -> Unit,
    onSwipeFavorite   : () -> Unit,
    onSwipeQueue      : () -> Unit,
    onRemove          : () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope   = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    // Swipe reveal colors & icons
    val currentOffset = offsetX.value
    val isSwipingRight = currentOffset > 20f
    val isSwipingLeft  = currentOffset < -20f

    val swipeRevealBg = when {
        isSwipingRight -> mint
        isSwipingLeft  -> peach
        else           -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(swipeRevealBg)
    ) {
        // Background swipe label & icon
        if (isSwipingRight) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Favorite,
                    contentDescription = null,
                    tint               = Color(0xFF10233A),
                    modifier           = Modifier.size(20.dp)
                )
                Text(
                    text       = "Guardar",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF10233A)
                )
            }
        } else if (isSwipingLeft) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = "A la cola +",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF10233A)
                )
                Icon(
                    imageVector        = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint               = Color(0xFF10233A),
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        // Foreground content row (slides with gestures)
        Row(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (isActive) surface else Color.Transparent)
                .clickable(onClick = onClick)
                .pointerInput(track.id) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            scope.launch {
                                offsetX.snapTo(totalDrag.coerceIn(-130.dp.toPx(), 130.dp.toPx()))
                            }
                        },
                        onDragEnd = {
                            if (totalDrag > 70.dp.toPx()) {
                                onSwipeFavorite()
                            } else if (totalDrag < -70.dp.toPx()) {
                                onSwipeQueue()
                            }
                            scope.launch {
                                offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(150)) }
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Asymmetric thumbnail (16/24/18/26dp corners)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart    = 16.dp,
                            topEnd      = 24.dp,
                            bottomEnd   = 18.dp,
                            bottomStart = 26.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MareaTrackColor(track.id, 0),
                                MareaTrackColor(track.id, 1)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model              = track.thumbnailUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                }

                // If active song: darken by 55% and show 3 animated white equalizer bars
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(background.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MareaEqualizerBars(
                            isPlaying         = isPlaying,
                            animationsEnabled = animationsEnabled
                        )
                    }
                }
            }

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text     = track.name,
                    style    = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize   = 17.sp
                    ),
                    color    = if (isActive) lilac else onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text     = "Taskly",
                        style    = androidx.compose.ui.text.TextStyle(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.Normal,
                            fontSize   = 13.sp
                        ),
                        color    = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector        = Icons.Default.Favorite,
                            contentDescription = "Favorita",
                            tint               = peach,
                            modifier           = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Mini 9-bar waveform (52x30dp)
            MareaMiniWaveform(
                trackId  = track.id,
                isActive = isActive,
                mint     = mint,
                secondary = secondary
            )

            // Duration: fixed 34dp width, tabular figures, aligned end
            val durStr = if (track.durationSeconds > 0) {
                "%d:%02d".format(track.durationSeconds / 60, track.durationSeconds % 60)
            } else "--:--"

            Text(
                text      = durStr,
                style     = androidx.compose.ui.text.TextStyle(
                    fontFamily          = BricolageGrotesque,
                    fontWeight          = FontWeight.Normal,
                    fontSize            = 13.sp,
                    fontFeatureSettings = "\"tnum\" on"
                ),
                color     = secondary,
                modifier  = Modifier.width(34.dp),
                textAlign = TextAlign.End
            )

            // Context Menu Button (options)
            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint               = secondary,
                        modifier           = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded          = showMenu,
                    onDismissRequest  = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text       = { Text("Reproducir a continuación") },
                        onClick    = { onSwipeQueue(); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.LibraryMusic, null, tint = lilac)
                        }
                    )
                    DropdownMenuItem(
                        text       = { Text("Quitar de la lista") },
                        onClick    = { onRemove(); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                        }
                    )
                }
            }
        }
    }
}

// ── 3 Barritas animadas tipo ecualizador ───────────────────────────────────────

@Composable
internal fun MareaEqualizerBars(
    isPlaying         : Boolean,
    animationsEnabled : Boolean
) {
    if (!animationsEnabled || !isPlaying) {
        // Static peaceful bars when paused
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment     = Alignment.Bottom,
            modifier              = Modifier.height(18.dp)
        ) {
            Box(Modifier.width(4.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.width(4.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "eqBars")

    val h1 by transition.animateFloat(
        initialValue  = 6f,
        targetValue   = 18f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue  = 16f,
        targetValue   = 6f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue  = 8f,
        targetValue   = 17f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.Bottom,
        modifier              = Modifier.height(18.dp)
    ) {
        Box(Modifier.width(4.dp).height(h1.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        Box(Modifier.width(4.dp).height(h2.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        Box(Modifier.width(4.dp).height(h3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}

// ── Mini forma de onda de 52x30dp con 9 barras ────────────────────────────────

@Composable
private fun MareaMiniWaveform(
    trackId   : String,
    isActive  : Boolean,
    mint      : Color,
    secondary : Color
) {
    val barColor = if (isActive) mint else secondary.copy(alpha = 0.50f)
    val heights = remember(trackId) {
        val seed = trackId.hashCode().toLong()
        List(9) { i ->
            val v = (seed * 1103515245L + 12345L + i * 214013L) and 0x7fffffffL
            val fraction = ((v % 1000).toFloat() / 1000f)
            // Bell curve
            val env = 1f - abs((i - 4f) / 4f) * 0.45f
            (fraction * env).coerceIn(0.20f, 1.0f) * 22f + 4f
        }
    }

    Row(
        modifier              = Modifier.size(width = 52.dp, height = 30.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  6. Mini Reproductor Flotante Abajo (Píldora fija de superficie)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MareaFloatingMiniPlayer(
    track       : AudioTrack,
    isPlaying   : Boolean,
    progress    : Float,
    surface     : Color,
    onSurface   : Color,
    secondary   : Color,
    lilac       : Color,
    peach       : Color,
    onAccent    : Color,
    darkTheme   : Boolean,
    onPlayPause : () -> Unit,
    onClick     : () -> Unit
) {
    val containerBg = if (darkTheme) Color(0xFF223E59) else Color(0xFFEAF0F6)
    val containerBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.50f)
            )
            .border(1.dp, containerBorder, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(containerBg)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail 46dp with 14dp corners
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MareaTrackColor(track.id, 0),
                                MareaTrackColor(track.id, 1)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model              = track.thumbnailUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint               = onSurface.copy(alpha = 0.5f),
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = track.name,
                    style    = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 16.sp
                    ),
                    color    = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = "Taskly",
                    style    = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 13.sp
                    ),
                    color    = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play / Pause Button 52x46dp with 18dp corners in lilac
            MareaScaleButton(
                onClick   = onPlayPause,
                modifier  = Modifier.size(width = 52.dp, height = 46.dp),
                shape     = RoundedCornerShape(18.dp),
                color     = lilac,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
            ) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint               = onAccent,
                    modifier           = Modifier.size(24.dp)
                )
            }
        }

        // 3dp progress line in peach pinned to bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(peach)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  State & Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaEmptyPlaylist(
    surface   : Color,
    onSurface : Color,
    secondary : Color,
    lilac     : Color,
    onAdd     : () -> Unit
) {
    Column(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint               = lilac,
                modifier           = Modifier.size(36.dp)
            )
        }
        Text(
            text       = "Esta lista está vacía",
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            color      = onSurface
        )
        Text(
            text       = "Agrega audios descargados desde tus chats de Taskly",
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.Normal,
            fontSize   = 14.sp,
            color      = secondary,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(4.dp))
        MareaScaleButton(
            onClick   = onAdd,
            modifier  = Modifier.height(48.dp),
            shape     = RoundedCornerShape(24.dp),
            color     = lilac
        ) {
            Text(
                text     = "+ Agregar canciones",
                style    = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                ),
                color    = Color(0xFF10233A),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
internal fun MareaScaleButton(
    onClick            : () -> Unit,
    modifier           : Modifier = Modifier,
    shape              : androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    color              : Color = Color.Transparent,
    contentDescription : String? = null,
    content            : @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label         = "btnScale"
    )

    val semModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .then(semModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun MareaIconButton(
    onClick            : () -> Unit,
    contentDescription : String,
    surface            : Color,
    size               : androidx.compose.ui.unit.Dp = 48.dp,
    content            : @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = tween(150),
        label         = "iconBtnScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

internal fun MareaTrackColor(id: String, index: Int): Color {
    val h = (id.hashCode() + index * 97) and 0x7fffffff
    val softMutedColors = listOf(
        Color(0xFF5E8096), // Soft slate blue
        Color(0xFF6B9486), // Soft sage green
        Color(0xFFA87A6C), // Soft muted terracotta
        Color(0xFF8F7B99), // Soft lavender mauve
        Color(0xFF648574), // Soft muted eucalyptus
        Color(0xFFA89172), // Soft warm wheat / amber
        Color(0xFF75859E), // Soft dusty denim
        Color(0xFF9E7280), // Soft dusty rose
        Color(0xFF5E8A8F), // Soft muted teal
        Color(0xFF8A8270)  // Soft warm stone
    )
    return softMutedColors[h % softMutedColors.size]
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)
