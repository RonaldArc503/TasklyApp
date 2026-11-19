package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.PRESET_PLAYER_COLORS
import com.ronaldcolocho.taskly.audio.PlayerColorMode
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

/**
 * Contenido rediseñado para la selección de color de reproducción (Maquetación Imagen 1).
 */
@Composable
fun PlayerColorPickerContent(
    colorMode: PlayerColorMode,
    activePaletteId: String,
    currentPaletteName: String,
    activePalettePrimary: Color?,
    onSetColorMode: (PlayerColorMode) -> Unit,
    onSelectPalette: (String) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit = onClose,
    surface: Color,
    onSurface: Color,
    secondary: Color,
    accentColor: Color
) {
    val activePreset = PRESET_PLAYER_COLORS.firstOrNull {
        it.id == activePaletteId || it.name.equals(currentPaletteName, ignoreCase = true)
    }
    val effectiveColor = activePreset?.let { Color(it.colorHex) } ?: activePalettePrimary ?: accentColor
    val isLightColor = effectiveColor.isColorLight()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Color de reproducción",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = onSurface
            )

            IconButton(
                onClick = onClose,
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

        Spacer(Modifier.height(16.dp))

        // Segmented Control (Automático variado | Original)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0E1A28))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAuto = colorMode == PlayerColorMode.AUTOMATIC
            // Automático variado tab
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAuto) Color(0xFF2C4560) else Color.Transparent)
                    .clickable { onSetColorMode(PlayerColorMode.AUTOMATIC) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Automático variado",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.5.sp
                    ),
                    color = if (isAuto) Color.White else secondary
                )
            }

            // Original tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isAuto) Color(0xFF2C4560) else Color.Transparent)
                    .clickable { onSetColorMode(PlayerColorMode.ORIGINAL) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Original",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontWeight = if (!isAuto) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.5.sp
                    ),
                    color = if (!isAuto) Color.White else secondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Subtitle description
        Text(
            text = if (colorMode == PlayerColorMode.ORIGINAL) {
                "Cada canción conserva su propia paleta original."
            } else {
                "Evita las dos familias de color recientes."
            },
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = BricolageGrotesque,
                fontSize = 13.sp
            ),
            color = secondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(26.dp))

        // 6 Preset Color Swatches (2 rows of 3)
        val row1 = PRESET_PLAYER_COLORS.take(3)
        val row2 = PRESET_PLAYER_COLORS.drop(3).take(3)

        ColorSwatchRow(
            presets = row1,
            colorMode = colorMode,
            activePaletteId = activePaletteId,
            currentPaletteName = currentPaletteName,
            onSurface = onSurface,
            secondary = secondary,
            onSelectPalette = onSelectPalette
        )

        Spacer(Modifier.height(24.dp))

        ColorSwatchRow(
            presets = row2,
            colorMode = colorMode,
            activePaletteId = activePaletteId,
            currentPaletteName = currentPaletteName,
            onSurface = onSurface,
            secondary = secondary,
            onSelectPalette = onSelectPalette
        )

        Spacer(Modifier.height(34.dp))

        // Botón "Listo"
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = effectiveColor,
                contentColor = if (isLightColor) Color(0xFF101B27) else Color.White
            )
        ) {
            Text(
                text = "Listo",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = if (isLightColor) Color(0xFF101B27) else Color.White
            )
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ColorSwatchRow(
    presets: List<com.ronaldcolocho.taskly.audio.PlayerPresetColor>,
    colorMode: PlayerColorMode,
    activePaletteId: String,
    currentPaletteName: String,
    onSurface: Color,
    secondary: Color,
    onSelectPalette: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (preset in presets) {
            val isSelected = colorMode != PlayerColorMode.ORIGINAL && (
                preset.id == activePaletteId || preset.name.equals(currentPaletteName, ignoreCase = true)
            )
            val presetColor = Color(preset.colorHex)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(105.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelectPalette(preset.id)
                    }
            ) {
                // Circle with outer selection ring
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(presetColor)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = preset.name,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = BricolageGrotesque,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) onSurface else secondary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun Color.isColorLight(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance > 0.55f
}

/**
 * Control retrocompatible previo.
 */
@Composable
internal fun PlayerColorControls(
    paletteName: String, paletteColor: Color, mode: PlayerColorMode,
    onChange: () -> Unit, onMode: (PlayerColorMode) -> Unit,
    surface: Color, onSurface: Color, secondary: Color, accent: Color
) {
    Surface(shape = RoundedCornerShape(20.dp), color = surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(paletteColor))
                Column(Modifier.weight(1f)) {
                    Text("Color de reproducción", color = secondary, fontFamily = BricolageGrotesque, fontSize = 11.sp)
                    Text(paletteName, color = onSurface, fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == PlayerColorMode.AUTOMATIC,
                    onClick = { onMode(PlayerColorMode.AUTOMATIC) }, modifier = Modifier.weight(1.5f),
                    label = { Text("Automático variado", fontSize = 12.sp, fontFamily = BricolageGrotesque) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.16f),
                        selectedLabelColor = onSurface, labelColor = secondary))
                FilterChip(selected = mode == PlayerColorMode.ORIGINAL,
                    onClick = { onMode(PlayerColorMode.ORIGINAL) }, modifier = Modifier.weight(1f),
                    label = { Text("Original", fontSize = 12.sp, fontFamily = BricolageGrotesque) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.16f),
                        selectedLabelColor = onSurface, labelColor = secondary))
            }
            Text(if (mode == PlayerColorMode.ORIGINAL) "Cada canción conserva su propia paleta original."
                else "Evita las dos familias de color recientes.", color = secondary,
                fontFamily = BricolageGrotesque, fontSize = 12.sp)
            OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
                Text("Cambiar color", color = onSurface, fontFamily = BricolageGrotesque)
            }
            if (mode == PlayerColorMode.ORIGINAL) Text("Cambiar color activa el modo automático.",
                color = secondary, fontSize = 11.sp, fontFamily = BricolageGrotesque)
        }
    }
}
