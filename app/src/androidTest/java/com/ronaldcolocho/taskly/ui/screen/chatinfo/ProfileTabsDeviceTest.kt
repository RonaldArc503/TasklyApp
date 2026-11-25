package com.ronaldcolocho.taskly.ui.screen.chatinfo

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronaldcolocho.taskly.MainActivity
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileTabsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun labelsFitInOneLineOnCompactScreensAndWithLargeText() {
        val width = mutableStateOf(328.dp)
        val fontScale = mutableStateOf(1.25f)
        val selected = mutableStateOf(ProfileTab.MEDIA)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TasklyTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                        Box(Modifier.width(width.value)) {
                            ProfileTabs(selected.value) { selected.value = it }
                        }
                    }
                }
            } }

            assertLabelsFit()
            assertEquals(1, tabTops().distinct().size)
            compose.onNode(hasText("Archivos") and hasClickAction()).performClick().assertIsSelected()

            compose.runOnIdle {
                width.value = 280.dp
                fontScale.value = 2f
            }
            assertLabelsFit()
            assertEquals(2, tabTops().distinct().size)
            compose.onNode(hasText("Archivos") and hasClickAction()).assertIsSelected()
        }
    }

    @Test fun longPressStartsSelectionAndAnotherTapAddsToIt() {
        val selected = mutableStateOf(emptySet<String>())
        var opens = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TasklyTheme {
                    Column {
                        for (name in listOf("Foto de prueba", "Archivo de prueba")) {
                            val toggle = {
                                val current = selected.value
                                selected.value = if (name in current) current - name else current + name
                            }
                            ProfileAttachmentCard(
                                isSelected = name in selected.value,
                                onOpen = { if (selected.value.isEmpty()) opens++ else toggle() },
                                onSelect = toggle
                            ) { Text(name) }
                        }
                    }
                }
            } }
            compose.onNodeWithText("Foto de prueba").performTouchInput { longClick() }.assertIsSelected()
            compose.onNodeWithText("Archivo de prueba").performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(setOf("Foto de prueba", "Archivo de prueba"), selected.value)
                assertEquals(0, opens)
            }
        }
    }

    private fun tabTops() = ProfileTab.entries.map { tab ->
        compose.onNode(hasText(tab.label) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top
    }

    private fun assertLabelsFit() {
        val heights = ProfileTab.entries.map { tab ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(tab.label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing text layout for ${tab.label}", layouts.size == 1)
            val layout = layouts.single()
            assertEquals(tab.label, 1, layout.lineCount)
            assertFalse("Truncated label ${tab.label}", layout.isLineEllipsized(0))
            assertEquals(tab.label, tab.label.length, layout.getLineEnd(0, visibleEnd = true))
            compose.onNode(hasText(tab.label) and hasClickAction()).fetchSemanticsNode().boundsInRoot.height
        }
        assertEquals(1, heights.distinct().size)
    }
}
