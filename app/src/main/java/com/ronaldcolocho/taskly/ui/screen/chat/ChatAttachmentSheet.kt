package com.ronaldcolocho.taskly.ui.screen.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.ui.theme.ChatAccent
import com.ronaldcolocho.taskly.ui.theme.ChatActionGradient
import com.ronaldcolocho.taskly.ui.theme.ChatDivider
import com.ronaldcolocho.taskly.ui.theme.ChatMuted
import com.ronaldcolocho.taskly.ui.theme.ChatSurfaceRaised
import com.ronaldcolocho.taskly.ui.theme.ChatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatAttachmentSheet(
    choices: List<AttachmentDraft>,
    drafts: List<AttachmentDraft>,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onAudio: () -> Unit,
    onToggle: (AttachmentDraft) -> Unit,
    onSend: () -> Unit,
    recording: Boolean,
    recordingSeconds: Int,
    voiceEnabled: Boolean,
    onVoiceStart: () -> Boolean,
    onVoiceFinish: () -> Unit,
    onVoiceCancel: () -> Unit
) {
    val uploading = drafts.any { it.status == DraftStatus.UPLOADING }
    val selection = remember(drafts) { drafts.mapIndexed { index, draft -> draft.uri to index + 1 }.toMap() }
    val onAccent = MaterialTheme.colorScheme.onPrimary
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFF171C25),
        contentColor = ChatText,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A4558), width = 44.dp, height = 4.dp) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Compartir", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                TextButton(onClick = onGallery, enabled = !recording && !uploading) {
                    Text("Ver galería", color = ChatAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item("camera") {
                    Column(
                        Modifier.size(width = 96.dp, height = 112.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(listOf(ChatAccent, Color(0xFF6F78FF))))
                            .clickable(enabled = !recording && !uploading, role = Role.Button, onClick = onCamera),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        val onCameraGradient = Color(0xFF0E1217)
                        Icon(Icons.Outlined.PhotoCamera, null, tint = onCameraGradient, modifier = Modifier.size(25.dp))
                        Text("Cámara", color = onCameraGradient, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(choices, key = { it.uri.toString() }) { draft ->
                    SharePreview(draft, selection[draft.uri], enabled = !recording && !uploading) { onToggle(draft) }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val enabled = !recording && !uploading
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareAction("Fotos y videos", "Galería", Icons.Outlined.Photo, ChatAccent, Modifier.weight(1f), enabled, onGallery)
                    ShareAction("Archivos", "PDF y documentos", Icons.Outlined.Description, Color(0xFFBAA8FF), Modifier.weight(1f), enabled, onFiles)
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareAction("Audios", "Música y notas", Icons.Outlined.MusicNote, Color(0xFFFFC64A), Modifier.weight(1f), enabled, onAudio)
                    ShareAction("Crear tarea", "Próximamente", Icons.Outlined.CheckCircle, ChatMuted, Modifier.weight(1f), false, {})
                }
                ShareVoice(
                    recording, recordingSeconds, voiceEnabled && !uploading,
                    onVoiceStart, onVoiceFinish, onVoiceCancel
                )
                if (drafts.isNotEmpty()) {
                    val canSend = !recording && !uploading
                    val buttonShape = RoundedCornerShape(26.dp)
                    val count = drafts.size
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(buttonShape)
                            .background(ChatActionGradient, alpha = if (canSend) 1f else 0.45f)
                            .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (count == 1) "Enviar 1 elemento" else "Enviar $count elementos",
                            color = onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharePreview(draft: AttachmentDraft, index: Int?, enabled: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val video = draft.kind == AttachmentKind.VIDEO
    val videoMetadata by produceState<Pair<Bitmap?, Long>?>(null, draft.uri, video) {
        value = if (!video) null else withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, draft.uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val frame = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 280, 280)
                        } else {
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }.getOrNull()
                    frame to duration
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(width = 96.dp, height = 112.dp).clip(shape).background(ChatSurfaceRaised)
            .border(if (index == null) 1.dp else 2.dp, if (index == null) ChatDivider else ChatAccent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (index == null) "Seleccionar" else "Quitar de la selección", onClick = onToggle)
            .semantics { selected = index != null }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val image = if (video) videoMetadata?.first else draft.previewUri
        if (image != null) {
            AsyncImage(
                image, draft.fileName, Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            val icon = when (draft.kind) {
                AttachmentKind.IMAGE -> Icons.Outlined.Photo
                AttachmentKind.VIDEO -> Icons.Outlined.Videocam
                AttachmentKind.AUDIO -> Icons.Outlined.MusicNote
                else -> Icons.Outlined.Description
            }
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = ChatAccent)
                Text(draft.fileName, fontSize = 11.sp, color = ChatText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (video) {
            videoMetadata?.second?.takeIf { it > 0L }?.let { duration ->
                Text(
                    formatDuration(duration),
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color(0xD910141D), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (index != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).background(ChatAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(index.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShareAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier.fillMaxHeight().heightIn(min = 72.dp).clip(shape)
            .background(ChatSurfaceRaised).border(1.dp, ChatDivider, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) ChatText else ChatMuted, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ChatMuted, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ShareVoice(
    recording: Boolean,
    seconds: Int,
    enabled: Boolean,
    onStart: () -> Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    val cancel by rememberUpdatedState(onCancel)
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(ChatSurfaceRaised).border(1.dp, ChatDivider, shape)
            .then(if (enabled) Modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(if (recording) "Detener y enviar nota de voz" else "Iniciar grabación") {
                    if (recording) finish() else start()
                    true
                }
            }.pointerInput(Unit) {
                var active = false
                var drift = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        drift = 0f
                        active = start()
                    },
                    onDrag = { _, amount ->
                        drift += amount.x
                        if (active && drift < -64.dp.toPx()) {
                            active = false
                            cancel()
                        }
                    },
                    onDragEnd = {
                        if (active) finish()
                        active = false
                    },
                    onDragCancel = {
                        if (active) cancel()
                        active = false
                    }
                )
            } else Modifier)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(44.dp).background(ChatActionGradient, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(if (recording) "Grabando · ${formatDuration(seconds * 1000L)}" else "Mantén para grabar un audio",
                color = if (enabled) ChatText else ChatMuted, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Desliza para cancelar", color = ChatMuted, fontSize = 11.sp)
        }
        Canvas(Modifier.width(72.dp).height(18.dp)) {
            val step = size.width / 16f
            repeat(16) { index ->
                val x = step * (index + 0.5f)
                val half = (3 + index * 7 % 6).dp.toPx() / 2
                drawLine(
                    ChatAccent.copy(alpha = if (recording) 1f else 0.42f + index * 0.035f),
                    Offset(x, center.y - half), Offset(x, center.y + half),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
    }
}
