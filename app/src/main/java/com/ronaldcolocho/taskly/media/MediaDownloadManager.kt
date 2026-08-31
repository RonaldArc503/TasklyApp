package com.ronaldcolocho.taskly.media

import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloadManager @Inject constructor(
    private val cache: IMediaCacheRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

    fun isDownloaded(mediaId: String): Boolean = cache.isDownloaded(mediaId)

    fun fileFor(mediaId: String, kind: MediaKind): File? = cache.fileFor(mediaId, kind)

    fun currentState(mediaId: String): MediaDownloadState =
        _states.value[mediaId] ?: if (cache.isDownloaded(mediaId)) {
            MediaDownloadState.Downloaded
        } else {
            MediaDownloadState.NotDownloaded
        }

    /**
     * Inicia la descarga si no existe localmente. Single-flight: los llamados
     * repetidos reutilizan la descarga activa.
     */
    fun ensureDownloaded(mediaId: String, url: String, kind: MediaKind) {
        if (cache.fileFor(mediaId, kind) != null) return
        if (jobs.containsKey(mediaId)) return

        val job = scope.launch {
            cache.download(mediaId, url, kind).collect { state ->
                _states.update { it + (mediaId to state) }
            }
            jobs.remove(mediaId)
        }
        jobs[mediaId] = job
    }

    /**
     * Devuelve el archivo local, descargándolo (si hace falta) y esperando el
     * estado terminal. Devuelve null si la descarga falla.
     */
    suspend fun awaitLocalFile(mediaId: String, url: String, kind: MediaKind): File? {
        cache.fileFor(mediaId, kind)?.let { return it }
        ensureDownloaded(mediaId, url, kind)

        val terminal = _states
            .map { it[mediaId] }
            .filterNotNull()
            .first { it is MediaDownloadState.Downloaded || it is MediaDownloadState.Error }

        return if (terminal is MediaDownloadState.Downloaded) cache.fileFor(mediaId, kind) else null
    }
}
