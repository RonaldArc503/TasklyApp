package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.util.attachmentMediaUrl
import com.ronaldcolocho.taskly.media.MediaDownloadManager

/**
 * Imagen de chat con descarga automática inteligente y prioridad local.
 *
 * - Si el archivo ya existe en `Taskly/Images/`, se muestra localmente (offline).
 * - Si no existe, se descarga automáticamente al hacerse visible (single-flight).
 * - Mientras se descarga muestra un indicador de progreso.
 */
@Composable
fun ChatImage(
    att: ChatAttachment,
    mediaManager: MediaDownloadManager,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val mediaId = att.publicId
    val downloadUrl = attachmentMediaUrl(att.url, "w_700,f_auto,q_auto")
    val mediaStates by mediaManager.states.collectAsState()

    val state = mediaStates[mediaId]
        ?: remember(mediaId) {
            if (mediaManager.fileFor(mediaId, MediaKind.IMAGE) != null) MediaDownloadState.Downloaded
            else MediaDownloadState.NotDownloaded
        }

    LaunchedEffect(mediaId) {
        mediaManager.ensureDownloaded(mediaId, downloadUrl, MediaKind.IMAGE)
    }

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is MediaDownloadState.Downloaded -> {
                val file = mediaManager.fileFor(mediaId, MediaKind.IMAGE)
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ImagePlaceholder(progress = 1f)
                }
            }
            is MediaDownloadState.Downloading -> ImagePlaceholder(progress = state.progress)
            is MediaDownloadState.NotDownloaded -> ImagePlaceholder(progress = 0f)
            is MediaDownloadState.Error -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Error al descargar",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ImagePlaceholder(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A).copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0.05f, 1f) },
            modifier = Modifier.size(28.dp),
            color = Color.White,
            strokeWidth = 3.dp
        )
    }
}
