package com.jackcaow.smoothmarkdown.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorHostStateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostReceivesCommittedSourceSelectionAndUndo() {
        val controller = MarkdownEditorController("Before")
        val changes = mutableListOf<String>()
        val selections = mutableListOf<TextRange>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, onChanged = { changes += it },
                    onSelectionChanged = { selections += it })
            }
        }

        compose.runOnIdle {
            assertEquals(emptyList<String>(), changes)
            controller.replaceRange(0, 6, "After")
            controller.setSelection(1, 3)
        }
        compose.waitUntil(5_000) { changes == listOf("After") && selections.contains(TextRange(1, 3)) }
        compose.runOnIdle { check(controller.undo()) }
        compose.waitUntil(5_000) { changes == listOf("After", "Before") }
    }

    @Test fun hostControlsModeAndReceivesFocusModeRequests() {
        val controller = MarkdownEditorController("Content")
        var hostMode by mutableStateOf(MarkdownEditorMode.SOURCE)
        val modes = mutableListOf<MarkdownEditorMode>()
        val focusModes = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, mode = hostMode,
                    onModeChanged = { modes += it; hostMode = it },
                    onFocusModeChanged = { focusModes += it })
            }
        }

        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(5_000) { controller.mode == MarkdownEditorMode.PREVIEW }
        compose.onNodeWithTag("editor-focus-mode").performClick()
        compose.onNodeWithTag("editor-exit-focus").performClick()
        compose.runOnIdle {
            assertEquals(listOf(MarkdownEditorMode.PREVIEW), modes)
            assertEquals(listOf(true, false), focusModes)
        }
    }
}
