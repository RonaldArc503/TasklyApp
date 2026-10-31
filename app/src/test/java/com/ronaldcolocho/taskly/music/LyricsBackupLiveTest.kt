package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.data.music.*
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsBackupLiveTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun eachBackupCanReturnRealLyrics() = runBlocking {
        assumeTrue(System.getenv("TASKLY_LYRICS_BACKUP_LIVE_TEST") == "1")
        for (provider in listOf(LyricsSource.KUGOU, LyricsSource.BETTER)) {
            val events = mutableListOf<LyricsRequestEvent>()
            val repo = LyricsRepository(OkHttpClient(), folder.newFolder(), sourceOrder = listOf(provider), diagnostic = { events += it })
            val result = repo.lookup(LyricsQuery("Never Gonna Give You Up", "Rick Astley", 213))
            println("${provider.label}: status=${events.map { it.status }}; calls=${events.size}")
            assertTrue("${provider.label} debe devolver una letra; resultado=${result::class.simpleName}", result is LyricsLookup.Found)
            val song = (result as LyricsLookup.Found).lyrics
            assertEquals(provider.label, song.provider)
            assertTrue(song.lines.size > 2)
            println("${provider.label}: ${song.lines.size} líneas sincronizadas; contenido no impreso")
        }
    }
}
