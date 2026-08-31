package com.ronaldcolocho.taskly.ui.util

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ChatBackgroundColor = Color(0xFF1E293B)
private val ChatDotColor = Color(0xFF94A3B8).copy(alpha = 0.09f)

/**
 * Fondo del área de mensajes con patrón de puntos (paridad con `.chat-bg` de la web).
 */
fun Modifier.chatBackground(): Modifier = this
    .background(ChatBackgroundColor)
    .drawBehind {
        val step = 18.dp.toPx()
        val dotRadius = 1.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            var y = step / 2
            while (y < size.height) {
                drawCircle(color = ChatDotColor, radius = dotRadius, center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
