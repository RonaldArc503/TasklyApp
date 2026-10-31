package com.ronaldcolocho.taskly.data.music

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ronaldcolocho.taskly.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository internal constructor(
    http: OkHttpClient, private val directory: File, private val now: () -> Long = System::currentTimeMillis,
    private val sourceOrder: List<LyricsSource> = LyricsSource.entries,
    diagnostic: (LyricsRequestEvent) -> Unit = {}
) {
    @Inject constructor(@ApplicationContext context: Context, http: OkHttpClient) :
        this(http, File(context.filesDir, "lyrics_lrclib_v1"), diagnostic = { event ->
            android.util.Log.d("MusicLyrics", "source=${event.source} status=${event.status} ms=${event.elapsedMs} callsPerMinute=${event.callsInMinute}")
        })

    private class Flight(val task: Deferred<LyricsLookup>, var users: Int = 0)
    private data class Recent(val result: LyricsLookup, val until: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Mutex()
    private val flights = mutableMapOf<String, Flight>()
    private val recent = ConcurrentHashMap<String, Recent>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val transport = LyricsHttpTransport(http, now, diagnostic)
    private val sources = LyricsSources(transport)
    private val sourceSignature = sourceOrder.joinToString(",") { it.label }

    /** Waiters share the same work; when the last waiter leaves, cancellation reaches OkHttp. */
    suspend fun lookup(query: LyricsQuery, refresh: Boolean = false): LyricsLookup {
        val flight = guard.withLock {
            (flights[query.key]?.takeIf { !it.task.isCompleted && !it.task.isCancelled } ?: run {
                lateinit var entry: Flight
                val task = scope.async(start = CoroutineStart.LAZY) {
                    try { fetch(query, refresh) }
                    finally {
                        withContext(NonCancellable) { guard.withLock {
                            if (flights[query.key] === entry) flights.remove(query.key)
                        } }
                    }
                }
                entry = Flight(task)
                flights[query.key] = entry
                entry
            }).also { it.users++ }
        }
        try {
            flight.task.start()
            return flight.task.await()
        } finally {
            withContext(NonCancellable) { guard.withLock {
                flight.users--
                if (flight.users == 0 && !flight.task.isCompleted) {
                    if (flights[query.key] === flight) flights.remove(query.key)
                    flight.task.cancel()
                }
            } }
        }
    }

    private suspend fun fetch(query: LyricsQuery, refresh: Boolean): LyricsLookup {
        if (query.title.isBlank() || (query.artist.isBlank() && query.durationSeconds <= 0)) return LyricsLookup.NotFound
        val cached = readCache(query)
        // A retry never discards an existing readable lyric.
        if (cached is LyricsLookup.Found || (!refresh && cached != null)) return cached
        recent[query.key]?.takeIf { it.until > now() }?.let { return it.result }
        val unavailable = mutableListOf<Long>()
        for (source in sourceOrder) {
            val missKey = "${source.name}:${query.key}"
            if (!refresh && (misses[missKey] ?: 0L) > now()) continue
            if (transport.availableAt(source) > now()) {
                unavailable += transport.availableAt(source)
                continue
            }
            try {
                val lyrics = withTimeout(6500) { sources.lookup(source, query) }
                transport.healthy(source)
                if (lyrics != null && lyrics.usable) {
                    val found = LyricsLookup.Found(lyrics)
                    writeCache(query, found)
                    recent.remove(query.key)
                    return found
                }
                misses[missKey] = now() + 15 * 60_000
                if (misses.size > 256) misses.entries.minByOrNull { it.value }?.let { misses.remove(it.key) }
            } catch (_: TimeoutCancellationException) {
                unavailable += transport.failed(source)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: LyricsUnavailable) { unavailable += error.availableAtMs }
            catch (_: Exception) { unavailable += transport.failed(source) }
        }
        val result = if (unavailable.isEmpty()) LyricsLookup.NotFound else LyricsLookup.Failed(
            "Los proveedores de letras están temporalmente ocupados o sin conexión.",
            maxOf(now() + 5000, unavailable.min()))
        if (result is LyricsLookup.NotFound) writeCache(query, result)
        recent[query.key] = Recent(result, (result as? LyricsLookup.Failed)?.retryAtMs ?: (now() + 5000))
        if (recent.size > 64) recent.entries.minByOrNull { it.value.until }?.let { recent.remove(it.key) }
        return result
    }

    private fun file(query: LyricsQuery): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(query.key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }

    private fun readCache(query: LyricsQuery): LyricsLookup? = runCatching {
        val file = file(query)
        if (!file.isFile || file.length() > 1_048_576) return null
        val json = JsonParser.parseString(file.readText()).asJsonObject
        if (!json.get("found").asBoolean) {
            return if (json.get("sources")?.asString == sourceSignature &&
                now() - json.get("savedAt").asLong in 0 until 3_600_000) LyricsLookup.NotFound else null
        }
        val song = SongLyrics(json.get("plain").asString, json.get("synced").asString,
            json.get("instrumental").asBoolean, json.get("recordId")?.takeUnless { it.isJsonNull }?.asLong,
            json.get("provider")?.asString ?: "LRCLIB")
        file.setLastModified(now())
        song.takeIf { it.usable }?.let { LyricsLookup.Found(it) }
    }.getOrNull()

    private fun writeCache(query: LyricsQuery, result: LyricsLookup) {
        runCatching {
            if (!directory.exists() && !directory.mkdirs()) return
            val json = JsonObject().apply {
                addProperty("found", result is LyricsLookup.Found)
                addProperty("savedAt", now())
                addProperty("sources", sourceSignature)
                if (result is LyricsLookup.Found) {
                    addProperty("plain", result.lyrics.plainText); addProperty("synced", result.lyrics.syncedText)
                    addProperty("instrumental", result.lyrics.instrumental); addProperty("recordId", result.lyrics.recordId)
                    addProperty("provider", result.lyrics.provider)
                }
            }
            val target = file(query)
            val temp = File(directory, target.name + ".tmp")
            temp.writeText(json.toString())
            try {
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            target.setLastModified(now())
            directory.listFiles { entry -> entry.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(128)?.forEach { it.delete() }
        }
    }

}
