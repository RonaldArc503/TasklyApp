package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.audio.PlayerColorMode
import com.ronaldcolocho.taskly.ui.theme.BricolageGrotesque

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
