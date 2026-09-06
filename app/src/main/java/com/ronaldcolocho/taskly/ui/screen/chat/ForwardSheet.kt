package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import com.ronaldcolocho.taskly.ui.util.avatarColor
import com.ronaldcolocho.taskly.ui.util.initialsOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    conversations: List<ChatConversation>,
    currentUserId: String,
    currentConvId: String,
    previewText: String,
    attachmentCount: Int,
    onDismiss: () -> Unit,
    onSend: (List<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val targets = remember(conversations, currentConvId, query) {
        val normalized = ChatSearchNormalizer.normalize(query)
        conversations.filter { conversation ->
            conversation.id != currentConvId && (
                normalized.isBlank() || forwardSearchValues(conversation).any {
                    ChatSearchNormalizer.matches(it, normalized)
                }
            )
        }
    }
    val selected = remember { mutableStateListOf<String>() }
    var sending by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reenviar a…",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = previewText.ifBlank { if (attachmentCount > 0) "📎 $attachmentCount adjunto(s)" else "" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Buscar conversacion") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar busqueda")
                        }
                    }
                }
            )

            if (targets.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No hay otros chats para reenviar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                    items(targets, key = { it.id }) { conv ->
                        val isGroup = conv.isGroup
                        val isSelf = !isGroup && (conv.participantIds.size == 1 ||
                                (conv.participantIds.size == 2 && conv.participantIds.all { it == currentUserId }))
                        val otherId = if (isSelf) currentUserId
                        else conv.participantIds.firstOrNull { it != currentUserId } ?: currentUserId
                        val name = when {
                            isGroup -> conv.name?.takeIf { it.isNotBlank() } ?: "Grupo"
                            isSelf -> "Mensajes guardados"
                            else -> conv.members[otherId]?.displayName ?: "Usuario"
                        }
                        val photoUrl = if (isGroup || isSelf) null
                        else conv.members[otherId]?.photoURL?.takeIf { it.isNotBlank() }
                        val isSelected = conv.id in selected

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    if (isSelected) selected.remove(conv.id) else selected.add(conv.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp)) {
                                if (photoUrl != null) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(36.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(avatarColor(otherId)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(initialsOf(name), color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { sending = true; onSend(selected.toList()) },
                    enabled = selected.isNotEmpty() && !sending,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (sending) "Enviando…" else "Reenviar a (${selected.size})", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun forwardSearchValues(conversation: ChatConversation): List<String> = buildList {
    conversation.name?.let(::add)
    conversation.members.values.forEach { member ->
        add(member.displayName)
        add(member.phone)
    }
}
