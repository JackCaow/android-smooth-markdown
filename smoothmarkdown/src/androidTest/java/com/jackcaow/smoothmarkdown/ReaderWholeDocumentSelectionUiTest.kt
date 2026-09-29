package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run on a physical Android device to verify native handles and offscreen Copy. */
class ReaderWholeDocumentSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitWholeDocumentSelectionMountsOffscreenTextAndCopiesThroughNativeRegion() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val markdown = buildString {
            repeat(80) { index ->
                append("Paragraph $index unique text.\n\n")
                if (index == 39) append("---\n\n")
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(markdown, modifier = Modifier.fillMaxSize(),
                        selectable = true, selectionController = controller)
                }
            }
        }
        compose.runOnIdle { assertTrue(controller.selectAllDocument()) }
        compose.waitUntil(timeoutMillis = 10_000) {
            controller.fullDocumentSelectionEstablished &&
                controller.selectedText.contains("Paragraph 79 unique text.")
        }
        compose.runOnIdle {
            assertTrue(controller.fullDocumentSelectionMode)
            assertTrue(controller.selectedText.contains("Paragraph 0 unique text."))
            assertFalse(controller.selectedText.contains("smd"))
        }
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copied.contains("Paragraph 0 unique text."))
        assertTrue(copied.contains("Paragraph 79 unique text."))
        assertFalse(copied.contains("smd"))
        compose.runOnIdle { assertFalse(controller.fullDocumentSelectionMode) }
    }

    @Test fun expandedDetailsStayExpandedWhenSwitchingSelectionLayout() {
        val controller = SmoothSelectionController()
        val markdown = "<details>\n<summary>Disclosure</summary>\nVisible body\n</details>\n\n" +
            (0..60).joinToString("\n\n") { "Paragraph $it" }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(markdown, modifier = Modifier.fillMaxSize(),
                    selectable = true, selectionController = controller)
            }
        }
        compose.onNodeWithText("Disclosure", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Visible body").assertExists()
        compose.runOnIdle { assertTrue(controller.selectAllDocument()) }
        compose.waitUntil(timeoutMillis = 10_000) {
            controller.fullDocumentSelectionEstablished && controller.selectedText.contains("Paragraph 60")
        }
        compose.runOnIdle { assertTrue(controller.selectedText.contains("Visible body")) }
        compose.runOnIdle { controller.clear() }
        compose.waitForIdle()
        compose.onNodeWithText("Visible body").assertExists()
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
}
