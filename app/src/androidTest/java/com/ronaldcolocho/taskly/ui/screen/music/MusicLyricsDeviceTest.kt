package com.ronaldcolocho.taskly.ui.screen.music

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.MainActivity
import com.ronaldcolocho.taskly.audio.*
import com.ronaldcolocho.taskly.data.music.*
import com.ronaldcolocho.taskly.data.repository.*
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** Uses local PCM audio and fictional lyrics; never writes to production Firestore. */
@RunWith(AndroidJUnit4::class)
class MusicLyricsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes().toString(Charsets.UTF_8).trim() }
    }

    @Test fun switchesBetweenAnimationAndLyricsAndFollowsSeekAndNextTrack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        for (op in listOf("SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT", "SYSTEM_ALERT_WINDOW"))
            shell("appops set ${context.packageName} $op allow")
        val scale = shell("settings get global animator_duration_scale")
        shell("settings put global animator_duration_scale 0")
        val prefs = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
        val hadCoverPreference = prefs.contains("player_show_animation_cover")
        val originalCoverPreference = prefs.getBoolean("player_show_animation_cover", true)
        prefs.edit().putBoolean("player_show_animation_cover", true).commit()
        var controller: AudioPlayerController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var lyrics: MusicLyricsViewModel
                lateinit var media: MediaCacheRepositoryImpl
                val busyCalls = AtomicInteger()
                val persistentBusyCalls = AtomicInteger()
                val rejectedCalls = AtomicInteger()
                val wav = createSilentWav(context)
                val cover = File(context.cacheDir, "player-cover-fixture.png")
                android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.rgb(90, 160, 135))
                    cover.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    recycle()
                }
                val first = AudioTrack("lyrics-test-first", wav.toURI().toString(), "Test Song", "", 60,
                    thumbnailUrl = cover.toURI().toString(), artist = "Test Artist")
                val secondCover = File(context.cacheDir, "player-cover-second.png").also { cover.copyTo(it, overwrite = true) }
                val second = first.copy(id = "lyrics-test-second", name = "Second Song", thumbnailUrl = secondCover.toURI().toString())
                scenario.onActivity { activity ->
                    val http = OkHttpClient()
                    media = MediaCacheRepositoryImpl(context, http, OnlineMusicRepository(http))
                    runBlocking(Dispatchers.IO) {
                        media.cacheLocalFile(first.id, wav, MediaKind.AUDIO)
                        media.cacheLocalFile(second.id, wav, MediaKind.AUDIO)
                    }
                    controller = AudioPlayerController(context, MusicRepositoryImpl(context, FirebaseFirestore.getInstance(), media),
                        DownloadHistoryRepositoryImpl(context), FirebaseAuth.getInstance(), media, PlayerColorsController(context))
                    val api = OkHttpClient.Builder().addInterceptor { chain ->
                        val title = chain.request().url.queryParameter("track_name").orEmpty()
                        if (title == "Rejected Song") {
                            rejectedCalls.incrementAndGet()
                            return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                                .code(401).message("Rejected fixture")
                                .body("{}".toResponseBody("application/json".toMediaType())).build()
                        }
                        if ((title == "Busy Song" && busyCalls.incrementAndGet() == 1) ||
                            (title == "Always Busy" && persistentBusyCalls.incrementAndGet() > 0)) {
                            return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                                .code(503).message("Busy fixture").header("Retry-After", "1")
                                .body("{}".toResponseBody("application/json".toMediaType())).build()
                        }
                        val lines = if (title == "Test Song") "[00:00]First fixture line\\n[00:10]Second fixture line\\n[00:20]Third fixture line\\n[00:30]Fourth fixture line\\n[00:40]Fifth fixture line\\n[00:50]Sixth fixture line"
                            else "[00:00]Next song fixture\\n[00:10]Next song continuation"
                        val body = """{"id":${if (title == "Test Song") 1 else 2},"trackName":"$title","artistName":"Test Artist","duration":60,"syncedLyrics":"$lines","instrumental":false}"""
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(body.toResponseBody("application/json".toMediaType())).build()
                    }.build()
                    lyrics = MusicLyricsViewModel(SavedStateHandle(), controller!!,
                        LyricsRepository(api, File(context.cacheDir, "lyrics-ui-${System.nanoTime()}"),
                            sourceOrder = listOf(LyricsSource.LRCLIB)))
                    activity.viewModelStore.put("lyrics-ui-test", lyrics)
                    controller!!.updatePlaylist(listOf(first, second))
                    controller!!.playWithQueue(first)
                    activity.setContent { TasklyTheme { MusicPlayerScreen(controller!!, lyricsViewModel = lyrics) } }
                }
                compose.waitUntil(15_000) { controller!!.state.value.currentTrack?.id == first.id }
                compose.onNodeWithTag("music_visual_stage").assertExists()
                compose.onNodeWithTag("music_animation_cover").assertExists()
                compose.onNodeWithContentDescription("Efectos visuales del reproductor").performClick()
                compose.onNodeWithTag("music_animation_cover_toggle").assertIsOn().performClick().assertIsOff()
                org.junit.Assert.assertFalse(prefs.getBoolean("player_show_animation_cover", true))
                compose.onNodeWithContentDescription("Cerrar").performClick()
                compose.onNodeWithTag("music_animation_cover").assertDoesNotExist()
                compose.onNodeWithTag("music_visual_stage").assertExists()
                compose.onNodeWithContentDescription("Efectos visuales del reproductor").performClick()
                compose.onNodeWithTag("music_animation_cover_toggle").assertIsOff()
                compose.onNodeWithContentDescription("Cerrar").performClick()
                compose.onNodeWithTag("music_lyrics_toggle").performClick()
                compose.waitUntil(15_000) { lyrics.state.value.result is LyricsLookup.Found }
                compose.onNodeWithTag("music_lyrics_pane").assertExists()
                compose.onNodeWithTag("music_visual_stage").assertDoesNotExist()
                compose.onNodeWithText("First fixture line").assertExists()
                compose.onNodeWithTag("synced_lyrics_list").performTouchInput { swipeUp() }
                compose.onNodeWithText("Seguir canción").assertExists().performClick()
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(context.getExternalFilesDir(null), "lyrics-ui-test.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                compose.runOnIdle { controller!!.seek(11_000) }
                compose.waitUntil(10_000) { controller!!.state.value.currentTimeMs >= 11_000 }
                compose.onNodeWithText("Second fixture line").assertIsSelected()
                compose.runOnIdle {
                    // A catalog refresh must not replace the metadata of the real player queue.
                    controller!!.updatePlaylist(emptyList())
                    controller!!.seek(59_600)
                }
                compose.waitUntil(15_000) {
                    lyrics.state.value.trackId == second.id && lyrics.state.value.result is LyricsLookup.Found
                }
                compose.onNodeWithText("Next song fixture").assertExists()
                compose.onNodeWithText("Second Song").assertExists()
                org.junit.Assert.assertEquals(second.thumbnailUrl, controller!!.state.value.currentTrack?.thumbnailUrl)
                compose.onNodeWithText("First fixture line").assertDoesNotExist()
                compose.onNodeWithTag("music_lyrics_toggle").assertIsOn().performClick()
                compose.onNodeWithTag("music_visual_stage").assertExists()
                compose.onNodeWithTag("music_lyrics_pane").assertDoesNotExist()
                compose.onNodeWithTag("music_animation_cover").assertDoesNotExist()
                compose.onNodeWithContentDescription("Efectos visuales del reproductor").performClick()
                compose.onNodeWithTag("music_animation_cover_toggle").performClick().assertIsOn()
                org.junit.Assert.assertTrue(prefs.getBoolean("player_show_animation_cover", false))
                compose.onNodeWithContentDescription("Cerrar").performClick()
                compose.onNodeWithTag("music_animation_cover").assertExists()

                val busy = first.copy(id = "lyrics-test-busy", name = "Busy Song")
                val persistent = first.copy(id = "lyrics-test-always-busy", name = "Always Busy")
                runBlocking(Dispatchers.IO) {
                    media.cacheLocalFile(busy.id, wav, MediaKind.AUDIO)
                    media.cacheLocalFile(persistent.id, wav, MediaKind.AUDIO)
                }
                compose.runOnIdle { controller!!.updatePlaylist(listOf(busy)); controller!!.playWithQueue(busy) }
                compose.onNodeWithTag("music_lyrics_toggle").performClick()
                compose.waitUntil(10_000) { lyrics.state.value.trackId == busy.id && lyrics.state.value.retrySeconds > 0 }
                val seconds = lyrics.state.value.retrySeconds
                compose.onNodeWithText("Reintentar").assertIsNotEnabled()
                compose.runOnIdle { lyrics.retry() }
                compose.waitUntil(6000) { lyrics.state.value.retrySeconds in 1 until seconds }
                org.junit.Assert.assertEquals(1, busyCalls.get())
                compose.waitUntil(10_000) { lyrics.state.value.trackId == busy.id && lyrics.state.value.result is LyricsLookup.Found }
                org.junit.Assert.assertEquals(2, busyCalls.get())
                compose.runOnIdle { controller!!.updatePlaylist(listOf(persistent)); controller!!.playWithQueue(persistent) }
                compose.waitUntil(25_000) {
                    lyrics.state.value.trackId == persistent.id && lyrics.state.value.result is LyricsLookup.Failed &&
                        !lyrics.state.value.loading && lyrics.state.value.retrySeconds == 0 && persistentBusyCalls.get() == 3
                }
                // Wait for the rendered button, not the intermediate result before its countdown is published.
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Reintentar").fetchSemanticsNodes().singleOrNull()?.config
                        ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
                }
                compose.onNodeWithText("Reintentar").assertIsEnabled()
                org.junit.Assert.assertEquals(3, persistentBusyCalls.get())
                val rejected = first.copy(id = "lyrics-test-rejected", name = "Rejected Song")
                runBlocking(Dispatchers.IO) { media.cacheLocalFile(rejected.id, wav, MediaKind.AUDIO) }
                compose.runOnIdle { controller!!.updatePlaylist(listOf(rejected)); controller!!.playWithQueue(rejected) }
                compose.waitUntil(10_000) { lyrics.state.value.trackId == rejected.id && lyrics.state.value.retrySeconds > 0 }
                org.junit.Assert.assertFalse(lyrics.state.value.autoRetry)
                compose.onNodeWithText("No se pudo obtener la letra. LRCLIB: consulta rechazada (HTTP 401).").assertExists()
                compose.waitUntil(10_000) { lyrics.state.value.retrySeconds == 0 }
                compose.onNodeWithText("Reintentar").assertIsEnabled()
                org.junit.Assert.assertEquals(1, rejectedCalls.get())
                if (InstrumentationRegistry.getArguments().getString("musicRadioLive") == "1") {
                    compose.onNodeWithTag("music_lyrics_toggle").performClick()
                    val radioSeed = first.copy(id = "youtube:DXiXPhvYuNU", url = "https://www.youtube.com/watch?v=DXiXPhvYuNU",
                        name = "Necio", artist = "Romeo Santos", relatedAutoplay = true)
                    runBlocking(Dispatchers.IO) { media.cacheLocalFile(radioSeed.id, wav, MediaKind.AUDIO) }
                    compose.runOnIdle { controller!!.updatePlaylist(listOf(radioSeed)); controller!!.playWithQueue(radioSeed) }
                    compose.waitUntil(15_000) { controller!!.state.value.currentTrack?.id == radioSeed.id && controller!!.state.value.playing }
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    val beforeBackground = controller!!.state.value.currentTimeMs
                    compose.waitUntil(30_000) {
                        controller!!.state.value.tracks.size > 1 && controller!!.state.value.currentTimeMs > beforeBackground + 400
                    }
                    val radio = controller!!.state.value
                    org.junit.Assert.assertEquals(radioSeed.id, radio.currentTrack?.id)
                    org.junit.Assert.assertTrue(radio.playing)
                    org.junit.Assert.assertTrue(radio.tracks.size in 2..6)
                    org.junit.Assert.assertEquals(radio.tracks.size, radio.tracks.map { it.id }.distinct().size)
                    org.junit.Assert.assertTrue(radio.tracks.all { it.relatedAutoplay && it.playlistId == null })
                    android.util.Log.i("MusicRadioTest", "Radio en servicio con pantalla detenida: ${radio.tracks.size - 1} recomendaciones; reproducción activa")
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                }
                compose.runOnIdle { controller!!.stop() }
            }
        } finally {
            instrumentation.runOnMainSync { controller?.stop(); controller?.release() }
            prefs.edit().apply {
                if (hadCoverPreference) putBoolean("player_show_animation_cover", originalCoverPreference)
                else remove("player_show_animation_cover")
            }.commit()
            shell("settings put global animator_duration_scale ${scale.takeIf { it.toFloatOrNull() != null } ?: "1"}")
        }
    }

    private fun createSilentWav(context: Context): File {
        val dataSize = 8000 * 2 * 60
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataSize)
        return File(context.cacheDir, "lyrics-ui-test.wav").apply {
            outputStream().use { it.write(header.array()); it.write(ByteArray(dataSize)) }
        }
    }
}
