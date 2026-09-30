package com.ronaldcolocho.taskly.ui.screen.music

// ─────────────────────────────────────────────────────────────────────────────
//  MusicPlayerScreen.kt  —  Diseño "Marea"
//  SOLO UI: no modifica ViewModels, repositorios, servicios ni lógica de audio.
//  Conecta a los callbacks ya existentes de AudioPlayerController.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOutQuart
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioPlayerState
import com.ronaldcolocho.taskly.audio.AudioRepeatMode
import com.ronaldcolocho.taskly.audio.AudioTrack
import kotlinx.coroutines.delay
import com.ronaldcolocho.taskly.ui.theme.MareaAccentMint
import com.ronaldcolocho.taskly.ui.theme.MareaAccentPurple
import com.ronaldcolocho.taskly.ui.theme.MareaAccentWarm
import com.ronaldcolocho.taskly.ui.theme.MareaArtistStyle
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundDark
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundLight
import com.ronaldcolocho.taskly.ui.theme.MareaContextStyle
import com.ronaldcolocho.taskly.ui.theme.MareaOnPlayButton
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceLight
import com.ronaldcolocho.taskly.ui.theme.MareaQueueHeaderStyle
import com.ronaldcolocho.taskly.ui.theme.MareaQueueSubtitleStyle
import com.ronaldcolocho.taskly.ui.theme.MareaQueueTitleStyle
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryDark
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryLight
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceLight
import com.ronaldcolocho.taskly.ui.theme.MareaTitleStyle
import com.ronaldcolocho.taskly.ui.theme.MareaTimeStyle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Public entry-point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen "Marea" music player.
 *
 * @param audioController  The existing [AudioPlayerController] singleton — not modified.
 * @param playlistName     Display name for the "Sonando de:" header (pass playlist or album name).
 * @param darkTheme        Follow system dark-mode setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    audioController: AudioPlayerController,
    playlistName: String = "Mi lista",
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    onNavigateBack: () -> Unit = {}
) {
    val playerState by audioController.state.collectAsStateWithLifecycle()

    // ── Theme tokens resolved for dark/light ─────────────────
    val background = if (darkTheme) MareaBackgroundDark else MareaBackgroundLight
    val surface    = if (darkTheme) MareaSurfaceDark    else MareaSurfaceLight
    val onSurface  = if (darkTheme) MareaOnSurfaceDark  else MareaOnSurfaceLight
    val secondary  = if (darkTheme) MareaSecondaryDark  else MareaSecondaryLight

    // ── Per-song dynamic color palette (deterministic per track + anti-repetition + user tap shuffle) ─
    val trackId = playerState.currentTrack?.id ?: ""
    var userPaletteOffset by remember(trackId) { mutableIntStateOf(0) }
    var previousPaletteIdx by remember { mutableIntStateOf(-1) }

    val basePaletteIdx = remember(trackId) {
        val p = getTrackPalette(playerState.currentTrack, 0, previousPaletteIdx)
        val idx = MAREA_PALETTES.indexOfFirst { it.id == p.id }.coerceAtLeast(0)
        previousPaletteIdx = idx
        idx
    }

    val trackPalette = remember(basePaletteIdx, userPaletteOffset) {
        val finalIdx = ((basePaletteIdx + userPaletteOffset) % MAREA_PALETTES.size + MAREA_PALETTES.size) % MAREA_PALETTES.size
        MAREA_PALETTES[finalIdx]
    }

    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2200)
            feedbackMessage = null
        }
    }

    // Animate colour transitions smoothly when song changes (~650 ms)
    val animPrimary   by animateColorAsState(trackPalette.primary, tween(650), label = "primary")
    val animSecondary by animateColorAsState(trackPalette.secondary, tween(650), label = "secondary")
    val animTertiary  by animateColorAsState(trackPalette.tertiary, tween(650), label = "tertiary")
    val animGlow      by animateColorAsState(trackPalette.glow, tween(650), label = "glow")

    // ── Queue bottom sheet ────────────────────────────────────
    var showQueue by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                // Soft atmospheric color wash from the song's primary aura
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animPrimary.copy(alpha = 0.16f),
                            animSecondary.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height * 0.35f),
                        radius = size.width * 1.05f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 20.dp)
        ) {
            // 1. Top bar
            MareaTopBar(
                playlistName = playlistName,
                secondary    = secondary,
                onBack       = onNavigateBack,
                onQueueClick = { showQueue = true }
            )

            Spacer(Modifier.height(12.dp))

            // 2. Central stage (remaining space)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val coverSize = min(maxWidth * 0.72f, 290.dp)

                MareaAlbumArt(
                    coverUrl        = playerState.currentTrack?.thumbnailUrl ?: "",
                    isPlaying       = playerState.playing,
                    size            = coverSize,
                    g1              = animPrimary,
                    g2              = animSecondary,
                    g3              = animTertiary,
                    glow            = animGlow,
                    background      = background,
                    onSwipeLeft     = { audioController.next() },
                    onSwipeRight    = { audioController.prev() },
                    onDoubleTap     = { /* Favorito — placeholder: no existe isFavorite en el modelo */ },
                    onCyclePalette  = {
                        userPaletteOffset++
                        val nextIdx = ((basePaletteIdx + userPaletteOffset) % MAREA_PALETTES.size + MAREA_PALETTES.size) % MAREA_PALETTES.size
                        val nextPalette = MAREA_PALETTES[nextIdx]
                        feedbackMessage = "Tema: ${nextPalette.name}"
                    }
                )
            }

            // Hint text & Theme / Mode badge
            val statusText = feedbackMessage
                ?: if (userPaletteOffset > 0) "Tema: ${trackPalette.name} · Toca para cambiar"
                else "Desliza para cambiar · Toca el disco para variar color"
            val statusColor = if (feedbackMessage != null || userPaletteOffset > 0) animPrimary else secondary

            Text(
                text       = statusText,
                style      = MareaContextStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color      = statusColor,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // 3. Song info
            MareaSongInfo(
                track       = playerState.currentTrack,
                onSurface   = onSurface,
                secondary   = secondary,
                accentColor = animPrimary
            )

            Spacer(Modifier.height(22.dp))

            // 4. Waveform seek bar
            val effectiveDurationMs = if (playerState.durationMs > 0) {
                playerState.durationMs
            } else {
                (playerState.currentTrack?.durationSeconds?.takeIf { it > 0 }?.toLong()?.times(1000L)) ?: 0L
            }

            MareaWaveformSeekBar(
                trackId      = trackId,
                currentMs    = playerState.currentTimeMs,
                durationMs   = effectiveDurationMs,
                secondary    = secondary,
                activeColor  = animPrimary,
                thumbColor   = animTertiary,
                onSeek       = { ms -> audioController.seek(ms) }
            )

            Spacer(Modifier.height(18.dp))

            // 5. Controls
            MareaControls(
                isPlaying          = playerState.playing,
                repeatMode         = playerState.repeatMode,
                shuffleModeEnabled = playerState.shuffleModeEnabled,
                surface            = surface,
                onSurface          = onSurface,
                accentColor        = animPrimary,
                onPlayPause        = { audioController.toggleCurrent() },
                onPrevious         = { audioController.prev() },
                onNext             = { audioController.next() },
                onRepeat           = {
                    val nextMode = playerState.repeatMode.next()
                    audioController.cycleRepeatMode()
                    feedbackMessage = when (nextMode) {
                        AudioRepeatMode.ALL -> "Bucle: toda la lista"
                        AudioRepeatMode.ONE -> "Bucle: esta canción"
                        AudioRepeatMode.OFF -> "Bucle: apagado"
                    }
                },
                onShuffle          = {
                    val willEnable = !playerState.shuffleModeEnabled
                    audioController.toggleShuffle()
                    feedbackMessage = if (willEnable) "Modo aleatorio activado" else "Modo aleatorio apagado"
                }
            )
        }
    }

    // Queue bottom sheet
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState       = sheetState,
            containerColor   = surface,
            shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            MareaQueuePanel(
                tracks           = playerState.tracks,
                currentId        = playerState.currentTrack?.id,
                onSurface        = onSurface,
                secondary        = secondary,
                activeTrackColor = animPrimary,
                onTrackClick     = { track ->
                    audioController.skipToQueueItem(track.id)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showQueue = false }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaTopBar(
    playlistName: String,
    secondary: Color,
    onBack: () -> Unit,
    onQueueClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimizar",
                    tint = secondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text     = "Sonando de: $playlistName",
                style    = MareaContextStyle,
                color    = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onQueueClick,
            modifier = Modifier.semantics { contentDescription = "Ver cola de reproducción" }
        ) {
            Text(
                text  = "Ver cola",
                style = MareaContextStyle,
                color = secondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. Album art — organic blob with gestures
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaAlbumArt(
    coverUrl       : String,
    isPlaying      : Boolean,
    size           : Dp,
    g1             : Color,
    g2             : Color,
    g3             : Color,
    glow           : Color,
    background     : Color,
    onSwipeLeft    : () -> Unit,
    onSwipeRight   : () -> Unit,
    onDoubleTap    : () -> Unit,
    onCyclePalette : () -> Unit
) {
    val currentOnCyclePalette by rememberUpdatedState(onCyclePalette)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    // Detect if user has disabled animations (accessibility)
    val context = androidx.compose.ui.platform.LocalContext.current
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

    // Continuous monotonic time driver — never resets, never loops, 100% seamless & organic
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    val playbackSpeed by animateFloatAsState(
        targetValue   = if (isPlaying) 1.0f else 0.35f,
        animationSpec = tween(1000, easing = LinearEasing),
        label         = "playbackSpeed"
    )

    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dt = (nowNanos - lastNanos) / 1_000_000_000f
                    animTimeSec += dt.coerceIn(0f, 0.05f) * playbackSpeed
                }
                lastNanos = nowNanos
            }
        }
    }

    val t = animTimeSec

    // Multi-harmonic gentle oceanic sway (non-repeating natural drift between -5.5° and +5.5°)
    val tiltAngle = if (animationsEnabled) {
        sin(t * 0.42f) * 3.4f + cos(t * 0.23f) * 2.1f
    } else 0f

    // Organic biological breathing scale (non-repeating rhythm between 0.975 and 1.025)
    val breatheScale = if (animationsEnabled) {
        1.0f + sin(t * 0.68f) * 0.018f + cos(t * 0.39f) * 0.009f
    } else 1.0f

    // Soft atmospheric aura pulsation (alpha between 0.48 and 0.74)
    val glowAlpha = if (animationsEnabled) {
        0.58f + sin(t * 0.54f) * 0.14f + cos(t * 0.28f) * 0.05f
    } else 0.55f

    // Swipe state
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeAlpha   = remember { Animatable(1f) }
    val swipeScale   = remember { Animatable(1f) }
    val scope        = rememberCoroutineScope()

    // Double-tap heart animation
    var showHeart by remember { mutableStateOf(false) }
    val heartScale  = remember { Animatable(0f) }
    val heartAlpha  = remember { Animatable(0f) }

    LaunchedEffect(showHeart) {
        if (showHeart) {
            heartScale.snapTo(0.4f)
            heartAlpha.snapTo(1f)
            heartScale.animateTo(1.05f, tween(320, easing = FastOutSlowInEasing))
            heartAlpha.animateTo(0f, tween(320))
            showHeart = false
        }
    }

    Box(
        modifier = Modifier.size(size * 1.16f),
        contentAlignment = Alignment.Center
    ) {
        // 1. Lush atmospheric ambient aura behind the tide (breathes and glows in song colors)
        Box(
            modifier = Modifier
                .size(size * 1.10f)
                .graphicsLayer {
                    translationY = (size * 0.04f).toPx()
                    alpha        = if (animationsEnabled) glowAlpha else 0.55f
                    scaleX       = if (animationsEnabled) breatheScale * 1.04f else 1f
                    scaleY       = if (animationsEnabled) breatheScale * 1.04f else 1f
                }
                .blur(radius = 52.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.88f),
                            g1.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 2. The living "Marea" (dual-layer fluid organic body)
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationX = swipeOffsetX.value
                    alpha        = swipeAlpha.value
                    scaleX       = swipeScale.value * if (animationsEnabled) breatheScale else 1f
                    scaleY       = swipeScale.value * if (animationsEnabled) breatheScale else 1f
                    if (animationsEnabled) {
                        rotationZ = tiltAngle
                    }
                }
                .drawBehind {
                    drawLivingMarea(
                        timeSec = if (animationsEnabled) animTimeSec else 0f,
                        g1      = g1,
                        g2      = g2,
                        g3      = g3
                    )
                }
                .pointerInput(Unit) {
                    var totalX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalX = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            totalX += amount
                            scope.launch {
                                swipeOffsetX.snapTo(totalX.coerceIn(-90.dp.toPx(), 90.dp.toPx()))
                                val progress = (abs(totalX) / 90.dp.toPx()).coerceIn(0f, 1f)
                                swipeAlpha.snapTo(1f - progress * 0.6f)
                                swipeScale.snapTo(1f - progress * 0.2f)
                            }
                        },
                        onDragEnd = {
                            if (abs(totalX) > 50.dp.toPx()) {
                                if (totalX < 0) currentOnSwipeLeft() else currentOnSwipeRight()
                            }
                            scope.launch {
                                swipeOffsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                swipeAlpha.animateTo(1f, tween(200))
                                swipeScale.animateTo(1f, tween(200))
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            currentOnDoubleTap()
                            showHeart = true
                        },
                        onTap = {
                            currentOnCyclePalette()
                        }
                    )
                }
        ) {
            // Optional album cover clipped inside the fluid vinyl disc
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model              = coverUrl,
                    contentDescription = "Portada de la canción",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxSize(0.80f)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .alpha(0.85f)
                )
            } else {
                // Subtle vinyl grooves if no custom image
                Icon(
                    imageVector        = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = 0.25f),
                    modifier           = Modifier
                        .align(Alignment.Center)
                        .size(size * 0.26f)
                )
            }

            // 3. Central Vinyl Hole with beveled shadow, metallic rim and spindle
            Box(
                modifier = Modifier
                    .size(size * 0.32f)
                    .align(Alignment.Center)
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
                                radius = this.size.minDimension * 0.5f
                            )
                        )
                        // Delicate metallic/acrylic rim tinted with song's accent
                        drawCircle(
                            color  = g1.copy(alpha = 0.35f),
                            radius = this.size.minDimension / 2f,
                            style  = Stroke(width = 1.5.dp.toPx())
                        )
                        // Turntable center spindle dot
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.55f),
                            radius = 2.5.dp.toPx(),
                            center = Offset(this.size.width / 2f, this.size.height / 2f)
                        )
                    }
            )
        }

        // 4. Double-tap heart overlay
        Icon(
            imageVector        = Icons.Default.Favorite,
            contentDescription = null,
            tint               = MareaAccentWarm,
            modifier           = Modifier
                .size(size * 0.38f)
                .graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                    alpha  = heartAlpha.value
                }
        )
    }
}

