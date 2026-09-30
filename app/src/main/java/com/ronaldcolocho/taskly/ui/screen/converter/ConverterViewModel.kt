package com.ronaldcolocho.taskly.ui.screen.converter

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
    val converterProviderLabel: String = "",
    val primaryConversionStatus: String? = null,
    val isPrimaryConversionRunning: Boolean = false,
    val primaryQuality: String = "192",
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
    private val getProfileUseCase: GetProfileUseCase,
    private val okHttpClient: OkHttpClient
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
                converterProviderLabel = "",
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
                converterProviderLabel = OWN_SERVICE_LABEL,
                iframeUrl = "",
                selectedVideo = null
            )
        }
    }

    fun pickVideo(video: YouTubeVideo) {
        _uiState.update {
            it.copy(
                manualError = null,
                selectedTitle = video.title,
                manualUrl = "https://www.youtube.com/watch?v=${video.id}",
                selectedMediaLabel = "Audio MP3 / Video MP4",
                converterProviderLabel = OWN_SERVICE_LABEL,
                iframeUrl = "",
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
                converterProviderLabel = "",
                manualError = null,
                selectedVideo = null
            )
        }
    }

    /** Keeps the prior provider available if ClickAPI is temporarily unavailable. */
    fun useFallbackConverter() {
        val videoId = resolveSelectedVideoId() ?: return
        _uiState.update {
            it.copy(
                converterProviderLabel = VEVIOZ_LABEL,
                iframeUrl = veviozUrl(videoId),
                downloadFeedback = "Usando la fuente alternativa."
            )
        }
    }

    fun startPrimaryConversion(context: Context) {
        Log.i(CONVERTER_LOG_TAG, "Convert button pressed")
        val videoId = resolveSelectedVideoId()
        if (videoId == null) {
            val message = "No se pudo identificar el video. Selecciona otro o pega su enlace de YouTube."
            _uiState.update { it.copy(primaryConversionStatus = message, downloadFeedback = message) }
            Log.w(CONVERTER_LOG_TAG, "Conversion rejected: invalid video ID")
            showConversionFeedback(context, message)
            return
        }
        val sourceUrl = "https://www.youtube.com/watch?v=$videoId"
        if (_uiState.value.isPrimaryConversionRunning) return

        _uiState.update {
            it.copy(
                isPrimaryConversionRunning = true,
                primaryConversionStatus = "Enviando conversión a tu servicio...",
                downloadFeedback = "Iniciando conversión en calidad ${qualityLabel(it.primaryQuality)}..."
            )
        }
        showConversionFeedback(context, "Enviando conversión al servicio...")
        Log.i(CONVERTER_LOG_TAG, "Conversion requested: quality=${_uiState.value.primaryQuality}")
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { createConversion(sourceUrl, _uiState.value.primaryQuality) }
                val id = response.getString("id")
                Log.i(CONVERTER_LOG_TAG, "Conversion accepted: id=$id status=${response.optString("status")}")
                showConversionFeedback(context, "Conversión en cola. Preparando audio...")
                val downloadUrl = withContext(Dispatchers.IO) { waitForConversion(id, response) }
                onDownloadRequested(
                    context = context,
                    url = downloadUrl,
                    contentDisposition = null,
                    mimeType = "audio/mpeg",
                    userAgent = null,
                    cookies = null,
                    referer = API_BASE_URL
                )
                _uiState.update { it.copy(primaryConversionStatus = "Archivo listo. Iniciando descarga...") }
                showConversionFeedback(context, "Archivo listo. Iniciando descarga...")
            } catch (e: Exception) {
                Log.e(CONVERTER_LOG_TAG, "Conversion failed", e)
                _uiState.update {
                    it.copy(
                        primaryConversionStatus = "Error: ${e.message ?: "error desconocido"}",
                        downloadFeedback = "Tu servicio no pudo convertir el archivo: ${e.message ?: "error desconocido"}"
                    )
                }
                showConversionFeedback(context, "No se pudo iniciar la conversión: ${e.message ?: "error desconocido"}")
            } finally {
                _uiState.update { it.copy(isPrimaryConversionRunning = false) }
            }
        }
    }

    fun selectPrimaryQuality(quality: String) {
        if (quality !in SUPPORTED_AUDIO_QUALITIES || _uiState.value.isPrimaryConversionRunning) return
        _uiState.update { it.copy(primaryQuality = quality) }
    }

    private fun createConversion(sourceUrl: String, quality: String): JSONObject {
        val body = JSONObject()
            .put("source_url", sourceUrl)
            .put("output_format", "mp3")
            .put("quality", quality)
            .put("rights_confirmed", true)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        return executeJson(Request.Builder().url("$API_BASE_URL/v1/conversions").post(body).build(), expectedCode = 202)
    }

    private suspend fun waitForConversion(id: String, initial: JSONObject): String {
        var conversion = initial
        repeat(MAX_SERVICE_STATUS_CHECKS) {
            when (conversion.optString("status").lowercase()) {
                "completed" -> return conversion.optString("download_url")
                    .takeIf { it.isNotBlank() }
                    ?.let(::absoluteServiceUrl)
                    ?: "$API_BASE_URL/v1/conversions/$id/download"
                "failed" -> throw IllegalStateException(conversion.optString("error", "La conversión falló."))
            }
            Log.i(CONVERTER_LOG_TAG, "Conversion status: ${conversion.optString("status", "queued")}")
            _uiState.update { it.copy(primaryConversionStatus = "Convirtiendo en tu servicio...") }
            delay(SERVICE_STATUS_POLL_MS)
            conversion = withContext(Dispatchers.IO) {
                executeJson(Request.Builder().url("$API_BASE_URL/v1/conversions/$id").get().build(), expectedCode = 200)
            }
        }
        throw IllegalStateException("La conversión tardó demasiado. Intenta nuevamente.")
    }

    private fun executeJson(request: Request, expectedCode: Int): JSONObject = okHttpClient.newCall(request).execute().use { response ->
        val payload = response.body?.string().orEmpty()
        if (response.code != expectedCode) {
            throw IllegalStateException(JSONObject(payload).optString("detail", "HTTP ${response.code}"))
        }
        JSONObject(payload)
    }

    private fun absoluteServiceUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "$API_BASE_URL${if (url.startsWith('/')) "" else "/"}$url"

    private fun qualityLabel(quality: String): String = when (quality) {
        "128" -> "baja (128 kbps)"
        "320" -> "alta (320 kbps)"
        else -> "media (192 kbps)"
    }

    private fun showConversionFeedback(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    /** Lets the browser complete provider-specific flows that a WebView cannot support. */
    fun completeConversionInBrowser(context: Context) {
        val url = _uiState.value.iframeUrl
        if (url.isBlank()) return
        AttachmentActions.openExternal(context, url)
        _uiState.update {
            it.copy(downloadFeedback = "Se abrió el conversor en el navegador para completar la descarga.")
        }
    }

    fun onDownloadRequested(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String?,
        cookies: String?,
        referer: String?
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
                userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                cookies?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                referer?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Referer", it) }
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
                _uiState.update { it.copy(downloadFeedback = "Descargando: $rawTitle") }
                observeDownloadResult(dm, downloadId, targetFile, rawTitle)
            } else {
                _uiState.update { it.copy(downloadFeedback = "No se pudo acceder al gestor de descargas del dispositivo.") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(downloadFeedback = "Error al iniciar la descarga: ${e.message ?: "URL no valida"}") }
        }
    }

    /**
     * Enqueue only means Android accepted the request. Keep watching the system download so the
     * UI does not report success until the target file has actually been written.
     */
    private fun observeDownloadResult(
        downloadManager: DownloadManager,
        downloadId: Long,
        targetFile: File,
        title: String
    ) {
        viewModelScope.launch {
            repeat(MAX_DOWNLOAD_STATUS_CHECKS) {
                delay(DOWNLOAD_STATUS_POLL_MS)
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return@launch
                cursor.use {
                    if (!it.moveToFirst()) return@launch
                    when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val message = if (targetFile.exists() && targetFile.length() > 0L) {
                                "Descarga completada: $title"
                            } else {
                                "La descarga termino, pero el archivo no se guardo. Intenta nuevamente."
                            }
                            _uiState.update { state -> state.copy(downloadFeedback = message) }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else null
                            _uiState.update { state ->
                                state.copy(downloadFeedback = "La descarga fallo${reason?.let { code -> " (codigo $code)" } ?: ""}. Intenta nuevamente.")
                            }
                            return@launch
                        }
                    }
                }
            }
            _uiState.update { it.copy(downloadFeedback = "La descarga sigue pendiente. Revisa la notificacion de Android.") }
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

    private fun resolveSelectedVideoId(): String? =
        _uiState.value.selectedVideo?.id?.let(::extractYouTubeVideoId)
            ?: extractYouTubeVideoId(_uiState.value.manualUrl)

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

    /** ClickAPI widgetplus accepts the canonical YouTube URL as its source. */
    private fun clickApiWidgetPlusUrl(videoId: String): String {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
        return "https://clickapi.net/api/widgetplus?url=${Uri.encode(youtubeUrl)}"
    }

    private companion object {
        const val API_BASE_URL = "http://3.19.79.99:8000"
        const val CONVERTER_LOG_TAG = "TasklyConverter"
        const val OWN_SERVICE_LABEL = "Tu servicio"
        const val CLICK_API_LABEL = "ClickAPI"
        const val VEVIOZ_LABEL = "Fuente alternativa"
        const val DOWNLOAD_STATUS_POLL_MS = 1_000L
        const val MAX_DOWNLOAD_STATUS_CHECKS = 120
        const val SERVICE_STATUS_POLL_MS = 2_000L
        const val MAX_SERVICE_STATUS_CHECKS = 150
        const val DEFAULT_AUDIO_QUALITY = "192"
        val SUPPORTED_AUDIO_QUALITIES = setOf("128", "192", "320")
    }
}

