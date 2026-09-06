package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.model.CachedAudioReference
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) : IMediaCacheRepository {

    private val activeDownloads = ConcurrentHashMap<String, Flow<MediaDownloadState>>()
    private val rootDir: File by lazy { File(context.filesDir, "Taskly") }
    private val audioCatalog = context.getSharedPreferences("taskly_media_catalog", Context.MODE_PRIVATE)

    /** Caché en memoria de rutas resueltas y estado de descarga (evita stats de disco en composición).
     *  ConcurrentHashMap no admite null, así que usamos un File centinela para "no encontrado". */
    private val notFoundSentinel = File("/dev/null/__NOT_FOUND__")
    private val resolvedFiles = ConcurrentHashMap<String, File>()
    private val downloadedIds = ConcurrentHashMap.newKeySet<String>()

    /** Limita descargas concurrentes para no saturar el ancho de banda. */
    private val downloadLimiter = Semaphore(2)
    @Volatile private var tempCleanupStarted = false

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Limpieza de .tmp asíncrona (una sola vez), nunca en el hilo principal.
        ioScope.launch { cleanupTempFiles() }
    }

    private fun categoryDir(kind: MediaKind): File {
        val dir = File(rootDir, kind.folder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fileName(mediaId: String, kind: MediaKind): String =
        "${sha1(mediaId)}.${extFor(kind)}"

    override fun isDownloaded(mediaId: String): Boolean {
        // Fast path: solo memoria, sin I/O de disco (seguro para Main/Compose)
        if (mediaId in downloadedIds) return true
        val cached = resolvedFiles[mediaId]
        if (cached != null) return cached !== notFoundSentinel
        // No conocemos el estado: programar una resolución en background y por ahora decir "no"
        ioScope.launch {
            val found = resolveFile(mediaId)
            if (found != null) {
                downloadedIds += mediaId
                resolvedFiles[mediaId] = found
            } else {
                resolvedFiles[mediaId] = notFoundSentinel
            }
        }
        return false
    }

    override fun fileFor(mediaId: String, kind: MediaKind): File? {
        val cached = resolvedFiles[mediaId]
        if (cached != null) {
            return if (cached === notFoundSentinel) null else cached
        }
        // Lookup síncrono solo cuando no hay entrada en caché (primer acceso).
        // Se ejecuta pocas veces; posteriores lecturas son de memoria.
        val f = File(categoryDir(kind), fileName(mediaId, kind))
        val result = if (f.exists() && f.length() > 0) f else null
        resolvedFiles[mediaId] = result ?: notFoundSentinel
        if (result != null) downloadedIds += mediaId
        return result
    }

    override suspend fun cacheLocalFile(mediaId: String, sourceFile: File, kind: MediaKind): File? =
        withContext(Dispatchers.IO) {
            if (!sourceFile.exists() || sourceFile.length() <= 0) return@withContext null

            runCatching {
                val dir = categoryDir(kind)
                val finalFile = File(dir, fileName(mediaId, kind))
                if (finalFile.exists() && finalFile.length() > 0) {
                    resolvedFiles[mediaId] = finalFile
                    downloadedIds += mediaId
                    return@runCatching finalFile
                }

                val tmp = File(dir, finalFile.name + ".local.tmp")
                try {
                    if (tmp.exists()) tmp.delete()
                    sourceFile.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(finalFile)) {
                        sourceFile.copyTo(finalFile, overwrite = true)
                        tmp.delete()
                    }
                    if (finalFile.length() <= 0) {
                        finalFile.delete()
                        return@runCatching null
                    }
                    resolvedFiles[mediaId] = finalFile
                    downloadedIds += mediaId
                    finalFile
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
            }.getOrNull()
        }

    override fun registerDownloadedAudio(reference: CachedAudioReference) {
        if (reference.mediaId.isBlank() || reference.url.isBlank()) return
        ioScope.launch {
            val entries = readAudioCatalog().filterNot { it.mediaId == reference.mediaId }.toMutableList()
            entries += reference.copy(downloadedAt = System.currentTimeMillis())
            val bounded = entries.sortedByDescending { it.downloadedAt }.take(MAX_AUDIO_CATALOG)
            val array = JSONArray()
            bounded.forEach { item ->
                array.put(JSONObject().apply {
                    put("mediaId", item.mediaId); put("url", item.url); put("name", item.name)
                    put("sourceMessageId", item.sourceMessageId); put("durationSeconds", item.durationSeconds)
                    put("downloadedAt", item.downloadedAt)
                })
            }
            audioCatalog.edit().putString(KEY_AUDIO_CATALOG, array.toString()).apply()
        }
    }

    override suspend fun downloadedAudioReferences(): List<CachedAudioReference> = withContext(Dispatchers.IO) {
        readAudioCatalog().filter { reference ->
            val file = File(categoryDir(MediaKind.AUDIO), fileName(reference.mediaId, MediaKind.AUDIO))
            file.exists() && file.length() > 0
        }.sortedByDescending { it.downloadedAt }
    }

    private fun readAudioCatalog(): List<CachedAudioReference> = runCatching {
        val array = JSONArray(audioCatalog.getString(KEY_AUDIO_CATALOG, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(CachedAudioReference(
                    mediaId = item.optString("mediaId"), url = item.optString("url"),
                    name = item.optString("name"), sourceMessageId = item.optString("sourceMessageId"),
                    durationSeconds = item.optInt("durationSeconds"), downloadedAt = item.optLong("downloadedAt")
                ))
            }
        }.filter { it.mediaId.isNotBlank() && it.url.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun resolveFile(mediaId: String): File? {
        MediaKind.entries.forEach { kind ->
            val f = File(categoryDir(kind), fileName(mediaId, kind))
            if (f.exists() && f.length() > 0) return f
        }
        return null
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
        if (tempCleanupStarted) return
        tempCleanupStarted = true
        rootDir.listFiles()?.forEach { cat ->
            cat.listFiles()?.forEach { f ->
                if (f.name.endsWith(".tmp")) f.delete()
            }
        }
    }

    private fun doDownload(mediaId: String, url: String, kind: MediaKind): Flow<MediaDownloadState> =
        callbackFlow {
            val dir = categoryDir(kind)
            val finalFile = File(dir, fileName(mediaId, kind))
            
            // Check immediately on whatever thread this starts on, but inside flow builder
            // Actually, we should do this in ioScope to avoid blocking caller.
            val job = ioScope.launch {
                if (finalFile.exists() && finalFile.length() > 0) {
                    resolvedFiles[mediaId] = finalFile
                    downloadedIds += mediaId
                    trySend(MediaDownloadState.Downloaded)
                    close()
                    return@launch
                }

                trySend(MediaDownloadState.Downloading(0f))
                var tmp: File? = null
                downloadLimiter.withPermit {
                    try {
                        // Re-check just in case another thread downloaded it
                        if (finalFile.exists() && finalFile.length() > 0) {
                            resolvedFiles[mediaId] = finalFile
                            downloadedIds += mediaId
                            trySend(MediaDownloadState.Downloaded)
                            close()
                            return@withPermit
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
                                    var lastEmit = 0L
                                    var lastPercent = -1
                                    while (read != -1) {
                                        sink.write(buffer, 0, read)
                                        downloaded += read
                                        if (total > 0) {
                                            val pct = downloaded.toFloat() / total
                                            val intPct = (pct * 100).toInt()
                                            val now = System.currentTimeMillis()
                                            // Throttle: emite como máximo ~10 veces/seg o por percentil.
                                            if (intPct != lastPercent && now - lastEmit >= 100) {
                                                lastPercent = intPct
                                                lastEmit = now
                                                trySend(MediaDownloadState.Downloading(pct))
                                            }
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
                        resolvedFiles[mediaId] = finalFile
                        downloadedIds += mediaId
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
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private companion object {
        const val KEY_AUDIO_CATALOG = "audio_references"
        const val MAX_AUDIO_CATALOG = 500
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
