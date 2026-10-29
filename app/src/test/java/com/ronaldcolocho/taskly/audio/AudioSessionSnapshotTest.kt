package com.ronaldcolocho.taskly.audio

import org.junit.Assert.*
import org.junit.Test

class AudioSessionSnapshotTest {
    private fun track(id: String) = AudioTrack(id, "https://audio.example/$id.mp3", "Canción $id", "message-$id",
        240, "https://audio.example/cover.jpg", "playlist")
    private val a = track("a")
    private val b = track("b")
    private val c = track("c")

    @Test fun sessionRoundTripKeepsQueueAndPositionButRestoresPaused() {
        val playing = AudioPlayerState(listOf(a, b, c), b, playing = true, mode = AudioMode.LOOP,
            repeatMode = AudioRepeatMode.ALL, shuffleModeEnabled = true, currentTimeMs = 72_531)
        val restored = AudioSessionCodec.decode(AudioSessionCodec.encode(AudioSessionSnapshot.from(playing)))!!.toState()
        assertEquals(playing.tracks, restored.tracks)
        assertEquals(b, restored.currentTrack)
        assertEquals(72_531L, restored.currentTimeMs)
        assertEquals(AudioRepeatMode.ALL, restored.repeatMode)
        assertTrue(restored.shuffleModeEnabled)
        assertFalse(restored.playing)
    }

    @Test fun corruptSessionAndMissingCurrentTrackAreIgnored() {
        assertNull(AudioSessionCodec.decode("invalid JSON"))
        assertNull(AudioSessionCodec.decode(null))
        assertNull(AudioSessionCodec.decode(AudioSessionCodec.encode(AudioSessionSnapshot(
            listOf(a), "missing", 0, AudioMode.QUEUE, AudioRepeatMode.OFF, false))))
    }

    @Test fun unknownModesAndNegativePositionAreSafe() {
        val json = AudioSessionCodec.encode(AudioSessionSnapshot(listOf(a), a.id, -10,
            AudioMode.QUEUE, AudioRepeatMode.OFF, false))
            .replace("\"QUEUE\"", "\"FUTURE_MODE\"").replace("\"OFF\"", "\"FUTURE_REPEAT\"")
        val session = AudioSessionCodec.decode(json)!!
        assertEquals(AudioMode.QUEUE, session.mode)
        assertEquals(AudioRepeatMode.OFF, session.repeatMode)
        assertEquals(0L, session.positionMs)
    }

    @Test fun movingTheActiveTrackKeepsItsPosition() {
        val edit = planAudioQueueEdit(listOf(a, b, c), b.id, 83_901, listOf(b, c, a))
        assertEquals(0, edit.currentIndex)
        assertEquals(83_901L, edit.positionMs)
    }

    @Test fun removingAnUpcomingTrackKeepsCurrentPosition() {
        val edit = planAudioQueueEdit(listOf(a, b, c), b.id, 42_000, listOf(a, b))
        assertEquals(b, edit.tracks[edit.currentIndex])
        assertEquals(42_000L, edit.positionMs)
    }

    @Test fun removingCurrentChoosesNextSurvivingSong() {
        val edit = planAudioQueueEdit(listOf(a, b, c), b.id, 42_000, listOf(c, a))
        assertEquals(c, edit.tracks[edit.currentIndex])
        assertEquals(0L, edit.positionMs)
    }

    @Test fun removingLastSongAndEmptyQueueAreSafe() {
        assertEquals(b, planAudioQueueEdit(listOf(a, b, c), c.id, 12_000, listOf(a, b)).let { it.tracks[it.currentIndex] })
        assertEquals(-1, planAudioQueueEdit(listOf(a), a.id, 10, emptyList()).currentIndex)
    }

    @Test fun duplicateQueueEntriesAreCollapsedWithoutChangingSavedPlaylist() {
        val playlist = listOf(a, b, c)
        val edit = planAudioQueueEdit(playlist, b.id, 500, listOf(c, b, b, a))
        assertEquals(listOf(c, b, a), edit.tracks)
        assertEquals(listOf(a, b, c), playlist)
        assertEquals(500L, edit.positionMs)
    }
}
