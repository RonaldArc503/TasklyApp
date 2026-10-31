package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.data.music.LyricsRepository
import com.ronaldcolocho.taskly.data.music.LyricsSource
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val query = LyricsQuery("Song", "Artist", 200)
    private val record = """{"id":12,"trackName":"Song","artistName":"Artist","duration":200,"instrumental":false,"plainLyrics":"First\nSecond","syncedLyrics":"[00:01]First\n[00:02]Second"}"""
    private fun response(request: Request, code: Int, body: String, retry: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
            .body(body.toResponseBody("application/json".toMediaType())).apply { if (retry != null) header("Retry-After", retry) }.build()

    private fun repository(http: OkHttpClient, directory: java.io.File, now: () -> Long = System::currentTimeMillis) =
        LyricsRepository(http, directory, now, listOf(LyricsSource.LRCLIB))

    @Test fun exactLookupIdentifiesAppAndPersistsForOfflineUse() = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertTrue(chain.request().header("User-Agent")!!.startsWith("Taskly/"))
            assertEquals("200", chain.request().url.queryParameter("duration"))
            response(chain.request(), 200, record)
        }.build()
        val directory = folder.newFolder()
        val first = repository(http, directory).lookup(query)
        assertTrue(first is LyricsLookup.Found)
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("Offline") }.build()
        val restored = repository(offline, directory).lookup(query)
        assertEquals(first, restored)
        assertEquals(1, requests)
        assertTrue(repository(offline, directory).lookup(query, refresh = true) is LyricsLookup.Found)
        directory.listFiles()!!.single().writeText("{broken")
        assertTrue(repository(http, directory).lookup(query) is LyricsLookup.Found)
        assertEquals(2, requests)
    }

    @Test fun fallsBackToSearchAndRejectsMismatchingVersions() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().url.encodedPath
            if (paths.size == 1) response(chain.request(), 404, "{}")
            else response(chain.request(), 200, "[${record.replace("\"duration\":200", "\"duration\":230")}]")
        }.build()
        assertEquals(LyricsLookup.NotFound, repository(http, folder.newFolder()).lookup(query))
        assertEquals(listOf("/api/get", "/api/search"), paths)
    }

    @Test fun honorsRetryAfterWithoutAnotherRequest() = runBlocking {
        var count = 0
        var time = 100_000L
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            response(chain.request(), 429, "not JSON", "60")
        }.build()
        val repo = repository(http, folder.newFolder()) { time }
        assertTrue(repo.lookup(query) is LyricsLookup.Failed)
        time += 59_000
        assertTrue(repo.lookup(query, refresh = true) is LyricsLookup.Failed)
        assertEquals(1, count)
        time += 2000
        repo.lookup(query, refresh = true)
        assertEquals(2, count)
    }

    @Test fun notFoundCacheExpires() = runBlocking {
        var time = 100_000L
        var count = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            response(chain.request(), if (count <= 2) 404 else 200, if (count <= 2) "{}" else record)
        }.build()
        val repo = repository(http, folder.newFolder()) { time }
        assertEquals(LyricsLookup.NotFound, repo.lookup(query))
        assertEquals(LyricsLookup.NotFound, repo.lookup(query))
        assertEquals(2, count)
        time += 3_600_001
        assertTrue(repo.lookup(query) is LyricsLookup.Found)
        assertEquals(3, count)
    }

    @Test fun htmlResponsesFailSafelyAndAreNotCached() = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            response(chain.request(), 200, "<html>unavailable</html>")
        }.build()
        val directory = folder.newFolder()
        val repo = repository(http, directory)
        assertTrue(repo.lookup(query) is LyricsLookup.Failed)
        assertTrue(repo.lookup(query) is LyricsLookup.Failed)
        assertEquals(1, requests)
        assertTrue(repository(http, directory).lookup(query) is LyricsLookup.Failed)
        assertEquals(2, requests)
    }
}
