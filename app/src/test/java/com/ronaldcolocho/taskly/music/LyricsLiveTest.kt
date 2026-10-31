package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.data.music.LyricsRepository
import com.ronaldcolocho.taskly.data.music.LyricsSource
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsLiveTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun readsRealLyricsAndReopensFromDiskOffline() = runBlocking {
        assumeTrue(System.getenv("TASKLY_LYRICS_LIVE_TEST") == "1")
        val directory = folder.newFolder()
        val query = LyricsQuery("Never Gonna Give You Up", "Rick Astley", 213)
        val result = LyricsRepository(OkHttpClient(), directory, sourceOrder = listOf(LyricsSource.LRCLIB)).lookup(query)
        assertTrue("La consulta real debe obtener letras: $result", result is LyricsLookup.Found)
        val song = (result as LyricsLookup.Found).lyrics
        assertTrue(song.displayText.isNotBlank())
        println("LRCLIB real: synced=${song.lines.isNotEmpty()}, lines=${song.lines.size}; contenido no impreso")
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("Offline") }.build()
        val cached = LyricsRepository(offline, directory).lookup(query)
        assertEquals(result, cached)
        println("Caché persistida disponible sin red")
    }
}