// ── Living Marea drawing (Continuous Multi-Harmonic Fluid Tide) ───────────────

internal fun DrawScope.drawLivingMarea(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color
) {
    val w  = size.width
    val h  = size.height
    val cx = w / 2f
    val cy = h / 2f

    // ── Layer 1: Under-tide (Ola secundaria / aura profunda) ──
    val underPath = createHarmonicTidePath(
        cx         = cx,
        cy         = cy,
        baseRadius = minOf(cx, cy) * 0.96f,
        timeSec    = timeSec * 0.72f + 1.25f,
        amplitude  = 1.35f
    )
    val underAngleRad = (timeSec * 0.22f).toDouble()
    drawPath(
        path  = underPath,
        brush = Brush.linearGradient(
            colors = listOf(
                g2.copy(alpha = 0.50f),
                g3.copy(alpha = 0.40f),
                g1.copy(alpha = 0.50f)
            ),
            start = Offset(cx + cx * cos(underAngleRad).toFloat(), cy + cy * sin(underAngleRad).toFloat()),
            end   = Offset(cx - cx * cos(underAngleRad).toFloat(), cy - cy * sin(underAngleRad).toFloat())
        )
    )

    // ── Layer 2: Main Tide Body (Marea principal fluida) ─────
    val mainPath = createHarmonicTidePath(
        cx         = cx,
        cy         = cy,
        baseRadius = minOf(cx, cy) * 0.92f,
        timeSec    = timeSec,
        amplitude  = 1.0f
    )
    val mainAngleRad = (timeSec * 0.38f).toDouble()
    drawPath(
        path  = mainPath,
        brush = Brush.linearGradient(
            colors = listOf(g1, g2, g3, g1),
            start  = Offset(cx + cx * cos(mainAngleRad).toFloat(), cy + cy * sin(mainAngleRad).toFloat()),
            end    = Offset(cx - cx * cos(mainAngleRad).toFloat(), cy - cy * sin(mainAngleRad).toFloat())
        )
    )

    // ── Layer 3: Fluid Sheen & Specular Highlight ────────────
    drawPath(
        path  = mainPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = Offset(cx - w * 0.20f, cy - h * 0.22f),
            radius = w * 0.62f
        )
    )

    // ── Layer 4: Concentric Tidal Ripples (Ondas concéntricas sutiles) ──
    val rippleFractions = listOf(0.50f, 0.66f, 0.80f)
    rippleFractions.forEachIndexed { idx, frac ->
        val rippleTime = timeSec * 0.70f + idx * 0.85f
        val ripplePath = createHarmonicTidePath(
            cx         = cx,
            cy         = cy,
            baseRadius = minOf(cx, cy) * frac,
            timeSec    = rippleTime,
            amplitude  = 0.40f
        )
        drawPath(
            path  = ripplePath,
            color = Color.White.copy(alpha = 0.07f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * Generates a 10-point smooth closed Catmull-Rom cubic spline.
 * Combines incommensurate fluid wave harmonics with dynamic swell envelopes
 * to create an organic, ever-evolving, non-repeating liquid tide
 * that flows 100% seamlessly without any restart or snap.
 */
internal fun createHarmonicTidePath(
    cx         : Float,
    cy         : Float,
    baseRadius : Float,
    timeSec    : Float,
    amplitude  : Float = 1f,
    pointCount : Int = 10
): Path {
    val t = timeSec
    // Organic wave modulation envelopes (slow, incommensurate frequencies)
    // Ensures movements are NOT fixed and continuously morph/change
    val swell1 = 0.078f + 0.022f * sin(t * 0.23f)
    val swell2 = 0.050f + 0.018f * cos(t * 0.17f)
    val swell3 = 0.025f + 0.010f * sin(t * 0.31f)

    val points = Array(pointCount) { i ->
        val theta = (i * 2.0 * Math.PI / pointCount).toFloat()
        // Superposition of fluid harmonics with evolving phases
        val h1 = sin(2f * theta + t * 0.88f) * swell1 * amplitude
        val h2 = cos(3f * theta - t * 0.62f + sin(t * 0.19f)) * swell2 * amplitude
        val h3 = sin(5f * theta + t * 1.15f + cos(t * 0.27f)) * swell3 * amplitude
        val r  = baseRadius * (1f + h1 + h2 + h3)
        Offset(
            cx + r * cos(theta),
            cy + r * sin(theta)
        )
    }

    val path = Path()
    path.moveTo(points[0].x, points[0].y)

    // Catmull-Rom to Cubic Bézier conversion
    for (i in 0 until pointCount) {
        val p0 = points[(i - 1 + pointCount) % pointCount]
        val p1 = points[i]
        val p2 = points[(i + 1) % pointCount]
        val p3 = points[(i + 2) % pointCount]

        val cp1 = Offset(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f
        )
        val cp2 = Offset(
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f
        )

        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
    }
    path.close()
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Song info
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaSongInfo(
    track       : AudioTrack?,
    onSurface   : Color,
    secondary   : Color,
    accentColor : Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text     = track?.name ?: "—",
            style    = MareaTitleStyle,
            color    = onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text     = "Taskly",   // AudioTrack.name is the only artist info available; "Taskly" mirrors MediaMetadata
                style    = MareaArtistStyle,
                color    = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. Waveform seek bar
// ─────────────────────────────────────────────────────────────────────────────

private const val BAR_COUNT = 48
private const val BAR_GAP_DP = 2.5f

@Composable
private fun MareaWaveformSeekBar(
    trackId     : String,
    currentMs   : Long,
    durationMs  : Long,
    secondary   : Color,
    activeColor : Color,
    thumbColor  : Color,
    onSeek      : (Long) -> Unit
) {
    // Drag-seek state strictly keyed to trackId to prevent state leakage from previous songs
    var dragging by remember(trackId) { mutableStateOf(false) }
    var dragProgress by remember(trackId) { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trackId) {
        dragging = false
        dragProgress = 0f
    }

    val progress = when {
        dragging       -> dragProgress
        durationMs > 0 -> (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        else           -> 0f
    }

    val currentBarIndex = (progress * (BAR_COUNT - 1)).roundToInt()
    val uniformBarHeight = 22.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics {
                    contentDescription = "Posición de la canción"
                    stateDescription   =
                        "${(progress * 100).roundToInt()}% · ${formatMs(currentMs)} de ${formatMs(durationMs)}"
                }
                .onGloballyPositioned { coords ->
                    barWidthPx = coords.size.width.toFloat()
                }
                .pointerInput(trackId, durationMs) {
                    detectTapGestures { offset ->
                        if (barWidthPx > 0 && durationMs > 0) {
                            val tapProgress = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onSeek((tapProgress * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(trackId, durationMs) {
                    try {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragging     = true
                                dragProgress = if (barWidthPx > 0) (offset.x / barWidthPx).coerceIn(0f, 1f) else 0f
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragProgress = if (barWidthPx > 0) (change.position.x / barWidthPx).coerceIn(0f, 1f) else 0f
                            },
                            onDragEnd = {
                                dragging = false
                                if (durationMs > 0) onSeek((dragProgress * durationMs).toLong())
                            },
                            onDragCancel = {
                                dragging = false
                            }
                        )
                    } finally {
                        dragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP_DP.dp)
            ) {
                for (index in 0 until BAR_COUNT) {
                    val isPast = index < currentBarIndex
                    val isCurrent = index == currentBarIndex
                    val barColor = when {
                        isCurrent -> thumbColor
                        isPast    -> activeColor
                        else      -> secondary.copy(alpha = 0.22f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(uniformBarHeight)
                            .clip(RoundedCornerShape(50))
                            .background(barColor)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = if (dragging) (dragProgress * durationMs).toLong() else currentMs
            Text(
                text  = formatMs(displayMs),
                style = MareaTimeStyle,
                color = secondary
            )
            Text(
                text  = formatMs(durationMs),
                style = MareaTimeStyle,
                color = secondary
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
//  5. Controls row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaControls(
    isPlaying          : Boolean,
    repeatMode         : AudioRepeatMode,
    shuffleModeEnabled : Boolean,
    surface            : Color,
    onSurface          : Color,
    accentColor        : Color,
    onPlayPause        : () -> Unit,
    onPrevious         : () -> Unit,
    onNext             : () -> Unit,
    onRepeat           : () -> Unit,
    onShuffle          : () -> Unit
) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.CenterVertically
    ) {
        // 1. Previous
        MareaSecondaryButton(
            onClick = onPrevious,
            surface = surface,
            onSurface = onSurface,
            contentDescription = "Canción anterior"
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(26.dp))
        }

        // 2. Loop / Repeat (Replaces heart icon, directly to the left of Play/Pause)
        val isRepeatActive = repeatMode != AudioRepeatMode.OFF
        val repeatBg = if (isRepeatActive) accentColor.copy(alpha = 0.18f) else surface
        val repeatTint = if (isRepeatActive) accentColor else onSurface.copy(alpha = 0.65f)
        val repeatIcon = if (repeatMode == AudioRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
        val repeatDesc = when (repeatMode) {
            AudioRepeatMode.OFF -> "Bucle desactivado"
            AudioRepeatMode.ALL -> "Bucle de lista activo"
            AudioRepeatMode.ONE -> "Bucle de una canción activo"
        }

        MareaSecondaryButton(
            onClick = onRepeat,
            surface = repeatBg,
            onSurface = repeatTint,
            contentDescription = repeatDesc
        ) {
            Icon(
                imageVector        = repeatIcon,
                contentDescription = null,
                tint               = repeatTint,
                modifier           = Modifier.size(24.dp)
            )
        }

        // 3. Play / Pause — the prominent centerpiece
        MareaPlayButton(
            isPlaying   = isPlaying,
            accentColor = accentColor,
            onClick     = onPlayPause
        )

        // 4. Shuffle (Directly to the right of Play/Pause)
        val shuffleBg = if (shuffleModeEnabled) accentColor.copy(alpha = 0.18f) else surface
        val shuffleTint = if (shuffleModeEnabled) accentColor else onSurface.copy(alpha = 0.65f)
        val shuffleDesc = if (shuffleModeEnabled) "Orden aleatorio activado" else "Orden aleatorio desactivado"

        MareaSecondaryButton(
            onClick = onShuffle,
            surface = shuffleBg,
            onSurface = shuffleTint,
            contentDescription = shuffleDesc
        ) {
            Icon(
                imageVector        = Icons.Default.Shuffle,
                contentDescription = null,
                tint               = shuffleTint,
                modifier           = Modifier.size(24.dp)
            )
        }

        // 5. Next
        MareaSecondaryButton(
            onClick = onNext,
            surface = surface,
            onSurface = onSurface,
            contentDescription = "Canción siguiente"
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun MareaPlayButton(
    isPlaying   : Boolean,
    accentColor : Color,
    onClick     : () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue    = if (isPressed) 0.9f else 1f,
        animationSpec  = tween(150),
        label          = "playBtnScale"
    )
    val iconTint = if (accentColor.isLight()) Color(0xFF111827) else Color.White

    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 72.dp)
            .scale(scale)
            .shadow(
                elevation    = 14.dp,
                shape        = RoundedCornerShape(30.dp),
                ambientColor = accentColor.copy(alpha = 0.5f),
                spotColor    = accentColor.copy(alpha = 0.7f)
            )
            .clip(RoundedCornerShape(30.dp))
            .background(accentColor)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .semantics {
                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun MareaSecondaryButton(
    onClick            : () -> Unit,
    surface            : Color,
    onSurface          : Color,
    contentDescription : String,
    content            : @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.9f else 1f,
        animationSpec = tween(150),
        label         = "secBtnScale"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
//  6. Queue panel (inside ModalBottomSheet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaQueuePanel(
    tracks           : List<AudioTrack>,
    currentId        : String?,
    onSurface        : Color,
    secondary        : Color,
    activeTrackColor : Color,
    onTrackClick     : (AudioTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(secondary.copy(alpha = 0.4f))
        )

        Text(
            text     = "Sigue",
            style    = MareaQueueHeaderStyle,
            color    = onSurface,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { _, track ->
                val isCurrent = track.id == currentId
                MareaQueueRow(
                    track            = track,
                    isCurrent        = isCurrent,
                    onSurface        = onSurface,
                    secondary        = secondary,
                    activeTrackColor = activeTrackColor,
                    onClick          = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun MareaQueueRow(
    track            : AudioTrack,
    isCurrent        : Boolean,
    onSurface        : Color,
    secondary        : Color,
    activeTrackColor : Color,
    onClick          : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Thumbnail 44dp with 14dp corners
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isCurrent) activeTrackColor.copy(alpha = 0.22f) else onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model              = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint               = if (isCurrent) activeTrackColor else onSurface.copy(alpha = 0.45f),
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.name,
                style    = MareaQueueTitleStyle,
                color    = if (isCurrent) activeTrackColor else onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold
                             else          androidx.compose.ui.text.font.FontWeight.Medium
            )
            val dur = if (track.durationSeconds > 0)
                "%d:%02d".format(track.durationSeconds / 60, track.durationSeconds % 60)
            else ""
            val subtitle = buildString {
                append("Taskly")
                if (dur.isNotBlank()) append(" · $dur")
            }
            Text(
                text     = subtitle,
                style    = MareaQueueSubtitleStyle,
                color    = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dynamic Palette System — 60 Curated High-Contrast Themes (Max 3 Colors Each)
// ─────────────────────────────────────────────────────────────────────────────

data class MareaTrackPalette(
    val id        : String,
    val name      : String,
    val primary   : Color,
    val secondary : Color,
    val tertiary  : Color,
    val glow      : Color
)

internal val MAREA_PALETTES = listOf(
    // 1. Océano Eléctrico (Cyan neón + Azul cobalto)
    MareaTrackPalette(
        id        = "electric_ocean",
        name      = "Océano Eléctrico",
        primary   = Color(0xFF00E5FF),
        secondary = Color(0xFF0052D4),
        tertiary  = Color(0xFF4364F7),
        glow      = Color(0xFF00E5FF)
    ),
    // 2. Atardecer Carmesí (Coral vivo + Naranja volcánico)
    MareaTrackPalette(
        id        = "sunset_crimson",
        name      = "Atardecer Carmesí",
        primary   = Color(0xFFFF2A6D),
        secondary = Color(0xFFFF5E36),
        tertiary  = Color(0xFFFF9F1C),
        glow      = Color(0xFFFF2A6D)
    ),
    // 3. Aurora Boreal (Verde esmeralda neón + Turquesa ártico)
    MareaTrackPalette(
        id        = "nordic_aurora",
        name      = "Aurora Boreal",
        primary   = Color(0xFF00F260),
        secondary = Color(0xFF0575E6),
        tertiary  = Color(0xFF00F5D4),
        glow      = Color(0xFF00F260)
    ),
    // 4. Cíber Fucsia (Magenta ultravioleta + Violeta neón)
    MareaTrackPalette(
        id        = "cyber_magenta",
        name      = "Cíber Fucsia",
        primary   = Color(0xFFFF007F),
        secondary = Color(0xFF7928CA),
        tertiary  = Color(0xFFFF4D94),
        glow      = Color(0xFFFF007F)
    ),
    // 5. Fuego Solar (Ámbar dorado + Naranja fuego)
    MareaTrackPalette(
        id        = "solar_fire",
        name      = "Fuego Solar",
        primary   = Color(0xFFFF9900),
        secondary = Color(0xFFFF3300),
        tertiary  = Color(0xFFFFD700),
        glow      = Color(0xFFFF9900)
    ),
    // 6. Lavanda Cósmica (Lavanda luminoso + Índigo astral)
    MareaTrackPalette(
        id        = "cosmic_lavender",
        name      = "Lavanda Cósmica",
        primary   = Color(0xFFA855F7),
        secondary = Color(0xFF6366F1),
        tertiary  = Color(0xFFC084FC),
        glow      = Color(0xFFA855F7)
    ),
    // 7. Laguna Esmeralda (Jade profundo + Menta brillante)
    MareaTrackPalette(
        id        = "emerald_lagoon",
        name      = "Laguna Esmeralda",
        primary   = Color(0xFF10B981),
        secondary = Color(0xFF047857),
        tertiary  = Color(0xFF34D399),
        glow      = Color(0xFF10B981)
    ),
    // 8. Rubí Sangre (Rojo rubí puro + Borgoña oscuro)
    MareaTrackPalette(
        id        = "blood_ruby",
        name      = "Rubí Sangre",
        primary   = Color(0xFFFF1E56),
        secondary = Color(0xFF8B0000),
        tertiary  = Color(0xFFFF647C),
        glow      = Color(0xFFFF1E56)
    ),
    // 9. Neón Lima (Lima eléctrico + Verde bosque)
    MareaTrackPalette(
        id        = "neon_lime",
        name      = "Neón Lima",
        primary   = Color(0xFFCCFF00),
        secondary = Color(0xFF10B981),
        tertiary  = Color(0xFFE2F952),
        glow      = Color(0xFFCCFF00)
    ),
    // 10. Bruma Glacial (Celeste polar + Pervinca)
    MareaTrackPalette(
        id        = "glacial_frost",
        name      = "Bruma Glacial",
        primary   = Color(0xFF38BDF8),
        secondary = Color(0xFF6366F1),
        tertiary  = Color(0xFFBAE6FD),
        glow      = Color(0xFF38BDF8)
    ),
    // 11. Durazno Tropical (Coral cálido + Rosa albaricoque)
    MareaTrackPalette(
        id        = "tropical_peach",
        name      = "Durazno Tropical",
        primary   = Color(0xFFFF6B6B),
        secondary = Color(0xFFFF8E53),
        tertiary  = Color(0xFFFFA07A),
        glow      = Color(0xFFFF6B6B)
    ),
    // 12. Amatista Mística (Púrpura real + Violeta noche)
    MareaTrackPalette(
        id        = "mystic_amethyst",
        name      = "Amatista Mística",
        primary   = Color(0xFF8B5CF6),
        secondary = Color(0xFF3B0764),
        tertiary  = Color(0xFFA78BFA),
        glow      = Color(0xFF8B5CF6)
    ),
    // 13. Menta Marina (Turquesa mentolado + Azul abismo)
    MareaTrackPalette(
        id        = "marine_mint",
        name      = "Menta Marina",
        primary   = Color(0xFF2DD4BF),
        secondary = Color(0xFF0F766E),
        tertiary  = Color(0xFF5EEAD4),
        glow      = Color(0xFF2DD4BF)
    ),
    // 14. Oro Celestial (Oro radiante + Naranja tostado)
    MareaTrackPalette(
        id        = "celestial_gold",
        name      = "Oro Celestial",
        primary   = Color(0xFFFACC15),
        secondary = Color(0xFFEA580C),
        tertiary  = Color(0xFFFEF08A),
        glow      = Color(0xFFFACC15)
    ),
    // 15. Ciruela Tokio (Rosa cerezo oscuro + Frambuesa)
    MareaTrackPalette(
        id        = "tokyo_plum",
        name      = "Ciruela Tokio",
        primary   = Color(0xFFE11D48),
        secondary = Color(0xFF4C0519),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFFE11D48)
    ),
    // 16. Índigo Galáctico (Azul zafiro eléctrico + Violeta espacial)
    MareaTrackPalette(
        id        = "galactic_indigo",
        name      = "Índigo Galáctico",
        primary   = Color(0xFF4F46E5),
        secondary = Color(0xFF7C3AED),
        tertiary  = Color(0xFF818CF8),
        glow      = Color(0xFF4F46E5)
    ),
    // 17. Coral Crepúsculo (Naranja coral + Púrpura atardecer)
    MareaTrackPalette(
        id        = "twilight_coral",
        name      = "Coral Crepúsculo",
        primary   = Color(0xFFFB923C),
        secondary = Color(0xFFC026D3),
        tertiary  = Color(0xFFFDE047),
        glow      = Color(0xFFFB923C)
    ),
    // 18. Aqua Estelar (Turquesa cian + Azul ultramar)
    MareaTrackPalette(
        id        = "stellar_aqua",
        name      = "Aqua Estelar",
        primary   = Color(0xFF06B6D4),
        secondary = Color(0xFF1E3A8A),
        tertiary  = Color(0xFF67E8F9),
        glow      = Color(0xFF06B6D4)
    ),
    // 19. Orquídea Neón (Rosa orquídea viva + Violeta eléctrico)
    MareaTrackPalette(
        id        = "neon_orchid",
        name      = "Orquídea Neón",
        primary   = Color(0xFFD946EF),
        secondary = Color(0xFF6D28D9),
        tertiary  = Color(0xFFF0ABFC),
        glow      = Color(0xFFD946EF)
    ),
    // 20. Cobre Ígneo (Cobre metálico + Escarlata ardiente)
    MareaTrackPalette(
        id        = "fiery_copper",
        name      = "Cobre Ígneo",
        primary   = Color(0xFFF97316),
        secondary = Color(0xFF991B1B),
        tertiary  = Color(0xFFFDBA74),
        glow      = Color(0xFFF97316)
    ),
    // 21. Azul Cobalto Profundo (Azul cobalto eléctrico + Cian glacial)
    MareaTrackPalette(
        id        = "deep_cobalt",
        name      = "Azul Cobalto Profundo",
        primary   = Color(0xFF2563EB),
        secondary = Color(0xFF0284C7),
        tertiary  = Color(0xFF93C5FD),
        glow      = Color(0xFF2563EB)
    ),
    // 22. Bosque Encantado (Verde pino + Lima dorada)
    MareaTrackPalette(
        id        = "enchanted_forest",
        name      = "Bosque Encantado",
        primary   = Color(0xFF059669),
        secondary = Color(0xFF84CC16),
        tertiary  = Color(0xFF6EE7B7),
        glow      = Color(0xFF059669)
    ),
    // 23. Rosa Sakura (Rosa pastel intenso + Melocotón vivo)
    MareaTrackPalette(
        id        = "sakura_bloom",
        name      = "Rosa Sakura",
        primary   = Color(0xFFEC4899),
        secondary = Color(0xFFF43F5E),
        tertiary  = Color(0xFFFBCFE8),
        glow      = Color(0xFFEC4899)
    ),
    // 24. Llama Púrpura (Púrpura brillante + Azul eléctrico)
    MareaTrackPalette(
        id        = "purple_flame",
        name      = "Llama Púrpura",
        primary   = Color(0xFF9333EA),
        secondary = Color(0xFF2563EB),
        tertiary  = Color(0xFFC084FC),
        glow      = Color(0xFF9333EA)
    ),
    // 25. Medianoche Cian (Azul noche abisal + Cian hielo)
    MareaTrackPalette(
        id        = "midnight_cyan",
        name      = "Medianoche Cian",
        primary   = Color(0xFF06B6D4),
        secondary = Color(0xFF0F172A),
        tertiary  = Color(0xFF38BDF8),
        glow      = Color(0xFF06B6D4)
    ),
    // 26. Duna Dorada (Ámbar sahariano + Oro cálido)
    MareaTrackPalette(
        id        = "golden_dune",
        name      = "Duna Dorada",
        primary   = Color(0xFFD97706),
        secondary = Color(0xFFB45309),
        tertiary  = Color(0xFFFDE68A),
        glow      = Color(0xFFD97706)
    ),
    // 27. Zafiro Real (Azul real + Cian resplandor)
    MareaTrackPalette(
        id        = "royal_sapphire",
        name      = "Zafiro Real",
        primary   = Color(0xFF1D4ED8),
        secondary = Color(0xFF1E3A8A),
        tertiary  = Color(0xFF93C5FD),
        glow      = Color(0xFF1D4ED8)
    ),
    // 28. Té Matcha (Verde matcha zen + Brote lima)
    MareaTrackPalette(
        id        = "zen_matcha",
        name      = "Té Matcha",
        primary   = Color(0xFF65A30D),
        secondary = Color(0xFF365314),
        tertiary  = Color(0xFFBEF264),
        glow      = Color(0xFF65A30D)
    ),
    // 29. Nebulosa Magenta (Frambuesa cósmico + Violeta noche)
    MareaTrackPalette(
        id        = "magenta_nebula",
        name      = "Nebulosa Magenta",
        primary   = Color(0xFFBE185D),
        secondary = Color(0xFF581C87),
        tertiary  = Color(0xFFF472B6),
        glow      = Color(0xFFBE185D)
    ),
    // 30. Lava Volcánica (Rojo magma + Naranja brasa)
    MareaTrackPalette(
        id        = "volcanic_lava",
        name      = "Lava Volcánica",
        primary   = Color(0xFFDC2626),
        secondary = Color(0xFF7F1D1D),
        tertiary  = Color(0xFFF97316),
        glow      = Color(0xFFDC2626)
    ),
    // 31. Alquimia Turquesa (Turquesa marino + Lima fresco)
    MareaTrackPalette(
        id        = "turquoise_alchemy",
        name      = "Alquimia Turquesa",
        primary   = Color(0xFF0D9488),
        secondary = Color(0xFF84CC16),
        tertiary  = Color(0xFF5EEAD4),
        glow      = Color(0xFF0D9488)
    ),
    // 32. Atardecer Ochentero (Magenta retro + Azul índigo)
    MareaTrackPalette(
        id        = "sunset_retro",
        name      = "Atardecer Ochentero",
        primary   = Color(0xFFE11D48),
        secondary = Color(0xFF4338CA),
        tertiary  = Color(0xFFFDA4AF),
        glow      = Color(0xFFE11D48)
    ),
    // 33. Caribe Profundo (Azul caribeño + Aguamarina)
    MareaTrackPalette(
        id        = "deep_caribbean",
        name      = "Caribe Profundo",
        primary   = Color(0xFF0284C7),
        secondary = Color(0xFF0E7490),
        tertiary  = Color(0xFF67E8F9),
        glow      = Color(0xFF0284C7)
    ),
    // 34. Especias de Bengala (Escarlata vivo + Cúrcuma dorada)
    MareaTrackPalette(
        id        = "bengal_spices",
        name      = "Especias de Bengala",
        primary   = Color(0xFFEA580C),
        secondary = Color(0xFFEAB308),
        tertiary  = Color(0xFFFDBA74),
        glow      = Color(0xFFEA580C)
    ),
    // 35. Selva Esmeralda (Verde botánico + Cian tropical)
    MareaTrackPalette(
        id        = "emerald_rainforest",
        name      = "Selva Esmeralda",
        primary   = Color(0xFF16A34A),
        secondary = Color(0xFF06B6D4),
        tertiary  = Color(0xFF86EFAC),
        glow      = Color(0xFF16A34A)
    ),
    // 36. Cuarzo Rosa (Rosa mineral + Orquídea suave)
    MareaTrackPalette(
        id        = "rose_quartz",
        name      = "Cuarzo Rosa",
        primary   = Color(0xFFF43F5E),
        secondary = Color(0xFFA21CAF),
        tertiary  = Color(0xFFFECDD3),
        glow      = Color(0xFFF43F5E)
    ),
    // 37. Tormenta Eléctrica (Índigo noche + Amarillo rayo)
    MareaTrackPalette(
        id        = "electric_storm",
        name      = "Tormenta Eléctrica",
        primary   = Color(0xFF6366F1),
        secondary = Color(0xFF1E1B4B),
        tertiary  = Color(0xFFFACC15),
        glow      = Color(0xFF6366F1)
    ),
    // 38. Cereza Negra (Guinda profunda + Ciruela mística)
    MareaTrackPalette(
        id        = "black_cherry",
        name      = "Cereza Negra",
        primary   = Color(0xFF9F1239),
        secondary = Color(0xFF4C0519),
        tertiary  = Color(0xFFFDA4AF),
        glow      = Color(0xFF9F1239)
    ),
    // 39. Ámbar Silvestre (Ámbar miel + Oliva suave)
    MareaTrackPalette(
        id        = "wild_amber",
        name      = "Ámbar Silvestre",
        primary   = Color(0xFFD97706),
        secondary = Color(0xFF4D7C0F),
        tertiary  = Color(0xFFFEF08A),
        glow      = Color(0xFFD97706)
    ),
    // 40. Hielo Ártico (Turquesa gélido + Violeta polar)
    MareaTrackPalette(
        id        = "arctic_ice",
        name      = "Hielo Ártico",
        primary   = Color(0xFF06B6D4),
        secondary = Color(0xFF7C3AED),
        tertiary  = Color(0xFFA5F3FC),
        glow      = Color(0xFF06B6D4)
    ),
    // 41. Brisa Mediterránea (Cobalto marino + Terracota cálido)
    MareaTrackPalette(
        id        = "mediterranean_breeze",
        name      = "Brisa Mediterránea",
        primary   = Color(0xFF2563EB),
        secondary = Color(0xFFEA580C),
        tertiary  = Color(0xFFBAE6FD),
        glow      = Color(0xFF2563EB)
    ),
    // 42. Eclipse Solar (Púrpura sideral + Naranja corona)
    MareaTrackPalette(
        id        = "solar_eclipse",
        name      = "Eclipse Solar",
        primary   = Color(0xFF7C3AED),
        secondary = Color(0xFFF97316),
        tertiary  = Color(0xFFFDE047),
        glow      = Color(0xFF7C3AED)
    ),
    // 43. Manzanilla y Miel (Amarillo solar + Verde oliva)
    MareaTrackPalette(
        id        = "chamomile_honey",
        name      = "Manzanilla y Miel",
        primary   = Color(0xFFEAB308),
        secondary = Color(0xFF65A30D),
        tertiary  = Color(0xFFFEF08A),
        glow      = Color(0xFFEAB308)
    ),
    // 44. Arrecife de Coral (Coral brillante + Turquesa marino)
    MareaTrackPalette(
        id        = "coral_reef",
        name      = "Arrecife de Coral",
        primary   = Color(0xFFF43F5E),
        secondary = Color(0xFF0D9488),
        tertiary  = Color(0xFFFECDD3),
        glow      = Color(0xFFF43F5E)
    ),
    // 45. Iris Astral (Violeta iris + Cian estelar)
    MareaTrackPalette(
        id        = "astral_iris",
        name      = "Iris Astral",
        primary   = Color(0xFF8B5CF6),
        secondary = Color(0xFF06B6D4),
        tertiary  = Color(0xFFC4B5FD),
        glow      = Color(0xFF8B5CF6)
    ),
    // 46. Carmesí y Oro (Rojo carmesí + Oro imperial)
    MareaTrackPalette(
        id        = "crimson_gold",
        name      = "Carmesí y Oro",
        primary   = Color(0xFFBE123C),
        secondary = Color(0xFFEAB308),
        tertiary  = Color(0xFFFCA5A5),
        glow      = Color(0xFFBE123C)
    ),
    // 47. Esmeralda Sublime (Esmeralda puro + Menta agua)
    MareaTrackPalette(
        id        = "sublime_emerald",
        name      = "Esmeralda Sublime",
        primary   = Color(0xFF059669),
        secondary = Color(0xFF064E3B),
        tertiary  = Color(0xFF6EE7B7),
        glow      = Color(0xFF059669)
    ),
    // 48. Neón Cyberpunk (Cian neón + Azul medianoche)
    MareaTrackPalette(
        id        = "cyberpunk_neon",
        name      = "Neón Cyberpunk",
        primary   = Color(0xFF00F5FF),
        secondary = Color(0xFF1E1B4B),
        tertiary  = Color(0xFF38BDF8),
        glow      = Color(0xFF00F5FF)
    ),
    // 49. Crepúsculo de Kioto (Bermellón japonés + Púrpura santuario)
    MareaTrackPalette(
        id        = "kyoto_twilight",
        name      = "Crepúsculo de Kioto",
        primary   = Color(0xFFEA580C),
        secondary = Color(0xFF7E22CE),
        tertiary  = Color(0xFFFDBA74),
        glow      = Color(0xFFEA580C)
    ),
    // 50. Glaciar Andino (Azul glaciar + Verde mentol)
    MareaTrackPalette(
        id        = "andean_glacier",
        name      = "Glaciar Andino",
        primary   = Color(0xFF0284C7),
        secondary = Color(0xFF10B981),
        tertiary  = Color(0xFFBAE6FD),
        glow      = Color(0xFF0284C7)
    ),
    // 51. Frambuesa Eléctrica (Frambuesa puro + Azul zafiro)
    MareaTrackPalette(
        id        = "electric_raspberry",
        name      = "Frambuesa Eléctrica",
        primary   = Color(0xFFE11D48),
        secondary = Color(0xFF3B82F6),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFFE11D48)
    ),
    // 52. Luciérnaga Nocturna (Púrpura terciopelo + Lima luminosa)
    MareaTrackPalette(
        id        = "night_firefly",
        name      = "Luciérnaga Nocturna",
        primary   = Color(0xFF7C3AED),
        secondary = Color(0xFF84CC16),
        tertiary  = Color(0xFFC084FC),
        glow      = Color(0xFF7C3AED)
    ),
    // 53. Chicle Neón (Rosa chicle vivo + Azul celeste)
    MareaTrackPalette(
        id        = "neon_bubblegum",
        name      = "Chicle Neón",
        primary   = Color(0xFFF43F5E),
        secondary = Color(0xFF38BDF8),
        tertiary  = Color(0xFFF472B6),
        glow      = Color(0xFFF43F5E)
    ),
    // 54. Terracota Solar (Terracota volcánico + Oro ámbar)
    MareaTrackPalette(
        id        = "solar_terracotta",
        name      = "Terracota Solar",
        primary   = Color(0xFFC2410C),
        secondary = Color(0xFFD97706),
        tertiary  = Color(0xFFFDBA74),
        glow      = Color(0xFFC2410C)
    ),
    // 55. Zafiro y Menta (Azul índigo + Menta caribe)
    MareaTrackPalette(
        id        = "sapphire_mint",
        name      = "Zafiro y Menta",
        primary   = Color(0xFF4338CA),
        secondary = Color(0xFF14B8A6),
        tertiary  = Color(0xFF818CF8),
        glow      = Color(0xFF4338CA)
    ),
    // 56. Granate Real (Rojo granate oscuro + Azafrán)
    MareaTrackPalette(
        id        = "royal_garnet",
        name      = "Granate Real",
        primary   = Color(0xFF881337),
        secondary = Color(0xFFF59E0B),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFF881337)
    ),
    // 57. Bambú Fresco (Verde bambú + Azul brisa)
    MareaTrackPalette(
        id        = "fresh_bamboo",
        name      = "Bambú Fresco",
        primary   = Color(0xFF16A34A),
        secondary = Color(0xFF0284C7),
        tertiary  = Color(0xFF86EFAC),
        glow      = Color(0xFF16A34A)
    ),
    // 58. Horizonte Eléctrico (Azul eléctrico + Magenta neón)
    MareaTrackPalette(
        id        = "electric_horizon",
        name      = "Horizonte Eléctrico",
        primary   = Color(0xFF2563EB),
        secondary = Color(0xFFD946EF),
        tertiary  = Color(0xFF93C5FD),
        glow      = Color(0xFF2563EB)
    ),
    // 59. Solsticio Dorado (Oro solar vivo + Naranja mandarino)
    MareaTrackPalette(
        id        = "solstice_gold",
        name      = "Solsticio Dorado",
        primary   = Color(0xFFEAB308),
        secondary = Color(0xFFF97316),
        tertiary  = Color(0xFFFEF08A),
        glow      = Color(0xFFEAB308)
    ),
    // 60. Amatista y Jade (Púrpura amatista + Jade fresco)
    MareaTrackPalette(
        id        = "amethyst_jade",
        name      = "Amatista y Jade",
        primary   = Color(0xFF8B5CF6),
        secondary = Color(0xFF10B981),
        tertiary  = Color(0xFFDDD6FE),
        glow      = Color(0xFF8B5CF6)
    )
)

/** Returns true if this color is perceptually light (for contrasting dark icons/text). */
private fun Color.isLight(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance > 0.60f
}

/**
 * Maps any [AudioTrack] deterministically to one of the 60 curated vibrant palettes
 * using a 64-bit Murmur-style avalanche hash on id, name, and url.
 * An optional [previousIdx] guarantees consecutive tracks never repeat the same theme (shifting by a coprime stride).
 * An optional [offset] allows manually cycling/randomizing themes on demand.
 */
internal fun getTrackPalette(track: AudioTrack?, offset: Int = 0, previousIdx: Int = -1): MareaTrackPalette {
    if (track == null) {
        val idx = ((offset % MAREA_PALETTES.size) + MAREA_PALETTES.size) % MAREA_PALETTES.size
        return MAREA_PALETTES[idx]
    }
    val rawKey = "${track.id}|${track.name}|${track.url}".ifBlank { track.name }
    if (rawKey.isBlank()) {
        val idx = ((offset % MAREA_PALETTES.size) + MAREA_PALETTES.size) % MAREA_PALETTES.size
        return MAREA_PALETTES[idx]
    }

    var h = -3750763034362895579L
    for (i in 0 until rawKey.length) {
        h = h xor rawKey[i].code.toLong()
        h = h * 1099511628211L
    }
    // Avalanche bit mixing
    h = h xor (h ushr 33)
    h = h * -49064778989728563L
    h = h xor (h ushr 33)

    var baseIdx = (kotlin.math.abs(h) % MAREA_PALETTES.size).toInt()
    // Anti-repetition: if it matches the immediately previous song's palette, jump by 7 (coprime to 60)
    if (previousIdx in MAREA_PALETTES.indices && baseIdx == previousIdx) {
        baseIdx = (baseIdx + 7) % MAREA_PALETTES.size
    }
    val finalIdx = ((baseIdx + offset) % MAREA_PALETTES.size + MAREA_PALETTES.size) % MAREA_PALETTES.size
    return MAREA_PALETTES[finalIdx]
}
