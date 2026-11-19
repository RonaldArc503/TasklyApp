package com.ronaldcolocho.taskly.ui.screen.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.LyricsAnimationMode
import com.ronaldcolocho.taskly.audio.PlayerColorMode
import com.ronaldcolocho.taskly.audio.PlayerVisualEffect
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundDark
import com.ronaldcolocho.taskly.ui.theme.MareaBackgroundLight
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaOnSurfaceLight
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryDark
import com.ronaldcolocho.taskly.ui.theme.MareaSecondaryLight
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceDark
import com.ronaldcolocho.taskly.ui.theme.MareaSurfaceLight

/**
 * Bottom sheet rediseñado para los Efectos del Reproductor y Color de Reproducción.
 * Respeta rigurosamente el espacio superior del sistema (barra de estado, hora, carga y notificaciones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerVisualEffectsSheet(
    currentEffect: PlayerVisualEffect,
    onSelectEffect: (PlayerVisualEffect) -> Unit,
    onDismiss: () -> Unit,
    showAnimationCover: Boolean,
    onShowAnimationCoverChange: (Boolean) -> Unit,
    accentColor: Color = Color(0xFF70A896),
    currentPaletteName: String = "",
    activePalettePrimary: Color? = null,
    activePaletteId: String = "",
    colorMode: PlayerColorMode = PlayerColorMode.AUTOMATIC,
    onChangeColor: (() -> Unit)? = null,
    onSelectPalette: ((String) -> Unit)? = null,
    onSetColorMode: ((PlayerColorMode) -> Unit)? = null,
    currentLyricsAnimation: LyricsAnimationMode = LyricsAnimationMode.STATIC,
    onSelectLyricsAnimation: ((LyricsAnimationMode) -> Unit)? = null
) {
    val darkTheme = isSystemInDarkTheme()
    val background = if (darkTheme) MareaBackgroundDark else MareaBackgroundLight
    val surface    = if (darkTheme) MareaSurfaceDark    else MareaSurfaceLight
    val onSurface  = if (darkTheme) MareaOnSurfaceDark  else MareaOnSurfaceLight
    val secondary  = if (darkTheme) MareaSecondaryDark  else MareaSecondaryLight

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showColorPicker by remember { mutableStateOf(false) }

    // Interceptar el botón "Atrás" cuando el selector de color está abierto
    BackHandler(enabled = showColorPicker) {
        showColorPicker = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = background,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Asa de arrastre (drag handle) centrada y ubicada con seguridad bajo la barra de estado
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(secondary.copy(alpha = 0.35f))
            )

            AnimatedContent(
                targetState = showColorPicker,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "EffectsSheetTransition"
            ) { isColorPickerView ->
                if (isColorPickerView) {
                    PlayerColorPickerContent(
                        colorMode = colorMode,
                        activePaletteId = activePaletteId,
                        currentPaletteName = currentPaletteName,
                        activePalettePrimary = activePalettePrimary,
                        onSetColorMode = { mode -> onSetColorMode?.invoke(mode) },
                        onSelectPalette = { paletteId ->
                            onSelectPalette?.invoke(paletteId)
                        },
                        onClose = { showColorPicker = false },
                        onDone = { showColorPicker = false },
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        accentColor = accentColor
                    )
                } else {
                    MainEffectsContent(
                        currentEffect = currentEffect,
                        onSelectEffect = onSelectEffect,
                        onDismiss = onDismiss,
                        showAnimationCover = showAnimationCover,
                        onShowAnimationCoverChange = onShowAnimationCoverChange,
                        accentColor = accentColor,
                        currentPaletteName = currentPaletteName,
                        activePalettePrimary = activePalettePrimary,
                        onOpenColorPicker = { showColorPicker = true },
                        currentLyricsAnimation = currentLyricsAnimation,
                        onSelectLyricsAnimation = onSelectLyricsAnimation,
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary
                    )
                }
            }
        }
    }
}

/**
 * Vista principal: "Efectos del reproductor" (Maquetación Imagen 2).
 */
