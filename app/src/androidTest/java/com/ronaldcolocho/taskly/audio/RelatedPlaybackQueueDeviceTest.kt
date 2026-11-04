package com.ronaldcolocho.taskly.audio

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** Local PCM and controlled radio results exercise real automatic ExoPlayer transitions. */
@RunWith(AndroidJUnit4::class)
class RelatedPlaybackQueueDeviceTest {
    private lateinit var player: ExoPlayer
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            main { done = check() }
            if (done) return
            SystemClock.sleep(50)
        }
        fail("El reproductor no alcanzó el estado esperado")
    }

    @Test fun radioAdvancesAutomaticallyAndPlaylistsRejectRecommendationsAndLateResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wav = File(context.cacheDir, "radio-test.wav")
        val dataSize = 8000 * 2 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataSize)
        wav.outputStream().use { it.write(header.array()); it.write(ByteArray(dataSize)) }
        fun track(id: String, name: String) = AudioTrack("youtube:$id", "https://www.youtube.com/watch?v=$id",
            name, "", 2, "https://example.com/$id.jpg", artist = "Artist $name", relatedAutoplay = true)
        val a = track("aaaaaaaaaaa", "First")
        val b = track("bbbbbbbbbbb", "Second")
        val c = track("ccccccccccc", "Third")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        var blocked = false
        var failRequest = false
        try {
            main {
                player = ExoPlayer.Builder(context).build()
                player.volume = 0f
                val manager = RelatedPlaybackQueue(player, scope, load = { seed ->
                    calls.incrementAndGet()
                    if (blocked) gate.await()
                    if (failRequest) throw java.io.IOException("Offline fixture")
                    if (seed.id == a.id) {
                        delay(2500) // Arrives after the two-second seed ends.
                        listOf(a, b, b, c)
                    } else emptyList()
                }, mediaItem = { it.toPlaybackMediaItem(Uri.fromFile(wav)) })
                player.addListener(manager)
                player.setMediaItem(a.toPlaybackMediaItem(Uri.fromFile(wav)))
                player.prepare(); player.play()
            }
            await { player.mediaItemCount == 3 }
            await { player.currentMediaItem?.mediaId == b.id && player.isPlaying }
            main {
                val actual = player.currentMediaItem!!.toAudioTrack(b.copy(playlistId = "stale-playlist"))
                assertEquals(b.name, actual.name)
                assertEquals(b.artist, actual.artist)
                assertEquals(b.thumbnailUrl, actual.thumbnailUrl)
                assertTrue(actual.relatedAutoplay)
                assertNull(actual.playlistId)
                val noCover = b.copy(thumbnailUrl = "").toPlaybackMediaItem(Uri.fromFile(wav)).toAudioTrack(b)
                assertEquals("", noCover.thumbnailUrl)
            }
            await { player.playbackState == Player.STATE_ENDED }
            val beforePlaylist = calls.get()
            main {
                player.setMediaItems(listOf(a, b).map { it.copy(playlistId = "saved-playlist").toPlaybackMediaItem(Uri.fromFile(wav)) })
                player.prepare(); player.play()
            }
            await { player.currentMediaItem?.mediaId == b.id && player.isPlaying }
            await { player.playbackState == Player.STATE_ENDED }
            assertEquals(beforePlaylist, calls.get())
            main { assertEquals(2, player.mediaItemCount) }

            main {
                blocked = true
                player.setMediaItem(a.toPlaybackMediaItem(Uri.fromFile(wav)))
                player.prepare(); player.play()
            }
            await { calls.get() > beforePlaylist }
            main { assertTrue(player.playlistMetadata.extras?.getBoolean("tasklyRadioLoading") == true) }
            main {
                player.setMediaItem(b.copy(playlistId = "another-playlist").toPlaybackMediaItem(Uri.fromFile(wav)))
                player.prepare(); player.play()
                gate.complete(Unit)
            }
            await { player.playbackState == Player.STATE_ENDED }
            main {
                assertEquals(1, player.mediaItemCount)
                assertEquals("another-playlist", player.currentMediaItem!!.toAudioTrack().playlistId)
                assertFalse(player.playlistMetadata.extras?.getBoolean("tasklyRadioLoading") == true)
                assertNull(player.playlistMetadata.extras?.getString("tasklyRadioError"))
            }

            val beforeFailure = calls.get()
            main {
                blocked = false; failRequest = true
                player.setMediaItem(a.toPlaybackMediaItem(Uri.fromFile(wav)))
                player.prepare(); player.play()
            }
            await { calls.get() == beforeFailure + 1 }
            await { player.playbackState == Player.STATE_ENDED }
            assertEquals(beforeFailure + 1, calls.get())
            main {
                assertEquals("No se pudieron cargar canciones similares.", player.playlistMetadata.extras?.getString("tasklyRadioError"))
                assertFalse(player.playlistMetadata.extras?.getBoolean("tasklyRadioLoading") == true)
            }
        } finally {
            main { scope.cancel(); if (::player.isInitialized) player.release() }
        }
    }
}
