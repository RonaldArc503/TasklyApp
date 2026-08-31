package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = items[pagerState.currentPage].attachment.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (items.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClose, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                LightboxPage(
                    item = items[page],
                    mediaManager = mediaManager,
                    onClose = onClose
                )
            }

            if (items[pagerState.currentPage].caption.isNotBlank()) {
                Text(
                    text = items[pagerState.currentPage].caption,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun LightboxPage(
    item: LightboxItem,
    mediaManager: MediaDownloadManager,
    onClose: () -> Unit
) {
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

        ChatImage(
            att = item.attachment,
            mediaManager = mediaManager,
            modifier = Modifier
                .width(fitW)
                .height(fitH)
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
