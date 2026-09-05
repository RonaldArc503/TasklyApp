package com.ronaldcolocho.taskly.ui.screen.converter

import android.annotation.SuppressLint
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.BuildConfig
import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import com.ronaldcolocho.taskly.ui.theme.Slate100
import com.ronaldcolocho.taskly.ui.theme.Slate200
import com.ronaldcolocho.taskly.ui.theme.Slate400
import com.ronaldcolocho.taskly.ui.theme.Slate500
import com.ronaldcolocho.taskly.ui.theme.Slate600
import com.ronaldcolocho.taskly.ui.theme.Slate800
import com.ronaldcolocho.taskly.util.AttachmentActions

@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.iframeUrl.isEmpty()) {
            SearchMode(
                uiState = uiState,
                onQueryChange = viewModel::onQueryChange,
                onSubmitSearch = viewModel::submitSearch,
                onPickVideo = viewModel::pickVideo,
                onManualUrlChange = viewModel::onManualUrlChange,
                onSubmitManualUrl = viewModel::submitManualUrl,
                onNavigateBack = onNavigateBack
            )
        } else {
            IframeMode(
                uiState = uiState,
                onChangeVideo = viewModel::clearSelection,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun SearchMode(
    uiState: ConverterUiState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onPickVideo: (YouTubeVideo) -> Unit,
    onManualUrlChange: (String) -> Unit,
    onSubmitManualUrl: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
                Text("Conversor de YouTube", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Text("Busca una canción o artista y descárgala en MP3/MP4.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.hasSearched && uiState.results.isEmpty() && !uiState.isSearching) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sin resultados para esa búsqueda.", modifier = Modifier.padding(24.dp), color = Slate600, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (uiState.results.isNotEmpty()) {
                item {
                    Text("RESULTADOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate400, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(uiState.results) { video ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            Column(modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)) {
                                Text(video.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                var expanded by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "O pega un enlace manualmente", 
                            style = MaterialTheme.typography.labelMedium, 
                            fontWeight = FontWeight.SemiBold, 
                            color = Slate600,
                            modifier = Modifier.clickable { expanded = !expanded }.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        if (expanded) {
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
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                            ) {
                                Text("Abrir conversor", fontWeight = FontWeight.Bold)
                            }
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
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                            AttachmentActions.download(context, url, fileName)
                        }
                        loadUrl(uiState.iframeUrl)
                    }
                },
                update = { webView ->
                    // Only load if it's different to prevent reloading on recomposition
                    if (webView.url != uiState.iframeUrl) {
                        webView.loadUrl(uiState.iframeUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
