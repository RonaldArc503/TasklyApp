package com.ronaldcolocho.taskly.ui.screen.saved

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.screen.chat.AttachmentLightbox
import com.ronaldcolocho.taskly.ui.screen.chat.LightboxItem
import com.ronaldcolocho.taskly.ui.screen.chat.rememberMediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.*
import com.ronaldcolocho.taskly.util.AttachmentActions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    viewModel: SavedViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<SavedItem?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val mediaManager = rememberMediaDownloadManager()
    var lightboxItems by remember { mutableStateOf<List<LightboxItem>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    fun openOriginal(item: SavedItem) {
        onNavigateToChat(item.convId, item.sourceMessageId.takeIf { it.isNotBlank() })
    }

    fun openAttachment(item: SavedItem, attachment: ChatAttachment) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> lightboxItems = listOf(LightboxItem(attachment, item.text))
            AttachmentKind.PDF, AttachmentKind.DOC, AttachmentKind.FILE -> scope.launch {
                val local = mediaManager.fileFor(attachment.publicId, MediaKind.DOCUMENT)
                    ?: mediaManager.awaitLocalFile(attachment.publicId, attachment.url, MediaKind.DOCUMENT)
                if (local != null) AttachmentActions.openLocalFile(context, local, attachment.mimeType)
                else AttachmentActions.openExternal(context, attachment.url)
            }
            else -> openOriginal(item)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val state = uiState) {
            is SavedUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SavedUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Header
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mis guardados", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Ordenar guardados")
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    SavedSort.entries.forEach { sort ->
                                        DropdownMenuItem(
                                            text = { Text(sort.label) },
                                            onClick = { viewModel.setSort(sort); showSortMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Mensajes y links que guardaste, con los más importantes fijados al inicio.",
                            fontSize = 14.sp,
                            color = Slate500,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        placeholder = { Text("Buscar texto, archivo, enlace, chat o remitente") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar busqueda")
                            }
                        }
                    )

                    // Filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.filter == "all",
                            onClick = { viewModel.setFilter("all") },
                            label = { Text("Todos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Indigo600,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = CircleShape
                        )
                        FilterChip(
                            selected = state.filter == "messages",
                            onClick = { viewModel.setFilter("messages") },
                            label = { Text("Mensajes") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Indigo600,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = CircleShape
                        )
                        FilterChip(
                            selected = state.filter == "links",
                            onClick = { viewModel.setFilter("links") },
                            label = { Text("Links") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Indigo600,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = CircleShape
                        )
                        FilterChip(
                            selected = state.filter == "pinned",
                            onClick = { viewModel.setFilter("pinned") },
                            label = { 
                                Text(if (state.pinnedCount > 0) "Fijados (${state.pinnedCount})" else "Fijados") 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Indigo600,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = CircleShape
                        )
                    }

                    // Content
                    if (state.isEmptyTotal) {
                        EmptyTotalState()
                    } else if (state.items.isEmpty()) {
                        EmptyFilterState()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                SavedCard(
                                item = item,
                                onPin = { pinned -> viewModel.pinItem(item.id, pinned) },
                                onMenu = { selectedItem = item },
                                onClick = { openOriginal(item) },
                                onOpenAttachment = { attachment -> openAttachment(item, attachment) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    lightboxItems?.let { items ->
        AttachmentLightbox(
            items = items,
            initialIndex = 0,
            mediaManager = mediaManager,
            onClose = { lightboxItems = null }
        )
    }

    if (selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                if (selectedItem!!.text.isNotBlank()) {
                    Text(
                        text = "“${selectedItem!!.text.trim()}”",
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                ListItem(
                    headlineContent = { Text("Ir al mensaje") },
                    leadingContent = {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Indigo100), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Forward, contentDescription = null, tint = Indigo600, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.clickable {
                        openOriginal(selectedItem!!)
                        selectedItem = null
                    }
                )

                ListItem(
                    headlineContent = { Text("Copiar") },
                    leadingContent = {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Indigo100), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Indigo600, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mensaje", selectedItem!!.text))
                        selectedItem = null
                    }
                )

                ListItem(
                    headlineContent = { Text(if (selectedItem!!.pinned) "Desfijar" else "Fijar") },
                    leadingContent = {
                        val bg = if (selectedItem!!.pinned) Color(0xFFFEF3C7) else Slate100
                        val tint = if (selectedItem!!.pinned) Color(0xFFD97706) else Slate600
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.clickable {
                        viewModel.pinItem(selectedItem!!.id, !selectedItem!!.pinned)
                        selectedItem = null
                    }
                )

                ListItem(
                    headlineContent = { Text("Eliminar", color = Red600) },
                    leadingContent = {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFEE2E2)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Red600, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.clickable {
                        viewModel.deleteItem(selectedItem!!.id)
                        selectedItem = null
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { selectedItem = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate600)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
fun EmptyTotalState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Indigo100),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Indigo600, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Aún no has guardado nada", fontWeight = FontWeight.Bold, color = Slate700)
        Text(
            "Abre un mensaje en el chat, toca el menú y elige \"Guardar en Mis guardados\".",
            fontSize = 12.sp,
            color = Slate500,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun EmptyFilterState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No hay guardados con este filtro.", fontWeight = FontWeight.Bold, color = Slate700)
    }
}

@Composable
fun SavedCard(
    item: SavedItem,
    onPin: (Boolean) -> Unit,
    onMenu: () -> Unit,
    onClick: () -> Unit,
    onOpenAttachment: (ChatAttachment) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (item.pinned) Color(0xFFFCD34D) else Slate200
    val context = LocalContext.current
    val image = remember(item.attachments) {
        item.attachments.firstOrNull { it.kind == com.ronaldcolocho.taskly.domain.model.AttachmentKind.IMAGE }
    }
    val otherAttachments = remember(item.attachments) {
        item.attachments.filter { it.kind != com.ronaldcolocho.taskly.domain.model.AttachmentKind.IMAGE }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onMenu() },
                    onTap = { onClick() }
                )
            }
            .padding(16.dp)
    ) {
        // Icon Left
        val iconBg = if (item.kind == "link") Color(0xFFCCFBF1) else Indigo100
        val iconTint = if (item.kind == "link") Color(0xFF0D9488) else Indigo600
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.kind == "link") Icons.Default.Link else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                // Open links
                if (item.links.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    item.links.forEach { link ->
                        Text(
                            text = link,
                            fontSize = 14.sp,
                            color = Indigo600,
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                } catch (e: Exception) {}
                            }
                        )
                    }
                }
            } else {
                Text("Adjunto", fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Slate500)
            }

            if (image != null) {
                AsyncImage(
                    model = image.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenAttachment(image) }
                )
            }

            if (otherAttachments.isNotEmpty()) {
                Box(modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Slate100)
                    .clickable { onOpenAttachment(otherAttachments.first()) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(
                        text = if (otherAttachments.size == 1) otherAttachments.first().name else "${otherAttachments.size} archivos",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                }
            }

            Text(
                text = savedDateLabel(item),
                fontSize = 11.sp,
                color = Slate500,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Pin Icon
        val pinBg = if (item.pinned) Color(0xFFFEF3C7) else Slate100
        val pinTint = if (item.pinned) Color(0xFFD97706) else Slate500
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(pinBg)
                .clickable { onPin(!item.pinned) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = pinTint, modifier = Modifier.size(16.dp))
        }
    }
}

private fun savedDateLabel(item: SavedItem): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val saved = formatter.format(Date(item.savedAt))
    val original = item.createdAt.takeIf { it > 0L }?.let { formatter.format(Date(it)) }
    return if (original != null && original != saved) {
        "Guardado: $saved · Mensaje: $original"
    } else {
        "Guardado: $saved"
    }
}
