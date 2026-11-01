package com.ronaldcolocho.taskly.data.music

import com.ronaldcolocho.taskly.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

internal enum class LyricsSource(val label: String) { LRCLIB("LRCLIB"), KUGOU("KuGou"), BETTER("BetterLyrics") }

internal data class LyricsRequestEvent(val source: String, val status: Int, val elapsedMs: Long, val callsInMinute: Int)
internal class LyricsUnavailable(val availableAtMs: Long, val detail: String = "servicio ocupado", val autoRetry: Boolean = true) : IOException(detail)
internal class LyricsHttpError(val status: Int) : IOException("HTTP $status")

/** Each provider has its own serial gate, rolling quota and cooldown. */
internal class LyricsHttpTransport(
    http: OkHttpClient, private val now: () -> Long,
    private val diagnostic: (LyricsRequestEvent) -> Unit = {}
) {
    private class State {
        val mutex = Mutex()
        val calls = ArrayDeque<Long>()
        @Volatile var availableAt = 0L
        var lastCall = 0L
        var failures = 0
        var detail = "servicio ocupado"
    }
    private data class Payload(val code: Int, val retryAfter: String?, val text: String)
    private val states = LyricsSource.entries.associateWith { State() }
    private val client = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()

    fun unavailable(source: LyricsSource): LyricsUnavailable? {
        val state = states.getValue(source)
        return synchronized(state) { if (state.availableAt > now()) LyricsUnavailable(state.availableAt, state.detail) else null }
    }
    fun healthy(source: LyricsSource) {
        val state = states.getValue(source)
        synchronized(state) { state.failures = 0 }
    }

    fun failed(source: LyricsSource, retryAfter: String? = null, defaultSeconds: Long = 5, detail: String = "servicio ocupado"): Long {
        val state = states.getValue(source)
        return synchronized(state) {
        state.detail = detail
        state.failures = (state.failures + 1).coerceAtMost(6)
        val exponential = (1L shl (state.failures - 1)) * 1000
        val seconds = retryAfter?.trim()?.toLongOrNull()
        val deadline = if (seconds != null) runCatching {
            Math.addExact(now(), Math.multiplyExact(seconds.coerceAtLeast(1), 1000))
        }.getOrDefault(Long.MAX_VALUE) else runCatching {
            ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: (now() + defaultSeconds * 1000)
        val jitter = Random.nextLong(80, 301)
        state.availableAt = maxOf(state.availableAt, deadline, now() + exponential).let {
            if (it > Long.MAX_VALUE - jitter) Long.MAX_VALUE else it + jitter
        }
        state.availableAt
        }
    }

    suspend fun get(source: LyricsSource, address: String, parameters: Map<String, String>): String? {
        val state = states.getValue(source)
        return state.mutex.withLock {
            unavailable(source)?.let { throw it }
            while (state.calls.isNotEmpty() && now() - state.calls.first >= 60_000) state.calls.removeFirst()
            if (state.calls.size >= 20) {
                synchronized(state) {
                    state.availableAt = maxOf(state.availableAt, state.calls.first + 60_000)
                    state.detail = "límite de consultas por minuto alcanzado"
                }
                throw LyricsUnavailable(state.availableAt, state.detail)
            }
            val wait = 500 - (now() - state.lastCall)
            if (wait > 0) delay(wait)
            val url = address.toHttpUrl().newBuilder()
            parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
            val request = Request.Builder().url(url.build()).header("Accept", "application/json")
                .header("User-Agent", "Taskly/${BuildConfig.VERSION_NAME} (https://github.com/RonaldArc503/TasklyApp)").build()
            val started = now()
            state.calls.addLast(started)
            state.lastCall = started
            val payload = try { execute(request) } catch (cancelled: CancellationException) {
                diagnostic(LyricsRequestEvent(source.label, -1, now() - started, state.calls.size))
                throw cancelled
            } catch (error: IOException) {
                diagnostic(LyricsRequestEvent(source.label, 0, now() - started, state.calls.size))
                throw error
            }
            diagnostic(LyricsRequestEvent(source.label, payload.code, now() - started, state.calls.size))
            when {
                payload.code == 404 -> null
                payload.code == 429 || payload.code == 503 -> throw LyricsUnavailable(
                    failed(source, payload.retryAfter, if (payload.code == 429) 60 else 5))
                payload.code !in 200..299 -> throw LyricsHttpError(payload.code)
                else -> payload.text
            }
        }
    }

    private suspend fun execute(request: Request): Payload = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        // Error bodies are irrelevant; do not parse or retain them.
                        val text = if (it.isSuccessful) {
                            val body = it.body ?: throw IOException("Respuesta vacía")
                            val source = body.source()
                            source.request(1_048_577)
                            if (source.buffer.size > 1_048_576) throw IOException("Respuesta de letras demasiado grande")
                            source.buffer.readUtf8()
                        } else ""
                        Payload(it.code, it.header("Retry-After"), text)
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}
