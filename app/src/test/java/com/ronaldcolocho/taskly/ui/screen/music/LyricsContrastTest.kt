package com.ronaldcolocho.taskly.ui.screen.music

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class LyricsContrastTest {
    @Test fun brightAndDarkPalettesKeepTheActiveLineReadable() {
        for ((accent, background, foreground) in listOf(
            Triple(Color(0xFF1DDDE2), Color(0xFFEAF1F5), Color(0xFF102B35)),
            Triple(Color(0xFF3B187E), Color(0xFF0E1721), Color(0xFFF2F5F8))
        )) {
            val selected = lyricsTextAccent(accent, background, foreground)
            val values = listOf(selected.luminance(), background.luminance()).sorted()
            assertTrue((values[1] + 0.05f) / (values[0] + 0.05f) >= 4.5f)
        }
    }
}
