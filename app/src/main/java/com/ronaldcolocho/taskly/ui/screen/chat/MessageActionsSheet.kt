package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist

val REACT_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

private val Violet = Color(0xFF7C3AED)
private val Teal = Color(0xFF14B8A6)
private val Amber = Color(0xFFD97706)
private val Sky = Color(0xFF0284C7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: ChatMessage?,
    currentUserId: String,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onSave: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    playlists: List<MusicPlaylist>,
    isAddingToPlaylist: Boolean,
    playlistAddError: String?,
    onAddAudioToPlaylist: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteForMe: () -> Unit
) {
    if (message == null) return
    val isMine = message.senderId == currentUserId
    val myReaction = message.reactions[currentUserId]
    val hasAttachments = message.attachments.isNotEmpty()
    val hasValidAudio = message.attachments.any {
        it.kind == com.ronaldcolocho.taskly.domain.model.AttachmentKind.AUDIO &&
            it.publicId.isNotBlank() && it.url.isNotBlank()
    }
    val isOnlyMedia = hasAttachments && message.text.isBlank()
    var showingPlaylists by remember(message.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                REACT_EMOJIS.forEach { emoji ->
                    val isSelected = myReaction == emoji
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                onReact(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (message.text.isNotBlank()) {
                Text(
                    text = "“${message.text.trim()}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            ActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Violet) },
                text = "Responder",
                onClick = { onReply(); onDismiss() }
            )

            ActionRow(
                icon = { Icon(Icons.Filled.Forward, contentDescription = null, tint = Teal) },
                text = "Reenviar",
                onClick = { onForward(); onDismiss() }
            )

            ActionRow(
                icon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = Amber) },
                text = "Guardar en Mis guardados",
                onClick = { onSave(); onDismiss() }
            )

            ActionRow(
                icon = { Icon(Icons.Filled.PushPin, contentDescription = null, tint = if (isPinned) Amber else MaterialTheme.colorScheme.onSurfaceVariant) },
                text = if (isPinned) "Desfijar mensaje" else "Fijar mensaje",
                onClick = { onPin(); onDismiss() }
            )

            if (!isOnlyMedia) {
                ActionRow(
                    icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    text = "Copiar texto",
                    onClick = { onCopy(); onDismiss() }
                )
            }

            if (hasAttachments) {
                ActionRow(
                    icon = { Icon(Icons.Filled.Download, contentDescription = null, tint = Color(0xFF10B981)) },
                    text = "Descargar archivos",
                    onClick = { onDownload(); onDismiss() }
                )
            }

            if (hasValidAudio) {
                ActionRow(
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Violet) },
                    text = "Agregar a una lista",
                    enabled = !isAddingToPlaylist,
                    onClick = { showingPlaylists = !showingPlaylists }
                )
                if (showingPlaylists) {
                    if (playlistAddError != null) {
                        Text(
                            text = playlistAddError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 68.dp, vertical = 8.dp)
                        )
                    }
                    if (isAddingToPlaylist) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 68.dp, vertical = 8.dp)
                        )
                    }
                    if (playlists.isEmpty()) {
                        Text(
                            text = "No tienes listas creadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 68.dp, vertical = 8.dp)
                        )
                    } else {
                        playlists.forEach { playlist ->
                            ActionRow(
                                icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Teal) },
                                text = playlist.name,
                                subtitle = "${playlist.songCount} canciones",
                                enabled = !isAddingToPlaylist,
                                onClick = {
                                    onAddAudioToPlaylist(playlist.id)
                                }
                            )
                        }
                    }
                }
            }

            if (isMine && !isOnlyMedia) {
                ActionRow(
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = Sky) },
                    text = "Editar mensaje",
                    onClick = { onEdit(); onDismiss() }
                )
            }

            ActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                text = "Eliminar para mí",
                subtitle = "Solo se borrará en tu dispositivo",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { onDeleteForMe(); onDismiss() }
            )

            ActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                text = "Eliminar para todos",
                subtitle = "Se borrará para todos los miembros del chat",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { onDelete(); onDismiss() }
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
fun ActionRow(
    icon: @Composable () -> Unit,
    text: String,
    subtitle: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = textColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