@Composable
private fun MainEffectsContent(
    currentEffect: PlayerVisualEffect,
    onSelectEffect: (PlayerVisualEffect) -> Unit,
    onDismiss: () -> Unit,
    showAnimationCover: Boolean,
    onShowAnimationCoverChange: (Boolean) -> Unit,
    accentColor: Color,
    currentPaletteName: String,
    activePalettePrimary: Color?,
    onOpenColorPicker: () -> Unit,
    currentLyricsAnimation: LyricsAnimationMode,
    onSelectLyricsAnimation: ((LyricsAnimationMode) -> Unit)?,
    surface: Color,
    onSurface: Color,
    secondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 4.dp)
    ) {
        // Header: Título y botón cerrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Efectos del reproductor",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Elige el color y la animación",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontSize = 12.5.sp
                    ),
                    color = secondary
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Card superior: Color de reproducción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surface)
                .clickable { onOpenColorPicker() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(activePalettePrimary ?: accentColor)
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Color de reproducción",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontSize = 11.5.sp
                    ),
                    color = secondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentPaletteName.ifBlank { "Duna Dorada" },
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = onSurface
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Elegir color de reproducción",
                tint = secondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        // Sección ANIMACIÓN con botón Aleatorio
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ANIMACIÓN",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.5.sp
                ),
                color = secondary
            )

            val isRandom = currentEffect == PlayerVisualEffect.RANDOM
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isRandom) accentColor.copy(alpha = 0.2f) else surface)
                    .border(
                        width = 1.dp,
                        color = if (isRandom) accentColor else secondary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelectEffect(PlayerVisualEffect.RANDOM) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = if (isRandom) accentColor else onSurface,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Aleatorio",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = if (isRandom) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = if (isRandom) accentColor else onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Grid 2x4 de Efectos Visuales
        val effects = listOf(
            PlayerVisualEffect.MAREA to PlayerVisualEffect.MAREA_V2,
            PlayerVisualEffect.GALAXY to PlayerVisualEffect.VORTEX,
            PlayerVisualEffect.BLACK_HOLE to PlayerVisualEffect.SUPERNOVA,
            PlayerVisualEffect.PLASMA_STORM to PlayerVisualEffect.PRISM_FRACTAL
        )

        effects.forEach { (firstEffect, secondEffect) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EffectCard(
                    effect = firstEffect,
                    isSelected = currentEffect == firstEffect,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onSelect = { onSelectEffect(firstEffect) }
                )
                EffectCard(
                    effect = secondEffect,
                    isSelected = currentEffect == secondEffect,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onSelect = { onSelectEffect(secondEffect) }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Sección ANIMACIÓN DE LA LETRA
        if (onSelectLyricsAnimation != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ANIMACIÓN DE LA LETRA",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.5.sp
                ),
                color = secondary
            )
            Spacer(Modifier.height(10.dp))

            val lyricsRows = listOf(
                LyricsAnimationMode.STATIC to LyricsAnimationMode.TYPEWRITER,
                LyricsAnimationMode.BLUR_FOCUS to LyricsAnimationMode.KARAOKE_GLOW
            )

            lyricsRows.forEach { (firstMode, secondMode) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LyricsModeCard(
                        mode = firstMode,
                        isSelected = currentLyricsAnimation == firstMode,
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelectLyricsAnimation(firstMode) }
                    )
                    LyricsModeCard(
                        mode = secondMode,
                        isSelected = currentLyricsAnimation == secondMode,
                        surface = surface,
                        onSurface = onSurface,
                        secondary = secondary,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelectLyricsAnimation(secondMode) }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Switch Mostrar portada en la animación
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surface)
                .testTag("music_animation_cover_toggle")
                .toggleable(
                    value = showAnimationCover,
                    role = Role.Switch,
                    onValueChange = onShowAnimationCoverChange
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mostrar portada en la animación",
                color = onSurface,
                fontFamily = BricolageGrotesque,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showAnimationCover,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accentColor,
                    checkedThumbColor = Color.White
                )
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Tarjeta individual para previsualizar y seleccionar un efecto de animación.
 */
@Composable
private fun EffectCard(
    effect: PlayerVisualEffect,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val cardTitle = when (effect) {
        PlayerVisualEffect.MAREA_V2 -> "Fluido vivo"
        else -> effect.title
    }

    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(getEffectBrush(effect))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        // En Agujero Negro representamos el disco de acreción con horizonte de sucesos
        if (effect == PlayerVisualEffect.BLACK_HOLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8BB5D6).copy(alpha = 0.45f),
                                Color(0xFF8BB5D6).copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF020406))
                )
            }
        }

        // Indicador de selección (badge circular con check en la esquina superior derecha)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Seleccionado",
                    tint = Color(0xFF101B27),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Título de la animación en la esquina inferior izquierda
        Text(
            text = cardTitle,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp)
        )
    }
}

/**
 * Tarjeta individual para seleccionar la animación de la letra.
 */
@Composable
private fun LyricsModeCard(
    mode: LyricsAnimationMode,
    isSelected: Boolean,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else secondary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mode.title,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = if (isSelected) Color.White else onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Gradientes visuales estilizados para cada tarjeta de animación.
 */
private fun getEffectBrush(effect: PlayerVisualEffect): Brush {
    return when (effect) {
        PlayerVisualEffect.MAREA -> Brush.linearGradient(
            colors = listOf(Color(0xFF0F3652), Color(0xFF154C6F))
        )
        PlayerVisualEffect.MAREA_V2 -> Brush.linearGradient(
            colors = listOf(Color(0xFF132F5C), Color(0xFF225796))
        )
        PlayerVisualEffect.GALAXY -> Brush.radialGradient(
            colors = listOf(Color(0xFF4C2F6B), Color(0xFF16152B))
        )
        PlayerVisualEffect.VORTEX -> Brush.radialGradient(
            colors = listOf(Color(0xFF246672), Color(0xFF0D2530))
        )
        PlayerVisualEffect.BLACK_HOLE -> Brush.verticalGradient(
            colors = listOf(Color(0xFF101F30), Color(0xFF08121C))
        )
        PlayerVisualEffect.SUPERNOVA -> Brush.radialGradient(
            colors = listOf(Color(0xFF6B3125), Color(0xFF20161A))
        )
        PlayerVisualEffect.PLASMA_STORM -> Brush.linearGradient(
            colors = listOf(Color(0xFF2E2C6E), Color(0xFF181836))
        )
        PlayerVisualEffect.PRISM_FRACTAL -> Brush.linearGradient(
            colors = listOf(Color(0xFF2B3A64), Color(0xFF242646))
        )
        PlayerVisualEffect.RANDOM -> Brush.linearGradient(
            colors = listOf(Color(0xFF162538), Color(0xFF1F3550))
        )
    }
}
