package com.ronaldcolocho.taskly.data.music

import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.JsonObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

data class MusicSearchPage(val tracks: List<MusicTrack>, val nextPage: Page?)

class ResolvedMusicStream(
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val contentLength: Long?
) {
    override fun toString() = "ResolvedMusicStream(redacted)"
}

/** Blocking network operations; callers run on IO, including Media3's loader thread. */
class YoutubeMusicClient(client: OkHttpClient) {
    init { initialize(client) }
    private val radioHttp = client.newBuilder().callTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()

    fun search(query: String, page: Page? = null): MusicSearchPage {
        require(query.isNotBlank()) { "Escribe una canción o artista." }
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query.trim(), listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
        )
        val result = if (page == null) {
            extractor.fetchPage()
            extractor.initialPage
        } else extractor.getPage(page)
        return MusicSearchPage(tracksFrom(result.items.filterIsInstance<StreamInfoItem>()), result.nextPage?.takeIf { Page.isValid(it) })
    }

    fun related(reference: String): List<MusicTrack> {
        val id = OnlineMusicSource.youtubeId(reference) ?: throw IOException("Canción no válida.")
        val payload = JsonObject().apply {
            add("context", JsonObject().apply { add("client", JsonObject().apply {
                addProperty("clientName", "WEB_REMIX")
                addProperty("clientVersion", YoutubeParsingHelper.getYoutubeMusicClientVersion())
                addProperty("hl", "es"); addProperty("gl", "SV")
            }) })
            addProperty("videoId", id); addProperty("playlistId", "RDAMVM$id")
            addProperty("isAudioOnly", true); addProperty("enablePersistentPlaylistPanel", true)
        }
        val request = okhttp3.Request.Builder().url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        YoutubeParsingHelper.getYoutubeMusicHeaders().forEach { (name, values) -> values.forEach { request.addHeader(name, it) } }
        request.header("User-Agent", USER_AGENT)
        return radioHttp.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Radio musical HTTP ${response.code}")
            val source = response.body?.source() ?: throw IOException("Respuesta vacía de radio")
            source.request(2_097_153)
            if (source.buffer.size > 2_097_152) throw IOException("Respuesta de radio demasiado grande")
            parseMusicRadioMetadata(source.buffer.readUtf8(), id)
        }
    }

    private fun tracksFrom(items: List<StreamInfoItem>): List<MusicTrack> = items.mapNotNull { item ->
            val id = OnlineMusicSource.youtubeId(item.url) ?: return@mapNotNull null
            MusicTrack(
                id = OnlineMusicSource.trackId(id), url = OnlineMusicSource.reference(id),
                name = item.name, durationSeconds = item.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
                artist = item.uploaderName.orEmpty()
            )
        }.distinctBy { it.id }

    fun resolve(reference: String): ResolvedMusicStream {
        val id = OnlineMusicSource.youtubeId(reference) ?: throw IOException("Canción no válida.")
        val extractor = ServiceList.YouTube.getStreamExtractor(OnlineMusicSource.reference(id))
        extractor.fetchPage()
        val streams = extractor.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                it.content.toHttpUrlOrNull()?.let { url ->
                    url.isHttps && (url.host == "googlevideo.com" || url.host.endsWith(".googlevideo.com"))
                } == true
        }
        // Keep one representation across refreshes so old/new cached ranges never mix codecs or bitrates.
        val stream = streams.firstOrNull { it.itag == 140 }
            ?: throw IOException("YouTube no entregó audio M4A compatible. Intenta otra canción o vuelve a intentarlo más tarde.")
        val url = stream.content.toHttpUrlOrNull() ?: throw IOException("Enlace de audio no válido.")
        val expires = url.queryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 5 * 60_000)
        return ResolvedMusicStream(url.toString(), mapOf("User-Agent" to USER_AGENT), expires,
            stream.itagItem?.contentLength?.takeIf { it > 0 }
                ?: url.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 })
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
        private var initialized = false

        @Synchronized private fun initialize(client: OkHttpClient) {
            if (initialized) return
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val body = request.dataToSend()?.toRequestBody()
                    val builder = okhttp3.Request.Builder().url(request.url())
                        .header("User-Agent", USER_AGENT)
                    request.headers().forEach { (name, values) ->
                        builder.removeHeader(name)
                        values.forEach { builder.addHeader(name, it) }
                    }
                    builder.method(request.httpMethod(), body)
                    return client.newCall(builder.build()).execute().use { response ->
                        if (response.code == 429) throw ReCaptchaException("YouTube limitó temporalmente las solicitudes.", request.url())
                        Response(response.code, response.message, response.headers.toMultimap(),
                            response.body?.string().orEmpty(), response.request.url.toString())
                    }
                }
            }, Localization("es", "SV"))
            initialized = true
        }
    }
}
