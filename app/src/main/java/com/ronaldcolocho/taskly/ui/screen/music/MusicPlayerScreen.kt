package com.ronaldcolocho.taskly.ui.screen.music

// ─────────────────────────────────────────────────────────────────────────────
//  MusicPlayerScreen.kt  —  Diseño "Marea"
//  SOLO UI: no modifica ViewModels, repositorios, servicios ni lógica de audio.
//  Conecta a los callbacks ya existentes de AudioPlayerController.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import com.ronaldcolocho.taskly.audio.PlayerVisualEffect
import androidx.compose.material.icons.filled.Tune
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
import kotlin.math.sqrt

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

    // ── Per-song dynamic color palette (deterministic per track + anti-repetition + persistent user tap shuffle) ─
    val trackId = playerState.currentTrack?.id ?: ""
    val userPaletteOffset by audioController.paletteOffset.collectAsStateWithLifecycle()
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

    // ── Visual effects state ──────────────────────────────────
    val currentEffect by audioController.visualEffect.collectAsStateWithLifecycle()
    val resolvedEffect = remember(trackId, currentEffect) {
        if (currentEffect == PlayerVisualEffect.RANDOM) {
            val nonRandomStyles = listOf(
                PlayerVisualEffect.MAREA,
                PlayerVisualEffect.MAREA_V2,
                PlayerVisualEffect.GALAXY,
                PlayerVisualEffect.VORTEX,
                PlayerVisualEffect.BLACK_HOLE,
                PlayerVisualEffect.SUPERNOVA,
                PlayerVisualEffect.PLASMA_STORM,
                PlayerVisualEffect.PRISM_FRACTAL
            )
            val hash = kotlin.math.abs((trackId + (playerState.currentTrack?.name ?: "")).hashCode())
            nonRandomStyles[hash % nonRandomStyles.size]
        } else {
            currentEffect
        }
    }
    var showEffectsSheet by remember { mutableStateOf(false) }

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
                playlistName   = playlistName,
                secondary      = secondary,
                onBack         = onNavigateBack,
                onEffectsClick = { showEffectsSheet = true },
                onQueueClick   = { showQueue = true }
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
                    effect          = resolvedEffect,
                    onSwipeLeft     = { audioController.next() },
                    onSwipeRight    = { audioController.prev() },
                    onDoubleTap     = { /* Favorito — placeholder: no existe isFavorite en el modelo */ },
                    onCyclePalette  = {
                        val nextOffset = audioController.cyclePaletteForTrack(trackId, MAREA_PALETTES.size)
                        val nextIdx = ((basePaletteIdx + nextOffset) % MAREA_PALETTES.size + MAREA_PALETTES.size) % MAREA_PALETTES.size
                        val nextPalette = MAREA_PALETTES[nextIdx]
                        feedbackMessage = "Tema: ${nextPalette.name}"
                    }
                )
            }

            // Hint text & Theme / Mode badge
            val statusText = feedbackMessage
                ?: if (userPaletteOffset > 0) "Tema: ${trackPalette.name} · ${resolvedEffect.title}"
                else "Desliza para cambiar · Toca para variar color (${resolvedEffect.title})"
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
            EditableMusicQueue(
                tracks           = playerState.tracks,
                currentId        = playerState.currentTrack?.id,
                shuffleEnabled   = playerState.shuffleModeEnabled,
                onSelect = { id ->
                    audioController.skipToQueueItem(id)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showQueue = false }
                },
                onReorder = audioController::reorderQueue,
                onRemove = audioController::removeFromQueue
            )
        }
    }

    // Visual effects bottom sheet
    if (showEffectsSheet) {
        PlayerVisualEffectsSheet(
            currentEffect  = currentEffect,
            onSelectEffect = { effect ->
                audioController.setVisualEffect(effect)
                showEffectsSheet = false
                feedbackMessage = "Efecto: ${effect.title}"
            },
            onDismiss      = { showEffectsSheet = false },
            accentColor    = animPrimary,
            currentPaletteName = trackPalette.name,
            activePalettePrimary = trackPalette.primary,
            onCyclePalette = {
                val nextOffset = audioController.cyclePaletteForTrack(trackId, MAREA_PALETTES.size)
                val nextIdx = ((basePaletteIdx + nextOffset) % MAREA_PALETTES.size + MAREA_PALETTES.size) % MAREA_PALETTES.size
                val nextPalette = MAREA_PALETTES[nextIdx]
                feedbackMessage = "Tema: ${nextPalette.name}"
            },
            onResetPalette = {
                audioController.setPaletteOffsetForTrack(trackId, 0)
                feedbackMessage = "Color restablecido"
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MareaTopBar(
    playlistName   : String,
    secondary      : Color,
    onBack         : () -> Unit,
    onEffectsClick : () -> Unit,
    onQueueClick   : () -> Unit
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onEffectsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Efectos visuales del reproductor",
                    tint = secondary,
                    modifier = Modifier.size(20.dp)
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
    effect         : PlayerVisualEffect = PlayerVisualEffect.MAREA,
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
    val mareaV2Motion = remember { MareaV2Motion() }
    val mareaV2Geometry = remember { MareaV2Geometry() }
    val currentEffect by rememberUpdatedState(effect)

    val playbackSpeed by animateFloatAsState(
        targetValue   = if (isPlaying) 1.0f else 0.65f,
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
                    if (currentEffect == PlayerVisualEffect.MAREA_V2) {
                        mareaV2Motion.advance(dt)
                    }
                }
                lastNanos = nowNanos
            }
        }
    }

    // V2 reads its clock in drawing/layer phases, without recomposing the album every frame.
    val t = if (effect == PlayerVisualEffect.MAREA_V2) 0f else animTimeSec

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

    // ── Marea v2: Real-time Accelerometer & Motion Physics ────────────────
    DisposableEffect(effect, animationsEnabled) {
        if (effect != PlayerVisualEffect.MAREA_V2 || !animationsEnabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    mareaV2Motion.onSensor(
                        event.values[0], event.values[1], event.values[2], event.timestamp
                    )
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (accelSensor != null) {
                sensorManager?.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }

            onDispose {
                sensorManager?.unregisterListener(listener)
                mareaV2Motion.reset()
            }
        }
    }

    // Touch ripple interaction tracking
    var touchRipplePos by remember { mutableStateOf(Offset.Zero) }
    var touchRippleStartSec by remember { mutableFloatStateOf(-100f) }

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
                    if (animationsEnabled && effect == PlayerVisualEffect.MAREA_V2) {
                        val layerTime = animTimeSec
                        alpha = 0.58f + sin(layerTime * 0.54f) * 0.14f + cos(layerTime * 0.28f) * 0.05f
                        val scale = (1f + sin(layerTime * 0.68f) * 0.018f + cos(layerTime * 0.39f) * 0.009f) * 1.04f
                        scaleX = scale
                        scaleY = scale
                    }
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
                    if (animationsEnabled && effect == PlayerVisualEffect.MAREA_V2) {
                        val layerTime = animTimeSec
                        val scale = swipeScale.value * (1f + sin(layerTime * 0.68f) * 0.018f + cos(layerTime * 0.39f) * 0.009f)
                        scaleX = scale
                        scaleY = scale
                    }
                    if (animationsEnabled && effect != PlayerVisualEffect.MAREA_V2) {
                        rotationZ = tiltAngle
                    }
                }
                .drawBehind {
                    val activeTime = if (animationsEnabled) animTimeSec else 0f
                    when (effect) {
                        PlayerVisualEffect.MAREA -> drawLivingMarea(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3
                        )
                        PlayerVisualEffect.MAREA_V2 -> drawLivingMareaV2(
                            timeSec      = activeTime * 1.25f,
                            g1           = g1,
                            g2           = g2,
                            g3           = g3,
                            glow         = glow,
                            tiltX        = mareaV2Motion.x,
                            tiltY        = mareaV2Motion.y,
                            shakeImpulse = mareaV2Motion.energy,
                            geometry     = mareaV2Geometry,
                            touchPos     = touchRipplePos,
                            touchAgeSec  = if (touchRippleStartSec > 0f) (activeTime - touchRippleStartSec) else 99f
                        )
                        PlayerVisualEffect.GALAXY -> drawLivingGalaxy(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.VORTEX -> drawLivingVortex(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.BLACK_HOLE -> drawLivingBlackHole(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.SUPERNOVA -> drawLivingSupernova(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.PLASMA_STORM -> drawLivingPlasmaStorm(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.PRISM_FRACTAL -> drawLivingPrismFractal(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3, glow = glow
                        )
                        PlayerVisualEffect.RANDOM -> drawLivingMarea(
                            timeSec = activeTime,
                            g1 = g1, g2 = g2, g3 = g3
                        )
                    }
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
                        onPress = { offset ->
                            touchRipplePos = offset
                            touchRippleStartSec = animTimeSec
                        },
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
            } else if (effect == PlayerVisualEffect.MAREA) {
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

            // 3. Central Vinyl Hole with beveled shadow, metallic rim and spindle (only for Marea turntable mode)
            if (effect == PlayerVisualEffect.MAREA) {
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

// ── Living Marea v2 drawing — Interactive Hydrodynamic Fluid Physics ──────────

/** Reused only by Marea V2; no point arrays or native paths allocated per frame. */
internal class MareaV2Contour(val count: Int) {
    val path = Path()
    val x = FloatArray(count)
    val y = FloatArray(count)
    val viscousX = FloatArray(count)
    val viscousY = FloatArray(count)
    var lastTime = Float.NaN
    val cosines = FloatArray(count) { cos(it * 2f * Math.PI.toFloat() / count) }
    val sines = FloatArray(count) { sin(it * 2f * Math.PI.toFloat() / count) }
}

internal class MareaV2Geometry {
    val under = MareaV2Contour(24)
    val main = MareaV2Contour(24)
    val rings = Array(3) { MareaV2Contour(20) }
    val caustic = Path()
    val current = Path()
    val reflection = Path()
}

internal fun createSloshedLiquidPath(
    cx: Float,
    cy: Float,
    containerRadius: Float,
    tiltX: Float,
    tiltY: Float,
    sloshStrength: Float,
    timeSec: Float,
    amplitude: Float,
    shakeImpulse: Float = 0f,
    viscositySeconds: Float = 0.45f,
    edgeWaveStrength: Float = 0f,
    contour: MareaV2Contour
): Path {
    val path = contour.path
    path.reset()
    if (containerRadius < 5f) return path
    val t = timeSec
    // Independent, phase-modulated currents keep moving even with no sensor available.
    val driftX = MareaV2Current.x(t)
    val driftY = MareaV2Current.y(t)
    val centerX = cx + containerRadius * (driftX + tiltX * 0.065f * sloshStrength)
    val centerY = cy + containerRadius * (driftY + tiltY * 0.065f * sloshStrength)
    val offsetX = centerX - cx
    val offsetY = centerY - cy
    // Let the larger crests rise over the porthole rim while keeping the contour bounded.
    val safeRadius = containerRadius * 1.12f - sqrt(offsetX * offsetX + offsetY * offsetY)
    val swell2 = (0.047f + sin(t * 0.097f) * 0.013f) * amplitude
    val swell3 = (0.030f + cos(t * 0.073f + 1.1f) * 0.009f) * amplitude
    val phase2 = t * 0.82f + sin(t * 0.127f) * 0.65f
    val phase3 = -t * 0.61f + cos(t * 0.083f) * 0.48f
    val phase4 = t * 0.51f + sin(t * 0.109f) * 0.35f
    val count = contour.count
    val initialized = contour.lastTime.isFinite() && t >= contour.lastTime
    val dt = if (initialized) (t - contour.lastTime).coerceIn(0f, 0.05f) else 0f
    contour.lastTime = t
    for (i in 0 until count) {
        val theta = i * 2f * Math.PI.toFloat() / count
        val ux = contour.cosines[i]
        val uy = contour.sines[i]
        // Cartesian projection is continuous through zero tilt and across +/- PI.
        val pull = ux * tiltX + uy * tiltY
        val extension = pull * 0.060f * sloshStrength
        val wave2 = sin(theta * 2f + phase2)
        val wave3 = cos(theta * 3f + phase3)
        val swell = wave2 * swell2 + wave3 * swell3 +
            sin(theta * 4f + phase4) * 0.012f * amplitude
        // Crest and trough travel along the left and right shoulders of the vessel.
        val edgeWeight = ux * ux * ux * ux
        val edgeWave = edgeWaveStrength * edgeWeight * amplitude *
            (wave2 * 0.11f + wave3 * 0.038f)
        // A broad traveling surge replaces the old fast seven-lobed shake flutter.
        val surge = sin(theta * 2f - t * 0.67f + 0.4f) * 0.026f * shakeImpulse
        val radius = minOf(containerRadius * (0.735f + extension + swell + edgeWave + surge), safeRadius)
        // Neighboring regions follow at slightly different rates, stretching cohesively.
        // Store normalized positions so resizing never causes a stale-pixel jump.
        val targetX = (centerX - cx + radius * ux) / containerRadius
        val targetY = (centerY - cy + radius * uy) / containerRadius
        val response = viscositySeconds * (1f + 0.22f * sin(theta * 2f + 0.7f))
        val blend = if (initialized) 1f - kotlin.math.exp(-dt / response) else 1f
        contour.viscousX[i] += (targetX - contour.viscousX[i]) * blend
        contour.viscousY[i] += (targetY - contour.viscousY[i]) * blend
        contour.x[i] = cx + containerRadius * contour.viscousX[i]
        contour.y[i] = cy + containerRadius * contour.viscousY[i]
    }
    path.moveTo(contour.x[0], contour.y[0])
    for (i in 0 until count) {
        val p0 = (i - 1 + count) % count
        val p2 = (i + 1) % count
        val p3 = (i + 2) % count
        path.cubicTo(
            contour.x[i] + (contour.x[p2] - contour.x[p0]) / 6f,
            contour.y[i] + (contour.y[p2] - contour.y[p0]) / 6f,
            contour.x[p2] - (contour.x[p3] - contour.x[i]) / 6f,
            contour.y[p2] - (contour.y[p3] - contour.y[i]) / 6f,
            contour.x[p2], contour.y[p2]
        )
    }
    path.close()
    return path
}

/** Shallow currents and highlights share the actual deformed surface, not a rotating overlay. */
private fun DrawScope.drawMareaV2Surface(
    t: Float,
    radius: Float,
    deep: Color,
    shadow: Color,
    glow: Color,
    geometry: MareaV2Geometry
) {
    val surface = geometry.main
    var centerX = 0f
    var centerY = 0f
    for (i in 0 until surface.count) {
        centerX += surface.x[i]
        centerY += surface.y[i]
    }
    centerX /= surface.count
    centerY /= surface.count
    val cap = androidx.compose.ui.graphics.StrokeCap.Round
    clipPath(surface.path) {
        // Three broad, slowly folding streams. Their ends fade inside the liquid.
        for (layer in 0..2) {
            val phase = t * (0.13f + layer * 0.017f) + layer * 2.1f
            val bend = sin(phase + 0.4f * sin(t * 0.057f)) * radius * 0.19f
            val depth = (layer - 1) * radius * 0.24f
            val lagX = (geometry.under.x[6] - surface.x[6]) * 0.4f
            val lagY = (geometry.under.y[6] - surface.y[6]) * 0.4f
            val start = Offset(centerX - radius * 0.73f, centerY + depth + bend * 0.3f)
            val end = Offset(centerX + radius * 0.73f, centerY + depth - bend * 0.4f)
            val path = geometry.current
            path.reset()
            path.moveTo(start.x, start.y)
            path.cubicTo(
                centerX - radius * 0.30f + lagX, centerY + depth - radius * 0.32f + bend + lagY,
                centerX + radius * 0.18f + lagX, centerY + depth + radius * 0.32f + bend + lagY,
                end.x, end.y
            )
            val tint = if (layer == 1) glow else if (layer == 0) deep else shadow
            val opacity = 0.065f + 0.015f * sin(phase * 0.7f + layer)
            val brush = Brush.linearGradient(
                0f to tint.copy(alpha = 0f),
                0.35f to tint.copy(alpha = opacity),
                0.65f to tint.copy(alpha = opacity * 0.8f),
                1f to tint.copy(alpha = 0f),
                start = start, end = end
            )
            drawPath(path, brush, style = Stroke(width = radius * 0.20f, cap = cap))
            drawPath(path, brush, style = Stroke(width = radius * 0.10f, cap = cap))
        }

        // The light pool rides the upper shoulder as that shoulder stretches.
        val shoulderX = (surface.x[15] + surface.x[18] + surface.x[20]) / 3f
        val shoulderY = (surface.y[15] + surface.y[18] + surface.y[20]) / 3f
        drawPath(
            surface.path,
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.27f + 0.025f * sin(t * 0.117f)),
                    glow.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(
                    centerX + (shoulderX - centerX) * 0.67f,
                    centerY + (shoulderY - centerY) * 0.67f
                ),
                radius = radius * 1.05f
            )
        )
        // Short specular arcs use the very same cubic tangents as the surface.
        for (arc in 0..1) {
            val first = if (arc == 0) 13 else 1
            val last = if (arc == 0) 20 else 5
            val inset = if (arc == 0) 0.93f else 0.90f
            val path = geometry.reflection
            path.reset()
            path.moveTo(
                centerX + (surface.x[first] - centerX) * inset,
                centerY + (surface.y[first] - centerY) * inset
            )
            for (i in first until last) {
                val p0 = i - 1
                val p2 = i + 1
                val p3 = i + 2
                path.cubicTo(
                    centerX + (surface.x[i] + (surface.x[p2] - surface.x[p0]) / 6f - centerX) * inset,
                    centerY + (surface.y[i] + (surface.y[p2] - surface.y[p0]) / 6f - centerY) * inset,
                    centerX + (surface.x[p2] - (surface.x[p3] - surface.x[i]) / 6f - centerX) * inset,
                    centerY + (surface.y[p2] - (surface.y[p3] - surface.y[i]) / 6f - centerY) * inset,
                    centerX + (surface.x[p2] - centerX) * inset,
                    centerY + (surface.y[p2] - centerY) * inset
                )
            }
            val opacity = (if (arc == 0) 0.26f else 0.11f) *
                (0.85f + 0.15f * sin(t * 0.163f + arc * 2f))
            val brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.45f to Color.White.copy(alpha = opacity),
                1f to Color.Transparent,
                start = Offset(surface.x[first], surface.y[first]),
                end = Offset(surface.x[last], surface.y[last])
            )
            drawPath(path, brush, style = Stroke(width = radius * 0.038f, cap = cap), alpha = 0.3f)
            drawPath(path, brush, style = Stroke(width = radius * 0.010f, cap = cap))
        }
    }
}

internal fun DrawScope.drawLivingMareaV2(
    timeSec      : Float,
    g1           : Color,
    g2           : Color,
    g3           : Color,
    glow         : Color,
    tiltX        : Float,
    tiltY        : Float,
    shakeImpulse : Float,
    geometry     : MareaV2Geometry,
    touchPos     : Offset,
    touchAgeSec  : Float
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f

    // Rotation & layout safety guard: if canvas is zero or tiny (e.g. landscape transition), skip draw
    if (maxR < 5f) return

    val t = timeSec
    val PI = Math.PI.toFloat()

    // ── Hydrodynamic Gravity & Motion Orientation ───────────────────────────
    val gravMag = sqrt(tiltX * tiltX + tiltY * tiltY).coerceIn(0f, 1.8f)
    // Lighting follows a continuous vector, avoiding atan2 branch-cut jumps.
    val lightX = 0.45f + tiltX * 0.24f + sin(t * 0.173f) * 0.10f
    val lightY = -0.65f + tiltY * 0.24f + cos(t * 0.137f) * 0.08f

    // Dynamic wave power combining gravity tilt with device shake
    val wavePower = 0.35f + gravMag * 0.20f + shakeImpulse * 0.45f

    // ── Outer Glass Porthole Rim (Vessel boundary) ──────────────────────────
    drawCircle(
        color = Color.White.copy(alpha = 0.12f),
        radius = maxR,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
        color = glow.copy(alpha = 0.07f),
        radius = maxR * 0.99f,
        center = Offset(cx, cy),
        style = Stroke(width = 3.dp.toPx())
    )

    // ── Layer 0: Ambient Fluid Luminous Floor (Deep centered water glow) ────
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                g2.copy(alpha = 0.62f),
                g3.copy(alpha = 0.42f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 1.02f
        ),
        radius = maxR * 0.98f,
        center = Offset(cx, cy)
    )

    // ── Layer 1: Immiscible Deep Fluid Base (Heavy dense liquid) ────────────
    val underPath = createSloshedLiquidPath(
        cx = cx,
        cy = cy,
        containerRadius = maxR,
        tiltX = tiltX,
        tiltY = tiltY,
        sloshStrength = 0.75f,
        timeSec = t,
        amplitude = 1.05f,
        shakeImpulse = shakeImpulse,
        viscositySeconds = 1.05f,
        edgeWaveStrength = 1.2f,
        contour = geometry.under
    )
    val underAngle = (t * 0.06f + tiltX * 0.12f + tiltY * 0.08f).toDouble()
    drawPath(
        path = underPath,
        brush = Brush.linearGradient(
            colors = listOf(
                g3.copy(alpha = 0.92f),
                g2.copy(alpha = 0.78f),
                g1.copy(alpha = 0.88f),
                g3.copy(alpha = 0.92f)
            ),
            start = Offset(cx + cx * cos(underAngle).toFloat(), cy + cy * sin(underAngle).toFloat()),
            end   = Offset(cx - cx * cos(underAngle).toFloat(), cy - cy * sin(underAngle).toFloat())
        )
    )

    // ── Layer 2: Sunlight Water Caustics (Submerged light refractions) ──────
    // Deep refractions respond gently to the same continuous gravity projection.
    val causticCount = 7
    for (c in 0 until causticCount) {
        val cFrac = c / causticCount.toFloat()
        val cPath = geometry.caustic
        cPath.reset()
        val steps = 36
        val cRadius = maxR * (0.22f + cFrac * 0.50f)
        val cSpeed = t * 0.14f + (c * 0.4f)
        for (s in 0..steps) {
            val p = s / steps.toFloat()
            val angle = p * 2f * PI
            val alignment = cos(angle) * tiltX + sin(angle) * tiltY
            val causticRipple = sin(angle * 3f + cSpeed) * (maxR * 0.035f) +
                                cos(angle * 5f - cSpeed * 0.6f + c * 0.37f) * (maxR * 0.020f)
            // Extremos stretch more as we move outward from center (cFrac)
            val sloshStretch = alignment * (maxR * 0.10f * cFrac)
            val minCausticR = (maxR * 0.04f).coerceAtLeast(1f)
            val maxCausticR = (maxR * 0.96f).coerceAtLeast(minCausticR)
            val cr = (cRadius + causticRipple + sloshStretch).coerceIn(minCausticR, maxCausticR)
            val x = cx + cr * cos(angle)
            val y = cy + cr * sin(angle)
            if (s == 0) cPath.moveTo(x, y) else cPath.lineTo(x, y)
        }
        cPath.close()

        val causticAlpha = (0.15f + 0.08f * sin(t * 0.6f + c * 1.5f)).coerceIn(0.04f, 0.30f)
        drawPath(
            path = cPath,
            color = Color.White.copy(alpha = causticAlpha),
            style = Stroke(width = (1.4f + cFrac * 1.8f).dp.toPx())
        )
    }

    // ── Layer 3: Floating Viscous Crest (Upper fluid layer with high inertia)
    val mainPath = createSloshedLiquidPath(
        cx = cx,
        cy = cy,
        containerRadius = maxR,
        tiltX = tiltX,
        tiltY = tiltY,
        sloshStrength = 1.10f,
        timeSec = t,
        amplitude = 1.0f,
        shakeImpulse = shakeImpulse,
        edgeWaveStrength = 1.55f,
        contour = geometry.main
    )
    val mainAngle = (t * 0.09f + tiltX * 0.16f + tiltY * 0.10f).toDouble()
    drawPath(
        path = mainPath,
        brush = Brush.linearGradient(
            colors = listOf(
                g1.copy(alpha = 0.96f),
                glow.copy(alpha = 0.90f),
                g2.copy(alpha = 0.92f),
                g1.copy(alpha = 0.96f)
            ),
            start = Offset(cx + cx * cos(mainAngle).toFloat(), cy + cy * sin(mainAngle).toFloat()),
            end   = Offset(cx - cx * cos(mainAngle).toFloat(), cy - cy * sin(mainAngle).toFloat())
        )
    )

    drawMareaV2Surface(t, maxR, g2, g3, glow, geometry)

    // ── Layer 3.5: Luminous Wave Foam on the Extremos ────────────────────────
    // Highlights the outer perimeter; brightens especially on the surging gravity edge!
    drawPath(
        path = mainPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                glow.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.20f),
                glow.copy(alpha = 0.40f)
            ),
            start = Offset(cx + maxR * lightX, cy + maxR * lightY),
            end   = Offset(cx - maxR * lightX, cy - maxR * lightY)
        ),
        style = Stroke(width = (2.0f + wavePower * 0.8f).dp.toPx())
    )

    // ── Layer 4: Buoyant Micro-Bubbles & Liquid Pearls (24 Particles) ────────
    // Floating within the central liquid body, migrating towards high extremo slowly
    val bubbleCount = 24
    for (b in 0 until bubbleCount) {
        val seed = b * 61.73f
        val bFrac = b / bubbleCount.toFloat()
        val floatCycle = (t * (0.035f + (b % 4) * 0.010f) + bFrac) % 1.0f

        // Buoyancy drift: bubbles rise towards the high side of the liquid gently
        val buoyantX = -tiltX * maxR * 0.30f * floatCycle
        val buoyantY = -tiltY * maxR * 0.30f * floatCycle

        val baseAngle = seed + t * (0.07f + (b % 3) * 0.025f)
        val baseR = maxR * (0.14f + floatCycle * 0.58f)
        val wobble = sin(t * 0.6f + seed) * (maxR * 0.028f * (1f + wavePower * 0.4f))

        val bx = cx + (baseR + wobble) * cos(baseAngle) + buoyantX
        val by = cy + (baseR + wobble) * sin(baseAngle) + buoyantY

        val distFromCenter = sqrt((bx - cx) * (bx - cx) + (by - cy) * (by - cy))
        if (distFromCenter < maxR * 0.94f) {
            val bRadius = (2.2f + (b % 4) * 1.5f) * (1f + 0.10f * sin(t * 0.9f + seed))
            // Fade at both ends so particle recycling never pops into view.
            val lifeAlpha = (sin(PI * floatCycle) * 4f).coerceIn(0f, 1f)
            val bAlpha = (0.58f + 0.30f * sin(t * 0.6f + seed)).coerceIn(0.15f, 0.95f) * lifeAlpha

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bAlpha * 0.92f),
                        glow.copy(alpha = bAlpha * 0.65f),
                        g1.copy(alpha = bAlpha * 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(bx - bRadius * 0.35f, by - bRadius * 0.35f),
                    radius = bRadius * 1.25f
                ),
                radius = bRadius,
                center = Offset(bx, by)
            )

            drawCircle(
                color = Color.White.copy(alpha = bAlpha * 0.90f),
                radius = bRadius * 0.35f,
                center = Offset(bx - bRadius * 0.32f, by - bRadius * 0.32f)
            )
        }
    }

    // ── Layer 5: Dynamic Splash Spray Droplets (Ejected from Extremos) ──────
    if (shakeImpulse > 0.45f) {
        val splashCount = 6
        for (s in 0 until splashCount) {
            val sCycle = (t * 0.32f + (s / splashCount.toFloat())) % 1.0f
            val angleSpread = (s - splashCount / 2f) * (PI / splashCount.toFloat() * 1.4f)
            val sAngle = t * 0.12f + tiltX * 0.4f + angleSpread
            val sDist = maxR * (0.72f + sCycle * 0.18f)
            val sx = cx + sDist * cos(sAngle)
            val sy = cy + sDist * sin(sAngle)
            val sAlpha = sin(PI * sCycle) * (shakeImpulse - 0.45f) * 0.40f

            drawCircle(
                color = Color.White.copy(alpha = sAlpha),
                radius = (1.6f + (s % 3) * 1.2f) * (1f - sCycle * 0.35f),
                center = Offset(sx, sy)
            )
        }
    }

    // ── Layer 6: Concentric Hydrodynamic Slosh Waves (Resonancia Líquida) ───
    // Inner currents drift autonomously, with increasing gravity influence outward.
    for (idx in 0..2) {
        val frac = 0.38f + idx * 0.22f
        val rippleTime = t * (0.87f + idx * 0.043f) + idx * 0.65f
        val ringSlosh = frac * frac * 1.0f
        val ripplePath = createSloshedLiquidPath(
            cx = cx,
            cy = cy,
            containerRadius = maxR * frac,
            tiltX = tiltX,
            tiltY = tiltY,
            sloshStrength = ringSlosh,
            timeSec = rippleTime,
            amplitude = 0.30f + wavePower * 0.15f,
            shakeImpulse = shakeImpulse,
            contour = geometry.rings[idx]
        )
        drawPath(
            path = ripplePath,
            color = Color.White.copy(alpha = (0.12f + wavePower * 0.08f).coerceIn(0.05f, 0.32f)),
            style = Stroke(width = (1.4f + idx * 0.4f).dp.toPx())
        )
    }

    // ── Layer 7: Interactive Touch Wave Propagation (Ondas al tocar) ─────────
    if (touchAgeSec in 0f..1.5f && touchPos != Offset.Zero) {
        val touchProgress = (touchAgeSec / 1.5f).coerceIn(0f, 1f)
        val easeProg = 1f - (1f - touchProgress) * (1f - touchProgress)
        val waveRadius = maxR * 0.85f * easeProg
        val waveAlpha = ((1f - touchProgress) * 0.65f).coerceIn(0f, 1f)

        for (r in 0..2) {
            val rOffset = r * 16.dp.toPx()
            val ringRadius = (waveRadius - rOffset).coerceAtLeast(0f)
            if (ringRadius > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = waveAlpha * (1f - r * 0.28f)),
                    radius = ringRadius,
                    center = touchPos,
                    style = Stroke(width = (2.2f - r * 0.5f).coerceAtLeast(1.0f).dp.toPx())
                )
                drawCircle(
                    color = glow.copy(alpha = waveAlpha * 0.35f),
                    radius = ringRadius,
                    center = touchPos,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
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

/**
 * Simulates a musical beat envelope (BPM beat breathing).
 * Produces a sharp punch (kick) followed by an exponential decay, repeating at [bpm] tempo.
 */
internal fun computeBeatPulse(timeSec: Float, bpm: Float = 118f): Float {
    val period = 60f / bpm
    val phase = (timeSec % period) / period
    return if (phase < 0.10f) {
        phase / 0.10f
    } else {
        kotlin.math.exp(-((phase - 0.10f) * 4.5f))
    }
}

// ── Living Galaxy drawing — Organic Nebula Spiral with Fluid Arms ─────────────

internal fun DrawScope.drawLivingGalaxy(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f
    val t = timeSec
    val PI2 = (2.0 * Math.PI).toFloat()

    // ── Layer 0: Deep space nebula cloud (organic blob, NOT a circle) ──
    val nebulaPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.98f,
        timeSec = t * 0.32f,
        amplitude = 0.6f,
        pointCount = 14
    )
    drawPath(
        path = nebulaPath,
        brush = Brush.radialGradient(
            colors = listOf(
                g1.copy(alpha = 0.40f),
                g2.copy(alpha = 0.22f),
                Color(0xFF050A18).copy(alpha = 0.90f)
            ),
            center = Offset(cx, cy),
            radius = maxR
        )
    )

    // ── Layer 1: Two organic fluid spiral arms (NOT thin lines but filled blobs) ──
    val armCount = 2
    val galacticRotation = t * 0.22f
    for (arm in 0 until armCount) {
        val armAngle = arm * Math.PI.toFloat() + galacticRotation
        val segments = 10
        for (seg in 0 until segments) {
            val progress = seg / segments.toFloat()
            val r = maxR * (0.16f + progress * 0.72f)
            val theta = armAngle + progress * progress * 3.5f
            val blobCx = cx + r * cos(theta)
            val blobCy = cy + r * sin(theta)
            // Each segment is a small organic blob, not a circle
            val blobRadius = maxR * (0.18f - progress * 0.10f).coerceAtLeast(0.04f)
            val blobPath = createHarmonicTidePath(
                cx = blobCx, cy = blobCy,
                baseRadius = blobRadius,
                timeSec = t * 0.8f + seg * 1.3f + arm * 2.7f,
                amplitude = 1.8f,
                pointCount = 8
            )
            val alphaFade = (1f - progress * 0.7f).coerceIn(0.1f, 1f)
            val armColor = if (arm == 0) g1 else g2
            drawPath(
                path = blobPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        armColor.copy(alpha = 0.75f * alphaFade),
                        g3.copy(alpha = 0.35f * alphaFade),
                        Color.Transparent
                    ),
                    center = Offset(blobCx, blobCy),
                    radius = blobRadius * 1.5f
                )
            )
        }
    }

    // ── Layer 2: Secondary glow arm trails (thinner organic wisps) ──
    for (arm in 0 until 3) {
        val trailAngle = arm * PI2 / 3f + galacticRotation * 1.15f + 0.6f
        val trailPath = Path()
        val steps = 40
        var started = false
        for (s in 0..steps) {
            val p = s / steps.toFloat()
            val r = maxR * (0.10f + p * 0.82f)
            val wobble = sin(p * 8f + t * 1.2f + arm * 1.4f) * maxR * 0.028f
            val theta = trailAngle + p * p * 3.8f
            val x = cx + (r + wobble) * cos(theta)
            val y = cy + (r + wobble) * sin(theta)
            if (!started) { trailPath.moveTo(x, y); started = true } else trailPath.lineTo(x, y)
        }
        val trailColor = when (arm % 3) { 0 -> g3; 1 -> g1; else -> g2 }
        drawPath(
            path = trailPath,
            color = trailColor.copy(alpha = 0.35f),
            style = Stroke(
                width = 3.5f + sin(t * 0.5f + arm.toFloat()) * 1.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }

    // ── Layer 3: Star field (varied sizes, twinkle, and orbital movement) ──
    val starCount = 45
    for (i in 0 until starCount) {
        val seed = i * 137.5f
        val distFrac = 0.12f + ((i * 23) % 100) / 100f * 0.82f
        val r = maxR * distFrac
        val speed = 0.25f * (1.3f - distFrac * 0.5f)
        val theta = t * speed + (seed * (Math.PI.toFloat() / 180f))
        val sx = cx + r * cos(theta) + sin(t * 0.4f + seed * 0.1f) * 3f
        val sy = cy + r * sin(theta) + cos(t * 0.35f + seed * 0.07f) * 3f
        val rawTwinkle = sin(t * (2.8f + (i % 5) * 0.4f) + i * 1.7f)
        val twinkle = 0.3f + 0.7f * if (rawTwinkle < 0f) -rawTwinkle else rawTwinkle
        val starR = when (i % 7) {
            0 -> 3.2f; 1 -> 2.4f; 2 -> 1.6f; 3 -> 2.8f; else -> 1.8f
        }
        val starColor = when (i % 4) {
            0 -> Color.White; 1 -> g3; 2 -> glow; else -> Color(0xFFE8E0F0)
        }
        drawCircle(color = starColor.copy(alpha = twinkle * 0.85f), radius = starR, center = Offset(sx, sy))
        // Subtle cross-glint on brighter stars
        if (i % 5 == 0 && twinkle > 0.7f) {
            val glintLen = starR * 3.5f
            drawLine(starColor.copy(alpha = twinkle * 0.3f), Offset(sx - glintLen, sy), Offset(sx + glintLen, sy), strokeWidth = 0.8f)
            drawLine(starColor.copy(alpha = twinkle * 0.3f), Offset(sx, sy - glintLen), Offset(sx, sy + glintLen), strokeWidth = 0.8f)
        }
    }

    // ── Layer 4: Living galactic core (organic pulsing nebula, NOT a flat circle) ──
    val corePulse = 1f + 0.12f * sin(t * 1.5f) + 0.06f * cos(t * 2.3f)
    val corePath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.26f * corePulse,
        timeSec = t * 1.4f,
        amplitude = 1.2f,
        pointCount = 12
    )
    drawPath(
        path = corePath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.92f),
                glow.copy(alpha = 0.70f),
                g1.copy(alpha = 0.30f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 0.38f * corePulse
        )
    )
    // Core specular
    drawPath(
        path = corePath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.50f),
                Color.Transparent
            ),
            center = Offset(cx - maxR * 0.06f, cy - maxR * 0.08f),
            radius = maxR * 0.22f
        )
    )
}


