package com.ronaldcolocho.taskly.music

import com.ronaldcolocho.taskly.audio.*
import com.ronaldcolocho.taskly.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class OnlineMusicSourceTest {
    private val id = "qapK8Gx6ZTo"

    @Test fun acceptsOnlyRealYoutubeWatchReferences() {
        assertEquals(id, OnlineMusicSource.youtubeId("https://music.youtube.com/watch?v=$id&list=RDAMVM$id"))
        assertEquals(id, OnlineMusicSource.youtubeId("https://youtu.be/$id"))
        for (url in listOf("https://youtube.com.evil.test/watch?v=$id", "https://evil.test/watch?v=$id",
            "http://www.youtube.com/watch?v=$id", "https://www.youtube.com/playlist?v=$id",
            "https://www.youtube.com/watch?v=short", "file:///song.mp3", "not a url"))
            assertNull(url, OnlineMusicSource.youtubeId(url))
    }

    @Test fun publicReferencesShareOneCacheKeyAndDoNotCollideWithAttachments() {
        assertEquals(OnlineMusicSource.cacheKey(OnlineMusicSource.reference(id)),
            OnlineMusicSource.cacheKey("https://music.youtube.com/watch?v=$id"))
        assertEquals("https://cdn.example/song.mp3", OnlineMusicSource.cacheKey("https://cdn.example/song.mp3"))
        assertEquals("youtube:$id", OnlineMusicSource.trackId(id))
    }

    @Test fun restoredSessionKeepsStableReferenceAndArtist() {
        val track = AudioTrack(OnlineMusicSource.trackId(id), OnlineMusicSource.reference(id), "Carefree", "",
            artist = "Kevin MacLeod")
        val snapshot = AudioSessionSnapshot(listOf(track), track.id, 39_000, AudioMode.QUEUE, AudioRepeatMode.OFF, false)
        val restored = AudioSessionCodec.decode(AudioSessionCodec.encode(snapshot))!!
        assertEquals(track, restored.tracks.single())
        assertEquals(39_000L, restored.positionMs)
        assertFalse(restored.toState().playing)
    }

    @Test fun legacySessionsStillRestoreWithoutArtist() {
        val restored = AudioSessionCodec.decode("""{"tracks":[{"id":"old","url":"https://cdn.example/song.mp3","name":"Audio"}],"currentId":"old"}""")!!
        assertEquals("", restored.tracks.single().artist)
    }

    @Test fun savingOnlineMusicDoesNotMarkItAsDownloadedAndArtistIsSearchable() {
        val item = MusicLibraryItem(MusicTrack(OnlineMusicSource.trackId(id), OnlineMusicSource.reference(id),
            "Carefree", artist = "Kevin MacLeod"), favorite = true)
        assertFalse(item.availableOffline)
        assertFalse(item.needsDownload)
        assertTrue(item.canDownload)
        assertEquals(listOf(item), filterMusicLibrary(listOf(item), "KEVIN", MusicLibraryFilter.FAVORITES, MusicLibraryOrder.NAME))
        assertTrue(filterMusicLibrary(listOf(item), "", MusicLibraryFilter.OFFLINE, MusicLibraryOrder.RECENT).isEmpty())
    }

    @Test fun firebaseProtosRemainUsableWithExtractorRuntime() {
        val document = com.google.firestore.v1.Document.newBuilder()
            .setName("projects/test/databases/(default)/documents/tracks/test")
            .setCreateTime(com.google.protobuf.Timestamp.newBuilder().setSeconds(123))
            .build()
        assertEquals(document, com.google.firestore.v1.Document.parseFrom(document.toByteArray()))
        val location = com.google.type.LatLng.newBuilder().setLatitude(13.69).setLongitude(-89.19).build()
        assertEquals(location, com.google.type.LatLng.parseFrom(location.toByteArray()))
    }
}
