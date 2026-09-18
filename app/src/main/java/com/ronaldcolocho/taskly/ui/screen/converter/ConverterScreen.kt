package com.ronaldcolocho.taskly.ui.screen.converter

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.BuildConfig
import com.ronaldcolocho.taskly.audio.AudioPlayerState
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDayLabel
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.ui.theme.Emerald500
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import com.ronaldcolocho.taskly.ui.theme.Red600
import com.ronaldcolocho.taskly.ui.theme.Slate100
import com.ronaldcolocho.taskly.ui.theme.Slate200
import com.ronaldcolocho.taskly.ui.theme.Slate400
import com.ronaldcolocho.taskly.ui.theme.Slate500
import com.ronaldcolocho.taskly.ui.theme.Slate600
import com.ronaldcolocho.taskly.ui.theme.Slate800
import java.io.File

@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel(),
    sharedUrl: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val saveStatusMap by viewModel.saveStatusMap.collectAsStateWithLifecycle()
    val audioPlayerState by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sharedUrl) {
        sharedUrl?.let(viewModel::onSharedUrlReceived)
    }

    LaunchedEffect(uiState.downloadFeedback) {
        val feedback = uiState.downloadFeedback
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearDownloadFeedback()
        }
    }

    BackHandler(enabled = uiState.iframeUrl.isNotEmpty()) {
        viewModel.clearSelection()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.iframeUrl.isEmpty()) {
                SearchMode(
                    uiState = uiState,
                    downloadHistory = downloadHistory,
                    saveStatusMap = saveStatusMap,
                    audioPlayerState = audioPlayerState,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmitSearch = viewModel::submitSearch,
                    onPickVideo = viewModel::pickVideo,
                    onManualUrlChange = viewModel::onManualUrlChange,
                    onSubmitManualUrl = viewModel::submitManualUrl,
                    onManualUrlInputExpandedChange = viewModel::setManualUrlInputExpanded,
                    onPlaySong = viewModel::playSong,
                    onSaveToMessages = { song -> viewModel.saveToSavedMessages(song) },
                    onDeleteSong = viewModel::deleteSong,
                    onNavigateBack = onNavigateBack
                )
            } else {
                val context = LocalContext.current
                IframeMode(
                    uiState = uiState,
                    onDownloadRequested = { url, disposition, mime ->
                        viewModel.onDownloadRequested(context, url, disposition, mime)
                    },
                    onChangeVideo = viewModel::clearSelection,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun SearchMode(
    uiState: ConverterUiState,
    downloadHistory: List<DownloadedSong>,
    saveStatusMap: Map<String, SaveToMessagesStatus>,
    audioPlayerState: AudioPlayerState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onPickVideo: (YouTubeVideo) -> Unit,
    onManualUrlChange: (String) -> Unit,
    onSubmitManualUrl: () -> Unit,
    onManualUrlInputExpandedChange: (Boolean) -> Unit,
    onPlaySong: (DownloadedSong) -> Unit,
    onSaveToMessages: (DownloadedSong) -> Unit,
    onDeleteSong: (DownloadedSong, Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    var songToDelete by remember { mutableStateOf<DownloadedSong?>(null) }
    var deletePhysicalFile by remember { mutableStateOf(false) }

    if (songToDelete != null) {
        val target = songToDelete!!
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("¿Eliminar descarga?") },
            text = {
                Column {
                    Text(
                        "¿Deseas quitar \"${target.title}\" del historial?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deletePhysicalFile = !deletePhysicalFile }
                    ) {
                        Checkbox(
                            checked = deletePhysicalFile,
                            onCheckedChange = { deletePhysicalFile = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Eliminar también el archivo del dispositivo",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSong(target, deletePhysicalFile)
                        songToDelete = null
                        deletePhysicalFile = false
                    }
                ) {
                    Text("Eliminar", color = Red600, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Slate500)
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Conversor de Música", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Text("Busca una canción o artista y descárgala en MP3/MP4.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Search Box
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Canción o artista", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = uiState.query,
                                onValueChange = onQueryChange,
                                placeholder = { Text("Ej. Bad Bunny", color = Slate400) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Slate200,
                                    focusedBorderColor = Indigo600
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSubmitSearch,
                                enabled = !uiState.isSearching && uiState.query.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                                modifier = Modifier.height(56.dp)
                            ) {
                                if (uiState.isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Buscar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "Falta tu clave de YouTube Data API v3 en local.properties (YOUTUBE_API_KEY).",
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFFB45309),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (uiState.searchError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    uiState.searchError,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFFDC2626),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // No results state
            if (uiState.hasSearched && uiState.results.isEmpty() && !uiState.isSearching) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            "Sin resultados para esa búsqueda.",
                            modifier = Modifier.padding(24.dp),
                            color = Slate600,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Search results list
            if (uiState.results.isNotEmpty()) {
                item {
                    Text(
                        "RESULTADOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(uiState.results, key = { it.id }) { video ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onPickVideo(video) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = video.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 64.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate100)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    video.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    video.channel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Manual URL Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "O pega un enlace manualmente",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate600,
                            modifier = Modifier
                                .clickable { onManualUrlInputExpandedChange(!uiState.isManualUrlInputExpanded) }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                        if (uiState.isManualUrlInputExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Enlace o ID de YouTube", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.manualUrl,
                                onValueChange = onManualUrlChange,
                                placeholder = { Text("https://www.youtube.com/watch?v=...", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            if (uiState.manualError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(uiState.manualError, color = Color(0xFFDC2626), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onSubmitManualUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                            ) {
                                Text("Abrir conversor", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Historial de descargas Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Indigo600,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Historial de descargas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (downloadHistory.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = Indigo600.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${downloadHistory.size}",
                                color = Indigo600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (downloadHistory.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Aún no hay descargas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Las canciones que descargues aparecerán aquí para escucharlas o guardarlas en tus Mensajes guardados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(downloadHistory, key = { it.id }) { song ->
                    DownloadedSongCard(
                        song = song,
                        saveStatus = when {
                            saveStatusMap[song.id] == SaveToMessagesStatus.UPLOADING -> SaveToMessagesStatus.UPLOADING
                            saveStatusMap[song.id] == SaveToMessagesStatus.SAVED || song.isSavedToMessages -> SaveToMessagesStatus.SAVED
                            saveStatusMap[song.id] == SaveToMessagesStatus.ERROR -> SaveToMessagesStatus.ERROR
                            else -> SaveToMessagesStatus.IDLE
                        },
                        isPlaying = audioPlayerState.playing && audioPlayerState.currentTrack?.id == song.id,
                        onPlayClick = { onPlaySong(song) },
                        onSaveClick = { onSaveToMessages(song) },
                        onDeleteClick = { songToDelete = song }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedSongCard(
    song: DownloadedSong,
    saveStatus: SaveToMessagesStatus,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val fileExists = remember(song.localPath) { File(song.localPath).exists() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Thumbnail + Song Details + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Indigo600,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.artist.isNotBlank()) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (song.durationSeconds > 0) {
                            Text(
                                text = formatDuration(song.durationSeconds * 1000L),
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                        }
                        if (song.sizeBytes > 0) {
                            Text(
                                text = formatBytes(song.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                        }
                        Text(
                            text = formatDayLabel(song.downloadedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }

                    if (!fileExists) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Red600.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Archivo no disponible",
                                color = Red600,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar",
                        tint = Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Bottom Actions: Play/Pause & Save to Messages
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Button
                Button(
                    onClick = onPlayClick,
                    enabled = fileExists,
                    shape = RoundedCornerShape(10.dp),
                    colors = if (isPlaying) {
                        ButtonDefaults.buttonColors(containerColor = Indigo600, contentColor = Color.White)
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "Pausar" else "Reproducir",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Save to Saved Messages Button
                when (saveStatus) {
                    SaveToMessagesStatus.IDLE -> {
                        Button(
                            onClick = onSaveClick,
                            enabled = fileExists,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardar",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    SaveToMessagesStatus.UPLOADING -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Slate500
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardando...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    SaveToMessagesStatus.SAVED -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Emerald500.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Emerald500,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Guardado ✓",
                                    color = Emerald500,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    SaveToMessagesStatus.ERROR -> {
                        Button(
                            onClick = onSaveClick,
                            enabled = fileExists,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Red600
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reintentar",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IframeMode(
    uiState: ConverterUiState,
    onDownloadRequested: (url: String, contentDisposition: String?, mimeType: String?) -> Unit,
    onChangeVideo: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Slate500)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = uiState.selectedTitle.ifEmpty { "Conversor Vevioz" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.selectedMediaLabel.isNotBlank()) {
                    Text(
                        text = uiState.selectedMediaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedButton(
                onClick = onChangeVideo,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Cambiar video", fontSize = 12.sp, color = Slate600)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                            onDownloadRequested(url, contentDisposition, mimeType)
                        }
                        loadUrl(uiState.iframeUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != uiState.iframeUrl) {
                        webView.loadUrl(uiState.iframeUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
