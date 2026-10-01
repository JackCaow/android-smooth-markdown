package com.jackcaow.smoothmarkdown

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReaderDetailsSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val source = """
        Before details.

        <details>
        <summary>Details summary</summary>
        Details body.
        </details>

        After details.
    """.trimIndent()

    @Test fun collapsedAndExpandedSelectAllKeepOnlyVisibleTextInSourceOrder() {
        val controller = SmoothSelectionController()
        compose.setContent {
            MaterialTheme { SmoothMarkdown(source, selectable = true, selectionController = controller) }
        }
        compose.runOnIdle {
            controller.selectAll()
            val selected = controller.selectedText
            assertOrdered(selected, "Before details.", "Details summary", "After details.")
            assertFalse("collapsed body was selected: $selected", selected.contains("Details body."))
            assertFalse("details arrow was selected: $selected", selected.contains("›") || selected.contains("⌄"))
            controller.clear()
        }

        compose.onNodeWithText("Details summary", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Details body.").assertExists()
        compose.runOnIdle {
            controller.selectAll()
            val selected = controller.selectedText
            assertOrdered(selected, "Before details.", "Details summary", "Details body.", "After details.")
            assertFalse("details arrow was selected: $selected", selected.contains("›") || selected.contains("⌄"))
            controller.clear()
        }
        compose.onNodeWithText("Details summary", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Details body.").assertDoesNotExist()
        compose.runOnIdle {
            controller.selectAll()
            val selected = controller.selectedText
            assertOrdered(selected, "Before details.", "Details summary", "After details.")
            assertFalse("closed body remained selected: $selected", selected.contains("Details body."))
        }
    }

    @Test fun dragFromExpandedBodyIntoFollowingProseCopiesVisibleText() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdown(source, selectable = true, selectionController = controller) }
            }
        }
        compose.onNodeWithText("Details summary", useUnmergedTree = true).performTouchInput { click() }
        val start = compose.onNodeWithText("Details body.", useUnmergedTree = true)
            .glyphCenterInRoot()
        val end = compose.onNodeWithText("After details.", useUnmergedTree = true)
            .glyphCenterInRoot("After details.".lastIndex)
        compose.onRoot().performTouchInput {
            down(start)
            advanceEventTime(750)
            repeat(16) { step -> moveTo(start + (end - start) * ((step + 1) / 16f)) }
            up()
        }
        compose.runOnIdle {
            assertOrdered(controller.selectedText, "body", "After details")
        }
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        val copied = clipboard.copiedText?.text.orEmpty()
        assertOrdered(copied, "body", "After details")
        assertFalse("Copy included details arrow: $copied", copied.contains("⌄") || copied.contains("›"))
    }

    private fun assertOrdered(text: String, vararg parts: String) {
        var cursor = 0
        for (part in parts) {
            val at = text.indexOf(part, cursor)
            assertTrue("Missing or reordered '$part' in: $text", at >= cursor)
            cursor = at + part.length
        }
    }

    private class RecordingClipboard : ClipboardManager {
        var copiedText: AnnotatedString? = null
        override fun getText(): AnnotatedString? = copiedText
        override fun setText(annotatedString: AnnotatedString) { copiedText = annotatedString }
    }
}
