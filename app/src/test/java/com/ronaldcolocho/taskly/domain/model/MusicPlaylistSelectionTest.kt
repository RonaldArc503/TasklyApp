package com.ronaldcolocho.taskly.domain.model

import org.junit.Assert.*
import org.junit.Test

class MusicPlaylistSelectionTest {
    private val original = MusicPlaylist("original", "alice", "Original", 0, 0)
    private val preview = original.copy(sharedOwnerId = "alice", sharedPlaylistId = "original", shareId = "token")
    private val copy = MusicPlaylist("shared_token", "bob", "Mi copia", 0, 0)

    @Test fun openingTheSharedLinkPrefersTheEditableCopy() {
        assertEquals(copy, selectMusicPlaylist(listOf(copy), preview, "bob", copy.id))
        assertEquals(copy, selectMusicPlaylist(listOf(copy), null, "bob", copy.id))
    }
    @Test fun unsavedLinksShowPreviewAndOwnersOpenTheirOriginal() {
        assertEquals(preview, selectMusicPlaylist(emptyList(), preview, "bob", copy.id))
        assertEquals(original, selectMusicPlaylist(listOf(original), preview, "alice", copy.id))
        assertNull(selectMusicPlaylist(emptyList(), null, "bob", copy.id))
    }
}
