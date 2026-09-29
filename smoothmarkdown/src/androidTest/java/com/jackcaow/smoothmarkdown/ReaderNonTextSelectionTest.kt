package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderNonTextSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectAllIncludesTextOnBothSidesOfRuleAndTableCells() {
        val controller = SmoothSelectionController()
        val source = """
            Before the rule.

            ---

            | Left | Right |
            | --- | --- |
            | One | Two |

            After the table.
        """.trimIndent()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(source, selectable = true, selectionController = controller)
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitForIdle()
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("Missing text before rule: $selected", selected.contains("Before the rule."))
            assertTrue("Missing table cell: $selected", selected.contains("One"))
            assertTrue("Missing text after table: $selected", selected.contains("After the table."))
        }
    }

    @Test fun longPressDragFromTableCellIntoFollowingParagraphSelectsVisibleText() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val source = """
            Before the table.

            | Left | Right |
            | --- | --- |
            | One | Two |

            After the table.
        """.trimIndent()
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(source, selectable = true, scrollable = false,
                        selectionController = controller)
                }
            }
        }
        val start = compose.onNodeWithText("One", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText("After the table.", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(start)
            advanceEventTime(750)
            repeat(16) { step -> moveTo(start + (end - start) * ((step + 1) / 16f)) }
            up()
        }
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("long-press drag missed first table cell: $selected", selected.contains("One"))
            assertTrue("long-press drag missed next table cell: $selected", selected.contains("Two"))
            assertTrue("long-press drag missed following paragraph: $selected", selected.contains("After the table"))
        }
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue("copy missed first table cell: $copied", copied.contains("One"))
        assertTrue("copy missed next table cell: $copied", copied.contains("Two"))
        assertTrue("copy missed following paragraph: $copied", copied.contains("After the table"))
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
}
