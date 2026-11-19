package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.LyricsAnimationMode
import com.ronaldcolocho.taskly.domain.model.LyricsLine
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.SongLyrics
import com.ronaldcolocho.taskly.domain.model.activeLyricsLine
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

internal fun lyricsTextAccent(accent: Color, background: Color, onSurface: Color): Color {
    var color = accent
    repeat(12) {
        val foreground = color.luminance()
        val behind = background.luminance()
        val contrast = (maxOf(foreground, behind) + 0.05f) / (minOf(foreground, behind) + 0.05f)
        if (contrast >= 5f) return color
        color = lerp(color, onSurface, 0.2f)
    }
    return onSurface
}

@Composable
internal fun MusicLyricsPane(
    state: MusicLyricsState,
    positionMs: Long,
    onSurface: Color,
    secondary: Color,
    accent: Color,
    isPlaying: Boolean = true,
    animationMode: LyricsAnimationMode = LyricsAnimationMode.STATIC,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize().testTag("music_lyrics_pane"), contentAlignment = Alignment.Center) {
        when {
            state.loading || state.result == null -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = accent)
                Text("Buscando letra…", color = secondary)
            }
            state.result is LyricsLookup.Found -> {
                val lyrics = state.result.lyrics
                when {
                    lyrics.instrumental -> LyricsMessage("Canción instrumental", "Esta canción no tiene letra.", secondary, lyrics.provider)
                    lyrics.lines.isNotEmpty() -> SyncedMusicLyrics(
                        lyrics = lyrics,
                        positionMs = positionMs,
                        isPlaying = isPlaying,
                        animationMode = animationMode,
                        onSurface = onSurface,
                        secondary = secondary,
                        accent = accent
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 24.dp)) {
                            item { Text(lyrics.displayText, color = onSurface, fontFamily = BricolageGrotesque,
                                fontSize = 22.sp, lineHeight = 34.sp) }
                        }
                        Text("Letra sin sincronización · ${lyrics.provider}", color = secondary, fontSize = 11.sp)
                    }
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.result is LyricsLookup.Failed) state.result.message else "Letra no disponible",
                    color = secondary, fontFamily = BricolageGrotesque, fontSize = 18.sp)
                if (state.result is LyricsLookup.NotFound) Text("No encontramos una letra que coincida con esta versión.",
                    color = secondary, fontSize = 13.sp)
                if (state.retrySeconds > 0) Text(
                    if (state.autoRetry) "Reintentando automáticamente en ${state.retrySeconds} s…"
                    else "Puedes reintentar en ${state.retrySeconds} s",
                    color = secondary, fontSize = 13.sp)
                TextButton(onClick = onRetry, enabled = !state.loading && state.retrySeconds == 0) {
                    Text("Reintentar", color = if (state.retrySeconds == 0) accent else secondary)
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, message: String, color: Color, provider: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = color, fontFamily = BricolageGrotesque, fontSize = 22.sp)
        Text(message, color = color, fontSize = 14.sp)
        Text(provider, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun SyncedMusicLyrics(
    lyrics: SongLyrics,
    positionMs: Long,
    isPlaying: Boolean,
    animationMode: LyricsAnimationMode,
    onSurface: Color,
    secondary: Color,
    accent: Color
) {
    val listState = rememberLazyListState()
    var following by remember(lyrics) { mutableStateOf(true) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val active = remember(lyrics, positionMs) { activeLyricsLine(lyrics.lines, positionMs) }
    LaunchedEffect(dragging) { if (dragging) following = false }
    LaunchedEffect(active, following, lyrics) {
        if (following && active >= 0) listState.animateScrollToItem(active, -listState.layoutInfo.viewportEndOffset / 3)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("synced_lyrics_list"), state = listState,
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(lyrics.lines, key = { _, line -> line.timeMs }) { index, line ->
                val nextLine = lyrics.lines.getOrNull(index + 1)
                val lineDurationMs = if (nextLine != null && nextLine.timeMs > line.timeMs) {
                    nextLine.timeMs - line.timeMs
                } else {
                    4000L
                }
                AnimatedLyricsLine(
                    line = line,
                    lineIndex = index,
                    activeLineIndex = active,
                    animationMode = animationMode,
                    positionMs = positionMs,
                    lineDurationMs = lineDurationMs,
                    isPlaying = isPlaying,
                    accent = accent,
                    onSurface = onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Letra sincronizada · ${lyrics.provider}", color = secondary, fontSize = 11.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!following) TextButton(onClick = { following = true }) { Text("Seguir canción", color = accent, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AnimatedLyricsLine(
    line: LyricsLine,
    lineIndex: Int,
    activeLineIndex: Int,
    animationMode: LyricsAnimationMode,
    positionMs: Long,
    lineDurationMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val isActive = lineIndex == activeLineIndex
    val rawText = line.text.ifBlank { "♪" }
    val isMusicSymbol = rawText == "♪"

    when (animationMode) {
        LyricsAnimationMode.STATIC -> {
            Text(
                text = rawText,
                color = if (isActive) accent else onSurface.copy(alpha = 0.65f),
                fontFamily = BricolageGrotesque,
                fontSize = 24.sp,
                lineHeight = 33.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                modifier = modifier.semantics { selected = isActive }
            )
        }
        LyricsAnimationMode.TYPEWRITER -> {
            TypewriterLyricsLine(
                rawText = rawText,
                isMusicSymbol = isMusicSymbol,
                isActive = isActive,
                lineIndex = lineIndex,
                activeLineIndex = activeLineIndex,
                lineTimeMs = line.timeMs,
                lineDurationMs = lineDurationMs,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = accent,
                onSurface = onSurface,
                modifier = modifier
            )
        }
        LyricsAnimationMode.BLUR_FOCUS -> {
            BlurFocusLyricsLine(
                rawText = rawText,
                isMusicSymbol = isMusicSymbol,
                isActive = isActive,
                lineIndex = lineIndex,
                activeLineIndex = activeLineIndex,
                accent = accent,
                onSurface = onSurface,
                modifier = modifier
            )
        }
        LyricsAnimationMode.KARAOKE_GLOW -> {
            KaraokeGlowLyricsLine(
                rawText = rawText,
                isMusicSymbol = isMusicSymbol,
                isActive = isActive,
                lineTimeMs = line.timeMs,
                lineDurationMs = lineDurationMs,
                positionMs = positionMs,
                isPlaying = isPlaying,
                accent = accent,
                onSurface = onSurface,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun TypewriterLyricsLine(
    rawText: String,
    isMusicSymbol: Boolean,
    isActive: Boolean,
    lineIndex: Int,
    activeLineIndex: Int,
    lineTimeMs: Long,
    lineDurationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.02f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "typewriter_line_scale"
    )

    if (!isActive) {
        val textColor = if (lineIndex < activeLineIndex) {
            onSurface.copy(alpha = 0.60f)
        } else {
            onSurface.copy(alpha = 0.35f)
        }
        Text(
            text = rawText,
            color = textColor,
            fontFamily = BricolageGrotesque,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .graphicsLayer {
                    scaleX = lineScale
                    scaleY = lineScale
                }
                .semantics { selected = false }
        )
    } else if (isMusicSymbol) {
        val infinitePulse = rememberInfiniteTransition(label = "music_pulse")
        val pulseScale by infinitePulse.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        Text(
            text = rawText,
            color = accent,
            fontFamily = BricolageGrotesque,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .semantics { selected = true }
        )
    } else {
        val elapsedMs = (positionMs - lineTimeMs).coerceAtLeast(0L)
        val typingBudgetMs = (lineDurationMs * 0.72f).toLong().coerceIn(600L, 6000L)
        val charPacedMs = (rawText.length * 60L).coerceAtLeast(500L)
        val typingDurationMs = minOf(typingBudgetMs, charPacedMs)

        val targetProgress = if (typingDurationMs > 0) {
            (elapsedMs.toFloat() / typingDurationMs).coerceIn(0f, 1f)
        } else 1f
        val targetChars = (rawText.length * targetProgress).toInt().coerceIn(0, rawText.length)

        val animatedChars = remember(lineTimeMs) { Animatable(0f) }

        LaunchedEffect(targetChars, isPlaying, lineTimeMs) {
            if (!isPlaying) {
                animatedChars.snapTo(targetChars.toFloat())
            } else {
                val diff = kotlin.math.abs(targetChars - animatedChars.value.toInt())
                if (diff > 8) {
                    animatedChars.snapTo(targetChars.toFloat())
                } else if (targetChars > animatedChars.value) {
                    animatedChars.animateTo(
                        targetValue = targetChars.toFloat(),
                        animationSpec = tween(durationMillis = 280, easing = LinearEasing)
                    )
                } else if (targetChars < animatedChars.value) {
                    animatedChars.snapTo(targetChars.toFloat())
                }
            }
        }

        val currentChars = animatedChars.value.toInt().coerceIn(0, rawText.length)
        val isTyping = currentChars < rawText.length && isPlaying

        val cursorTransition = rememberInfiniteTransition(label = "typewriter_cursor")
        val cursorAlpha by cursorTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursor_blink"
        )

        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        val annotatedString = remember(rawText, currentChars, accent, onSurface) {
            buildAnnotatedString {
                if (currentChars > 0) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(rawText.substring(0, currentChars))
                    }
                }
                if (currentChars < rawText.length) {
                    withStyle(SpanStyle(color = onSurface.copy(alpha = 0.20f), fontWeight = FontWeight.Normal)) {
                        append(rawText.substring(currentChars))
                    }
                }
            }
        }

        Text(
            text = annotatedString,
            fontFamily = BricolageGrotesque,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            onTextLayout = { layoutResult = it },
            modifier = modifier
                .graphicsLayer {
                    scaleX = lineScale
                    scaleY = lineScale
                }
                .drawWithContent {
                    drawContent()
                    val layout = layoutResult
                    if (layout != null && isTyping && cursorAlpha > 0.15f) {
                        val cursorOffset = currentChars.coerceIn(0, rawText.length)
                        val cursorRect = layout.getCursorRect(cursorOffset)
                        drawRect(
                            color = accent.copy(alpha = cursorAlpha),
                            topLeft = Offset(cursorRect.left, cursorRect.top + 3.dp.toPx()),
                            size = Size(2.5.dp.toPx(), (cursorRect.height - 6.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                        )
                    }
                }
                .semantics { selected = true }
        )
    }
}

@Composable
private fun BlurFocusLyricsLine(
    rawText: String,
    isMusicSymbol: Boolean,
    isActive: Boolean,
    lineIndex: Int,
    activeLineIndex: Int,
    accent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val distance = kotlin.math.abs(lineIndex - activeLineIndex)

    val targetBlur = when {
        isActive -> 0.dp
        distance == 1 -> 3.dp
        else -> 6.5.dp
    }
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "blur_depth"
    )

    val targetAlpha = when {
        isActive -> 1f
        distance == 1 -> 0.45f
        else -> 0.20f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "alpha_depth"
    )

    val targetScale = if (isActive) 1.04f else 0.98f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scale_depth"
    )

    Text(
        text = rawText,
        color = if (isActive) accent else onSurface.copy(alpha = animatedAlpha),
        fontFamily = BricolageGrotesque,
        fontSize = 24.sp,
        lineHeight = 33.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isActive) 1f else animatedAlpha
            }
            .blur(animatedBlur)
            .semantics { selected = isActive }
    )
}

@Composable
private fun KaraokeGlowLyricsLine(
    rawText: String,
    isMusicSymbol: Boolean,
    isActive: Boolean,
    lineTimeMs: Long,
    lineDurationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "karaoke_scale"
    )

    if (!isActive) {
        Text(
            text = rawText,
            color = onSurface.copy(alpha = 0.50f),
            fontFamily = BricolageGrotesque,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .graphicsLayer {
                    scaleX = lineScale
                    scaleY = lineScale
                }
                .semantics { selected = false }
        )
    } else if (isMusicSymbol) {
        val infinitePulse = rememberInfiniteTransition(label = "karaoke_pulse")
        val pulseScale by infinitePulse.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "karaoke_note_scale"
        )
        Text(
            text = rawText,
            color = accent,
            fontFamily = BricolageGrotesque,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .semantics { selected = true }
        )
    } else {
        val elapsedMs = (positionMs - lineTimeMs).coerceAtLeast(0L)
        val sweepBudgetMs = (lineDurationMs * 0.85f).toLong().coerceIn(700L, 7000L)
        val targetProgress = if (sweepBudgetMs > 0) (elapsedMs.toFloat() / sweepBudgetMs).coerceIn(0f, 1f) else 1f
        val animatedProgress = remember(lineTimeMs) { Animatable(0f) }

        LaunchedEffect(targetProgress, isPlaying, lineTimeMs) {
            if (!isPlaying) {
                animatedProgress.snapTo(targetProgress)
            } else {
                val diff = kotlin.math.abs(targetProgress - animatedProgress.value)
                if (diff > 0.25f) animatedProgress.snapTo(targetProgress)
                else animatedProgress.animateTo(targetProgress, tween(durationMillis = 280, easing = LinearEasing))
            }
        }

        val p = animatedProgress.value.coerceIn(0f, 1f)
        val p0 = maxOf(0f, p - 0.04f)
        val p1 = minOf(1f, p + 0.04f)

        val brush = Brush.horizontalGradient(
            0.0f to accent,
            p0 to accent,
            p1 to onSurface.copy(alpha = 0.30f),
            1.0f to onSurface.copy(alpha = 0.30f)
        )

        Text(
            text = rawText,
            style = TextStyle(
                brush = brush,
                fontFamily = BricolageGrotesque,
                fontSize = 24.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = modifier
                .graphicsLayer {
                    scaleX = lineScale
                    scaleY = lineScale
                }
                .semantics { selected = true }
        )
    }
}
