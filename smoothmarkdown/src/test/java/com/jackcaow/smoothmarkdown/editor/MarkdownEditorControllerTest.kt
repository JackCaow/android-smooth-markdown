package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorControllerTest {
    @Test fun wrapsUtf16SelectionAndRestoresHistory() {
        val controller = MarkdownEditorController("😀hi")
        controller.setSelection(2, 4)
        controller.applyCommand(MarkdownEditorCommand.BOLD)
        assertEquals("😀**hi**", controller.text)
        assertEquals(TextRange(4, 6), controller.selection)
        assertTrue(controller.isDirty)
        assertTrue(controller.undo())
        assertEquals("😀hi", controller.text)
        assertTrue(controller.redo())
        assertEquals("😀**hi**", controller.text)
        controller.markSaved()
        assertFalse(controller.isDirty)
    }

    @Test fun formatsOnlySelectedLinesWhenSelectionEndsAtNewline() {
        val controller = MarkdownEditorController("one\ntwo\nthree")
        controller.setSelection(0, 8)
        controller.applyCommand(MarkdownEditorCommand.HEADING2)
        assertEquals("## one\n## two\nthree", controller.text)
    }

    @Test fun groupsTransactionIntoOneUndoStep() {
        val controller = MarkdownEditorController("a")
        controller.transaction {
            controller.insertMarkdown("b")
            controller.insertMarkdown("c")
        }
        assertEquals("abc", controller.text)
        assertTrue(controller.undo())
        assertEquals("a", controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun findsAndSelectsNextMatch() {
        val controller = MarkdownEditorController("Alpha beta alpha")
        assertEquals(listOf(TextRange(0, 5), TextRange(11, 16)), controller.findMatches("alpha"))
        controller.setSelection(0, 5)
        assertEquals(TextRange(11, 16), controller.selectNextMatch("alpha"))
    }

    @Test fun paragraphRemovesTaskMarker() {
        val controller = MarkdownEditorController("- [x] done")
        controller.applyCommand(MarkdownEditorCommand.PARAGRAPH)
        assertEquals("done", controller.text)
    }
}
