package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.ui.theme.*

@Composable
internal fun ChatOptionsMenu(
    conversation: ChatConversation,
    onSearch: () -> Unit,
    onMute: () -> Unit,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onMedia: () -> Unit,
    onClear: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    fun act(action: () -> Unit) {
        expanded = false
        action()
    }
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(44.dp).background(if (expanded) ChatAccentContainer else Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.MoreVert, "Opciones del chat", tint = if (expanded) ChatAccent else ChatText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(308.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF171C25),
            border = androidx.compose.foundation.BorderStroke(1.dp, ChatDivider)
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChatAction("Buscar", Icons.Outlined.Search, Modifier.weight(1f)) { act(onSearch) }
                QuickChatAction(
                    if (conversation.isMuted) "Activar sonido" else "Silenciar",
                    if (conversation.isMuted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                    Modifier.weight(1f)
                ) { act(onMute) }
                QuickChatAction(if (conversation.isPinned) "Desfijar" else "Fijar", Icons.Outlined.PushPin, Modifier.weight(1f)) { act(onPin) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = ChatDivider)
            ChatOption(
                if (conversation.isGroup) "Info. del grupo" else "Info. del chat",
                if (conversation.isGroup) "${conversation.participantIds.size} miembros" else null,
                if (conversation.isGroup) Icons.Outlined.People else Icons.Outlined.Info
            ) { act(onInfo) }
            ChatOption("Multimedia y archivos", "Fotos, audios, enlaces", Icons.Outlined.PhotoLibrary) { act(onMedia) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = ChatDivider)
            ChatOption("Vaciar para mí", null, Icons.Outlined.Delete, ChatError) { act { onClear(false) } }
            ChatOption("Vaciar para todos", null, Icons.Outlined.Delete, ChatError) { act { onClear(true) } }
            if (conversation.isGroup) ChatOption("Salir del grupo", null, Icons.Outlined.ExitToApp, ChatError) { act(onLeave) }
        }
    }
}

@Composable
private fun QuickChatAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 68.dp).clip(shape).background(Color(0xFF1F2733)).border(1.dp, ChatDivider, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, null, tint = ChatAccent, modifier = Modifier.size(21.dp))
        Text(label, color = ChatText, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChatOption(label: String, subtitle: String?, icon: ImageVector, color: Color = ChatText, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, color = ChatMuted, fontSize = 12.sp) }
            }
        },
        leadingIcon = {
            Box(
                Modifier.size(36.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (color == ChatText) ChatMuted else color, modifier = Modifier.size(20.dp))
            }
        },
        onClick = onClick
    )
}
