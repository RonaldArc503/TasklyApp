package com.ronaldcolocho.taskly.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.ronaldcolocho.taskly.data.repository.ProfileRepositoryImpl
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.ui.screen.music.PlaylistDetailViewModel
import com.ronaldcolocho.taskly.data.repository.MusicRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.SharedMusicPlaylistRepository
import com.ronaldcolocho.taskly.domain.model.MusicPlaylistLink
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires only local Auth (9098) and Firestore (8088) emulators, project demo-taskly-music. */
@RunWith(AndroidJUnit4::class)
class MusicPlaylistRepositoryDeviceTest {
    @Test fun shareSaveAndLiveChangesNeverMutateTheOriginalOrDuplicateTracks() = runBlocking {
        withTimeout(60_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val host = InstrumentationRegistry.getArguments().getString("musicEmulatorHost") ?: "10.0.2.2"
            require(host in setOf("10.0.2.2", "127.0.0.1"))
            val viewModels = ViewModelStore()
            val clients = (1..2).map { index ->
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                    .setProjectId("demo-taskly-music").setApplicationId("1:123:android:music-test")
                    .setApiKey("fake-api-key").build(), "music-$index-${System.nanoTime()}")
                val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9098) }
                val uid = auth.createUserWithEmailAndPassword("music-${System.nanoTime()}@example.test", "test-password").await().user!!.uid
                val db = FirebaseFirestore.getInstance(app).apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
                    useEmulator(host, 8088)
                }
                Triple(app, uid, db)
            }
            try {
                val (_, alice, aliceDb) = clients[0]
                val (_, bob, bobDb) = clients[1]
                val cache = MusicPlaylistDownloadsTest.FakeCache(context)
                val owner = MusicRepositoryImpl(context, aliceDb, cache)
                val reader = MusicRepositoryImpl(context, bobDb, cache)
                val id = owner.createPlaylist(alice, "Compartida").getOrThrow()
                val song = MusicTrack("song/with/slashes", "https://example.invalid/audio", "Canción")
                owner.addTrackToPlaylist(alice, id, song).getOrThrow()
                val first = owner.observePlaylistTracks(alice, id).first { it.size == 1 }.single()
                owner.addTrackToPlaylist(alice, id, song.copy(name = "Duplicate", position = 900)).getOrThrow()
                val unchanged = owner.observePlaylistTracks(alice, id).first { it.size == 1 }.single()
                assertEquals(first, unchanged)
                val list = owner.observePlaylists(alice).first { it.isNotEmpty() }.single()
                assertEquals(1, list.songCount)
                val sharing = SharedMusicPlaylistRepository(aliceDb)
                val links = coroutineScope { listOf(async { sharing.share(alice, list) }, async { sharing.share(alice, list) }).awaitAll() }
                assertEquals(links[0], links[1])
                val token = MusicPlaylistLink.parse(links[0])!!
                val receiving = SharedMusicPlaylistRepository(bobDb)
                val preview = receiving.observe(token).first { it != null }!!
                val savedIds = coroutineScope { listOf(async { receiving.save(bob, preview) }, async { receiving.save(bob, preview) }).awaitAll() }
                assertEquals(savedIds[0], savedIds[1])
                assertEquals(1, reader.observePlaylists(bob).first { it.isNotEmpty() }.size)
                assertEquals(song.id, reader.observePlaylistTracks(bob, savedIds[0]).first { it.isNotEmpty() }.single().id)
                assertTrue(reader.observePlaylists(bob).first { it.isNotEmpty() }.single().editable)
                reader.addTrackToPlaylist(bob, savedIds[0], song).getOrThrow()
                assertEquals(1, reader.observePlaylistTracks(bob, savedIds[0]).first { it.isNotEmpty() }.size)
                reader.addTrackToPlaylist(bob, savedIds[0], song.copy(id = "my-song")).getOrThrow()
                reader.renamePlaylist(bob, savedIds[0], "Mi copia").getOrThrow()
                receiving.save(bob, preview)
                assertEquals(2, reader.observePlaylistTracks(bob, savedIds[0]).first { it.size == 2 }.size)
                assertTrue(reader.addTrackToPlaylist(alice, id, song.copy(id = "attack")).isFailure)
                owner.addTrackToPlaylist(alice, id, song.copy(id = "second")).getOrThrow()
                assertEquals(2, reader.observePlaylistTracks(bob, savedIds[0]).first { it.size == 2 }.size)
                owner.renamePlaylist(alice, id, "Nuevo nombre").getOrThrow()
                assertEquals(2, reader.observePlaylists(bob).first { it.singleOrNull()?.name == "Mi copia" }.single().songCount)
                assertEquals(setOf(song.id, "my-song"), reader.observePlaylistTracks(bob, savedIds[0]).first { it.size == 2 }.map { it.id }.toSet())
                reader.deletePlaylist(bob, savedIds[0]).getOrThrow()
                assertEquals(2, owner.observePlaylistTracks(alice, id).first { it.size == 2 }.size)
                val source = aliceDb.collection("users").document(alice).collection("playlists").document(id)
                (0 until 501).chunked(250).forEach { chunk ->
                    val batch = aliceDb.batch()
                    chunk.forEach { index -> batch.set(source.collection("tracks").document("large-$index"),
                        mapOf("mediaId" to "large/$index", "url" to "https://example.invalid/$index", "name" to "Track $index", "position" to index)) }
                    batch.commit().await()
                }
                val target = bobDb.collection("users").document(bob).collection("playlists").document(savedIds[0])
                target.set(mapOf("userId" to bob, "name" to "Referencia antigua", "shareId" to token,
                    "sharedOwnerId" to alice, "sharedPlaylistId" to id, "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis(), "copyInProgress" to true, "copyAttempt" to "interrupted")).await()
                target.collection("tracks").document("partial").set(mapOf("mediaId" to "stale")).await()
                assertEquals("Named reader authentication must remain available", bob, FirebaseAuth.getInstance(clients[1].first).currentUser?.uid)
                assertEquals(1, bobDb.collection("users").document(bob).collection("playlists")
                    .orderBy("updatedAt").get(com.google.firebase.firestore.Source.SERVER).await().size())
                assertTrue(bobDb.document(source.path).get(com.google.firebase.firestore.Source.SERVER).await().exists())
                assertEquals(1, withTimeout(5_000) { reader.observePlaylists(bob).first { it.isNotEmpty() }.size })
                val detail = withContext(Dispatchers.Main.immediate) {
                    PlaylistDetailViewModel(SavedStateHandle(mapOf("playlistId" to savedIds[0])), reader, receiving,
                        GetCurrentUserIdUseCase(ProfileRepositoryImpl(FirebaseAuth.getInstance(clients[1].first), bobDb)))
                        .also { viewModels.put("legacy-copy", it) }
                }
                // Opening an old saved reference repairs it in the same library slot.
                try { withTimeout(15_000) { detail.selectedPlaylist.first { it?.editable == true } } }
                catch (error: TimeoutCancellationException) {
                    throw AssertionError("Migration did not finish: playlists=${detail.playlists.value}, saving=${detail.saving.value}, error=${detail.error.value}", error)
                }
                assertEquals(savedIds[0], detail.selectedPlaylistId)
                val largeCopy = reader.observePlaylistTracks(bob, savedIds[0]).first { it.size == 503 }
                assertEquals(503, largeCopy.map { it.id }.distinct().size)
                assertFalse(largeCopy.any { it.id == "stale" })
                assertTrue(reader.observePlaylists(bob).first { it.isNotEmpty() }.single().editable)
                source.delete().await()
                receiving.save(bob, preview)
                assertEquals(503, detail.tracks.first { it.size == 503 }.size)
                withContext(Dispatchers.Main.immediate) { detail.addTrack(song.copy(id = "copy-only-after-source-deleted")) }
                assertEquals(504, detail.tracks.first { it.size == 504 }.size)
                assertEquals(1, reader.observePlaylists(bob).first { it.singleOrNull()?.songCount == 504 }.size)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { viewModels.clear() }
                clients.forEach { (app, _, db) -> db.terminate().await(); app.delete() }
            }
        }
    }
}
