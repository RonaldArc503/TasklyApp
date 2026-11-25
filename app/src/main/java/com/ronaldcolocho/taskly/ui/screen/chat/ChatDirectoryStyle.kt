package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.util.formatDayLabel
import com.ronaldcolocho.taskly.domain.util.formatTime
import com.ronaldcolocho.taskly.domain.util.sameDay
import com.ronaldcolocho.taskly.ui.theme.*
import com.ronaldcolocho.taskly.ui.util.initialsOf

internal fun Modifier.chatDirectoryBackground() = background(ChatBackground).drawWithCache {
    val glow = Brush.radialGradient(
        listOf(Color(0xFF1A1D34), Color.Transparent),
        center = Offset(size.width * .36f, 0f),
        radius = size.width.coerceAtLeast(1f) * 1.3f
    )
    onDrawBehind { drawRect(glow) }
}

@Composable
internal fun <T> ChatDirectoryFilters(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val active = selected == option
            Box(
                Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (active) ChatAccent else ChatSurfaceRaised)
                    .then(if (active) Modifier else Modifier.border(1.dp, ChatDivider, CircleShape))
                    .selectable(active, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 17.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(option), color = if (active) ChatColorScheme.onPrimary else ChatText,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal fun ChatConversation.isSelfChat(uid: String) =
    !isGroup && participantIds.isNotEmpty() && participantIds.all { it == uid }

internal fun ChatConversation.chatTitle(uid: String): String = when {
    isGroup -> name?.takeIf(String::isNotBlank) ?: "Grupo"
    isSelfChat(uid) -> "Mensajes guardados"
    else -> members[participantIds.firstOrNull { it != uid }]?.displayName?.takeIf(String::isNotBlank)
        ?: name?.takeIf(String::isNotBlank) ?: "Usuario"
}

internal fun chatDirectoryTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else if (sameDay(timestamp, System.currentTimeMillis())) formatTime(timestamp)
    else formatDayLabel(timestamp)

@Composable
internal fun ChatDirectoryAvatar(conversation: ChatConversation, uid: String, size: Dp, online: Boolean = false) {
    val title = conversation.chatTitle(uid)
    val self = conversation.isSelfChat(uid)
    val peer = conversation.participantIds.firstOrNull { it != uid }
    val photo = if (self || conversation.isGroup) null else conversation.members[peer]?.photoURL?.takeIf(String::isNotBlank)
    val colors = when {
        self -> listOf(Color(0xFF07835D), Color(0xFF047458))
        conversation.isGroup -> listOf(Color(0xFF087F88), Color(0xFF235CCD))
        else -> listOf(Color(0xFF7947DB), Color(0xFF6238BE))
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(
                if (self) "MG" else if (conversation.isGroup) title.take(1).uppercase() else initialsOf(title),
                fontSize = if (size >= 50.dp) 19.sp else 15.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            photo?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        if (online && !self && !conversation.isGroup) {
            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).background(ChatGreen, CircleShape)
                .border(2.dp, ChatBackground, CircleShape))
        }
    }
}