// ── Living Vortex drawing — Organic Gravitational Plasma Maelstrom ───────────

internal fun DrawScope.drawLivingVortex(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f
    val t = timeSec

    // ── Layer 0: Accretion background (organic blob, NOT a circle) ──
    val bgPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.97f,
        timeSec = t * 0.20f,
        amplitude = 0.4f,
        pointCount = 14
    )
    drawPath(
        path = bgPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF020408),
                g1.copy(alpha = 0.35f),
                g2.copy(alpha = 0.22f),
                Color(0xFF030610).copy(alpha = 0.92f)
            ),
            center = Offset(cx, cy),
            radius = maxR
        )
    )

    // ── Layer 1: Turbulent plasma streams (5 organic blob-chain spirals) ──
    val streamCount = 5
    val swirlSpeed = t * 0.42f
    for (s in 0 until streamCount) {
        val streamAngle = s * (2f * Math.PI.toFloat() / streamCount) + swirlSpeed
        val blobsPerStream = 12
        for (b in 0 until blobsPerStream) {
            val progress = b / blobsPerStream.toFloat()
            val r = maxR * (0.92f - progress * 0.68f)
            val theta = streamAngle + progress * progress * 5.5f
            // Organic wobble to prevent mechanical look
            val wobbleR = sin(t * 1.2f + b * 1.6f + s * 2.1f) * maxR * 0.025f
            val wobbleT = cos(t * 0.8f + b * 1.3f + s * 1.7f) * 0.06f
            val blobX = cx + (r + wobbleR) * cos(theta + wobbleT)
            val blobY = cy + (r + wobbleR) * sin(theta + wobbleT)

            val blobSize = maxR * (0.10f - progress * 0.06f).coerceAtLeast(0.025f)
            val blobPath = createHarmonicTidePath(
                cx = blobX, cy = blobY,
                baseRadius = blobSize,
                timeSec = t * 1.1f + b * 0.9f + s * 1.5f,
                amplitude = 1.5f + progress * 1.2f,
                pointCount = 8
            )

            val streamColor = when (s % 3) { 0 -> g1; 1 -> g2; else -> g3 }
            val alphaFade = (1f - progress * 0.6f).coerceIn(0.15f, 1f)

            drawPath(
                path = blobPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        streamColor.copy(alpha = 0.70f * alphaFade),
                        streamColor.copy(alpha = 0.30f * alphaFade),
                        Color.Transparent
                    ),
                    center = Offset(blobX, blobY),
                    radius = blobSize * 1.6f
                )
            )
        }
    }

    // ── Layer 2: Distortion filament rings (deformed organic rings at varying depths) ──
    val ringCount = 3
    for (ring in 0 until ringCount) {
        val ringProgress = (ring + 1) / (ringCount + 1).toFloat()
        val ringRadius = maxR * ringProgress * 0.80f + maxR * 0.10f
        val ringPath = createHarmonicTidePath(
            cx = cx, cy = cy,
            baseRadius = ringRadius,
            timeSec = t * (0.65f + ring * 0.2f) + ring * 3.5f,
            amplitude = 1.0f + ring * 0.5f,
            pointCount = 14
        )
        val ringColor = when (ring % 3) { 0 -> glow; 1 -> g3; else -> g1 }
        drawPath(
            path = ringPath,
            color = ringColor.copy(alpha = 0.20f + 0.06f * sin(t * 1.4f + ring.toFloat())),
            style = Stroke(
                width = 2.5f - ring * 0.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }

    // ── Layer 3: Photon sphere — organic pulsing accretion ring ──
    val photonPulse = 1f + 0.08f * sin(t * 2.5f) + 0.04f * cos(t * 3.7f)
    val photonPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.28f * photonPulse,
        timeSec = t * 1.8f,
        amplitude = 1.0f,
        pointCount = 12
    )
    drawPath(
        path = photonPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.80f),
                glow.copy(alpha = 0.60f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 0.35f * photonPulse
        )
    )
    drawPath(
        path = photonPath,
        color = glow.copy(alpha = 0.45f),
        style = Stroke(width = 2.5f)
    )

    // ── Layer 4: Central singularity (organic dark hole, NOT a circle) ──
    val singularityPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.16f,
        timeSec = t * 0.7f,
        amplitude = 0.8f,
        pointCount = 10
    )
    drawPath(
        path = singularityPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black,
                Color.Black.copy(alpha = 0.95f),
                Color(0xFF0A0A18).copy(alpha = 0.60f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 0.24f
        )
    )
    // Subtle event horizon glow
    drawPath(
        path = singularityPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                glow.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 0.22f
        )
    )
}

