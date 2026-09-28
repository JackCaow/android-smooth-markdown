package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slashSuggestionIsVisibleAndOneUndoStep() {
        val controller = MarkdownEditorController("/he")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-slash-suggestions").assertExists()
        compose.onNodeWithTag("editor-slash-suggestion-1").performClick()
        compose.runOnIdle {
            assertEquals("## ", controller.text)
            check(controller.undo())
            assertEquals("/he", controller.text)
        }
    }

    @Test fun findNavigatesSourceAndFocusModeCanBeExited() {
        val controller = MarkdownEditorController("Alpha beta alpha")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-focus-mode").performClick()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("editor-exit-focus").performClick()
        compose.onNodeWithText("Undo").assertExists()

        compose.onNodeWithTag("editor-find").performClick()
        compose.onNodeWithTag("editor-search-query").performTextInput("alpha")
        compose.onNodeWithTag("editor-search-count").assertTextEquals("2 matches")
        compose.onNodeWithTag("editor-search-next").performClick()
        compose.runOnIdle {
            assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
            assertEquals("Alpha beta alpha", controller.text)
            check(!controller.canUndo)
        }
    }
}
