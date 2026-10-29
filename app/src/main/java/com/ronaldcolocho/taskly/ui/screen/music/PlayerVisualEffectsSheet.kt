package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerVisualEffectsSheet(
    currentEffect: PlayerVisualEffect,
    onSelectEffect: (PlayerVisualEffect) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color = Color(0xFF70A896), // Marea Mint
    currentPaletteName: String = "",
    activePalettePrimary: Color? = null,
    onCyclePalette: (() -> Unit)? = null,
    onResetPalette: (() -> Unit)? = null
) {
    val darkTheme = isSystemInDarkTheme()
    val background = if (darkTheme) MareaBackgroundDark else MareaBackgroundLight
    val surface    = if (darkTheme) MareaSurfaceDark    else MareaSurfaceLight
    val onSurface  = if (darkTheme) MareaOnSurfaceDark  else MareaOnSurfaceLight
    val secondary  = if (darkTheme) MareaSecondaryDark  else MareaSecondaryLight

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = background,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(secondary.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Efectos del reproductor",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = BricolageGrotesque,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = onSurface
                        )
                        Text(
                            text = "Estilo de animación visual del disco",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = BricolageGrotesque,
                                fontSize = 12.sp
                            ),
                            color = secondary
                        )
                    }
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

            if (currentPaletteName.isNotBlank() && onCyclePalette != null) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(activePalettePrimary ?: accentColor)
                            )
                            Column {
                                Text(
                                    text = "Color / Tema",
                                    fontSize = 11.sp,
                                    color = secondary,
                                    fontFamily = BricolageGrotesque
                                )
                                Text(
                                    text = currentPaletteName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface,
                                    fontFamily = BricolageGrotesque
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onResetPalette != null) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = onResetPalette,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Original", fontSize = 11.sp, color = secondary)
                                }
                            }
                            androidx.compose.material3.Button(
                                onClick = onCyclePalette,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = accentColor
                                )
                            ) {
                                Text("Variar", fontSize = 11.sp, color = background)
                            }
                        }
                    }
                }
            }

            // Effects List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PlayerVisualEffect.entries.toTypedArray()) { effect ->
                    val isSelected = effect == currentEffect
                    val cardBg by animateColorAsState(
                        if (isSelected) accentColor.copy(alpha = 0.14f) else surface,
                        animationSpec = tween(220),
                        label = "cardBg"
                    )
                    val borderColor by animateColorAsState(
                        if (isSelected) accentColor.copy(alpha = 0.75f) else Color.Transparent,
                        animationSpec = tween(220),
                        label = "borderColor"
                    )

                    val effectIcon: ImageVector = when (effect) {
                        PlayerVisualEffect.RANDOM -> Icons.Default.Shuffle
                        PlayerVisualEffect.MAREA -> Icons.Default.Waves
                        PlayerVisualEffect.MAREA_V2 -> Icons.Default.WaterDrop
                        PlayerVisualEffect.GALAXY -> Icons.Default.AutoAwesome
                        PlayerVisualEffect.VORTEX -> Icons.Default.Cyclone
                        PlayerVisualEffect.BLACK_HOLE -> Icons.Default.BlurCircular
                        PlayerVisualEffect.SUPERNOVA -> Icons.Default.Whatshot
                        PlayerVisualEffect.PLASMA_STORM -> Icons.Default.ElectricBolt
                        PlayerVisualEffect.PRISM_FRACTAL -> Icons.Default.Diamond
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
                            .clickable { onSelectEffect(effect) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.25f)
                                    else background.copy(alpha = 0.70f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = effectIcon,
                                contentDescription = null,
                                tint = if (isSelected) accentColor else secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = effect.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = if (isSelected) onSurface else onSurface.copy(alpha = 0.88f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = effect.subtitle,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = BricolageGrotesque,
                                    fontSize = 11.5.sp
                                ),
                                color = secondary,
                                maxLines = 1
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        // Radio indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .border(
                                    1.5.dp,
                                    if (isSelected) accentColor else secondary.copy(alpha = 0.45f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = background,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
