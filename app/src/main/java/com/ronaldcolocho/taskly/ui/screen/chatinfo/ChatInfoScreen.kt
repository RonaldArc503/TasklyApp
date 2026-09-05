package com.ronaldcolocho.taskly.ui.screen.chatinfo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.ui.screen.chat.AttachmentLightbox
import com.ronaldcolocho.taskly.ui.screen.chat.LightboxItem
import com.ronaldcolocho.taskly.ui.screen.chat.AudioCard
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    onNavigateBack: () -> Unit,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    viewModel: ChatInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var lightboxAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var lightboxIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { /* No action yet */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is ChatInfoUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ChatInfoUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ChatInfoUiState.Success -> {
                    ChatInfoContent(
                        state = state,
                        audioController = audioController,
                        mediaManager = mediaManager,
                        onLoadMore = viewModel::loadMore,
                        onImageClick = { att, index ->
                            lightboxAttachments = state.mediaAttachments.map { it.first }
                            lightboxIndex = index
                        }
                    )
                }
            }
        }
    }

    if (lightboxAttachments.isNotEmpty()) {
        AttachmentLightbox(
            items = lightboxAttachments.map { LightboxItem(it) },
            initialIndex = lightboxIndex,
            mediaManager = mediaManager,
            onClose = { lightboxAttachments = emptyList() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoContent(
    state: ChatInfoUiState.Success,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    onLoadMore: () -> Unit,
    onImageClick: (ChatAttachment, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // User Info Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Find other participant for 1v1
            val isGroup = state.conversation.isGroup
            val isSelf = state.conversation.participantIds.size == 1 || (state.conversation.participantIds.size == 2 && state.conversation.participantIds.all { it == state.currentUserId })
            
            val peerId = state.conversation.participantIds.firstOrNull { it != state.currentUserId }
            val peerMember = state.conversation.members[peerId]

            val title = if (isGroup) {
                state.conversation.name ?: "Grupo"
            } else if (isSelf) {
                "Mensajes guardados"
            } else {
                peerMember?.displayName ?: "Usuario"
            }
            
            val photoUrl = if (!isGroup && !isSelf) peerMember?.photoURL else null

            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tabs
        val tabs = listOf("Multimedia", "Enlaces", "Música")
        val pagerState = rememberPagerState(pageCount = { tabs.size })
        val coroutineScope = rememberCoroutineScope()

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> MediaTab(state.mediaAttachments, state.isLoadingMore, state.hasMore, onImageClick, onLoadMore)
                1 -> LinksTab(state.links, state.isLoadingMore, state.hasMore, onLoadMore)
                2 -> MusicTab(state.audioAttachments, audioController, mediaManager, state.isLoadingMore, state.hasMore, onLoadMore)
            }
        }
    }
}

@Composable
fun MediaTab(media: List<Pair<ChatAttachment, String>>, isLoadingMore: Boolean, hasMore: Boolean, onImageClick: (ChatAttachment, Int) -> Unit, onLoadMore: () -> Unit) {
    if (media.isEmpty()) {
        EmptyTabWithLoadMore("No hay archivos multimedia", isLoadingMore, hasMore, onLoadMore)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(media.size) { index ->
            val att = media[index].first
            AsyncImage(
                model = att.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(1.dp)
                    .clickable { onImageClick(att, index) }
                    .background(Color.LightGray)
            )
        }
        if (hasMore || isLoadingMore) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { LoadMoreButton(isLoadingMore, onLoadMore) }
    }
}

@Composable
fun LinksTab(links: List<ChatLink>, isLoadingMore: Boolean, hasMore: Boolean, onLoadMore: () -> Unit) {
    if (links.isEmpty()) {
        EmptyTabWithLoadMore("No hay enlaces", isLoadingMore, hasMore, onLoadMore)
        return
    }
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(links) { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Enlace",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (hasMore || isLoadingMore) item { LoadMoreButton(isLoadingMore, onLoadMore) }
    }
}

@Composable
fun MusicTab(
    audio: List<Pair<ChatAttachment, String>>,
    audioController: AudioPlayerController,
    mediaManager: MediaDownloadManager,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (audio.isEmpty()) {
        EmptyTabWithLoadMore("No hay mensajes de voz", isLoadingMore, hasMore, onLoadMore)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(audio) { (att, _) ->
            // Pass isMe = false so they all look uniform in the info screen
            AudioCard(
                att = att,
                isMe = false,
                controller = audioController,
                mediaManager = mediaManager
            )
        }
        if (hasMore || isLoadingMore) item { LoadMoreButton(isLoadingMore, onLoadMore) }
    }
}

@Composable
private fun LoadMoreButton(isLoading: Boolean, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        if (isLoading) CircularProgressIndicator() else TextButton(onClick = onLoadMore) { Text("Cargar mÃ¡s") }
    }
}

@Composable
private fun EmptyTabWithLoadMore(message: String, isLoading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasMore || isLoading) LoadMoreButton(isLoading, onLoadMore)
    }
}

@Composable
fun EmptyTab(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
