package com.ronaldcolocho.taskly.data.music

import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.Page
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepository @Inject constructor(private val http: OkHttpClient) {
    private val client by lazy { YoutubeMusicClient(http) }
    private val streams = object : LinkedHashMap<String, ResolvedMusicStream>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolvedMusicStream>) = size > 64
    }

    fun search(query: String, page: Page? = null) = client.search(query, page)

    @Synchronized fun resolve(reference: String, refresh: Boolean = false): ResolvedMusicStream {
        val id = OnlineMusicSource.youtubeId(reference) ?: throw IOException("Canción no válida.")
        if (!refresh) streams[id]?.takeIf { it.expiresAtMs > System.currentTimeMillis() + 60_000 }?.let { return it }
        streams.remove(id)
        return client.resolve(reference).also { streams[id] = it }
    }

    /** Downloads retain the stable reference in the catalog and resolve only at network access. */
    fun openDownload(reference: String): okhttp3.Response {
        for (attempt in 0..1) {
            val stream = resolve(reference, refresh = attempt > 0)
            val request = Request.Builder().url(stream.url)
            stream.headers.forEach { (name, value) -> request.header(name, value) }
            stream.contentLength?.let { request.header("Range", "bytes=0-${it - 1}") }
            val response = http.newCall(request.build()).execute()
            if (attempt == 0 && response.code in listOf(403, 410, 416)) response.close()
            else {
                if (response.isSuccessful && stream.contentLength != null &&
                    response.body?.contentLength()?.takeIf { it > 0 }?.let { it != stream.contentLength } == true) {
                    response.close()
                    throw IOException("YouTube entregó una descarga incompleta. Vuelve a intentarlo.")
                }
                return response
            }
        }
        throw IOException("No se pudo descargar la canción.")
    }
}
