package com.ronaldcolocho.taskly.domain.model

import org.junit.Assert.*
import org.junit.Test

class MusicLibraryTest {
    private val favorite = MusicLibraryItem(MusicTrack("1", "https://audio.example/1", "Canción Azul",
        playCount = 2, addedAt = 10), favorite = true, localPath = "/music/1.mp3", sizeBytes = 200,
        wasDownloaded = true, playlistNames = listOf("Para estudiar"))
    private val missing = MusicLibraryItem(MusicTrack("2", "https://audio.example/2", "Árbol",
        playCount = 12, addedAt = 30), wasDownloaded = true)
    private val remote = MusicLibraryItem(MusicTrack("3", "https://audio.example/3", "Brisa", addedAt = 20))
    private val items = listOf(favorite, missing, remote)

    @Test fun searchIgnoresAccentsAndCaseAndMatchesPlaylistNames() {
        assertEquals(listOf(favorite), filterMusicLibrary(items, "CANCION estudiar", MusicLibraryFilter.ALL, MusicLibraryOrder.NAME))
        assertEquals(listOf(missing), filterMusicLibrary(items, "arbol", MusicLibraryFilter.ALL, MusicLibraryOrder.RECENT))
    }
    @Test fun eachFilterReflectsPhysicalOfflineAvailability() {
        assertEquals(listOf(favorite), filterMusicLibrary(items, "", MusicLibraryFilter.FAVORITES, MusicLibraryOrder.RECENT))
        assertEquals(listOf(favorite), filterMusicLibrary(items, "", MusicLibraryFilter.OFFLINE, MusicLibraryOrder.RECENT))
        assertEquals(listOf(missing), filterMusicLibrary(items, "", MusicLibraryFilter.MISSING, MusicLibraryOrder.RECENT))
        assertFalse(remote.needsDownload)
    }
    @Test fun sortingUsesRecencyNamePlaybackCountAndActualSize() {
        assertEquals(listOf(missing, remote, favorite), filterMusicLibrary(items, "", MusicLibraryFilter.ALL, MusicLibraryOrder.RECENT))
        assertEquals(listOf(missing, remote, favorite), filterMusicLibrary(items, "", MusicLibraryFilter.ALL, MusicLibraryOrder.NAME))
        assertEquals(missing, filterMusicLibrary(items, "", MusicLibraryFilter.ALL, MusicLibraryOrder.MOST_PLAYED).first())
        assertEquals(favorite, filterMusicLibrary(items, "", MusicLibraryFilter.ALL, MusicLibraryOrder.SIZE).first())
    }
    @Test fun missingLocalOnlySongsRequireConverterRecovery() {
        val localOnly = MusicLibraryItem(MusicTrack("4", "file:///missing.mp3", "Audio"), wasDownloaded = true)
        assertTrue(localOnly.needsDownload)
        assertFalse(localOnly.canDownload)
    }
}
