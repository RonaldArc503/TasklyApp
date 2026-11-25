package com.ronaldcolocho.taskly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette shared by conversations, messages, threads, search and chat details.
val ChatBackground = Color(0xFF0E1217)
val ChatSurface = Color(0xFF141A22)
val ChatSurfaceRaised = Color(0xFF1A212B)
val ChatDivider = Color(0xFF252D3A)
val ChatText = Color(0xFFEEF2F8)
val ChatMuted = Color(0xFF8F9BB0)
val ChatAccent = Color(0xFF3DD6F5)
internal val ChatBarBorder = Color(0xFF222A36)
internal val ChatBarSurface = ChatSurface.copy(alpha = 0.96f)
internal val ChatActionGradient = Brush.linearGradient(listOf(ChatAccent, Color(0xFF6EA8FF)))
internal val ChatAvatarGradient = Brush.linearGradient(listOf(Color(0xFF7773EA), Color(0xFF9269F9)))
val ChatAccentContainer = Color(0xFF123845)
val ChatOutgoing = Color(0xFF182C39)
val ChatOutgoingBorder = Color(0xFF253F51)
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
