package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedBlockSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectButtonsStillReplaceWholeBlocksAfterCharacterDragTakesLongPress() {
        val original = "before\n\n# Old\n\n> quote\n\nafter"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-block-select-block-1").performScrollTo().performClick()
        compose.onNodeWithTag("formatted-block-select-block-2").performScrollTo().performClick()
        compose.onNodeWithTag("formatted-block-selection-count").assertExists()
        compose.onNodeWithTag("formatted-block-replacement").performTextInput("## New")
        compose.onNodeWithTag("formatted-block-replace").performClick()
        compose.runOnIdle {
            assertEquals("before\n\n## New\n\nafter", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }

    @Test fun selectedProseCanBecomeOneBulletListWithoutLeavingFormattedMode() {
        val original = "Before\n\n# One\n\nTwo\n\nAfter"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-block-select-block-1").performScrollTo().performClick()
        compose.onNodeWithTag("formatted-block-select-block-2").performScrollTo().performClick()
        compose.onNodeWithTag("formatted-block-transform-unordered_list").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Before\n\n- One\n- Two\n\nAfter", controller.text)
            assertEquals(MarkdownEditorMode.FORMATTED, controller.mode)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }
}
