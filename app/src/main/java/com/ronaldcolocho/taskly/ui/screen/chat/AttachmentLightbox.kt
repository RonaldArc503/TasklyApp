package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.util.attachmentMediaUrl
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class LightboxItem(
    val attachment: ChatAttachment,
    val caption: String = "",
    val author: String? = null,
    val message: ChatMessage? = null,
    val createdAt: Long? = message?.createdAt
)

@Composable
internal fun AttachmentLightbox(
    items: List<LightboxItem>,
    initialIndex: Int,
    mediaManager: MediaDownloadManager,
    actions: MediaViewerActions = MediaViewerActions(),
    onClose: () -> Unit
) {
    if (items.isEmpty()) return
    var zoomedPage by remember { mutableStateOf<Int?>(null) }
    val initialPage = initialIndex.coerceIn(0, items.lastIndex)
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
    val thumbnails = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager.settledPage, items) {
        val page = pager.settledPage
        for (index in (page - 1).coerceAtLeast(0)..(page + 1).coerceAtMost(items.lastIndex)) {
            val attachment = items[index].attachment
            mediaManager.ensureDownloaded(attachment.publicId, attachmentMediaUrl(attachment.url, "w_700,f_auto,q_auto"), MediaKind.IMAGE)
        }
        if (items.size > 1 && thumbnails.layoutInfo.visibleItemsInfo.none { it.index == page }) {
            thumbnails.scrollToItem(page)
        }
    }

    MediaViewerDialog(onClose) {
        val current = items[pager.currentPage]
        MediaViewerHeader(current, actions, onClose)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = zoomedPage == null
            ) { page ->
                LightboxPage(items[page], mediaManager, onClose, page == pager.currentPage) { zoomed ->
                    if (page == pager.currentPage) zoomedPage = if (zoomed) page else null
                }
            }
            if (items.size > 1) {
                Text(
                    "${pager.currentPage + 1} de ${items.size}",
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = .7f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = ChatText, fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (current.caption.isNotBlank()) {
            Text(current.caption, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                color = ChatText, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (items.size > 1) {
            LazyRow(
                state = thumbnails, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items.size) { index ->
                    val entry = items[index]
                    val active = index == pager.currentPage
                    val shape = RoundedCornerShape(10.dp)
                    AsyncImage(
                        model = mediaManager.fileFor(entry.attachment.publicId, MediaKind.IMAGE)
                            ?: attachmentMediaUrl(entry.attachment.url, "w_160,f_auto,q_auto"),
                        contentDescription = "Foto ${index + 1}",
                        modifier = Modifier.size(46.dp).clip(shape).background(ChatSurfaceRaised)
                            .border(2.dp, if (active) ChatAccent else Color.Transparent, shape)
                            .semantics { selected = active }.clickable {
                                zoomedPage = null
                                scope.launch { pager.scrollToPage(index) }
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        MediaViewerFooter(current, actions)
    }
}

@Composable
private fun LightboxPage(
    item: LightboxItem,
    mediaManager: MediaDownloadManager,
    onClose: () -> Unit,
    isActive: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var transform by remember(item.attachment.publicId) { mutableStateOf(PhotoTransform()) }
    val isZoomed by remember(item.attachment.publicId) { derivedStateOf { transform.scale > 1f } }
    val close by rememberUpdatedState(onClose)
    val notifyZoom by rememberUpdatedState(onZoomChanged)
    fun updateTransform(next: PhotoTransform) {
        val wasZoomed = transform.scale > 1f
        transform = next
        if (wasZoomed != (next.scale > 1f)) notifyZoom(next.scale > 1f)
    }

    LaunchedEffect(isActive) {
        if (isActive) notifyZoom(transform.scale > 1f)
        else transform = PhotoTransform()
    }

    val ratio = item.attachment.width?.takeIf { it > 0 }?.let { w ->
        item.attachment.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h.toFloat() }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
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

        val imageSize = with(LocalDensity.current) { Size(fitW.toPx(), fitH.toPx()) }
        val mediaKey = item.attachment.publicId
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .semantics {
                    contentDescription = "Foto"
                    stateDescription = if (isZoomed) "Ampliada" else "Tamaño original"
                }
                .pointerInput(mediaKey, imageSize, isActive) {
                    if (!isActive) return@pointerInput
                    fun insidePhoto(point: Offset) = Rect(
                        offset = size.toSize().center - imageSize.center,
                        size = imageSize
                    ).contains(point)
                    detectTapGestures(
                        onTap = { point -> if (transform.scale == 1f && !insidePhoto(point)) close() },
                        onDoubleTap = { point ->
                            if (transform.scale > 1f) updateTransform(PhotoTransform())
                            else if (insidePhoto(point)) {
                                updateTransform(transform.adjust(2.5f, Offset.Zero, point, size.toSize(), imageSize))
                            }
                        }
                    )
                }
                .pointerInput(mediaKey, imageSize, isActive) {
                    if (!isActive) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var claimed = false
                        var pendingZoom = 1f
                        var pendingPan = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pinching = event.changes.count { it.pressed && it.previousPressed } >= 2
                            if (!claimed && !pinching && transform.scale == 1f) continue

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!claimed) {
                                pendingZoom *= zoom
                                pendingPan += pan
                                val zoomMotion = abs(1f - pendingZoom) * event.calculateCentroidSize(useCurrent = false)
                                val slop = viewConfiguration.touchSlop * if (pinching) 0.5f else 1f
                                if (maxOf(zoomMotion, pendingPan.getDistance()) < slop) continue
                                claimed = true
                            } else {
                                pendingZoom = zoom
                                pendingPan = pan
                            }
                            val focus = event.calculateCentroid(useCurrent = false)
                            if (focus != Offset.Unspecified) {
                                updateTransform(transform.adjust(pendingZoom, pendingPan, focus, size.toSize(), imageSize))
                            }
                            event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ChatImage(
                att = item.attachment,
                mediaManager = mediaManager,
                modifier = Modifier
                    .size(fitW, fitH)
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y
                    },
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(0.dp)
            )
        }
    }
}

private data class PhotoTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun adjust(zoom: Float, pan: Offset, focus: Offset, viewport: Size, image: Size): PhotoTransform {
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        if (nextScale == 1f) return PhotoTransform()
        val ratio = nextScale / scale
        val moved = offset * ratio + (focus - viewport.center) * (1f - ratio) + pan
        val limitX = ((image.width * nextScale - viewport.width) / 2f).coerceAtLeast(0f)
        val limitY = ((image.height * nextScale - viewport.height) / 2f).coerceAtLeast(0f)
        return PhotoTransform(nextScale, Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY)))
    }
}