// ── Living Black Hole drawing — Gargantua Relativistic Gravitational Lensing ─

internal fun DrawScope.drawLivingBlackHole(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.96f
    val t = timeSec
    val PI = Math.PI.toFloat()

    // Gargantua interstellar relativistic plasma palette dynamically modulated by active track palette
    val cWhiteHot = Color(0xFFFFFDF5)
    val cSolarGold = glow
    val cFireOrange = g1
    val cPlasmaRed = g2
    val cDeepCrimson = g3

    // Gargantua event horizon shadow radius
    val shadowRadius = maxR * 0.29f

    // Cinematic angle tilt (matching Christopher Nolan's camera angle in the reference photo)
    rotate(degrees = -13f, pivot = Offset(cx, cy)) {
        // ── Layer 0: Cosmic Starfield & Golden Nebula Dust ────────────────────
        val starCount = 45
        for (i in 0 until starCount) {
            val seed = i * 73.19f
            val sDist = shadowRadius * 1.35f + ((i * 47) % 100) / 100f * (maxR - shadowRadius * 1.35f)
            val sAngle = seed + t * 0.08f
            val sx = cx + sDist * cos(sAngle)
            val sy = cy + sDist * sin(sAngle)
            val twinkle = 0.3f + 0.7f * abs(sin(t * 3.2f + seed))
            val sColor = if (i % 4 == 0) cSolarGold else Color.White
            drawCircle(
                color = sColor.copy(alpha = twinkle * 0.75f),
                radius = if (i % 5 == 0) 1.8f else 1.1f,
                center = Offset(sx, sy)
            )
        }

        // Nebula atmospheric glow on the left (approaching matter cloud)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cFireOrange.copy(alpha = 0.22f),
                    cDeepCrimson.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx - maxR * 0.40f, cy + maxR * 0.10f),
                radius = maxR * 0.75f
            ),
            radius = maxR * 0.75f,
            center = Offset(cx - maxR * 0.40f, cy + maxR * 0.10f)
        )

        // ── Shared Multi-Particle Orbit System (130 Particles) ────────────────
        val particleCount = 130
        val diskTilt = 0.26f

        val drawParticlesPass: (Boolean) -> Unit = { targetFront ->
            for (i in 0 until particleCount) {
                val isInfall = (i % 3 == 0)
                val seed = i * 137.508f

                val r: Float
                val theta: Float
                val baseAlpha: Float

                if (isInfall) {
                    // Fast matter spiraling inward into the event horizon!
                    val cycle = (t * 0.22f + (i * 0.033f)) % 1.0f
                    r = maxR * 0.94f - cycle * (maxR * 0.94f - shadowRadius * 1.02f)
                    val infallOmega = 1.4f + (1f - (r / maxR)) * 4.2f
                    theta = seed + t * infallOmega + cycle * cycle * 6.5f
                    baseAlpha = if (cycle > 0.88f) {
                        ((1f - cycle) / 0.12f).coerceIn(0f, 1f)
                    } else {
                        (cycle / 0.15f).coerceIn(0f, 1f)
                    }
                } else {
                    // Keplerian differential rotation: fast inner, majestic outer
                    val normIdx = (i - (i / 3)).toFloat() / (particleCount * 0.67f)
                    r = shadowRadius * 1.08f + (maxR * 0.92f - shadowRadius * 1.08f) * (normIdx * normIdx)
                    val omega = 1.5f * sqrt((shadowRadius / r).coerceIn(0.28f, 1.4f)) + 0.8f
                    theta = seed + t * omega
                    baseAlpha = 0.75f + 0.25f * sin(seed * 2.3f)
                }

                val sinTheta = sin(theta)
                val cosTheta = cos(theta)
                val isFront = sinTheta >= 0f

                if (isFront == targetFront) {
                    val inc = diskTilt * (0.90f + 0.20f * sin(seed * 1.5f))
                    val vertJitter = sin(t * 3.5f + seed) * (shadowRadius * 0.08f) * (r / maxR)

                    val px = cx + r * cosTheta
                    val py = cy + r * sinTheta * inc + vertJitter

                    // Relativistic Doppler beaming: left side (approaching) is boosted and hotter
                    val doppler = (1.0f - 0.50f * ((px - cx) / maxR)).coerceIn(0.50f, 1.55f)
                    val pAlpha = (baseAlpha * doppler * (if (isFront) 0.95f else 0.55f)).coerceIn(0.08f, 1.0f)
                    val pRadius = (1.2f + (i % 3) * 0.9f) * (if (isFront) 1.2f else 0.8f) * (if (doppler > 1.2f) 1.25f else 1.0f)

                    val pColor = if (doppler > 1.25f) {
                        if (i % 2 == 0) cWhiteHot else cSolarGold
                    } else if (doppler > 0.85f) {
                        if (i % 2 == 0) cSolarGold else cFireOrange
                    } else {
                        cPlasmaRed
                    }

                    // Tangent velocity streak (motion trail)
                    val speedMag = sqrt(sinTheta * sinTheta + (cosTheta * inc) * (cosTheta * inc)).coerceAtLeast(0.001f)
                    val tx = -sinTheta / speedMag
                    val ty = (cosTheta * inc) / speedMag
                    val streakLen = (6f + 16f * (shadowRadius / r).coerceIn(0.2f, 1.3f)) * (if (isFront) 1.2f else 0.8f)

                    // Draw fiery motion streak
                    drawLine(
                        color = pColor.copy(alpha = pAlpha * 0.65f),
                        start = Offset(px - tx * streakLen, py - ty * streakLen),
                        end = Offset(px, py),
                        strokeWidth = pRadius * 1.1f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Draw incandescent core spark
                    drawCircle(
                        color = pColor.copy(alpha = pAlpha),
                        radius = pRadius,
                        center = Offset(px, py)
                    )

                    // Corona glow on approaching fiery sparks
                    if (isFront && doppler > 1.20f) {
                        drawCircle(
                            color = cWhiteHot.copy(alpha = pAlpha * 0.35f),
                            radius = pRadius * 2.6f,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // ── Layer 1: Gravitational Lensing Arch (Upper Vertical Ring) ─────────
        val upperArcPath = Path()
        val upperSteps = 56
        val upperRadiusX = shadowRadius * 2.38f
        val upperRadiusY = shadowRadius * 1.95f
        for (s in 0..upperSteps) {
            val p = s / upperSteps.toFloat()
            val angle = PI + p * PI
            val wave = sin(p * 8f - t * 4.2f) * (maxR * 0.016f) +
                       cos(p * 14f + t * 2.8f) * (maxR * 0.008f)
            val rx = upperRadiusX + wave
            val ry = upperRadiusY + wave * 0.75f
            val x = cx + rx * cos(angle)
            val y = cy + ry * sin(angle)
            if (s == 0) upperArcPath.moveTo(x, y) else upperArcPath.lineTo(x, y)
        }
        val bandThick = shadowRadius * 0.62f
        for (s in upperSteps downTo 0) {
            val p = s / upperSteps.toFloat()
            val angle = PI + p * PI
            val rx = (upperRadiusX - bandThick).coerceAtLeast(shadowRadius * 1.04f)
            val ry = (upperRadiusY - bandThick * 0.82f).coerceAtLeast(shadowRadius * 1.03f)
            val x = cx + rx * cos(angle)
            val y = cy + ry * sin(angle)
            upperArcPath.lineTo(x, y)
        }
        upperArcPath.close()

        // Fiery arch gradient
        drawPath(
            path = upperArcPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    cWhiteHot.copy(alpha = 0.95f),
                    cSolarGold.copy(alpha = 0.92f),
                    cFireOrange.copy(alpha = 0.85f),
                    cPlasmaRed.copy(alpha = 0.55f),
                    cDeepCrimson.copy(alpha = 0.25f)
                ),
                startX = cx - upperRadiusX,
                endX = cx + upperRadiusX
            )
        )

        // Streaming striation arcs inside the upper gravitational lens (Visible live flow!)
        val archStriations = 12
        for (a in 0 until archStriations) {
            val aFrac = a / archStriations.toFloat()
            val aRadiusX = shadowRadius * 1.15f + (upperRadiusX - shadowRadius * 1.15f) * aFrac
            val aRadiusY = shadowRadius * 1.12f + (upperRadiusY - shadowRadius * 1.12f) * aFrac
            val flowPhase = (t * (120f + a * 35f)) % 360f

            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        cWhiteHot.copy(alpha = 0.80f),
                        cSolarGold.copy(alpha = 0.70f),
                        cFireOrange.copy(alpha = 0.40f),
                        Color.Transparent
                    ),
                    startX = cx - aRadiusX,
                    endX = cx + aRadiusX
                ),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - aRadiusX, cy - aRadiusY),
                size = Size(aRadiusX * 2f, aRadiusY * 2f),
                style = Stroke(
                    width = 2.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(35f, 25f), phase = flowPhase)
                )
            )
        }

        // ── Layer 2: Gravitational Warp Funnel (Waterfall of light on left) ────
        val funnelPath = Path()
        val funnelSteps = 30
        for (s in 0..funnelSteps) {
            val p = s / funnelSteps.toFloat()
            val fx = cx - shadowRadius * (1.1f + p * 1.25f)
            val fy = cy + sin(p * PI - t * 4.5f) * (shadowRadius * 0.18f) - (p * shadowRadius * 0.75f)
            if (s == 0) funnelPath.moveTo(fx, fy) else funnelPath.lineTo(fx, fy)
        }
        drawPath(
            path = funnelPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    cWhiteHot.copy(alpha = 0.85f),
                    cSolarGold.copy(alpha = 0.75f),
                    cFireOrange.copy(alpha = 0.40f)
                ),
                startY = cy - shadowRadius * 0.8f,
                endY = cy + shadowRadius * 0.2f
            ),
            style = Stroke(
                width = shadowRadius * 0.35f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // ── Layer 3: Lower Gravitational Lensing Arc (Light bent under shadow) ─
        val lowerArcPath = Path()
        val lowerRadiusX = shadowRadius * 2.18f
        val lowerRadiusY = shadowRadius * 1.35f
        for (s in 0..upperSteps) {
            val p = s / upperSteps.toFloat()
            val angle = p * PI
            val wave = sin(p * 7f + t * 3.8f) * (maxR * 0.014f)
            val rx = lowerRadiusX + wave
            val ry = lowerRadiusY + wave * 0.6f
            val x = cx + rx * cos(angle)
            val y = cy + ry * sin(angle)
            if (s == 0) lowerArcPath.moveTo(x, y) else lowerArcPath.lineTo(x, y)
        }
        val lowerBandThick = shadowRadius * 0.40f
        for (s in upperSteps downTo 0) {
            val p = s / upperSteps.toFloat()
            val angle = p * PI
            val rx = (lowerRadiusX - lowerBandThick).coerceAtLeast(shadowRadius * 1.04f)
            val ry = (lowerRadiusY - lowerBandThick * 0.72f).coerceAtLeast(shadowRadius * 1.03f)
            val x = cx + rx * cos(angle)
            val y = cy + ry * sin(angle)
            lowerArcPath.lineTo(x, y)
        }
        lowerArcPath.close()

        drawPath(
            path = lowerArcPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    cWhiteHot.copy(alpha = 0.85f),
                    cSolarGold.copy(alpha = 0.75f),
                    cFireOrange.copy(alpha = 0.50f),
                    cPlasmaRed.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                startX = cx - lowerRadiusX,
                endX = cx + lowerRadiusX
            )
        )

        // ── Layer 3.5: Back-Half Orbiting Particles (Behind Event Horizon) ────
        drawParticlesPass(false)

        // ── Layer 4: The Photon Ring (Super-radiant boundary) ─────────────────
        val photonRingRadius = shadowRadius * 1.04f
        val photonPulse = 1f + 0.025f * sin(t * 3.0f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cWhiteHot,
                    cSolarGold.copy(alpha = 0.90f),
                    cFireOrange.copy(alpha = 0.45f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = photonRingRadius * 1.25f * photonPulse
            ),
            radius = photonRingRadius * 1.18f * photonPulse,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White,
            radius = photonRingRadius * photonPulse,
            center = Offset(cx, cy),
            style = Stroke(width = 2.6f)
        )

        // ── Layer 5: Gargantua's Event Horizon (Pitch-black singularity) ──────
        drawCircle(
            color = Color.Black,
            radius = shadowRadius,
            center = Offset(cx, cy)
        )

        // ── Layer 6: Main Equatorial Accretion Disk (Front Raging Torrent) ────
        val diskW = maxR * 1.08f
        val diskH = shadowRadius * 0.78f
        val diskSteps = 56

        val filamentCount = 10
        for (f in 0 until filamentCount) {
            val fFrac = f / (filamentCount - 1).toFloat()
            val filH = diskH * (0.28f + fFrac * 0.78f)
            val filW = diskW * (0.70f + fFrac * 0.32f)
            val filamentPath = Path()
            val fSpeed = t * (3.8f + f * 0.45f)

            for (s in 0..diskSteps) {
                val p = s / diskSteps.toFloat()
                val angle = PI - p * PI
                val swirl = sin(p * 8f - fSpeed + f * 1.2f) * (diskH * 0.12f) +
                            cos(p * 14f - t * 5.0f) * (diskH * 0.06f)
                val x = cx + filW * cos(angle)
                val y = cy + (filH * sin(angle) * 0.32f) + swirl
                if (s == 0) filamentPath.moveTo(x, y) else filamentPath.lineTo(x, y)
            }

            val filThick = (8f + (1f - fFrac) * 14f).coerceAtLeast(3.5f)
            for (s in diskSteps downTo 0) {
                val p = s / diskSteps.toFloat()
                val angle = PI - p * PI
                val swirl = sin(p * 8f - fSpeed + f * 1.2f) * (diskH * 0.12f) +
                            cos(p * 14f - t * 5.0f) * (diskH * 0.06f)
                val x = cx + filW * cos(angle)
                val y = cy + (filH * sin(angle) * 0.32f) + swirl + filThick
                filamentPath.lineTo(x, y)
            }
            filamentPath.close()

            val filColor = when {
                fFrac < 0.25f -> cWhiteHot
                fFrac < 0.55f -> cSolarGold
                fFrac < 0.80f -> cFireOrange
                else -> cPlasmaRed
            }

            drawPath(
                path = filamentPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        cWhiteHot.copy(alpha = 0.96f),
                        filColor.copy(alpha = 0.92f),
                        filColor.copy(alpha = 0.78f),
                        cPlasmaRed.copy(alpha = 0.45f),
                        cDeepCrimson.copy(alpha = 0.18f)
                    ),
                    startX = cx - filW,
                    endX = cx + filW
                )
            )

            // Streaming dashed streaks along each filament (Visible plasma fiber rush!)
            val fiberStrokePath = Path()
            for (s in 0..diskSteps) {
                val p = s / diskSteps.toFloat()
                val angle = PI - p * PI
                val swirl = sin(p * 8f - fSpeed + f * 1.2f) * (diskH * 0.12f)
                val x = cx + filW * cos(angle)
                val y = cy + (filH * sin(angle) * 0.32f) + swirl + filThick * 0.5f
                if (s == 0) fiberStrokePath.moveTo(x, y) else fiberStrokePath.lineTo(x, y)
            }
            drawPath(
                path = fiberStrokePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        cSolarGold.copy(alpha = 0.80f),
                        Color.Transparent
                    ),
                    startX = cx - filW,
                    endX = cx + filW
                ),
                style = Stroke(
                    width = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(24f, 18f),
                        phase = t * (90f + f * 30f)
                    )
                )
            )
        }

        // Central blazing incandescent core beam line cutting across front
        val beamSweep = sin(t * 6f) * 2.5f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White,
                    cWhiteHot,
                    cSolarGold.copy(alpha = 0.95f),
                    cFireOrange.copy(alpha = 0.60f),
                    Color.Transparent
                ),
                startX = cx - diskW * 0.98f,
                endX = cx + diskW * 0.98f
            ),
            start = Offset(cx - diskW * 0.98f, cy + beamSweep),
            end = Offset(cx + diskW * 0.98f, cy - beamSweep),
            strokeWidth = 3.8f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // ── Layer 7: Front-Half Orbiting & Infalling Particles (Crossing Front) ─
        drawParticlesPass(true)

        // ── Layer 8: Miller's Planet (From Christopher Nolan's Reference Shot!) ─
        val planetOrbitAngle = t * 0.25f + 3.35f
        val planetDist = maxR * 0.52f
        val planetX = cx + planetDist * cos(planetOrbitAngle)
        val planetY = cy + planetDist * sin(planetOrbitAngle) * 0.38f
        val planetRadius = 7.5f

        // Dark celestial sphere body
        drawCircle(
            color = Color(0xFF0C0E14),
            radius = planetRadius,
            center = Offset(planetX, planetY)
        )
        // Glowing sunlit crescent rim facing the incandescent black hole
        drawArc(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    cSolarGold.copy(alpha = 0.70f),
                    cWhiteHot.copy(alpha = 0.95f)
                ),
                startX = planetX - planetRadius,
                endX = planetX + planetRadius
            ),
            startAngle = -75f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(planetX - planetRadius, planetY - planetRadius),
            size = Size(planetRadius * 2f, planetRadius * 2f),
            style = Stroke(width = 2.0f)
        )
        // Subtle planetary atmospheric haze
        drawCircle(
            color = cSolarGold.copy(alpha = 0.18f),
            radius = planetRadius * 1.5f,
            center = Offset(planetX, planetY)
        )

        // ── Layer 9: Relativistic Doppler Beaming Lobe ────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cWhiteHot.copy(alpha = 0.65f),
                    cSolarGold.copy(alpha = 0.45f),
                    cFireOrange.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(cx - shadowRadius * 1.45f, cy),
                radius = shadowRadius * 2.1f
            ),
            radius = shadowRadius * 2.1f,
            center = Offset(cx - shadowRadius * 1.45f, cy)
        )
    }
}

