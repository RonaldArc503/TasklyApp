package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.util.attachmentMediaUrl
import com.ronaldcolocho.taskly.media.MediaDownloadManager

data class LightboxItem(
    val attachment: ChatAttachment,
    val caption: String = ""
)

@Composable
fun AttachmentLightbox(
    items: List<LightboxItem>,
    initialIndex: Int,
    mediaManager: MediaDownloadManager,
    onClose: () -> Unit
) {
    if (items.isEmpty()) return
    var zoomedItemId by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size }
    )

    // Prefetch controlado de la imagen actual y sus vecinas
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        listOf(page - 1, page, page + 1).forEach { p ->
            if (p in items.indices) {
                val att = items[p].attachment
                mediaManager.ensureDownloaded(
                    att.publicId,
                    attachmentMediaUrl(att.url, "w_700,f_auto,q_auto"),
                    MediaKind.IMAGE
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // When zoomed, drag moves the photo instead of changing pages.
                userScrollEnabled = zoomedItemId == null
            ) { page ->
                LightboxPage(
                    item = items[page],
                    mediaManager = mediaManager,
                    onClose = onClose,
                    onZoomChanged = { itemId, isZoomed ->
                        zoomedItemId = when {
                            isZoomed -> itemId
                            zoomedItemId == itemId -> null
                            else -> zoomedItemId
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (items.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            if (items[pagerState.currentPage].caption.isNotBlank()) {
                Text(
                    text = items[pagerState.currentPage].caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LightboxPage(
    item: LightboxItem,
    mediaManager: MediaDownloadManager,
    onClose: () -> Unit,
    onZoomChanged: (itemId: String, isZoomed: Boolean) -> Unit
) {
    var scale by remember(item.attachment.publicId) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.attachment.publicId) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.attachment.publicId) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        if (nextScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    LaunchedEffect(scale) {
        onZoomChanged(item.attachment.publicId, scale > 1f)
    }

    val ratio = item.attachment.width?.takeIf { it > 0 }?.let { w ->
        item.attachment.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h.toFloat() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        val padding = 24.dp
        val availW = (maxWidth - padding).coerceAtLeast(1.dp)
        val availH = (maxHeight - padding).coerceAtLeast(1.dp)

        val fitW: Dp
        val fitH: Dp
        if (ratio != null && ratio > 0f) {
            if (availW / availH > ratio) {
                fitH = availH
                fitW = availH * ratio
            } else {
                fitW = availW
                fitH = availW / ratio
            }
        } else {
            fitW = availW
            fitH = availH
        }

        Box(
            modifier = Modifier
                .width(fitW)
                .height(fitH)
                .clipToBounds()
        ) {
            ChatImage(
                att = item.attachment,
                mediaManager = mediaManager,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(transformState)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(0.dp)
            )
        }
    }
}
