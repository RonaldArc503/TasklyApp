package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) : IMediaCacheRepository {

    private val activeDownloads = ConcurrentHashMap<String, Flow<MediaDownloadState>>()
    private val rootDir: File by lazy { File(context.filesDir, "Taskly") }

    init {
        cleanupTempFiles()
    }

    private fun categoryDir(kind: MediaKind): File =
        File(rootDir, kind.folder).apply { mkdirs() }

    private fun fileName(mediaId: String, kind: MediaKind): String =
        "${sha1(mediaId)}.${extFor(kind)}"

    override fun isDownloaded(mediaId: String): Boolean {
        MediaKind.entries.forEach { kind ->
            val f = File(categoryDir(kind), fileName(mediaId, kind))
            if (f.exists() && f.length() > 0) return true
        }
        return false
    }

    override fun fileFor(mediaId: String, kind: MediaKind): File? {
        val f = File(categoryDir(kind), fileName(mediaId, kind))
        return if (f.exists() && f.length() > 0) f else null
    }

    override fun download(mediaId: String, url: String, kind: MediaKind): Flow<MediaDownloadState> {
        activeDownloads[mediaId]?.let { return it }

        lateinit var flow: Flow<MediaDownloadState>
        flow = doDownload(mediaId, url, kind)
            .onCompletion { activeDownloads.remove(mediaId, flow) }

        val raced = activeDownloads.putIfAbsent(mediaId, flow)
        return raced ?: flow
    }

    override fun cleanupTempFiles() {
        rootDir.listFiles()?.forEach { cat ->
            cat.listFiles()?.forEach { f ->
                if (f.name.endsWith(".tmp")) f.delete()
            }
        }
    }

    private fun doDownload(mediaId: String, url: String, kind: MediaKind): Flow<MediaDownloadState> =
        callbackFlow {
            trySend(MediaDownloadState.Downloading(0f))
            var tmp: File? = null
            val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val dir = categoryDir(kind)
                    val finalFile = File(dir, fileName(mediaId, kind))
                    if (finalFile.exists() && finalFile.length() > 0) {
                        trySend(MediaDownloadState.Downloaded)
                        close()
                        return@launch
                    }

                    tmp = File(dir, finalFile.name + ".tmp")
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Error al descargar (${response.code})")
                        }
                        val body = response.body ?: throw Exception("Respuesta vacía")
                        val total = body.contentLength()
                        val buffer = ByteArray(8192)
                        body.byteStream().use { stream ->
                            tmp!!.outputStream().buffered().use { sink ->
                                var read = stream.read(buffer)
                                var downloaded = 0L
                                while (read != -1) {
                                    sink.write(buffer, 0, read)
                                    downloaded += read
                                    if (total > 0) {
                                        trySend(MediaDownloadState.Downloading(downloaded.toFloat() / total))
                                    }
                                    read = stream.read(buffer)
                                }
                            }
                        }
                    }

                    if (!tmp!!.renameTo(finalFile)) {
                        if (finalFile.exists() && finalFile.length() > 0) {
                            tmp!!.delete()
                        } else {
                            throw Exception("No se pudo guardar el archivo")
                        }
                    }
                    trySend(MediaDownloadState.Downloaded)
                    close()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    tmp?.delete()
                    throw e
                } catch (e: Exception) {
                    tmp?.delete()
                    trySend(MediaDownloadState.Error(e.message ?: "Error al descargar"))
                    close()
                }
            }
            awaitClose { job.cancel() }
        }

    private fun extFor(kind: MediaKind): String = when (kind) {
        MediaKind.IMAGE -> "img"
        MediaKind.AUDIO -> "audio"
        MediaKind.VIDEO -> "mp4"
        MediaKind.DOCUMENT -> "doc"
        MediaKind.OTHER -> "file"
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
