package com.ronaldcolocho.taskly.ui.screen.converter

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.audio.AudioPlayerState
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.model.MessageStatus
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.domain.usecase.chat.UploadAttachmentUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.youtube.SearchYouTubeVideosUseCase
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.util.AttachmentActions
import com.ronaldcolocho.taskly.util.DownloadTracker
import com.ronaldcolocho.taskly.util.PendingDownloadMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SaveToMessagesStatus {
    IDLE,
    UPLOADING,
    SAVED,
    ERROR
}

data class ConverterUiState(
    val query: String = "",
    val manualUrl: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchError: String? = null,
    val manualError: String? = null,
    val results: List<YouTubeVideo> = emptyList(),
    val iframeUrl: String = "",
    val selectedTitle: String = "",
    val selectedMediaLabel: String = "",
    val isManualUrlInputExpanded: Boolean = false,
    val selectedVideo: YouTubeVideo? = null,
    val downloadFeedback: String? = null
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val searchYouTubeVideosUseCase: SearchYouTubeVideosUseCase,
    private val downloadHistoryRepository: IDownloadHistoryRepository,
    private val uploadAttachmentUseCase: UploadAttachmentUseCase,
    private val chatRepository: IChatRepository,
    private val mediaDownloadManager: MediaDownloadManager,
    private val audioPlayerController: AudioPlayerController,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    val downloadHistory: StateFlow<List<DownloadedSong>> = downloadHistoryRepository
        .getDownloadedSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _saveStatusMap = MutableStateFlow<Map<String, SaveToMessagesStatus>>(emptyMap())
    val saveStatusMap: StateFlow<Map<String, SaveToMessagesStatus>> = _saveStatusMap.asStateFlow()

    val audioPlayerState: StateFlow<AudioPlayerState> = audioPlayerController.state

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        if (newQuery.trim().isEmpty()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    hasSearched = false,
                    searchError = null,
                    isSearching = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = false) }
            delay(800)
            executeSearch(newQuery)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        val currentQuery = _uiState.value.query
        if (currentQuery.trim().isEmpty()) return
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = false) }
            executeSearch(currentQuery)
        }
    }

    private suspend fun executeSearch(query: String) {
        val result = searchYouTubeVideosUseCase(query)
        result.onSuccess { videos ->
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = videos,
                    searchError = null
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = emptyList(),
                    searchError = error.message
                )
            }
        }
    }

    fun onManualUrlChange(newUrl: String) {
        _uiState.update { it.copy(manualUrl = newUrl) }
    }

    fun onSharedUrlReceived(url: String) {
        _uiState.update {
            it.copy(
                manualUrl = url,
                manualError = null,
                iframeUrl = "",
                selectedTitle = "",
                selectedMediaLabel = "",
                isManualUrlInputExpanded = true,
                selectedVideo = null
            )
        }
    }

    fun setManualUrlInputExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isManualUrlInputExpanded = expanded) }
    }

    fun submitManualUrl() {
        val videoId = extractYouTubeVideoId(_uiState.value.manualUrl)
        if (videoId == null) {
            _uiState.update { it.copy(manualError = "Ingresa un enlace o ID de YouTube valido.") }
            return
        }
        _uiState.update {
            it.copy(
                manualError = null,
                selectedTitle = "",
                selectedMediaLabel = "Audio MP3 / Video MP4",
                iframeUrl = veviozUrl(videoId),
                selectedVideo = null
            )
        }
    }

    fun pickVideo(video: YouTubeVideo) {
        _uiState.update {
            it.copy(
                manualError = null,
                selectedTitle = video.title,
                manualUrl = video.url,
                selectedMediaLabel = "Audio MP3 / Video MP4",
                iframeUrl = veviozUrl(video.id),
                selectedVideo = video
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                iframeUrl = "",
                manualUrl = "",
                selectedTitle = "",
                selectedMediaLabel = "",
                manualError = null,
                selectedVideo = null
            )
        }
    }

    fun onDownloadRequested(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val selected = _uiState.value.selectedVideo
        val rawTitle = selected?.title ?: _uiState.value.selectedTitle.ifBlank { "Audio" }
        val artist = selected?.channel ?: ""
        val thumb = selected?.thumbnail ?: ""
        val cleanMime = mimeType?.takeIf { it.isNotBlank() } ?: "audio/mpeg"

        // Sanitize title for filename
        val safeBaseName = rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val ext = if (cleanMime.contains("mp4") || cleanMime.contains("video")) "mp4" else "mp3"
        val initialFileName = "$safeBaseName.$ext"

        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        var targetFile = File(musicDir, initialFileName)
        if (targetFile.exists()) {
            targetFile = File(musicDir, "${safeBaseName}_${System.currentTimeMillis()}.$ext")
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(rawTitle)
                setDescription("Descargando de Taskly...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, targetFile.name)
                setMimeType(cleanMime)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val downloadId = dm.enqueue(request)
                DownloadTracker.registerPending(
                    context = context,
                    downloadId = downloadId,
                    metadata = PendingDownloadMetadata(
                        title = rawTitle,
                        artist = artist,
                        thumbnailUrl = thumb,
                        targetPath = targetFile.absolutePath,
                        mimeType = cleanMime
                    )
                )
                _uiState.update { it.copy(downloadFeedback = "Descarga iniciada: $rawTitle") }
            } else {
                AttachmentActions.download(context, url, targetFile.name)
            }
        } catch (e: Exception) {
            try {
                AttachmentActions.download(context, url, targetFile.name)
            } catch (_: Exception) {
                _uiState.update { it.copy(downloadFeedback = "Error al iniciar la descarga: ${e.message}") }
            }
        }
    }

    fun playSong(song: DownloadedSong) {
        val file = File(song.localPath)
        if (!file.exists()) {
            _uiState.update { it.copy(downloadFeedback = "El archivo no se encuentra en el dispositivo.") }
            return
        }

        val audioTrack = AudioTrack(
            id = song.id,
            url = Uri.fromFile(file).toString(),
            name = song.title,
            msgId = song.savedMessageId ?: song.id,
            durationSeconds = song.durationSeconds,
            thumbnailUrl = song.thumbnailUrl
        )

        if (audioPlayerController.state.value.currentTrack?.id == song.id) {
            audioPlayerController.toggleCurrent()
        } else {
            audioPlayerController.updatePlaylist(listOf(audioTrack))
            audioPlayerController.playWithIndividual(audioTrack)
        }
    }

    fun saveToSavedMessages(song: DownloadedSong, force: Boolean = false) {
        // Prevent concurrent or duplicate actions
        if (_saveStatusMap.value[song.id] == SaveToMessagesStatus.UPLOADING) {
            return
        }
        if (!force && (song.isSavedToMessages || _saveStatusMap.value[song.id] == SaveToMessagesStatus.SAVED)) {
            return
        }

        val file = File(song.localPath)
        if (!file.exists() || file.length() == 0L) {
            _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
            _uiState.update { it.copy(downloadFeedback = "El archivo no se encuentra en el almacenamiento local.") }
            return
        }

        _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.UPLOADING) }

        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserIdUseCase()
                if (currentUserId.isNullOrBlank()) {
                    _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
                    _uiState.update { it.copy(downloadFeedback = "Debes iniciar sesión para guardar mensajes.") }
                    return@launch
                }

                val profile = getProfileUseCase(currentUserId).firstOrNull()
                    ?: UserProfile(uid = currentUserId, displayName = "Usuario", phone = "")

                val convResult = chatRepository.getOrCreateSavedMessagesConversation(profile)
                val conversationId = convResult.getOrElse { error ->
                    _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
                    _uiState.update { it.copy(downloadFeedback = "Error al abrir Mensajes guardados: ${error.message}") }
                    return@launch
                }

                val uploadResult = uploadAttachmentUseCase(file, song.mimeType, currentUserId)
                uploadResult.onSuccess { uploadedAttachment ->
                    val attachment = uploadedAttachment.copy(
                        name = song.title,
                        duration = song.durationSeconds,
                        kind = AttachmentKind.AUDIO
                    )

                    mediaDownloadManager.registerLocalFile(
                        mediaId = attachment.publicId,
                        sourceFile = file,
                        kind = MediaKind.AUDIO
                    )

                    val messageId = generateFirestoreId()
                    val message = ChatMessage(
                        id = messageId,
                        senderId = currentUserId,
                        text = "",
                        status = MessageStatus.SENT,
                        createdAt = System.currentTimeMillis(),
                        attachments = listOf(attachment),
                        conversationId = conversationId
                    )

                    val sendResult = chatRepository.sendMessage(conversationId, currentUserId, message)
                    sendResult.onSuccess {
                        downloadHistoryRepository.updateSavedToMessages(song.id, isSaved = true, messageId = messageId)
                        _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.SAVED) }
                        _uiState.update { it.copy(downloadFeedback = "Guardado en Mensajes guardados") }
                    }.onFailure { sendError ->
                        _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
                        _uiState.update { it.copy(downloadFeedback = "Error al enviar mensaje: ${sendError.message}") }
                    }
                }.onFailure { uploadError ->
                    _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
                    _uiState.update { it.copy(downloadFeedback = "Error al subir audio: ${uploadError.message}") }
                }
            } catch (e: Exception) {
                _saveStatusMap.update { it + (song.id to SaveToMessagesStatus.ERROR) }
                _uiState.update { it.copy(downloadFeedback = "Error inesperado: ${e.message}") }
            }
        }
    }

    fun deleteSong(song: DownloadedSong, deleteFile: Boolean = false) {
        viewModelScope.launch {
            if (deleteFile) {
                try {
                    val file = File(song.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            downloadHistoryRepository.removeDownloadedSong(song.id)
        }
    }

    fun clearDownloadFeedback() {
        _uiState.update { it.copy(downloadFeedback = null) }
    }

    private fun generateFirestoreId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20).map { chars.random() }.joinToString("")
    }

    private fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return trimmed

        return runCatching {
            val uri = Uri.parse(trimmed)
            val host = uri.host.orEmpty()
            val pathSegments = uri.pathSegments
            val videoId = when {
                host.contains("youtu.be") -> uri.lastPathSegment
                host.contains("youtube.com") || host.contains("youtube-nocookie.com") -> {
                    uri.getQueryParameter("v")
                        ?: pathSegments.indexOf("shorts")
                            .takeIf { it >= 0 }
                            ?.let { pathSegments.getOrNull(it + 1) }
                        ?: pathSegments.indexOf("embed")
                            .takeIf { it >= 0 }
                            ?.let { pathSegments.getOrNull(it + 1) }
                }
                else -> null
            }
            videoId?.takeIf { Regex("^[a-zA-Z0-9_-]{11}$").matches(it) }
        }.getOrNull()
    }

    private fun veviozUrl(videoId: String): String = "https://api.vevioz.com/$videoId"
}
