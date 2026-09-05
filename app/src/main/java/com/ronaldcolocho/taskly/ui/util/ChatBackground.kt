package com.ronaldcolocho.taskly.ui.util

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ChatBackgroundColor = Color(0xFF1E293B)
private val ChatDotColor = Color(0xFF94A3B8).copy(alpha = 0.09f)

/**
 * Fondo del área de mensajes con patrón de puntos (paridad con `.chat-bg` de la web).
 * `drawWithCache` calcula el patrón una sola vez por tamaño y lo reutiliza en cada
 * frame, evitando volver a pintar ~600-900 círculos en cada redibujo/scroll.
 */
fun Modifier.chatBackground(): Modifier = this
    .background(ChatBackgroundColor)
    .drawWithCache {
        val step = 18.dp.toPx()
        val dotRadius = 1.dp.toPx()
        val bounds = size
        val dots = buildList {
            var x = step / 2
            while (x < bounds.width) {
                var y = step / 2
                while (y < bounds.height) {
                    add(Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        onDrawBehind {
            dots.forEach { drawCircle(color = ChatDotColor, radius = dotRadius, center = it) }
        }
    }
