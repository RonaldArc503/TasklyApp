package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.data.music.YoutubeMusicClient
import com.ronaldcolocho.taskly.data.music.OnlineMusicRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Opt-in network smoke test; ordinary unit runs remain offline. */
class YoutubeMusicLiveTest {
    @Test fun songRadioReturnsRelatedTracksWithCurrentMetadata() {
        assumeTrue(System.getenv("TASKLY_MUSIC_RADIO_LIVE_TEST") == "1")
        val source = "https://www.youtube.com/watch?v=DXiXPhvYuNU"
        val related = OnlineMusicRepository(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()).related(source)
        assertTrue("La radio debe devolver canciones similares", related.isNotEmpty())
        assertTrue(related.all { it.url != source && it.name.isNotBlank() && it.thumbnailUrl.isNotBlank() })
        assertTrue("El catálogo musical debe entregar al menos un artista identificado", related.any { it.artist.isNotBlank() })
        assertTrue(related.all { it.artist.isBlank() || "LETRAS4K" !in it.artist })
        assertEquals(related.size, related.map { it.id }.distinct().size)
        println("Radio de Necio: ${related.size} canciones; primeras=${related.take(3).map { it.name to it.artist }}")
    }

    @Test fun searchAndReadAudioRange() {
        assumeTrue(System.getenv("TASKLY_MUSIC_LIVE_TEST") == "1")
        val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
        val client = YoutubeMusicClient(http)
        val results = client.search("Kevin MacLeod Carefree")
        assertTrue("La búsqueda debe devolver canciones", results.tracks.isNotEmpty())
        val track = results.tracks.first()
        println("Búsqueda real: ${results.tracks.size} resultados; probando ${track.id}")
        results.nextPage?.let { page ->
            val more = client.search("Kevin MacLeod Carefree", page)
            assertTrue("La continuación debe devolver más canciones", more.tracks.isNotEmpty())
            println("Paginación real: ${more.tracks.size} resultados adicionales")
        }
        val stream = client.resolve(track.url)
        val request = Request.Builder().url(stream.url).header("Range", "bytes=0-65535")
        stream.headers.forEach { (name, value) -> request.header(name, value) }
        http.newCall(request.build()).execute().use { response ->
            assertTrue("Audio HTTP ${response.code}", response.isSuccessful)
            val firstBytes = ByteArray(1024)
            val read = response.body!!.byteStream().read(firstBytes)
            assertTrue("Debe recibir datos de audio", read > 0)
            assertFalse(response.header("Content-Type").orEmpty().contains("text/html"))
            println("Audio real recibido: HTTP ${response.code}; ${response.header("Content-Type")}; $read bytes iniciales")
        }
        val music = OnlineMusicRepository(http)
        music.openDownload(track.url).use { response ->
            assertTrue("Descarga HTTP ${response.code}", response.isSuccessful)
            val body = response.body!!
            val expected = body.contentLength()
            var total = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                }
            }
            assertTrue(total > 0)
            if (expected > 0) assertEquals(expected, total)
            println("Descarga completa real: $total bytes")
        }
    }
}
