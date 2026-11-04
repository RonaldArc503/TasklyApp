package com.ronaldcolocho.taskly.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.util.DownloadedSongResolver
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Stable
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import com.ronaldcolocho.taskly.domain.model.CachedAudioReference
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_CONCURRENT_DOWNLOADS = 2

private data class DownloadRequest(
    val mediaId: String,
    val url: String,
    val kind: MediaKind,
    val automatic: Boolean
)

@Stable
@Singleton
class MediaDownloadManager @Inject constructor(
    private val cache: IMediaCacheRepository,
    private val musicStore: MusicDownloadStore,
    private val history: IDownloadHistoryRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manualQueue = Channel<DownloadRequest>(Channel.UNLIMITED)
    private val automaticQueue = Channel<DownloadRequest>(capacity = 8)
    private val queuedIds = ConcurrentHashMap.newKeySet<String>()
    private val automaticQueuedIds = ConcurrentHashMap.newKeySet<String>()
    private val activeIds = ConcurrentHashMap.newKeySet<String>()
    private val pendingAudioReferences = ConcurrentHashMap<String, CachedAudioReference>()

    private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

    init {
        scope.launch {
            withContext(Dispatchers.IO) { musicStore.pending() }.forEach(::downloadAudio)
        }
        repeat(MAX_CONCURRENT_DOWNLOADS) {
            scope.launch { consumeDownloadQueue() }
        }
    }

    fun isDownloaded(mediaId: String): Boolean = cache.isDownloaded(mediaId)

    fun fileFor(mediaId: String, kind: MediaKind): File? = cache.fileFor(mediaId, kind)

    fun registerDownloadedAudio(reference: CachedAudioReference) = cache.registerDownloadedAudio(reference)

    fun registerDownloadedAudios(references: Collection<CachedAudioReference>) =
        cache.registerDownloadedAudios(references)

    fun resolveLocalFile(mediaId: String, kind: MediaKind) {
        if (_states.value[mediaId] is MediaDownloadState.Downloaded) return
        scope.launch(Dispatchers.IO) {
            val file = cache.fileFor(mediaId, kind)
            if (file != null) {
                _states.update { it + (mediaId to MediaDownloadState.Downloaded) }
                delay(2_000)
                _states.update { states ->
                    if (states[mediaId] is MediaDownloadState.Downloaded) states - mediaId else states
                }
            }
        }
    }

    suspend fun registerLocalFile(mediaId: String, sourceFile: File, kind: MediaKind): File? {
        val cached = cache.cacheLocalFile(mediaId, sourceFile, kind)
        if (cached != null) _states.update { it + (mediaId to MediaDownloadState.Downloaded) }
        return cached
    }

    fun currentState(mediaId: String): MediaDownloadState = _states.value[mediaId]
        ?: if (cache.isDownloaded(mediaId)) MediaDownloadState.Downloaded else MediaDownloadState.NotDownloaded

    fun stateFor(mediaId: String): Flow<MediaDownloadState> = states
        .map { map -> map[mediaId] ?: if (cache.isDownloaded(mediaId)) MediaDownloadState.Downloaded else MediaDownloadState.NotDownloaded }
        .distinctUntilChanged()

    /** Manual downloads use the same queue but are consumed before automatic requests. */
    fun ensureDownloaded(mediaId: String, url: String, kind: MediaKind) {
        enqueue(DownloadRequest(mediaId, url, kind, automatic = false))
    }

    /** Music downloads survive closing their screen and retain recovery metadata. */
    fun downloadTracks(tracks: Collection<MusicTrack>) {
        tracks.distinctBy { it.id }.forEach { track ->
            downloadAudio(CachedAudioReference(track.id, track.url, track.name, track.sourceMessageId,
                track.durationSeconds, System.currentTimeMillis(), track.artist, track.thumbnailUrl, track.sourceChannel))
        }
    }

    fun downloadAudio(reference: CachedAudioReference) {
        if (reference.mediaId.isBlank() || reference.url.isBlank()) return
        scope.launch {
            try {
                val local = withContext(Dispatchers.IO) {
                    cache.refreshFile(reference.mediaId, MediaKind.AUDIO)
                        ?: DownloadedSongResolver.resolveTrackFile(context, reference.url, trackId = reference.mediaId,
                            downloadedSongs = history.getDownloadedSongs().first().filter { it.id == reference.mediaId })
                }
                if (local != null) {
                    cache.registerDownloadedAudio(reference)
                    musicStore.remove(reference.mediaId)
                    _states.update { it + (reference.mediaId to MediaDownloadState.Downloaded) }
                    return@launch
                }
                require(reference.url.startsWith("https://") || reference.url.startsWith("http://")) {
                    "El archivo local no está disponible. Recupéralo desde el conversor."
                }
                withContext(Dispatchers.IO) { musicStore.put(reference) }
                pendingAudioReferences[reference.mediaId] = reference
                ensureDownloaded(reference.mediaId, reference.url, MediaKind.AUDIO)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _states.update { it + (reference.mediaId to MediaDownloadState.Error(error.message ?: "No se pudo iniciar la descarga.")) }
            }
        }
    }

    /**
     * Replaces the automatic window with the attachments currently near the viewport.
     * Requests that have not started and are no longer relevant are discarded; active
     * requests finish to avoid wasting an already transferred partial file.
     */
    fun updateAutomaticWindow(attachments: Collection<AutoDownloadAttachment>) {
        val desired = attachments.mapTo(linkedSetOf()) { it.mediaId }
        val stale = automaticQueuedIds.filter { it !in desired }
        stale.forEach { mediaId ->
            automaticQueuedIds.remove(mediaId)
            if (queuedIds.remove(mediaId)) _states.update { it - mediaId }
        }
        attachments.forEach { attachment ->
            enqueue(
                DownloadRequest(attachment.mediaId, attachment.url, attachment.kind, automatic = true)
            )
        }
    }

    private fun enqueue(request: DownloadRequest) {
        if (request.mediaId.isBlank() || request.url.isBlank() || cache.isDownloaded(request.mediaId)) return
        if (request.mediaId in activeIds) return

        if (request.automatic) {
            if (!automaticQueuedIds.add(request.mediaId)) return
            if (!queuedIds.add(request.mediaId)) return
            if (automaticQueue.trySend(request).isFailure) {
                automaticQueuedIds.remove(request.mediaId)
                queuedIds.remove(request.mediaId)
                return
            }
        } else {
            val promoted = automaticQueuedIds.remove(request.mediaId)
            if (!queuedIds.add(request.mediaId)) {
                // A visible automatic item can be promoted by putting the same id
                // in the manual queue; the stale automatic entry will be ignored.
                if (promoted) manualQueue.trySend(request)
                return
            }
            manualQueue.trySend(request)
        }
        _states.update { it + (request.mediaId to MediaDownloadState.Queued) }
    }

    private suspend fun consumeDownloadQueue() {
        while (scope.isActive) {
            val request = select<DownloadRequest> {
                manualQueue.onReceive { it }
                automaticQueue.onReceive { it }
            }
            if (request.automatic && !automaticQueuedIds.remove(request.mediaId)) continue
            if (!queuedIds.remove(request.mediaId) || !activeIds.add(request.mediaId)) continue

            try {
                cache.download(request.mediaId, request.url, request.kind).collect { state ->
                    if (state is MediaDownloadState.Downloaded) {
                        pendingAudioReferences[request.mediaId]?.let { reference ->
                            cache.registerDownloadedAudio(reference)
                            musicStore.remove(request.mediaId)
                            pendingAudioReferences.remove(request.mediaId)
                        }
                    }
                    _states.update { it + (request.mediaId to state) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _states.update { it + (request.mediaId to MediaDownloadState.Error(error.message ?: "Descarga interrumpida. Reintenta.")) }
            } finally {
                activeIds.remove(request.mediaId)
                val finalState = _states.value[request.mediaId]
                if (!pendingAudioReferences.containsKey(request.mediaId) &&
                    (finalState is MediaDownloadState.Downloaded || finalState is MediaDownloadState.Error)) {
                    scope.launch {
                        delay(2_000)
                        _states.update { states ->
                            val current = states[request.mediaId]
                            if (current is MediaDownloadState.Downloaded || current is MediaDownloadState.Error) {
                                states - request.mediaId
                            } else {
                                states
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun awaitLocalFile(mediaId: String, url: String, kind: MediaKind): File? {
        withContext(Dispatchers.IO) { cache.fileFor(mediaId, kind) }?.let { return it }
        ensureDownloaded(mediaId, url, kind)
        val terminal = _states.map { it[mediaId] }.filterNotNull().first {
            it is MediaDownloadState.Downloaded || it is MediaDownloadState.Error
        }
        return if (terminal is MediaDownloadState.Downloaded) {
            withContext(Dispatchers.IO) { cache.fileFor(mediaId, kind) }
        } else null
    }
}

data class AutoDownloadAttachment(
    val mediaId: String,
    val url: String,
    val kind: MediaKind
)