// ── Living Supernova drawing — Boiling Photosphere, Prominences & CME ─────────

internal fun DrawScope.drawLivingSupernova(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f
    val t = timeSec
    val beat = computeBeatPulse(t, 120f)
    val PI = Math.PI.toFloat()
    val PI2 = (2.0 * Math.PI).toFloat()

    // ── Layer 0: Deep space thermal corona halo (organic breathing backdrop) ──
    val haloPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.98f,
        timeSec = t * 0.20f,
        amplitude = 0.40f,
        pointCount = 14
    )
    drawPath(
        path = haloPath,
        brush = Brush.radialGradient(
            colors = listOf(
                g1.copy(alpha = 0.50f + 0.20f * beat),
                g2.copy(alpha = 0.25f),
                Color(0xFF120302).copy(alpha = 0.94f)
            ),
            center = Offset(cx, cy),
            radius = maxR
        )
    )

    // ── Layer 1: Coronal Mass Ejections (CME) — Expanding turbulent plasma bubbles ──
    val cmeCount = 4
    for (c in 0 until cmeCount) {
        val cmePhase = ((t * 0.35f + c / cmeCount.toFloat()) % 1.0f)
        val cmeAngle = c * (PI2 / cmeCount) + t * 0.12f
        val cmeDist = maxR * (0.35f + cmePhase * 0.65f)
        val cmeBlobR = maxR * (0.12f + cmePhase * 0.25f)
        val bx = cx + cmeDist * cos(cmeAngle)
        val by = cy + cmeDist * sin(cmeAngle)
        val cmePath = createHarmonicTidePath(
            cx = bx, cy = by,
            baseRadius = cmeBlobR,
            timeSec = t * 1.5f + c * 2.3f,
            amplitude = 1.4f + cmePhase * 0.8f,
            pointCount = 10
        )
        val cmeAlpha = ((1f - cmePhase) * (1f - cmePhase)).coerceIn(0f, 1f)
        val cmeColor = if (c % 2 == 0) glow else g1
        drawPath(
            path = cmePath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.80f * cmeAlpha),
                    cmeColor.copy(alpha = 0.55f * cmeAlpha),
                    g3.copy(alpha = 0.20f * cmeAlpha),
                    Color.Transparent
                ),
                center = Offset(bx, by),
                radius = cmeBlobR * 1.4f
            )
        )
    }

    // ── Layer 2: Twisted Magnetic Flux-Ropes (Arcos de prominencia trenzados) ──
    val loopCount = 8
    for (l in 0 until loopCount) {
        val baseAngle = l * (PI2 / loopCount) + t * 0.10f
        val loopSpan = 0.38f + 0.08f * sin(t * 1.4f + l.toFloat())
        val loopHeight = maxR * (0.28f + 0.16f * sin(t * 2.1f + l * 1.7f) + 0.14f * beat)
        val baseR = maxR * 0.38f

        val aStart = baseAngle - loopSpan / 2f
        val aEnd   = baseAngle + loopSpan / 2f

        val p0 = Offset(cx + baseR * cos(aStart), cy + baseR * sin(aStart))
        val p3 = Offset(cx + baseR * cos(aEnd), cy + baseR * sin(aEnd))
        val midR = baseR + loopHeight
        val pMid = Offset(cx + midR * cos(baseAngle), cy + midR * sin(baseAngle))

        // Control points creating an arched magnetic loop
        val p1 = Offset(
            p0.x + (pMid.x - p0.x) * 0.6f + sin(t * 3f + l.toFloat()) * 12f,
            p0.y + (pMid.y - p0.y) * 0.6f + cos(t * 3f + l.toFloat()) * 12f
        )
        val p2 = Offset(
            p3.x + (pMid.x - p3.x) * 0.6f - sin(t * 3f + l.toFloat()) * 12f,
            p3.y + (pMid.y - p3.y) * 0.6f - cos(t * 3f + l.toFloat()) * 12f
        )

        val loopPath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }

        val loopColor = when (l % 3) { 0 -> Color.White; 1 -> glow; else -> g1 }
        // Outer plasma glow
        drawPath(
            path = loopPath,
            color = loopColor.copy(alpha = 0.45f + 0.20f * beat),
            style = Stroke(width = 8f + 3f * beat, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // Inner white-hot filament
        drawPath(
            path = loopPath,
            color = Color.White.copy(alpha = 0.90f),
            style = Stroke(width = 2.2f + 1f * beat, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }

    // ── Layer 3: Boiling Granulation Photosphere (Multi-harmonic fluid star) ──
    val coreScale = (1f + 0.10f * sin(t * 1.8f)) * (1f + 0.18f * beat)
    val outerStarPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.44f * coreScale,
        timeSec = t * 1.4f,
        amplitude = 1.3f,
        pointCount = 14
    )
    drawPath(
        path = outerStarPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                glow.copy(alpha = 0.95f),
                g1.copy(alpha = 0.85f),
                g2.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxR * 0.58f * coreScale
        )
    )

    // Inner convection cells (boiling granule vortex blobs on star surface)
    for (v in 0 until 6) {
        val vAngle = v * (PI2 / 6f) + t * 0.45f
        val vDist = maxR * 0.18f * coreScale
        val vx = cx + vDist * cos(vAngle)
        val vy = cy + vDist * sin(vAngle)
        val vBlobR = maxR * 0.12f * coreScale
        val vPath = createHarmonicTidePath(
            cx = vx, cy = vy,
            baseRadius = vBlobR,
            timeSec = t * 2.2f + v * 1.5f,
            amplitude = 1.2f,
            pointCount = 8
        )
        drawPath(
            path = vPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    glow.copy(alpha = 0.40f),
                    Color.Transparent
                ),
                center = Offset(vx, vy),
                radius = vBlobR * 1.4f
            )
        )
    }

    // ── Layer 4: Radiant Anamorphic Diffraction Rays (Solar lens glare) ────────
    val rayCount = 8
    val rayLen = maxR * (0.85f + 0.15f * beat)
    for (r in 0 until rayCount) {
        val rAngle = r * (PI2 / rayCount) + t * 0.08f
        val rayW = 3.5f + if (r % 2 == 0) 2.5f else 0f
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f * (0.7f + 0.3f * beat)),
                    glow.copy(alpha = 0.40f),
                    Color.Transparent
                ),
                start = Offset(cx, cy),
                end = Offset(cx + rayLen * cos(rAngle), cy + rayLen * sin(rAngle))
            ),
            start = Offset(cx, cy),
            end = Offset(cx + rayLen * cos(rAngle), cy + rayLen * sin(rAngle)),
            strokeWidth = rayW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }

    // ── Layer 5: Ejected incandescent sparks with trailing tails ──────────────
    val sparkCount = 36
    for (i in 0 until sparkCount) {
        val seed = i * 47.3f
        val sProg = ((t * (0.35f + (i % 4) * 0.12f) + i / sparkCount.toFloat()) % 1.0f)
        val sDist = maxR * (0.38f + sProg * 0.58f)
        val sAngle = seed + t * 0.15f + sin(t * 1.2f + seed) * 0.20f
        val sx = cx + sDist * cos(sAngle)
        val sy = cy + sDist * sin(sAngle)
        val tailLen = 14f * (1f - sProg)
        val tx = sx - tailLen * cos(sAngle)
        val ty = sy - tailLen * sin(sAngle)

        val sAlpha = (1f - sProg) * (0.6f + 0.4f * sin(t * 4f + seed))
        val sColor = if (i % 3 == 0) Color.White else glow

        drawLine(
            color = sColor.copy(alpha = (sAlpha * 0.5f).coerceIn(0f, 1f)),
            start = Offset(tx, ty),
            end = Offset(sx, sy),
            strokeWidth = 1.6f
        )
        drawCircle(
            color = sColor.copy(alpha = (sAlpha * (0.8f + 0.2f * beat)).coerceIn(0f, 1f)),
            radius = 2.4f * (1f - sProg * 0.4f),
            center = Offset(sx, sy)
        )
    }
}

