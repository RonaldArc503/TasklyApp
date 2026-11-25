package com.ronaldcolocho.taskly.ui.screen.chat

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDayLabel
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.domain.util.formatTime
import com.ronaldcolocho.taskly.ui.theme.*
import com.ronaldcolocho.taskly.util.AttachmentActions
import kotlin.math.roundToInt

internal val ViewerBackground = Color(0xFF090B0E)

@Composable
internal fun MediaViewerDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalView.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        MaterialTheme(colorScheme = ChatColorScheme, typography = ChatTypography) {
            val dialogView = LocalView.current
            var safeArea by remember(host) { mutableStateOf(host.mediaSafeArea()) }
            var viewport by remember { mutableStateOf<IntRect?>(null) }
            DisposableEffect(dialogView, host) {
                val observer = dialogView.viewTreeObserver
                val listener = ViewTreeObserver.OnGlobalLayoutListener { safeArea = host.mediaSafeArea() }
                observer.addOnGlobalLayoutListener(listener)
                onDispose {
                    if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                }
            }
            val bounds = viewport
            val safe = safeArea
            // Some dialog windows already start below the status bar but still extend under navigation.
            val padding = if (bounds != null && safe != null) with(LocalDensity.current) {
                Modifier.absolutePadding(
                    left = (safe.left - bounds.left).coerceIn(0, bounds.width).toDp(),
                    top = (safe.top - bounds.top).coerceIn(0, bounds.height).toDp(),
                    right = (bounds.right - safe.right).coerceIn(0, bounds.width).toDp(),
                    bottom = (bounds.bottom - safe.bottom).coerceIn(0, bounds.height).toDp()
                )
            } else Modifier.safeDrawingPadding()
            Box(
                Modifier.fillMaxSize().background(ViewerBackground).onGloballyPositioned { coordinates ->
                    val position = coordinates.positionOnScreen()
                    if (position.x.isFinite() && position.y.isFinite()) {
                        val x = position.x.roundToInt()
                        val y = position.y.roundToInt()
                        viewport = IntRect(x, y, x + coordinates.size.width, y + coordinates.size.height)
                    }
                }
            ) {
                Column(Modifier.fillMaxSize().then(padding), content = content)
            }
        }
    }
}

private fun View.mediaSafeArea(): IntRect? {
    val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics ?: return null
        val bounds = metrics.bounds
        val bars = WindowInsetsCompat.toWindowInsetsCompat(metrics.windowInsets).getInsetsIgnoringVisibility(types)
        return IntRect(bounds.left + bars.left, bounds.top + bars.top, bounds.right - bars.right, bounds.bottom - bars.bottom)
    }
    val root = rootView
    val bars = ViewCompat.getRootWindowInsets(root)?.getInsetsIgnoringVisibility(types) ?: return null
    val position = IntArray(2)
    root.getLocationOnScreen(position)
    return IntRect(
        position[0] + bars.left,
        position[1] + bars.top,
        position[0] + root.width - bars.right,
        position[1] + root.height - bars.bottom
    )
}

internal data class MediaViewerActions(
    val reply: ((LightboxItem) -> Unit)? = null,
    val forward: ((LightboxItem) -> Unit)? = null,
    val saveMessage: ((LightboxItem) -> Unit)? = null,
    val delete: ((LightboxItem) -> Unit)? = null,
    val jumpToMessage: ((LightboxItem) -> Unit)? = null
)

@Composable
internal fun MediaViewerHeader(item: LightboxItem, actions: MediaViewerActions, onClose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().background(ViewerBackground).heightIn(min = 58.dp).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Volver", tint = ChatText)
        }
        Column(Modifier.weight(1f)) {
            Text(item.author ?: item.attachment.name, color = ChatText, fontSize = 17.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.createdAt?.takeIf { it > 0L }?.let {
                Text("${formatDayLabel(it)}, ${formatTime(it)}", color = ChatMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (item.message != null) actions.saveMessage?.let { save ->
            IconButton(onClick = { save(item) }) { Icon(Icons.Outlined.StarBorder, "Guardar mensaje", tint = ChatText) }
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Opciones del archivo", tint = ChatText) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Copiar enlace") }, onClick = {
                    open = false
                    AttachmentActions.copyText(context, item.attachment.url)
                })
                if (item.message != null) actions.jumpToMessage?.let { jump ->
                    DropdownMenuItem(text = { Text("Ir al mensaje") }, onClick = { open = false; jump(item) })
                }
            }
        }
    }
}

@Composable
internal fun MediaViewerFooter(item: LightboxItem, actions: MediaViewerActions) {
    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }
    HorizontalDivider(color = ChatBarBorder)
    Row(Modifier.fillMaxWidth().background(Color(0xFF0E1217)).padding(horizontal = 4.dp)) {
        if (item.message != null) {
            actions.reply?.let { reply -> ViewerAction("Responder", Icons.AutoMirrored.Outlined.Reply, Modifier.weight(1f)) { reply(item) } }
            actions.forward?.let { forward -> ViewerAction("Reenviar", Icons.AutoMirrored.Outlined.Forward, Modifier.weight(1f)) { forward(item) } }
        }
        ViewerAction("Guardar", Icons.Outlined.FileDownload, Modifier.weight(1f)) {
            AttachmentActions.download(context, item.attachment.url, item.attachment.name)
        }
        ViewerAction("Info", Icons.Outlined.Info, Modifier.weight(1f)) { showInfo = true }
        if (item.message != null) actions.delete?.let { delete ->
            ViewerAction("Eliminar", Icons.Outlined.Delete, Modifier.weight(1f), ChatError) { delete(item) }
        }
    }
    if (showInfo) {
        val file = item.attachment
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Información del archivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(file.name)
                    listOfNotNull(
                        file.size.takeIf { it > 0 }?.let(::formatBytes),
                        file.width?.let { width -> file.height?.let { height -> "$width × $height" } },
                        file.duration?.takeIf { it > 0 }?.let { formatDuration(it * 1000L) },
                        file.mimeType.takeIf(String::isNotBlank)
                    ).forEach { Text(it, color = ChatMuted) }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun ViewerAction(label: String, icon: ImageVector, modifier: Modifier, tint: Color = ChatText, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 58.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}
