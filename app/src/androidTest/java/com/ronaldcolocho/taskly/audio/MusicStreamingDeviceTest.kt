package com.ronaldcolocho.taskly.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import com.ronaldcolocho.taskly.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.ronaldcolocho.taskly.data.music.OnlineMusicRepository
import com.ronaldcolocho.taskly.data.repository.MediaCacheRepositoryImpl
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MusicStreamingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val reference = OnlineMusicSource.reference("qapK8Gx6ZTo")
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test fun cachedRangesDoNotPersistSignedUrlsAndCanBeReadWithoutNetwork() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("musicLive") == "1")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = SimpleCache(java.io.File(context.cacheDir, "music_source_test"),
            LeastRecentlyUsedCacheEvictor(128 * 1024L), StandaloneDatabaseProvider(context))
        val key = OnlineMusicSource.cacheKey(reference)
        try {
            cache.removeResource(key)
            val online = OnlineMusicRepository(OkHttpClient())
            val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory {
                MusicStreamDataSource(DefaultHttpDataSource.Factory().createDataSource(), online)
            }
            val spec = DataSpec.Builder().setUri(reference).setKey(key).setLength(65536).build()
            CacheWriter(factory.createDataSource(), spec, null, null).cache()
            assertTrue(cache.isCached(key, 0, 65536))
            assertNull("No debe guardar URLs firmadas como redirecciones",
                ContentMetadata.getRedirectedUri(cache.getContentMetadata(key)))
            val offline = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory {
                object : DataSource {
                    override fun addTransferListener(listener: TransferListener) = Unit
                    override fun open(dataSpec: DataSpec): Long = throw AssertionError("La caché no debe acceder a Internet")
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw AssertionError("Lectura de red inesperada")
                    override fun getUri(): android.net.Uri? = null
                    override fun close() = Unit
                }
            }.createDataSource()
            try {
                offline.open(spec)
                val bytes = ByteArray(65536)
                var total = 0
                while (total < bytes.size) {
                    val read = offline.read(bytes, total, bytes.size - total)
                    if (read == -1) break
                    total += read
                }
                assertEquals(65536, total)
            } finally { offline.close() }
        } finally { cache.release() }
    }

    @Test fun streamsSeeksAndPlaysDownloadedSongThroughTheRealService() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("musicLive") == "1")
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        for (operation in listOf("SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT", "SYSTEM_ALERT_WINDOW")) {
            instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} $operation allow").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
        }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        var future: ListenableFuture<MediaController>? = null
        main {
            future = MediaController.Builder(context,
                SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))).buildAsync()
        }
        val controller = future!!.get(30, TimeUnit.SECONDS)
        val failure = AtomicReference<PlaybackException?>()
        val ready = CountDownLatch(1)
        try {
            main {
                controller.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { failure.set(error); ready.countDown() }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }
                })
                controller.setMediaItem(MediaItem.Builder().setMediaId("youtube:qapK8Gx6ZTo")
                    .setUri(reference).setCustomCacheKey(OnlineMusicSource.cacheKey(reference)).build())
                controller.prepare()
                controller.play()
            }
            assertTrue("El audio debe estar listo", ready.await(60, TimeUnit.SECONDS))
            assertNull("No debe fallar el stream", failure.get())
            awaitPlayback(controller, minimumPosition = 1000)
            activity.moveToState(Lifecycle.State.CREATED)
            awaitPlayback(controller, minimumPosition = 2500)
            activity.moveToState(Lifecycle.State.RESUMED)
            main { controller.seekTo(45_000) }
            awaitPlayback(controller, minimumPosition = 45_000)
            main { controller.pause() }

            val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
            val cache = MediaCacheRepositoryImpl(context, http, OnlineMusicRepository(http))
            val id = "youtube:qapK8Gx6ZTo"
            val terminal = runBlocking {
                cache.download(id, reference, MediaKind.AUDIO).first {
                    it is MediaDownloadState.Downloaded || it is MediaDownloadState.Error
                }
            }
            assertTrue("Debe finalizar la descarga: $terminal", terminal is MediaDownloadState.Downloaded)
            val file = cache.fileFor(id, MediaKind.AUDIO)!!
            assertTrue(file.length() > 1024)
            cache.registerDownloadedAudio(CachedAudioReference(id, reference, "Carefree", "", 205,
                System.currentTimeMillis(), "Kevin MacLeod"))
            main {
                controller.stop()
                controller.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                controller.prepare()
                controller.play()
            }
            awaitPlayback(controller, minimumPosition = 1000)
            assertNull("El archivo descargado debe decodificarse", failure.get())
        } finally {
            main { controller.stop(); controller.clearMediaItems(); controller.release() }
            activity.close()
        }
    }

    private fun awaitPlayback(controller: MediaController, minimumPosition: Long) {
        val limit = android.os.SystemClock.elapsedRealtime() + 30_000
        var succeeded = false
        var status = ""
        while (!succeeded && android.os.SystemClock.elapsedRealtime() < limit) {
            main {
                succeeded = controller.isPlaying && controller.currentPosition >= minimumPosition
                status = "state=${controller.playbackState}, ready=${controller.playWhenReady}, " +
                    "suppression=${controller.playbackSuppressionReason}, position=${controller.currentPosition}, error=${controller.playerError?.errorCode}"
            }
            if (!succeeded) android.os.SystemClock.sleep(100)
        }
        assertTrue("La reproducción debe avanzar desde $minimumPosition ms: $status", succeeded)
    }
}
