package com.ronaldcolocho.taskly.ui.util

import androidx.compose.ui.graphics.Color

private val AvatarPalette = listOf(
    Color(0xFF6366F1), // Indigo-500
    Color(0xFF0EA5E9), // Sky-500
    Color(0xFF10B981), // Emerald-500
    Color(0xFFF59E0B), // Amber-500
    Color(0xFFF43F5E), // Rose-500
    Color(0xFF8B5CF6), // Violet-500
    Color(0xFF14B8A6), // Teal-500
    Color(0xFFD946EF)  // Fuchsia-500
)

private fun hashOf(value: String): Int {
    var h = 0
    for (c in value) {
        h = (h * 31 + c.code) and 0x7fffffff
    }
    return h
}

fun avatarColor(uid: String): Color = AvatarPalette[hashOf(uid) % AvatarPalette.size]

fun authorTextColor(uid: String): Color = AvatarPalette[hashOf(uid) % AvatarPalette.size]

fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
