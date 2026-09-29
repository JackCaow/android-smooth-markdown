package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedDragSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longPressAndDragAcrossHeadingFieldsSelectsMarkdownWithoutButtons() {
        val source = "# First\n\n## Second"
        val controller = MarkdownEditorController(source).apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        val first = compose.onNodeWithTag("formatted-block-drag-block-0")
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondCenter = compose.onNodeWithTag("formatted-block-drag-block-1")
            .fetchSemanticsNode().boundsInRoot.center
        first.performTouchInput {
            down(center)
            advanceEventTime(750)
            moveTo(secondCenter - firstBounds.topLeft)
            up()
        }
        compose.runOnIdle {
            assertEquals(source, controller.copyFormattedBlockSelectionAsMarkdown())
        }
    }
}
