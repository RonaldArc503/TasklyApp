package com.ronaldcolocho.taskly.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.ui.theme.*

@Composable
internal fun ChatDirectoryBottomBar(onSelect: (Route) -> Unit) {
    val items = listOf(
        Triple(Route.Home, "Inicio", Icons.Outlined.Home),
        Triple(Route.Tasks, "Tareas", Icons.AutoMirrored.Outlined.List),
        Triple(Route.ChatList, "Chat", Icons.Outlined.ChatBubbleOutline),
        Triple(Route.Reminders, "Avisos", Icons.Outlined.Notifications),
        Triple(Route.Profile, "Perfil", Icons.Outlined.PersonOutline)
    )
    Column(Modifier.fillMaxWidth().background(ChatSurface).navigationBarsPadding()) {
        HorizontalDivider(color = ChatBarBorder)
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { (route, label, icon) ->
                val selected = route == Route.ChatList
                val tint = if (selected) ChatAccent else ChatMuted
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (selected) ChatAccentContainer else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = { onSelect(route) })
                        .padding(horizontal = 2.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = tint)
                    Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
