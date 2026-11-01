package com.ronaldcolocho.taskly.data.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ronaldcolocho.taskly.domain.model.*
import java.util.Base64
import kotlin.math.abs

internal class LyricsSources(private val http: LyricsHttpTransport) {
    suspend fun lookup(source: LyricsSource, query: LyricsQuery): SongLyrics? = when (source) {
        LyricsSource.LRCLIB -> lrclib(query)
        LyricsSource.KUGOU -> kugou(query)
        LyricsSource.BETTER -> better(query)
    }

    private suspend fun lrclib(query: LyricsQuery): SongLyrics? {
        if (query.artist.isNotBlank()) {
            val params = mutableMapOf("track_name" to query.searchTitle, "artist_name" to query.searchArtist)
            if (query.durationSeconds in 1..3600) params["duration"] = query.durationSeconds.toString()
            val exact = http.get(LyricsSource.LRCLIB, "https://lrclib.net/api/get", params)
            if (exact != null) selectLyrics(listOf(lrcCandidate(JsonParser.parseString(exact).asJsonObject)), query)?.let { return it }
        }
        val params = mutableMapOf("track_name" to query.searchTitle)
        if (query.artist.isNotBlank()) params["artist_name"] = query.searchArtist
        val json = http.get(LyricsSource.LRCLIB, "https://lrclib.net/api/search", params) ?: return null
        return selectLyrics(JsonParser.parseString(json).asJsonArray.take(100).mapNotNull {
            runCatching { lrcCandidate(it.asJsonObject) }.getOrNull()
        }, query)
    }

    private fun lrcCandidate(json: JsonObject) = LyricsCandidate(json.text("trackName"), json.text("artistName"),
        json.number("duration"), SongLyrics(json.text("plainLyrics"), json.text("syncedLyrics"),
            json.get("instrumental")?.asBoolean ?: false, json.get("id")?.takeUnless { it.isJsonNull }?.asLong))

    private suspend fun kugou(query: LyricsQuery): SongLyrics? {
        if (query.artist.isBlank() || query.durationSeconds <= 0) return null
        // One catalog candidate, one optional keyword fallback and one download keep this bounded.
        val json = http.get(LyricsSource.KUGOU, "https://mobileservice.kugou.com/api/v3/search/song", mapOf(
            "version" to "9108", "plat" to "0", "pagesize" to "5", "showtype" to "0",
            "keyword" to "${query.title} - ${query.artist}"))
        val songs = json?.let { JsonParser.parseString(it).asJsonObject.getAsJsonObject("data")?.getAsJsonArray("info") }
        val song = songs?.map { it.asJsonObject }?.filter {
            lyricsMetadataMatches(it.text("songname"), it.text("singername"), query) &&
                abs(it.number("duration") - query.durationSeconds) <= 2
        }?.minByOrNull { abs(it.number("duration") - query.durationSeconds) }
        val hash = song?.text("hash")?.takeIf { it.matches(Regex("[A-Fa-f0-9]{32}")) }
        fun candidate(search: String?, catalogValidated: Boolean): JsonObject? = search?.let {
            JsonParser.parseString(it).asJsonObject.getAsJsonArray("candidates")?.map { value -> value.asJsonObject }?.firstOrNull { value ->
                abs(value.number("duration") / 1000.0 - query.durationSeconds) <= 2 &&
                    value.text("accesskey").isNotBlank() && value.text("id").toLongOrNull() != null &&
                    (if (value.text("song").isBlank() || value.text("singer").isBlank()) catalogValidated
                     else lyricsMetadataMatches(value.text("song"), value.text("singer"), query))
            }
        }
        val byHash = hash?.let { candidate(http.get(LyricsSource.KUGOU, "https://lyrics.kugou.com/search", mapOf(
            "ver" to "1", "man" to "yes", "client" to "pc", "hash" to it)), catalogValidated = true) }
        val candidate = byHash ?: candidate(http.get(LyricsSource.KUGOU, "https://lyrics.kugou.com/search", mapOf(
            "ver" to "1", "man" to "yes", "client" to "pc", "duration" to (query.durationSeconds * 1000L).toString(),
            "keyword" to "${query.searchTitle} - ${query.searchArtist}")), catalogValidated = false) ?: return null
        val downloaded = http.get(LyricsSource.KUGOU, "https://lyrics.kugou.com/download", mapOf(
            "fmt" to "lrc", "charset" to "utf8", "client" to "pc", "ver" to "1",
            "id" to candidate.text("id"), "accesskey" to candidate.text("accesskey"))) ?: return null
        val text = String(Base64.getDecoder().decode(JsonParser.parseString(downloaded).asJsonObject.text("content")), Charsets.UTF_8)
        val lyrics = SongLyrics(syncedText = text, recordId = candidate.text("id").toLongOrNull(), provider = LyricsSource.KUGOU.label)
        return lyrics.takeIf { it.usable && it.lines.lastOrNull()?.timeMs?.let { time -> time <= (query.durationSeconds + 2) * 1000L } == true }
    }

    private suspend fun better(query: LyricsQuery): SongLyrics? {
        if (query.artist.isBlank() || query.durationSeconds <= 0) return null
        val json = http.get(LyricsSource.BETTER, "https://lyrics-api.boidu.dev/getLyrics", mapOf(
            "s" to query.searchTitle, "a" to query.searchArtist, "d" to query.durationSeconds.toString())) ?: return null
        val ttml = JsonParser.parseString(json).asJsonObject.text("ttml")
        if (ttml.isBlank()) return null
        val lines = parseTtmlLyrics(ttml)
        if (lines.isEmpty() || lines.last().timeMs > (query.durationSeconds + 2) * 1000L) return null
        val lrc = lines.joinToString("\n") {
            "[${it.timeMs / 60_000}:${((it.timeMs / 1000) % 60).toString().padStart(2, '0')}.${(it.timeMs % 1000).toString().padStart(3, '0')}]${it.text.replace('\n', ' ')}"
        }
        return SongLyrics(syncedText = lrc, provider = LyricsSource.BETTER.label).takeIf { it.usable }
    }
}

private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.number(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0
