package com.jackcaow.smoothmarkdown

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NonTextSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeCopyInterceptorRemovesOnlyNonTextOverlayRows() = runBlocking {
        val recorded = RecordingClipboard()
        val registry = NonTextAnchorRegistry()
        val anchor = "smd" + "a".repeat(497)
        registry.register(anchor)
        val filter = OverlayFilteringClipboard(recorded, registry)
        filter.setClipEntry(ClipEntry(ClipData.newPlainText("selection", "before\n$anchor\nafter")))
        assertEquals("before\nafter", recorded.entry?.clipData?.getItemAt(0)?.text?.toString())
        filter.setClipEntry(ClipEntry(ClipData.newPlainText("selection", "normal\u00A0space")))
        assertEquals("normal\u00A0space", recorded.entry?.clipData?.getItemAt(0)?.text?.toString())
        filter.setClipEntry(ClipEntry(ClipData.newPlainText("selection", "before\n\u00A0\nafter")))
        assertEquals("before\n\u00A0\nafter", recorded.entry?.clipData?.getItemAt(0)?.text?.toString())
        val html = ClipEntry(ClipData.newHtmlText("selection", "before\n$anchor\nafter", "<b>before</b>"))
        filter.setClipEntry(html)
        assertTrue("HTML clipboard payload must be kept intact", recorded.entry === html)
    }

    @Test fun longPressStartsOnRuleAndImageAndImageTapStillOpens() {
        val controller = SmoothSelectionController()
        var imageTaps = 0
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    markdown = "Before.\n\n---\n\n![Diagram](diagram.png)\n\nAfter.",
                    selectable = true,
                    scrollable = false,
                    selectionController = controller,
                    onImageClick = { imageTaps++ },
                    imageBuilder = { _, _, _ -> Box(Modifier.fillMaxWidth().height(96.dp)) },
                )
            }
        }
        val anchors = compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)
        anchors.assertCountEquals(2)
        assertTrue("overlay must be hidden from accessibility", anchors[0].fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        assertTrue("overlay must be hidden from accessibility", anchors[1].fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle {
            assertTrue("anchors absent from selection registrar: ${controller.selectedText.length}",
                controller.selectedText.contains("smd"))
            controller.clear()
        }
        anchors[0].performTouchInput { longClick() }
        compose.runOnIdle { assertTrue("rule did not start selection: ${controller.selectedText.length}", controller.selectedText.contains("smd")) }
        compose.runOnIdle { controller.clear() }
        anchors[1].performTouchInput { longClick() }
        compose.runOnIdle { assertTrue("image did not start selection", controller.selectedText.contains("smd")) }
        compose.runOnIdle { controller.clear() }
        compose.onNodeWithContentDescription("Diagram", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, imageTaps) }
    }

    @Test fun nativeCopyAcrossRuleAndImageStripsRegisteredAnchors() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(
                        markdown = "Before.\n\n---\n\n![Diagram](diagram.png)\n\nAfter.",
                        selectable = true,
                        scrollable = false,
                        selectionController = controller,
                        imageBuilder = { _, _, _ -> Box(Modifier.fillMaxWidth().height(96.dp)) },
                    )
                }
            }
        }
        compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)[0].performTouchInput { longClick() }
        compose.runOnIdle { controller.selectAll() }
        compose.waitForIdle()
        // The pinned Compose toolbar delegates to SelectionManager.copy$foundation.
        // Invoke that exact action while SystemUI's floating toolbar is ANR on this AVD.
        compose.runOnIdle { copyThroughComposeSelectionManager(controller) }
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString()
        assertEquals("Before.\nAfter.", copied)
        compose.runOnIdle { controller.clear() }
        compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)[1].performTouchInput { longClick() }
        compose.runOnIdle { copyThroughComposeSelectionManager(controller) }
        compose.waitForIdle()
        assertEquals("", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())
    }

    private fun copyThroughComposeSelectionManager(controller: SmoothSelectionController) {
        val field = SmoothSelectionController::class.java.getDeclaredField("region").apply { isAccessible = true }
        val state = field.get(controller)!!
        val manager = state.javaClass.getMethod("getManager\$foundation").invoke(state)!!
        manager.javaClass.getMethod("copy\$foundation").invoke(manager)
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
}
