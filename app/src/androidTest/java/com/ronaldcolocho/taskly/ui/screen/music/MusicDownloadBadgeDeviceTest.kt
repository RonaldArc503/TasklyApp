package com.ronaldcolocho.taskly.ui.screen.music

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ronaldcolocho.taskly.MainActivity
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDownloadBadgeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun downloadsWithoutPlayingAndDisappearsAfterCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        for (op in listOf("SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT", "SYSTEM_ALERT_WINDOW")) {
            instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} $op allow").close()
        }
        var state by mutableStateOf<MediaDownloadState?>(null)
        var offline by mutableStateOf(false)
        var downloads = 0
        var playback = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TasklyTheme {
                    Box(Modifier.size(52.dp).clickable { playback++ }) {
                        MusicDownloadBadge(offline, state, "Demo", { downloads++ })
                    }
                }
            } }
            compose.onNodeWithContentDescription("Descargar Demo").performClick()
            compose.runOnIdle { assertEquals(1, downloads); assertEquals(0, playback); state = MediaDownloadState.Queued }
            compose.onNodeWithContentDescription("Descarga de Demo en cola").assertIsNotEnabled()
            compose.runOnIdle { state = MediaDownloadState.Downloading(0.5f) }
            compose.onNodeWithContentDescription("Descargando Demo: 50 %").assertExists()
            compose.runOnIdle { state = MediaDownloadState.Error("Sin red") }
            compose.onNodeWithContentDescription("Reintentar descarga de Demo: Sin red").performClick()
            compose.runOnIdle { assertEquals(2, downloads); assertEquals(0, playback); state = MediaDownloadState.Downloaded }
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            compose.onNodeWithText("Disponible sin conexión").assertDoesNotExist()
            compose.runOnIdle { state = null; offline = true }
            compose.onNodeWithContentDescription("Descargar Demo").assertDoesNotExist()
        }
    }
}