// ── Living Plasma Storm drawing — Multi-Tier Fractal Lightning & Tesla Arcs ──

internal fun DrawScope.drawLivingPlasmaStorm(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f
    val t = timeSec
    val beat = computeBeatPulse(t, 126f)
    val PI = Math.PI.toFloat()
    val PI2 = (2.0 * Math.PI).toFloat()

    // ── Layer 0: Strobe thundercloud background (ionized deep space nebula) ───
    val strobeCycle = ((t * 4.5f) % 1.0f)
    val strobe = if (strobeCycle < 0.08f) (1f - strobeCycle / 0.08f) * 0.35f else 0f
    val bgPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.98f,
        timeSec = t * 0.28f,
        amplitude = 0.38f,
        pointCount = 14
    )
    drawPath(
        path = bgPath,
        brush = Brush.radialGradient(
            colors = listOf(
                g1.copy(alpha = 0.35f + strobe + beat * 0.12f),
                g2.copy(alpha = 0.20f),
                Color(0xFF040511).copy(alpha = 0.94f)
            ),
            center = Offset(cx, cy),
            radius = maxR
        )
    )

    // ── Layer 1: Swirling magnetic flux vortex rings (plasma containment) ─────
    val ringCount = 3
    for (r in 0 until ringCount) {
        val rSpeed = if (r % 2 == 0) 0.55f else -0.42f
        val rR = maxR * (0.35f + r * 0.24f) * (1f + 0.06f * beat)
        val rPath = createHarmonicTidePath(
            cx = cx, cy = cy,
            baseRadius = rR,
            timeSec = t * rSpeed + r * 2.1f,
            amplitude = 0.8f + r * 0.4f,
            pointCount = 12
        )
        val rColor = when (r % 3) { 0 -> g1; 1 -> glow; else -> g3 }
        drawPath(
            path = rPath,
            color = rColor.copy(alpha = 0.25f + 0.15f * beat),
            style = Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }

    // ── Layer 2: 7 Fractal Lightning Bolts with Multi-Tier Ionization Glow ────
    val boltCount = 7
    for (b in 0 until boltCount) {
        val boltAngle = b * (PI2 / boltCount) + t * 0.22f + sin(t * 1.5f + b.toFloat()) * 0.20f
        val boltLen = maxR * (0.84f + 0.10f * sin(t * 6f + b * 2.7f) + 0.06f * beat)
        val steps = 18

        val mainPath = Path()
        mainPath.moveTo(cx, cy)
        val branches = mutableListOf<Pair<Offset, Float>>()

        var curX = cx
        var curY = cy
        val perp = boltAngle + PI / 2f

        for (s in 1..steps) {
            val prog = s / steps.toFloat()
            val r = boltLen * prog
            // Chaotic electrical displacement with multi-octave frequencies
            val disp = sin(s * 9.1f + t * 32f + b * 4.3f) * (maxR * 0.042f) +
                       cos(s * 5.4f - t * 24f) * (maxR * 0.022f)
            val tx = cx + r * cos(boltAngle) + disp * cos(perp)
            val ty = cy + r * sin(boltAngle) + disp * sin(perp)
            mainPath.lineTo(tx, ty)
            curX = tx
            curY = ty

            // Branch split points at 35% and 65% of bolt
            if (s == 6 || s == 12) {
                val bForkAngle = boltAngle + (if (s % 2 == 0) 0.52f else -0.52f)
                branches.add(Pair(Offset(curX, curY), bForkAngle))
            }
        }

        val boltAccent = if (b % 2 == 0) Color.White else glow
        // 1. Outer volumetric ionization sheath
        drawPath(
            path = mainPath,
            color = g1.copy(alpha = 0.40f + 0.30f * beat),
            style = Stroke(width = 11f + 3f * beat, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // 2. Neon electric core
        drawPath(
            path = mainPath,
            color = boltAccent.copy(alpha = 0.85f),
            style = Stroke(width = 4.2f + 1.2f * beat, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // 3. Razor-sharp pure white lightning filament
        drawPath(
            path = mainPath,
            color = Color.White,
            style = Stroke(width = 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Draw sub-branches with fading glow
        for ((fPos, fAngle) in branches) {
            val fPath = Path()
            fPath.moveTo(fPos.x, fPos.y)
            for (fs in 1..7) {
                val fProg = fs / 7f
                val fR = maxR * 0.24f * fProg
                val fDisp = sin(fs * 6.8f + t * 35f) * (maxR * 0.028f)
                val fx = fPos.x + fR * cos(fAngle) + fDisp * sin(fAngle)
                val fy = fPos.y + fR * sin(fAngle) - fDisp * cos(fAngle)
                fPath.lineTo(fx, fy)
            }
            drawPath(
                path = fPath,
                color = glow.copy(alpha = 0.65f),
                style = Stroke(width = 2.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawPath(
                path = fPath,
                color = Color.White.copy(alpha = 0.90f),
                style = Stroke(width = 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        // Grounding impact flash at bolt terminus
        drawCircle(
            color = Color.White.copy(alpha = 0.70f + 0.30f * beat),
            radius = 4f + 2f * beat,
            center = Offset(curX, curY)
        )
    }

    // ── Layer 3: Central hyper-charged plasma core (Tesla sphere) ─────────────
    val coreR = maxR * 0.24f * (1f + 0.22f * beat)
    val corePath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = coreR,
        timeSec = t * 3.2f,
        amplitude = 1.6f,
        pointCount = 12
    )
    drawPath(
        path = corePath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                glow.copy(alpha = 0.95f),
                g1.copy(alpha = 0.60f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = coreR * 1.7f
        )
    )

    // ── Layer 4: Ion sparks with leaping electric jumps ───────────────────────
    val sparkCount = 30
    for (i in 0 until sparkCount) {
        val seed = i * 43.1f
        val sDist = maxR * (0.18f + ((i * 23) % 100) / 100f * 0.76f)
        val sAngle = seed + t * (0.6f + (i % 3) * 0.35f)
        val sx = cx + sDist * cos(sAngle)
        val sy = cy + sDist * sin(sAngle)
        val flicker = (sin(t * 22f + seed) + 1f) * 0.5f

        drawCircle(
            color = if (i % 3 == 0) Color.White.copy(alpha = flicker) else glow.copy(alpha = flicker * 0.85f),
            radius = 2.0f + (i % 3) * 0.8f,
            center = Offset(sx, sy)
        )
    }
}

// ── Living Prism Fractal drawing — 3D Quartz Crystal & Chromatic Dispersion ───

internal fun DrawScope.drawLivingPrismFractal(
    timeSec : Float,
    g1      : Color,
    g2      : Color,
    g3      : Color,
    glow    : Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = minOf(cx, cy) * 0.94f
    val t = timeSec
    val beat = computeBeatPulse(t, 122f)
    val PI2 = (2.0 * Math.PI).toFloat()

    // ── Layer 0: Ambient prismatic field with caustic light refractions ───────
    val ambientPath = createHarmonicTidePath(
        cx = cx, cy = cy,
        baseRadius = maxR * 0.98f,
        timeSec = t * 0.18f,
        amplitude = 0.30f,
        pointCount = 12
    )
    drawPath(
        path = ambientPath,
        brush = Brush.radialGradient(
            colors = listOf(
                g1.copy(alpha = 0.32f),
                g2.copy(alpha = 0.16f),
                Color(0xFF040612).copy(alpha = 0.94f)
            ),
            center = Offset(cx, cy),
            radius = maxR
        )
    )

    // ── Layer 1: Chromatic Dispersion Rainbow Fans (Light split into spectra) ──
    val fanCount = 6
    for (f in 0 until fanCount) {
        val baseFanAngle = f * (PI2 / fanCount) + t * 0.28f
        val fanLen = maxR * (0.88f + 0.10f * sin(t * 2.2f + f.toFloat()))
        val spectralColors = listOf(
            Color(0xFFFF3366), // Red
            Color(0xFFFF9933), // Amber
            Color(0xFF33FF99), // Green
            Color(0xFF33CCFF), // Cyan
            Color(0xFF9933FF)  // Violet
        )
        // Draw 5 sub-spectral rays per fan
        for (sc in 0 until 5) {
            val subAngle = baseFanAngle + (sc - 2) * 0.045f
            val sColor = spectralColors[sc]
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.70f * (0.7f + 0.3f * beat)),
                        sColor.copy(alpha = 0.40f),
                        Color.Transparent
                    ),
                    start = Offset(cx, cy),
                    end = Offset(cx + fanLen * cos(subAngle), cy + fanLen * sin(subAngle))
                ),
                start = Offset(cx, cy),
                end = Offset(cx + fanLen * cos(subAngle), cy + fanLen * sin(subAngle)),
                strokeWidth = 2.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }

    // ── Layer 2: 3D Faceted Crystal Cluster (Faceted polyhedral geometry) ─────
    val facetLayers = 6
    for (layer in 0 until facetLayers) {
        val rotSpeed = if (layer % 2 == 0) 0.25f else -0.20f
        val layerAngle = t * rotSpeed + layer * (PI2 / facetLayers)
        val vertices = 6
        val rBase = maxR * (0.28f + layer * 0.09f) * (1f + 0.10f * beat)

        val facetPath = Path()
        for (v in 0 until vertices) {
            val theta = layerAngle + v * (PI2 / vertices)
            // 3D perspective foreshortening simulation
            val zPersp = 1f + 0.15f * sin(theta * 2f + t * 1.5f + layer.toFloat())
            val vx = cx + rBase * cos(theta) * zPersp
            val vy = cy + rBase * sin(theta) * (zPersp * 0.85f)
            if (v == 0) facetPath.moveTo(vx, vy) else facetPath.lineTo(vx, vy)
        }
        facetPath.close()

        val fColor = when (layer % 4) {
            0 -> g1; 1 -> glow; 2 -> g2; else -> g3
        }
        drawPath(
            path = facetPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    fColor.copy(alpha = 0.26f + 0.12f * beat),
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                start = Offset(cx + rBase * cos(layerAngle), cy + rBase * sin(layerAngle)),
                end = Offset(cx - rBase * cos(layerAngle), cy - rBase * sin(layerAngle))
            )
        )
        drawPath(
            path = facetPath,
            color = Color.White.copy(alpha = 0.45f + 0.25f * beat),
            style = Stroke(width = 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }

    // ── Layer 3: Caustic intersection lines (sharp internal gem refractions) ──
    for (c in 0 until 4) {
        val cAngle = t * 0.35f + c * (PI2 / 4f)
        val caustLen = maxR * 0.45f
        drawLine(
            color = Color.White.copy(alpha = 0.40f + 0.25f * beat),
            start = Offset(cx + caustLen * cos(cAngle), cy + caustLen * sin(cAngle)),
            end = Offset(cx - caustLen * cos(cAngle), cy - caustLen * sin(cAngle)),
            strokeWidth = 1.2f
        )
    }

    // ── Layer 4: Central radiant diamond crystal core with optical glints ─────
    val coreR = maxR * 0.22f * (1f + 0.18f * beat)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                glow.copy(alpha = 0.90f),
                g1.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = coreR * 1.6f
        ),
        radius = coreR * 1.6f,
        center = Offset(cx, cy)
    )

    // Diamond 8-point cross glint
    val glintLen = coreR * 3.2f
    val gAngle = t * 0.15f
    for (g in 0 until 4) {
        val ga = gAngle + g * (PI2 / 8f)
        drawLine(
            color = Color.White.copy(alpha = 0.92f),
            start = Offset(cx - glintLen * cos(ga), cy - glintLen * sin(ga)),
            end = Offset(cx + glintLen * cos(ga), cy + glintLen * sin(ga)),
            strokeWidth = if (g % 2 == 0) 2.2f else 1.0f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
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
    // 1. Cian Eléctrico
    MareaTrackPalette(
        id        = "electric_cyan",
        name      = "Cian Eléctrico",
        primary   = Color(0xFF00F5FF),
        secondary = Color(0xFF0052D4),
        tertiary  = Color(0xFF4364F7),
        glow      = Color(0xFF80FAFF)
    ),
    // 2. Glaciar Ártico
    MareaTrackPalette(
        id        = "nordic_glacier",
        name      = "Glaciar Ártico",
        primary   = Color(0xFF00D2FF),
        secondary = Color(0xFF0A369D),
        tertiary  = Color(0xFF3A86FF),
        glow      = Color(0xFF7FE5FF)
    ),
    // 3. Marea Caribeña
    MareaTrackPalette(
        id        = "caribbean_tide",
        name      = "Marea Caribeña",
        primary   = Color(0xFF00F0B5),
        secondary = Color(0xFF028090),
        tertiary  = Color(0xFF00A896),
        glow      = Color(0xFF8AFFE0)
    ),
    // 4. Aqua Cristalino
    MareaTrackPalette(
        id        = "crystal_aqua",
        name      = "Aqua Cristalino",
        primary   = Color(0xFF2DE2E6),
        secondary = Color(0xFF0353A4),
        tertiary  = Color(0xFF0466C8),
        glow      = Color(0xFF9CF8FA)
    ),
    // 5. Abismo Marino
    MareaTrackPalette(
        id        = "deep_abyss",
        name      = "Abismo Marino",
        primary   = Color(0xFF00B4D8),
        secondary = Color(0xFF03045E),
        tertiary  = Color(0xFF0077B6),
        glow      = Color(0xFF90E0EF)
    ),
    // 6. Aurora Menta
    MareaTrackPalette(
        id        = "mint_aurora",
        name      = "Aurora Menta",
        primary   = Color(0xFF05FFA1),
        secondary = Color(0xFF008F7A),
        tertiary  = Color(0xFF00C9A7),
        glow      = Color(0xFF80FFD0)
    ),
    // 7. Turquesa Polar
    MareaTrackPalette(
        id        = "polar_turquoise",
        name      = "Turquesa Polar",
        primary   = Color(0xFF00E8C6),
        secondary = Color(0xFF0F4C81),
        tertiary  = Color(0xFF1B998B),
        glow      = Color(0xFF85FFF0)
    ),
    // 8. Cobalto Real
    MareaTrackPalette(
        id        = "royal_cobalt",
        name      = "Cobalto Real",
        primary   = Color(0xFF2563EB),
        secondary = Color(0xFF1E3A8A),
        tertiary  = Color(0xFF60A5FA),
        glow      = Color(0xFF93C5FD)
    ),
    // 9. Azul Ultraneón
    MareaTrackPalette(
        id        = "electric_blue",
        name      = "Azul Ultraneón",
        primary   = Color(0xFF1D4ED8),
        secondary = Color(0xFF4F46E5),
        tertiary  = Color(0xFF818CF8),
        glow      = Color(0xFF67E8F9)
    ),
    // 10. Zafiro Profundo
    MareaTrackPalette(
        id        = "deep_sapphire",
        name      = "Zafiro Profundo",
        primary   = Color(0xFF0F52BA),
        secondary = Color(0xFF001858),
        tertiary  = Color(0xFF3A86FF),
        glow      = Color(0xFF6EA8FE)
    ),
    // 11. Cielo Ultramar
    MareaTrackPalette(
        id        = "ultramarine_sky",
        name      = "Cielo Ultramar",
        primary   = Color(0xFF3B82F6),
        secondary = Color(0xFF1D2D50),
        tertiary  = Color(0xFF93C5FD),
        glow      = Color(0xFFBFDBFE)
    ),
    // 12. Ola de Medianoche
    MareaTrackPalette(
        id        = "midnight_wave",
        name      = "Ola de Medianoche",
        primary   = Color(0xFF1E40AF),
        secondary = Color(0xFF0B132B),
        tertiary  = Color(0xFF4895EF),
        glow      = Color(0xFF60A5FA)
    ),
    // 13. Pulso Índigo
    MareaTrackPalette(
        id        = "indigo_pulse",
        name      = "Pulso Índigo",
        primary   = Color(0xFF4338CA),
        secondary = Color(0xFF1E1B4B),
        tertiary  = Color(0xFF818CF8),
        glow      = Color(0xFFA5B4FC)
    ),
    // 14. Azul Cuántico
    MareaTrackPalette(
        id        = "quantum_blue",
        name      = "Azul Cuántico",
        primary   = Color(0xFF312E81),
        secondary = Color(0xFF03071E),
        tertiary  = Color(0xFF4361EE),
        glow      = Color(0xFF6366F1)
    ),
    // 15. Brisa Egea
    MareaTrackPalette(
        id        = "aegean_breeze",
        name      = "Brisa Egea",
        primary   = Color(0xFF0284C7),
        secondary = Color(0xFF075985),
        tertiary  = Color(0xFF7DD3FC),
        glow      = Color(0xFFBAE6FD)
    ),
    // 16. Amatista Mística
    MareaTrackPalette(
        id        = "mystic_amethyst",
        name      = "Amatista Mística",
        primary   = Color(0xFF8B5CF6),
        secondary = Color(0xFF3B0764),
        tertiary  = Color(0xFFC4B5FD),
        glow      = Color(0xFFDDD6FE)
    ),
    // 17. Violeta Cósmico
    MareaTrackPalette(
        id        = "cosmic_violet",
        name      = "Violeta Cósmico",
        primary   = Color(0xFF7C3AED),
        secondary = Color(0xFF4C1D95),
        tertiary  = Color(0xFFA78BFA),
        glow      = Color(0xFFC084FC)
    ),
    // 18. Lavanda Astral
    MareaTrackPalette(
        id        = "astral_lavender",
        name      = "Lavanda Astral",
        primary   = Color(0xFFA855F7),
        secondary = Color(0xFF581C87),
        tertiary  = Color(0xFFD8B4FE),
        glow      = Color(0xFFF3E8FF)
    ),
    // 19. Púrpura Galáctico
    MareaTrackPalette(
        id        = "deep_purple",
        name      = "Púrpura Galáctico",
        primary   = Color(0xFF9333EA),
        secondary = Color(0xFF2E1065),
        tertiary  = Color(0xFFC084FC),
        glow      = Color(0xFFE9D5FF)
    ),
    // 20. Iris Cibernético
    MareaTrackPalette(
        id        = "cyber_iris",
        name      = "Iris Cibernético",
        primary   = Color(0xFF6D28D9),
        secondary = Color(0xFF1E1B4B),
        tertiary  = Color(0xFF8B5CF6),
        glow      = Color(0xFFA78BFA)
    ),
    // 21. Noche Aterciopelada
    MareaTrackPalette(
        id        = "velvet_night",
        name      = "Noche Aterciopelada",
        primary   = Color(0xFF581C87),
        secondary = Color(0xFF170F28),
        tertiary  = Color(0xFF9333EA),
        glow      = Color(0xFFC084FC)
    ),
    // 22. Lila Luminoso
    MareaTrackPalette(
        id        = "neon_lilac",
        name      = "Lila Luminoso",
        primary   = Color(0xFFB185DB),
        secondary = Color(0xFF4A154B),
        tertiary  = Color(0xFFDDA0DD),
        glow      = Color(0xFFE8D7F1)
    ),
    // 23. Cíber Fucsia
    MareaTrackPalette(
        id        = "cyber_magenta",
        name      = "Cíber Fucsia",
        primary   = Color(0xFFFF007F),
        secondary = Color(0xFF7928CA),
        tertiary  = Color(0xFFFF4D94),
        glow      = Color(0xFFFF80BF)
    ),
    // 24. Orquídea Eléctrica
    MareaTrackPalette(
        id        = "electric_orchid",
        name      = "Orquídea Eléctrica",
        primary   = Color(0xFFD946EF),
        secondary = Color(0xFF701A75),
        tertiary  = Color(0xFFF472B6),
        glow      = Color(0xFFFBCFE8)
    ),
    // 25. Flor de Sakura
    MareaTrackPalette(
        id        = "sakura_bloom",
        name      = "Flor de Sakura",
        primary   = Color(0xFFEC4899),
        secondary = Color(0xFF831843),
        tertiary  = Color(0xFFFBCFE8),
        glow      = Color(0xFFFCE7F3)
    ),
    // 26. Chicle Neón
    MareaTrackPalette(
        id        = "neon_bubblegum",
        name      = "Chicle Neón",
        primary   = Color(0xFFF43F5E),
        secondary = Color(0xFF881337),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFFFECDD3)
    ),
    // 27. Rosa Hiperneón
    MareaTrackPalette(
        id        = "hyper_pink",
        name      = "Rosa Hiperneón",
        primary   = Color(0xFFFF1493),
        secondary = Color(0xFF4A0033),
        tertiary  = Color(0xFFFF69B4),
        glow      = Color(0xFFFF85C0)
    ),
    // 28. Nebulosa Magenta
    MareaTrackPalette(
        id        = "magenta_nebula",
        name      = "Nebulosa Magenta",
        primary   = Color(0xFFBE185D),
        secondary = Color(0xFF3B0764),
        tertiary  = Color(0xFFF472B6),
        glow      = Color(0xFFF9A8D4)
    ),
    // 29. Cuarzo Rosa
    MareaTrackPalette(
        id        = "rose_quartz",
        name      = "Cuarzo Rosa",
        primary   = Color(0xFFFB7185),
        secondary = Color(0xFF4C0519),
        tertiary  = Color(0xFFFDA4AF),
        glow      = Color(0xFFFFE4E6)
    ),
    // 30. Coral Eléctrico
    MareaTrackPalette(
        id        = "hot_coral",
        name      = "Coral Eléctrico",
        primary   = Color(0xFFFF477E),
        secondary = Color(0xFF7F055F),
        tertiary  = Color(0xFFFF70A6),
        glow      = Color(0xFFFFA8C5)
    ),
    // 31. Rubí Sangre
    MareaTrackPalette(
        id        = "blood_ruby",
        name      = "Rubí Sangre",
        primary   = Color(0xFFFF1E56),
        secondary = Color(0xFF8B0000),
        tertiary  = Color(0xFFFF647C),
        glow      = Color(0xFFFFA3B2)
    ),
    // 32. Carmesí Tokio
    MareaTrackPalette(
        id        = "tokyo_crimson",
        name      = "Carmesí Tokio",
        primary   = Color(0xFFE11D48),
        secondary = Color(0xFF4C0519),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFFFDA4AF)
    ),
    // 33. Lava Volcánica
    MareaTrackPalette(
        id        = "volcanic_lava",
        name      = "Lava Volcánica",
        primary   = Color(0xFFDC2626),
        secondary = Color(0xFF450A0A),
        tertiary  = Color(0xFFF87171),
        glow      = Color(0xFFFCA5A5)
    ),
    // 34. Fuego Escarlata
    MareaTrackPalette(
        id        = "scarlet_fire",
        name      = "Fuego Escarlata",
        primary   = Color(0xFFEF4444),
        secondary = Color(0xFF7F1D1D),
        tertiary  = Color(0xFFFCA5A5),
        glow      = Color(0xFFFECACA)
    ),
    // 35. Granate Real
    MareaTrackPalette(
        id        = "dark_garnet",
        name      = "Granate Real",
        primary   = Color(0xFF881337),
        secondary = Color(0xFF38040E),
        tertiary  = Color(0xFFBE123C),
        glow      = Color(0xFFFB7185)
    ),
    // 36. Cereza Negra
    MareaTrackPalette(
        id        = "black_cherry",
        name      = "Cereza Negra",
        primary   = Color(0xFF9F1239),
        secondary = Color(0xFF2A0813),
        tertiary  = Color(0xFFE11D48),
        glow      = Color(0xFFFDA4AF)
    ),
    // 37. Carmesí Imperial
    MareaTrackPalette(
        id        = "imperial_crimson",
        name      = "Carmesí Imperial",
        primary   = Color(0xFFBE123C),
        secondary = Color(0xFF4C0519),
        tertiary  = Color(0xFFFB7185),
        glow      = Color(0xFFFECDD3)
    ),
    // 38. Llama Atómica
    MareaTrackPalette(
        id        = "atomic_flame",
        name      = "Llama Atómica",
        primary   = Color(0xFFFF3366),
        secondary = Color(0xFF660022),
        tertiary  = Color(0xFFFF6688),
        glow      = Color(0xFFFFA3B8)
    ),
    // 39. Atardecer Carmesí
    MareaTrackPalette(
        id        = "sunset_crimson",
        name      = "Atardecer Carmesí",
        primary   = Color(0xFFFF2A6D),
        secondary = Color(0xFFFF5E36),
        tertiary  = Color(0xFFFFA726),
        glow      = Color(0xFFFF8AA5)
    ),
    // 40. Cobre Ígneo
    MareaTrackPalette(
        id        = "fiery_copper",
        name      = "Cobre Ígneo",
        primary   = Color(0xFFF97316),
        secondary = Color(0xFF7C2D12),
        tertiary  = Color(0xFFFDBA74),
        glow      = Color(0xFFFFEDD5)
    ),
    // 41. Llamarada Solar
    MareaTrackPalette(
        id        = "solar_flare",
        name      = "Llamarada Solar",
        primary   = Color(0xFFFF6B35),
        secondary = Color(0xFF8B1E00),
        tertiary  = Color(0xFFFF9F1C),
        glow      = Color(0xFFFFB997)
    ),
    // 42. Especias de Bengala
    MareaTrackPalette(
        id        = "bengal_spice",
        name      = "Especias de Bengala",
        primary   = Color(0xFFEA580C),
        secondary = Color(0xFF431407),
        tertiary  = Color(0xFFFB923C),
        glow      = Color(0xFFFED7AA)
    ),
    // 43. Mandarina Crepúsculo
    MareaTrackPalette(
        id        = "twilight_tangerine",
        name      = "Mandarina Crepúsculo",
        primary   = Color(0xFFFB923C),
        secondary = Color(0xFF7C2D12),
        tertiary  = Color(0xFFFDE047),
        glow      = Color(0xFFFEF08A)
    ),
    // 44. Ámbar Volcánico
    MareaTrackPalette(
        id        = "volcanic_amber",
        name      = "Ámbar Volcánico",
        primary   = Color(0xFFC2410C),
        secondary = Color(0xFF3A1000),
        tertiary  = Color(0xFFEA580C),
        glow      = Color(0xFFFDBA74)
    ),
    // 45. Durazno Tropical
    MareaTrackPalette(
        id        = "tropical_peach",
        name      = "Durazno Tropical",
        primary   = Color(0xFFFF6B6B),
        secondary = Color(0xFF802020),
        tertiary  = Color(0xFFFF8E53),
        glow      = Color(0xFFFFB4B4)
    ),
    // 46. Terracota Zen
    MareaTrackPalette(
        id        = "terracotta_zen",
        name      = "Terracota Zen",
        primary   = Color(0xFFD9534F),
        secondary = Color(0xFF4A1515),
        tertiary  = Color(0xFFF0AD4E),
        glow      = Color(0xFFF8D7DA)
    ),
    // 47. Oro Solar
    MareaTrackPalette(
        id        = "solar_gold",
        name      = "Oro Solar",
        primary   = Color(0xFFEAB308),
        secondary = Color(0xFF713F12),
        tertiary  = Color(0xFFFDE047),
        glow      = Color(0xFFFEF9C3)
    ),
    // 48. Oro Celestial
    MareaTrackPalette(
        id        = "celestial_gold",
        name      = "Oro Celestial",
        primary   = Color(0xFFFACC15),
        secondary = Color(0xFF854D0E),
        tertiary  = Color(0xFFFEF08A),
        glow      = Color(0xFFFEFCE8)
    ),
    // 49. Duna Dorada
    MareaTrackPalette(
        id        = "golden_dune",
        name      = "Duna Dorada",
        primary   = Color(0xFFD97706),
        secondary = Color(0xFF451A03),
        tertiary  = Color(0xFFFCD34D),
        glow      = Color(0xFFFDE68A)
    ),
    // 50. Ámbar Silvestre
    MareaTrackPalette(
        id        = "wild_amber",
        name      = "Ámbar Silvestre",
        primary   = Color(0xFFB45309),
        secondary = Color(0xFF2E1202),
        tertiary  = Color(0xFFF59E0B),
        glow      = Color(0xFFFDE68A)
    ),
    // 51. Miel Pura
    MareaTrackPalette(
        id        = "honeycomb_glow",
        name      = "Miel Pura",
        primary   = Color(0xFFF59E0B),
        secondary = Color(0xFF78350F),
        tertiary  = Color(0xFFFBBF24),
        glow      = Color(0xFFFEF3C7)
    ),
    // 52. Citrino Neón
    MareaTrackPalette(
        id        = "lemon_zest",
        name      = "Citrino Neón",
        primary   = Color(0xFFFFE600),
        secondary = Color(0xFF665C00),
        tertiary  = Color(0xFFFFF066),
        glow      = Color(0xFFFFFF99)
    ),
    // 53. Neón Lima
    MareaTrackPalette(
        id        = "neon_lime",
        name      = "Neón Lima",
        primary   = Color(0xFFCCFF00),
        secondary = Color(0xFF15803D),
        tertiary  = Color(0xFF84CC16),
        glow      = Color(0xFFE6FF80)
    ),
    // 54. Verde Eléctrico
    MareaTrackPalette(
        id        = "acid_green",
        name      = "Verde Eléctrico",
        primary   = Color(0xFFA3E635),
        secondary = Color(0xFF365314),
        tertiary  = Color(0xFFBEF264),
        glow      = Color(0xFFECFCCB)
    ),
    // 55. Té Matcha
    MareaTrackPalette(
        id        = "zen_matcha",
        name      = "Té Matcha",
        primary   = Color(0xFF65A30D),
        secondary = Color(0xFF1A2E05),
        tertiary  = Color(0xFF84CC16),
        glow      = Color(0xFFBEF264)
    ),
    // 56. Chartreuse Vivo
    MareaTrackPalette(
        id        = "chartreuse_glow",
        name      = "Chartreuse Vivo",
        primary   = Color(0xFF84CC16),
        secondary = Color(0xFF1E3A0A),
        tertiary  = Color(0xFFA3E635),
        glow      = Color(0xFFD9F99D)
    ),
    // 57. Aurora Boreal
    MareaTrackPalette(
        id        = "nordic_aurora",
        name      = "Aurora Boreal",
        primary   = Color(0xFF00F260),
        secondary = Color(0xFF0575E6),
        tertiary  = Color(0xFF00F5D4),
        glow      = Color(0xFF80FFB0)
    ),
    // 58. Laguna Esmeralda
    MareaTrackPalette(
        id        = "emerald_lagoon",
        name      = "Laguna Esmeralda",
        primary   = Color(0xFF10B981),
        secondary = Color(0xFF064E3B),
        tertiary  = Color(0xFF34D399),
        glow      = Color(0xFFA7F3D0)
    ),
    // 59. Esmeralda Sublime
    MareaTrackPalette(
        id        = "sublime_emerald",
        name      = "Esmeralda Sublime",
        primary   = Color(0xFF059669),
        secondary = Color(0xFF022C22),
        tertiary  = Color(0xFF6EE7B7),
        glow      = Color(0xFFA7F3D0)
    ),
    // 60. Selva Tropical
    MareaTrackPalette(
        id        = "rainforest_bamboo",
        name      = "Selva Tropical",
        primary   = Color(0xFF16A34A),
        secondary = Color(0xFF052E16),
        tertiary  = Color(0xFF4ADE80),
        glow      = Color(0xFFBBF7D0)
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
