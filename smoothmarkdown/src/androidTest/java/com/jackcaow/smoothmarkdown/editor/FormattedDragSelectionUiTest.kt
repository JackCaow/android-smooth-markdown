package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            val destination = secondCenter - firstBounds.topLeft
            repeat(12) { step ->
                moveTo(center + (destination - center) * ((step + 1) / 12f))
            }
            up()
        }
        compose.runOnIdle {
            assertEquals(source, controller.copyFormattedBlockSelectionAsMarkdown())
        }
    }

    @Test fun longPressInsideOneHeadingLeavesItsNativeEditingAvailable() {
        val controller = MarkdownEditorController("# First").apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        val field = compose.onNodeWithTag("formatted-block-drag-block-0")
        field.performTouchInput {
            down(center)
            advanceEventTime(750)
            moveTo(center + Offset(0f, 8f))
            up()
        }
        compose.runOnIdle { assertNull(controller.formattedBlockSelection) }
        field.performClick()
        field.performTextReplacement("Edited")
        compose.runOnIdle { assertEquals("# Edited", controller.text) }
    }

    @Test fun longPressDragAcrossListItemsStillSelectsSiblings() {
        val source = "- first\n- second"
        val controller = MarkdownEditorController(source).apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        val first = compose.onNodeWithTag("formatted-list-drag-block-0-0")
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondCenter = compose.onNodeWithTag("formatted-list-drag-block-0-1")
            .fetchSemanticsNode().boundsInRoot.center
        first.performTouchInput {
            down(center)
            advanceEventTime(750)
            val destination = secondCenter - firstBounds.topLeft
            repeat(12) { step -> moveTo(center + (destination - center) * ((step + 1) / 12f)) }
            up()
        }
        compose.runOnIdle { assertEquals(source, controller.copyFormattedListItemSelectionAsMarkdown()) }
    }

    @Test fun longPressDragAcrossTableCellsStillSelectsRectangle() {
        val source = "| A | B |\n| --- | --- |\n| one | two |"
        val controller = MarkdownEditorController(source).apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        val first = compose.onNodeWithTag("formatted-table-drag-block-0-0-0")
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondCenter = compose.onNodeWithTag("formatted-table-drag-block-0-0-1")
            .fetchSemanticsNode().boundsInRoot.center
        first.performTouchInput {
            down(center)
            advanceEventTime(750)
            val destination = secondCenter - firstBounds.topLeft
            repeat(12) { step -> moveTo(center + (destination - center) * ((step + 1) / 12f)) }
            up()
        }
        compose.runOnIdle { assertEquals("A\tB", controller.copyFormattedTableCellSelectionAsTsv()) }
    }
}
