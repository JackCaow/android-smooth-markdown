package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedListSourcePasteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blankParagraphPasteOpensSourceWithItsContent() {
        val controller = MarkdownEditorController("- Before target after\n- Keep")
            .apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-list-item-block-0-0")
            .performTextReplacement("Before one\n\ntwo after")

        compose.onNodeWithTag("editor-source-input").assertExists().assertIsFocused()
        compose.runOnIdle {
            assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
            assertEquals("- Before one\n\n  two after\n- Keep", controller.text)
        }
    }

    @Test fun controlledModeHostReceivesSourceRequestAfterFallback() {
        val controller = MarkdownEditorController("- Before target after\n- Keep")
        val hostMode = mutableStateOf(MarkdownEditorMode.FORMATTED)
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(), mode = hostMode.value,
                    onModeChanged = { hostMode.value = it })
            }
        }

        compose.onNodeWithTag("formatted-list-item-block-0-0")
            .performTextReplacement("Before one\n\ntwo after")

        compose.onNodeWithTag("editor-source-input").assertExists().assertIsFocused()
        compose.runOnIdle {
            assertEquals(MarkdownEditorMode.SOURCE, hostMode.value)
            assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
            assertEquals("- Before one\n\n  two after\n- Keep", controller.text)
        }
    }
}
