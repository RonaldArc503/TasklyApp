package com.ronaldcolocho.taskly.domain.model

import org.junit.Assert.*
import org.junit.Test

class MusicPlaylistLinkTest {
    private val id = "a123456789abcdef0123456789abcdef"

    @Test fun linkRoundTripAndStableLibraryIdentity() {
        assertEquals(id, MusicPlaylistLink.parse(MusicPlaylistLink.create(id)))
        assertEquals("https://tasksv.web.app/musica/lista/$id", MusicPlaylistLink.create(id))
        assertEquals("shared_$id", MusicPlaylistLink.savedPlaylistId(id))
        assertEquals(MusicPlaylistLink.savedPlaylistId(id), MusicPlaylistLink.savedPlaylistId(id))
    }
    @Test fun acceptsLegacyLinksAndBothHostingDomains() {
        assertEquals(id, MusicPlaylistLink.parse("taskly://playlist/$id"))
        assertEquals(id, MusicPlaylistLink.parse("https://tasksv.firebaseapp.com/musica/lista/$id"))
        assertEquals(id, MusicPlaylistLink.parse("https://tasksv.web.app/musica/lista/$id/?utm_source=chat"))
    }
    @Test fun rejectMalformedAndForeignLinks() {
        listOf(null, "", "https://playlist/$id", "taskly://other/$id", "taskly://playlist/../$id",
            "taskly://playlist/$id?owner=someone", "taskly://playlist/$id#fragment", "taskly://playlist/$id/",
            "taskly://attacker@playlist/$id", "taskly://playlist:123/$id", "taskly://playlist/short",
            "https://attacker.example/musica/lista/$id", "http://tasksv.web.app/musica/lista/$id",
            "https://tasksv.web.app.attacker.example/musica/lista/$id", "https://tasksv.web.app/musica/lista/$id/extra",
            "https://attacker@tasksv.web.app/musica/lista/$id", "https://tasksv.web.app:443/musica/lista/$id")
            .forEach { assertNull(it, MusicPlaylistLink.parse(it)) }
    }
    @Test fun sharedReferencesAreReadOnlyAndOriginalsStayEditable() {
        val original = MusicPlaylist("one", "owner", "Lista", 0, 0)
        assertTrue(original.editable)
        assertFalse(original.copy(sharedOwnerId = "owner", sharedPlaylistId = "one", shareId = id).editable)
    }
}
