package com.ronaldcolocho.taskly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Palette shared by conversations, messages, threads, search and chat details.
val ChatBackground = Color(0xFF1A1D21)
val ChatSurface = Color(0xFF222529)
val ChatSurfaceRaised = Color(0xFF2B2D31)
val ChatDivider = Color(0xFF3A3D42)
val ChatText = Color(0xFFE8E8E8)
val ChatMuted = Color(0xFFADAFB2)
val ChatAccent = Color(0xFF36C5F0)
val ChatAccentContainer = Color(0xFF123845)
val ChatOutgoing = Color(0xFF254A56)
val ChatOutgoingBorder = Color(0xFF326575)
val ChatGreen = Color(0xFF2BAC76)
val ChatError = Color(0xFFF97066)

val ChatColorScheme = darkColorScheme(
    primary = ChatAccent,
    onPrimary = Color(0xFF102B35),
    primaryContainer = ChatAccentContainer,
    onPrimaryContainer = Color(0xFFB9EBFA),
    secondary = ChatGreen,
    onSecondary = Color(0xFF081F16),
    tertiary = Color(0xFFF2C744),
    onTertiary = Color(0xFF2A2107),
    background = ChatBackground,
    onBackground = ChatText,
    surface = ChatSurface,
    onSurface = ChatText,
    surfaceVariant = ChatSurfaceRaised,
    onSurfaceVariant = ChatMuted,
    outline = ChatDivider,
    outlineVariant = ChatDivider,
    error = ChatError,
    onError = Color(0xFF2A0B09)
)
