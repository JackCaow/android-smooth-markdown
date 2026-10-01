package com.jackcaow.smoothmarkdown

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReaderHtmlKbdSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyCapsStayInRenderedSelectionAndClipboardText() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(
                        markdown = "Press <kbd>Ctrl</kbd>+<kbd>C</kbd> now.",
                        enableHtml = true,
                        selectable = true,
                        scrollable = false,
                        selectionController = controller,
                    )
                }
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals("Press Ctrl+C now.", controller.selectedText) }
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        assertEquals("Press Ctrl+C now.", clipboard.copiedText?.text)
    }

    private class RecordingClipboard : ClipboardManager {
        var copiedText: AnnotatedString? = null
        override fun getText(): AnnotatedString? = copiedText
        override fun setText(annotatedString: AnnotatedString) { copiedText = annotatedString }
    }
}
