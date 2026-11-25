package com.ronaldcolocho.taskly.ui.util

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.ronaldcolocho.taskly.ui.theme.ChatBackground

fun Modifier.chatBackground(): Modifier = background(ChatBackground)

internal fun Modifier.chatConversationBackground(): Modifier = chatBackground().drawWithCache {
    val glow = Brush.radialGradient(
        colors = listOf(Color(0xFF1C1E37), Color.Transparent),
        center = Offset(size.width / 2, 0f),
        radius = size.width.coerceAtLeast(1f) * 1.35f
    )
    val spacing = 22.dp.toPx()
    val radius = 0.85.dp.toPx()
    val dots = Path().apply {
        var y = spacing / 2
        while (y < size.height) {
            var x = spacing / 2
            while (x < size.width) {
                addOval(Rect(Offset(x, y), radius))
                x += spacing
            }
            y += spacing
        }
    }
    onDrawBehind {
        drawRect(glow)
        drawPath(dots, Color.White.copy(alpha = 0.035f))
    }
}
