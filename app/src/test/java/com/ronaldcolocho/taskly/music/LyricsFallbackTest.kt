package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.data.music.*
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LyricsFallbackTest {
    @get:Rule val folder = TemporaryFolder()
    private val query = LyricsQuery("Song", "Artist", 200)
    private val lrc = "[00:01]First fixture line\n[00:05]Second fixture line"
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s">First fixture line</p><p begin="00:05.00">Second fixture line</p></div></body></tt>"""
    private fun record(title: String = "Song") = """{"trackName":"$title","artistName":"Artist","duration":200,"syncedLyrics":"[00:01]One\n[00:05]Two"}"""
    private fun response(request: Request, code: Int, body: String = "{}", retry: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
            .body(body.toResponseBody("application/json".toMediaType())).apply { retry?.let { header("Retry-After", it) } }.build()

    @Test fun necioCatalogCreditsMatchAndSpanishCreditsReuseOfflineCache() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val r = chain.request()
            response(r, 200, when {
                r.url.encodedPath.endsWith("/song") -> """{"data":{"info":[{"songname":"Necio","singername":"Romeo Santos、Carlos Santana","duration":264,"hash":"0123456789abcdef0123456789abcdef"}]}}"""
                r.url.encodedPath == "/search" -> """{"candidates":[{"song":"Necio","singer":"Romeo Santos","id":"42","accesskey":"fixture-key","duration":264430}]}"""
                else -> """{"content":"${Base64.getEncoder().encodeToString(lrc.toByteArray())}"}"""
            })
        }.build()
        val directory = folder.newFolder()
        val query = LyricsQuery("Necio (feat. Carlos Santana)", "Romeo Santos", 264)
        val found = LyricsRepository(http, directory, sourceOrder = listOf(LyricsSource.KUGOU)).lookup(query)
        assertTrue(found is LyricsLookup.Found)
        assertEquals(3, calls)
        val offline = OkHttpClient.Builder().addInterceptor { throw AssertionError("Cache must avoid network") }.build()
        assertEquals(found, LyricsRepository(offline, directory).lookup(
            LyricsQuery("Necio (con Carlos Santana)", "Romeo Santos & Carlos Santana", 264)))
    }

    @Test fun emptyCatalogFallsBackToKeywordLyricsWithValidatedMetadata() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request(); paths += r.url.encodedPath
            response(r, 200, when {
                r.url.encodedPath.endsWith("/song") -> """{"data":{"info":[]}}"""
                r.url.encodedPath == "/search" -> {
                    assertEquals("Song - Artist", r.url.queryParameter("keyword"))
                    assertEquals("200000", r.url.queryParameter("duration"))
                    """{"candidates":[{"song":"Song (Live)","singer":"Artist","id":"1","accesskey":"wrong","duration":200000},{"song":"Song","singer":"Other","id":"2","accesskey":"wrong","duration":200000},{"song":"Song","singer":"Artist","id":"42","accesskey":"fixture-key","duration":200100}]}"""
                }
                else -> {
                    assertEquals("42", r.url.queryParameter("id"))
                    """{"content":"${Base64.getEncoder().encodeToString(lrc.toByteArray())}"}"""
                }
            })
        }.build()
        assertTrue(LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.KUGOU)).lookup(query) is LyricsLookup.Found)
        assertEquals(listOf("/api/v3/search/song", "/search", "/download"), paths)
    }

    @Test fun directLyricsNeverDownloadsCandidatesWithUnknownMetadataOrWrongDuration() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val r = chain.request()
            assertFalse(r.url.encodedPath.endsWith("/download"))
            response(r, 200, if (r.url.encodedPath.endsWith("/song")) """{"data":{"info":[]}}"""
                else """{"candidates":[{"id":"1","accesskey":"fixture","duration":200000},{"song":"Song","singer":"Artist","id":"2","accesskey":"fixture","duration":210000}]}""")
        }.build()
        assertEquals(LyricsLookup.NotFound, LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.KUGOU)).lookup(query))
        assertEquals(2, calls)
    }

    @Test fun rejectedQueryShowsItsSourceAndDoesNotBlockAnotherSong() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val r = chain.request()
            if (r.url.queryParameter("track_name") == "Rejected") response(r, 401)
            else response(r, 200, record())
        }.build()
        val repo = LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.LRCLIB))
        val failed = repo.lookup(query.copy(title = "Rejected")) as LyricsLookup.Failed
        assertTrue(failed.message.contains("LRCLIB: consulta rechazada (HTTP 401)"))
        assertFalse(failed.autoRetry)
        assertFalse(failed.message.contains("ocupado"))
        assertEquals(failed, repo.lookup(query.copy(title = "Rejected"), refresh = true))
        assertEquals(1, calls)
        assertTrue(repo.lookup(query) is LyricsLookup.Found)
        assertEquals(2, calls)
    }

    @Test fun timeoutsKeepTheirReasonDuringProviderCooldown() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { calls++; throw java.io.InterruptedIOException("timeout") }.build()
        val repo = LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.LRCLIB))
        val first = repo.lookup(query) as LyricsLookup.Failed
        val second = repo.lookup(query.copy(title = "Another song")) as LyricsLookup.Failed
        assertTrue(first.message.contains("tiempo de espera agotado"))
        assertTrue(second.message.contains("tiempo de espera agotado"))
        assertTrue(first.autoRetry)
        assertFalse(second.message.contains("ocupado"))
        assertEquals(1, calls)
    }

    @Test fun lrclibBusyFallsBackToKugouAndStopsBeforeBetterLyrics() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request(); paths += r.url.host + r.url.encodedPath
            when {
                r.url.host == "lrclib.net" -> response(r, 503, retry = "60")
                r.url.encodedPath.endsWith("/song") -> response(r, 200,
                    """{"data":{"info":[{"songname":"Song","singername":"Artist","duration":200,"hash":"0123456789abcdef0123456789abcdef"}]}}""")
                r.url.encodedPath == "/search" -> response(r, 200,
                    """{"candidates":[{"id":"42","accesskey":"fixture-key","duration":200000}]}""")
                else -> response(r, 200, """{"content":"${Base64.getEncoder().encodeToString(lrc.toByteArray())}"}""")
            }
        }.build()
        val directory = folder.newFolder()
        val result = LyricsRepository(http, directory).lookup(query) as LyricsLookup.Found
        assertEquals("KuGou", result.lyrics.provider)
        assertEquals(2, result.lyrics.lines.size)
        assertEquals(4, paths.size)
        assertFalse(paths.any { "boidu" in it })
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("Offline") }.build()
        assertEquals(result, LyricsRepository(offline, directory).lookup(query, refresh = true))
    }

    @Test fun independentCooldownLetsBetterLyricsServeWhenOtherProvidersAreBusy() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            if (r.url.host == "lyrics-api.boidu.dev") response(r, 200,
                com.google.gson.JsonObject().apply { addProperty("ttml", ttml) }.toString())
            else response(r, 503, retry = "60")
        }.build()
        val result = LyricsRepository(http, folder.newFolder()).lookup(query) as LyricsLookup.Found
        assertEquals("BetterLyrics", result.lyrics.provider)
        assertEquals(1000, result.lyrics.lines.first().timeMs.toInt())
    }

    @Test fun matchingPlainLyricsReturnImmediatelyWithoutQueryingEveryProvider() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            response(chain.request(), 200, """{"trackName":"Song","artistName":"Artist","duration":200,"plainLyrics":"Plain fixture"}""")
        }.build()
        val result = LyricsRepository(http, folder.newFolder()).lookup(query)
        assertTrue(result is LyricsLookup.Found)
        assertEquals(1, calls)
    }

    @Test fun tenConcurrentReadersShareOneLookupAndCancellationOfOneDoesNotCancelOthers() = runBlocking {
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet(); started.countDown()
            release.await(3, TimeUnit.SECONDS)
            response(chain.request(), 200, record())
        }.build()
        val repo = LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.LRCLIB))
        val readers = (1..10).map { async(start = CoroutineStart.UNDISPATCHED) { repo.lookup(query) } }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        readers.first().cancelAndJoin()
        release.countDown()
        assertTrue(readers.drop(1).awaitAll().all { it is LyricsLookup.Found })
        assertEquals(1, calls.get())
    }

    @Test fun leavingTheOnlyReaderCancelsTheUnderlyingHttpCall() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(10)
            if (chain.call().isCanceled()) cancelled.countDown()
            throw java.io.IOException("Cancelled fixture")
        }.build()
        val repo = LyricsRepository(http, folder.newFolder(), sourceOrder = listOf(LyricsSource.LRCLIB))
        val reader = async(start = CoroutineStart.UNDISPATCHED) { repo.lookup(query) }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        reader.cancelAndJoin()
        assertTrue(cancelled.await(3, TimeUnit.SECONDS))
    }

    @Test fun perMinuteBudgetBoundsRequestsAcrossDifferentSongs() = runBlocking {
        var clock = 100_000L
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            response(chain.request(), 200, record(chain.request().url.queryParameter("track_name")!!))
        }.build()
        val repo = LyricsRepository(http, folder.newFolder(), { clock }, listOf(LyricsSource.LRCLIB))
        repeat(20) {
            assertTrue(repo.lookup(query.copy(title = "Song $it")) is LyricsLookup.Found)
            clock += 1000
        }
        val limit = repo.lookup(query.copy(title = "Over budget")) as LyricsLookup.Failed
        assertTrue(limit.retryAtMs!! >= 160_000)
        assertEquals(20, calls)
        clock = 161_000
        assertTrue(repo.lookup(query.copy(title = "New minute")) is LyricsLookup.Found)
        assertEquals(21, calls)
    }

    @Test fun busyIsNotCachedAsMissingAndRapidRetryReusesTheSameDeadline() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain -> calls++; response(chain.request(), 503, retry = "1") }.build()
        val directory = folder.newFolder()
        val repo = LyricsRepository(http, directory, sourceOrder = listOf(LyricsSource.LRCLIB))
        val first = repo.lookup(query) as LyricsLookup.Failed
        val second = repo.lookup(query, refresh = true)
        assertEquals(first, second)
        assertEquals(1, calls)
        assertTrue(first.retryAtMs!! > System.currentTimeMillis())
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun ttmlParserPreservesTimingAndRejectsExternalDeclarations() {
        assertEquals(listOf(1000L, 5000L), parseTtmlLyrics(ttml).map { it.timeMs })
        assertEquals("First fixture line", parseTtmlLyrics(ttml).first().text)
        assertEquals(17393L, parseTtmlLyrics(ttml.replace("1s", "17.393")).last().timeMs)
        assertTrue(runCatching { parseTtmlLyrics("""<!DOCTYPE tt [<!ENTITY secret SYSTEM "file:///secret">]><tt><body><p begin="1s">&secret;</p></body></tt>""") }.isFailure)
    }
}
