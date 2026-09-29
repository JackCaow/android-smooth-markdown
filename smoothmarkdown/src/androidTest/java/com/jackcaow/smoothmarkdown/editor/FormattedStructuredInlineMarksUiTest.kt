package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedStructuredInlineMarksUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolbarMarksSelectedListPhysicalLinesAndUndoRestoresSource() {
        val original = "- First line\n  second line\n- keep"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("formatted-list-item-block-0-0").performClick()
            .performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("formatted-list-text-start-block-0-0").performClick()
        compose.onNodeWithTag("formatted-list-continuation-block-0-0-1").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-list-text-end-block-0-0").performClick()
        compose.onNodeWithTag("formatted-text-bold").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("- Fi**rst line**\n  **second** line\n- keep", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun toolbarMarksSelectedTableCellsInVisibleRowOrder() {
        val original = "| Alpha | Beta |\n| --- | --- |\n| One | Two |"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("formatted-table-text-block-0-0-0").performClick()
            .performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("formatted-table-text-start-block-0-0-0").performClick()
        compose.onNodeWithTag("formatted-table-text-block-0-1-0").performClick()
            .performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("formatted-table-text-end-block-0-1-0").performClick()
        compose.onNodeWithTag("formatted-text-bold").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("| Al**pha** | **Beta** |\n| --- | --- |\n| **On**e | Two |", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun toolbarMarksSelectedQuoteRowsWithoutChangingMarkers() {
        val original = "> first text\n> second row\n> keep"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("formatted-quote-line-block-0-0").performClick()
            .performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("formatted-quote-text-start-block-0-0").performClick()
        compose.onNodeWithTag("formatted-quote-line-block-0-1").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-quote-text-end-block-0-1").performClick()
        compose.onNodeWithTag("formatted-text-bold").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("> fi**rst text**\n> **second** row\n> keep", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }
}
