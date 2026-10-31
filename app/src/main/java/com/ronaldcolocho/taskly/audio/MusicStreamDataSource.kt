package com.ronaldcolocho.taskly.audio

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.ronaldcolocho.taskly.data.music.OnlineMusicRepository
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import java.io.IOException

/** Resolves on the Media3 loader thread, after the outer cache has served any local ranges. */
internal class MusicStreamDataSource(
    private val upstream: DataSource,
    private val music: OnlineMusicRepository
) : DataSource {
    private var publicReference: Uri? = null
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val reference = dataSpec.uri.toString()
        publicReference = null
        if (OnlineMusicSource.youtubeId(reference) == null) return upstream.open(dataSpec)
        publicReference = dataSpec.uri
        for (attempt in 0..1) {
            val stream = try { music.resolve(reference, refresh = attempt > 0) }
            catch (error: Exception) {
                throw IOException("No se pudo obtener el audio de YouTube. Reintenta o elige otra canción.")
            }
            val resolved = dataSpec.withUri(Uri.parse(stream.url))
                .withRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
            val remaining = stream.contentLength?.minus(dataSpec.position)?.takeIf { it > 0 }
            val bounded = if (resolved.length == C.LENGTH_UNSET.toLong() && remaining != null)
                resolved.buildUpon().setLength(remaining).build() else resolved
            try {
                return upstream.open(bounded)
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                upstream.close()
                if (attempt > 0 || error.responseCode !in listOf(403, 410, 416))
                    throw IOException("YouTube rechazó el audio (HTTP ${error.responseCode}). Reintenta más tarde.")
            }
        }
        throw IOException("Audio no disponible.")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) = upstream.read(buffer, offset, length)
    // CacheDataSource must not persist a signed CDN URL as a redirect for the stable reference.
    override fun getUri(): Uri? = publicReference ?: upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() {
        try { upstream.close() } finally { publicReference = null }
    }
}
