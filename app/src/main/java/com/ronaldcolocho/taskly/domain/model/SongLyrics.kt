package com.ronaldcolocho.taskly.domain.model

import java.text.Normalizer
import java.util.Locale

data class LyricsLine(val timeMs: Long, val text: String)

data class SongLyrics(
    val plainText: String = "", val syncedText: String = "",
    val instrumental: Boolean = false, val recordId: Long? = null, val provider: String = "LRCLIB"
) {
    val lines: List<LyricsLine> = parseLrc(syncedText).takeIf { parsed ->
        val timedCharacters = parsed.sumOf { line -> line.text.count { !it.isWhitespace() } }
        val plainCharacters = plainText.count { !it.isWhitespace() }
        parsed.count { it.text.isNotBlank() } >= 2 && (plainCharacters < 80 || timedCharacters >= plainCharacters / 2)
    }.orEmpty()
    val displayText: String = plainText.ifBlank { lines.map { it.text }.filter { it.isNotBlank() }.joinToString("\n") }
    val usable: Boolean get() = instrumental || displayText.isNotBlank()
}

data class LyricsQuery(val title: String, val artist: String, val durationSeconds: Int) {
    val key: String get() = "${normalizeLyricsText(title)}|${normalizeLyricsText(artist)}|$durationSeconds"

    companion object {
        fun from(name: String, artist: String, durationSeconds: Int): LyricsQuery {
            var title = name.replace(Regex("\\.(mp3|m4a|wav|ogg|opus|flac|audio)$", RegexOption.IGNORE_CASE), "").trim()
            var performer = artist.replace(Regex("\\s*[-–]\\s*Topic$", RegexOption.IGNORE_CASE), "").trim()
            if (performer.isBlank()) {
                val parts = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
                if (parts.size == 2) { performer = parts[0].trim(); title = parts[1].trim() }
            } else if (title.startsWith("$performer - ", ignoreCase = true)) title = title.substring(performer.length + 3)
            // Strip presentation labels, while preserving remix/live/acoustic version names.
            title = title.replace(Regex("[\\[(](?:official (?:music )?(?:video|audio)|lyrics?|letra|audio oficial|video oficial)[\\])]",
                RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim()
            return LyricsQuery(title, performer, durationSeconds.coerceAtLeast(0))
        }
    }
}

data class LyricsCandidate(val title: String, val artist: String, val durationSeconds: Double, val lyrics: SongLyrics)

fun normalizeLyricsText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

fun selectLyrics(candidates: List<LyricsCandidate>, query: LyricsQuery): SongLyrics? {
    val matches = candidates.filter {
        it.lyrics.usable && normalizeLyricsText(it.title) == normalizeLyricsText(query.title) &&
            (query.artist.isBlank() || normalizeLyricsText(it.artist) == normalizeLyricsText(query.artist)) &&
            (query.durationSeconds <= 0 || kotlin.math.abs(it.durationSeconds - query.durationSeconds) <= 2.0)
    }
    if (query.artist.isBlank() && (query.durationSeconds <= 0 || matches.map { normalizeLyricsText(it.artist) }.distinct().size != 1))
        return null
    return matches.sortedWith(compareBy<LyricsCandidate> { if (it.lyrics.lines.isNotEmpty()) 0 else 1 }
        .thenBy { kotlin.math.abs(it.durationSeconds - query.durationSeconds) }).firstOrNull()?.lyrics
}

fun parseLrc(text: String): List<LyricsLine> {
    val timestamp = Regex("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    return text.lineSequence().take(10_000).flatMap { line ->
        val times = timestamp.findAll(line).toList()
        val words = if (times.isEmpty()) "" else line.substring(times.last().range.last + 1).trim()
        times.asSequence().map { time ->
            val fraction = time.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            LyricsLine(time.groupValues[1].toLong() * 60_000 + time.groupValues[2].toLong() * 1000 + fraction, words)
        }
    }.groupBy { it.timeMs }.toSortedMap().map { (time, lines) ->
        LyricsLine(time, lines.map { it.text }.distinct().joinToString("\n"))
    }
}

fun activeLyricsLine(lines: List<LyricsLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle - 1
    }
    return high
}

sealed interface LyricsLookup {
    data class Found(val lyrics: SongLyrics) : LyricsLookup
    data object NotFound : LyricsLookup
    data class Failed(val message: String, val retryAtMs: Long? = null) : LyricsLookup
}
