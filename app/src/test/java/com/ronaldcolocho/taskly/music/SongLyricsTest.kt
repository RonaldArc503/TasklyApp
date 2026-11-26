package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SongLyricsTest {
    @Test fun collaborationLabelsAndArtistSeparatorsShareAnIdentity() {
        val english = LyricsQuery("Necio (feat. Carlos Santana)", "Romeo Santos", 264)
        val spanish = LyricsQuery("Necio (con Carlos Santana)", "Romeo Santos、Carlos Santana", 264)
        assertEquals("Necio", english.searchTitle)
        assertEquals("Romeo Santos", spanish.searchArtist)
        assertEquals(english.key, spanish.key)
        assertTrue(lyricsMetadataMatches("Necio", "Romeo Santos、Carlos Santana", english))
        assertTrue(lyricsMetadataMatches("Necio (ft. Carlos Santana)", "Romeo Santos", spanish))
        assertTrue(lyricsMetadataMatches("Necio", "Romeo Santos", english))
    }

    @Test fun collaborationMatchingKeepsRecordingVersionAndKnownGuests() {
        val query = LyricsQuery("Song (feat. Guest)", "Artist", 200)
        assertFalse(lyricsMetadataMatches("Song (feat. Other)", "Artist", query))
        assertFalse(lyricsMetadataMatches("Song (Live)", "Artist、Guest", query))
        assertFalse(lyricsMetadataMatches("Song (Remix)", "Artist、Guest", query))
        assertFalse(lyricsMetadataMatches("Song", "Other Artist、Guest", query))
        assertTrue(lyricsMetadataMatches("Song (Live)", "Artist、Guest", query.copy(title = "Song ft. Guest (Live)")))
        assertNotEquals(query.key, query.copy(title = "Song (Live) (feat. Guest)").key)
        assertNull(selectLyrics(listOf(LyricsCandidate("Song", "Artist、Guest", 205.0, SongLyrics("Fixture"))), query))
    }

    @Test fun parsesFractionsRepeatedTimestampsAndSortedLines() {
        val lines = parseLrc("[ar:Artista]\n[00:20.125]Tercera\n[00:01.5][00:10.05]Repetida\n[00:04]Segunda\n[99:99]Inválida")
        assertEquals(listOf(1500L, 4000L, 10050L, 20125L), lines.map { it.timeMs })
        assertEquals(listOf("Repetida", "Segunda", "Repetida", "Tercera"), lines.map { it.text })
    }

    @Test fun groupsSimultaneousTextAndKeepsInstrumentalGaps() {
        val lines = parseLrc("[00:01]Una\n[00:01]Otra\n[00:03]\n[00:05]Regresa")
        assertEquals("Una\nOtra", lines[0].text)
        assertEquals("", lines[1].text)
        assertEquals(1, activeLyricsLine(lines, 4200))
    }

    @Test fun activeLineFollowsForwardAndBackwardSeeksAndPrelude() {
        val lines = parseLrc("[00:10]A\n[00:20]B\n[00:30]C")
        assertEquals(-1, activeLyricsLine(lines, 9000))
        assertEquals(0, activeLyricsLine(lines, 10_000))
        assertEquals(2, activeLyricsLine(lines, 55_000))
        assertEquals(0, activeLyricsLine(lines, 15_000))
        assertEquals(-1, activeLyricsLine(emptyList(), 50_000))
    }

    @Test fun stripsPresentationLabelsButPreservesDifferentVersions() {
        assertEquals(LyricsQuery("Canción", "Artista", 200), LyricsQuery.from("Artista - Canción (Official Video).mp3", "", 200))
        assertEquals("Canción (Live)", LyricsQuery.from("Canción (Live)", "Artista - Topic", 200).title)
        assertEquals("Artista", LyricsQuery.from("Canción", "Artista - Topic", 200).artist)
    }

    @Test fun avoidsWrongArtistsDurationsAndLiveVersions() {
        val song = SongLyrics("Texto")
        val query = LyricsQuery("Canción", "Artista", 200)
        val wrong = listOf(LyricsCandidate("Canción", "Otro", 200.0, song),
            LyricsCandidate("Canción", "Artista", 225.0, song),
            LyricsCandidate("Canción (Live)", "Artista", 200.0, song))
        assertNull(selectLyrics(wrong, query))
        assertEquals(song, selectLyrics(wrong + LyricsCandidate("CANCION", "ARTISTA", 202.0, song), query))
    }

    @Test fun missingArtistNeedsDurationAndUnambiguousResult() {
        val candidates = listOf(LyricsCandidate("Song", "One", 100.0, SongLyrics("First")),
            LyricsCandidate("Song", "Two", 100.0, SongLyrics("Second")))
        assertNull(selectLyrics(candidates, LyricsQuery("Song", "", 100)))
        assertNull(selectLyrics(candidates.take(1), LyricsQuery("Song", "", 0)))
        assertNotNull(selectLyrics(candidates.take(1), LyricsQuery("Song", "", 100)))
    }

    @Test fun prefersSyncedLyricsAndRecognizesInstrumental() {
        val synced = SongLyrics(syncedText = "[00:01]Texto\n[00:02]Otro verso")
        val query = LyricsQuery("Song", "Artist", 100)
        assertEquals(synced, selectLyrics(listOf(LyricsCandidate("Song", "Artist", 100.0, SongLyrics("Texto")),
            LyricsCandidate("Song", "Artist", 100.0, synced)), query))
        assertTrue(SongLyrics(instrumental = true).usable)
        assertFalse(SongLyrics().usable)
    }

    @Test fun incompleteSyncedRecordsFallBackToCompletePlainText() {
        val text = "Una línea completa de la canción con palabras para leer. ".repeat(5)
        val lyrics = SongLyrics(text, "[00:00]Fragmento")
        assertTrue(lyrics.lines.isEmpty())
        assertEquals(text, lyrics.displayText)
    }

    @Test fun translationKeepsThePlaybackRowForRepeatsAndInstrumentalGaps() {
        val lyrics = SongLyrics(
            plainText = "Different formatting for the plain lyric",
            syncedText = "[00:07]Last line\n[00:01][00:05]Repeated line\n[00:03]"
        )

        val rows = lyrics.translationLines()
        assertEquals(listOf("Repeated line", "", "Repeated line", "Last line"), rows)
        assertEquals("Repeated line", rows[activeLyricsLine(lyrics.lines, 5_000)])
    }

    @Test fun translationKeepsPlainTextLineBreaksWithoutAddingTimestamps() {
        val lyrics = SongLyrics(plainText = "First line\r\n\r\nSecond line")

        assertEquals(listOf("First line", "", "Second line"), lyrics.translationLines())
        assertTrue(lyrics.lines.isEmpty())
    }
}
