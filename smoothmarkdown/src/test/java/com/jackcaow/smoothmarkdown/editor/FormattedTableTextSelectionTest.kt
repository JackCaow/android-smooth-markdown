package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedTableTextSelectionTest {
    private fun prose(block: Int, offset: Int) = MarkdownFormattedTextPosition("block-$block", offset)
    private fun cell(block: Int, row: Int, column: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, tableCell = MarkdownTableCellPosition(row, column))
    private fun selected(source: String, first: MarkdownFormattedTextPosition, last: MarkdownFormattedTextPosition) =
        MarkdownFormattedTextSelection(source, first, last)

    @Test fun sameCellCopyDeleteReplaceKeepPipesPaddingCrLfAndOneUndo() {
        val source = "outside\r\n\r\n| A\\|B  | H |\r\n| :--- | ---: |\r\n| keep | BeforeXAfter |\r\n\r\nend"
        val pipe = selected(source, cell(1, 0, 0, 1), cell(1, 0, 0, 2))
        val copy = MarkdownEditorController(source)
        assertEquals("\\|", copy.copyFormattedTextSelectionAsMarkdown(pipe))
        assertFalse(copy.canUndo)
        val delete = MarkdownEditorController(source)
        assertTrue(delete.deleteFormattedTextSelection(pipe))
        assertEquals(source.replace("A\\|B", "AB"), delete.text)
        assertTrue(delete.undo())
        assertEquals(source, delete.text)
        assertFalse(delete.canUndo)
        assertTrue(delete.redo())
        assertEquals(source.replace("A\\|B", "AB"), delete.text)
        val replace = MarkdownEditorController(source)
        val x = selected(source, cell(1, 1, 1, 6), cell(1, 1, 1, 7))
        assertTrue(replace.replaceFormattedTextSelectionWithMarkdown(x, "new"))
        assertEquals(source.replace("BeforeXAfter", "BeforenewAfter"), replace.text)
        assertTrue(replace.undo())
        assertEquals(source, replace.text)
    }

    @Test fun lastTableCellToFollowingProseEditsSafeTableEdge() {
        val source = "top\r\n\r\n| H | Tail |\r\n| --- | --- |\r\n| A | BeforeX |\r\n\r\nYAfter\r\n\r\nbottom"
        val selection = selected(source, cell(1, 1, 1, 6), prose(2, 1))
        val editor = MarkdownEditorController(source)
        assertEquals("X\r\n\r\nY", editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertEquals("X\r\n\r\nY", editor.copyFormattedTextSelectionAsMarkdown(
            selection.copy(anchor = selection.focus, focus = selection.anchor)))
        assertTrue(editor.deleteFormattedTextSelection(selection))
        assertEquals("top\r\n\r\n| H | Tail |\r\n| --- | --- |\r\n| A | Before |\r\n\r\nAfter\r\n\r\nbottom", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        val replace = MarkdownEditorController(source)
        assertTrue(replace.replaceFormattedTextSelectionWithMarkdown(selection, "# New"))
        assertEquals("top\r\n\r\n| H | Tail |\r\n| --- | --- |\r\n| A | Before |\r\n\r\n# New\r\n\r\nAfter\r\n\r\nbottom", replace.text)
        assertTrue(replace.undo())
        assertEquals(source, replace.text)
    }

    @Test fun precedingProseToFirstHeaderCellPreservesRemainingTable() {
        val source = "BeforeX\n\n| YAfter | H |\n| --- | --- |\n| A | B |\n\noutside"
        val selection = selected(source, prose(0, 6), cell(1, 0, 0, 1))
        val editor = MarkdownEditorController(source)
        assertEquals("X\n\nY", editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertTrue(editor.deleteFormattedTextSelection(selection))
        assertEquals("Before\n\n| After | H |\n| --- | --- |\n| A | B |\n\noutside", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun staleUnsafeAndInteriorTableEndpointsRejectWithoutMutation() {
        val source = "BeforeX\n\n| First | Second |\n| --- | --- |\n| a | 😀tail |\n\nAfter"
        val editor = MarkdownEditorController(source)
        val bad = listOf(
            selected(source, prose(0, 6), cell(1, 0, 1, 2)),
            selected(source, cell(1, 1, 0, 1), prose(2, 1)),
            selected(source, cell(1, 1, 1, 1), cell(1, 1, 1, 3)),
            selected("stale", cell(1, 0, 0, 0), cell(1, 0, 0, 2)),
        )
        bad.forEach { selection ->
            assertNull(editor.copyFormattedTextSelectionAsMarkdown(selection))
            assertFalse(editor.deleteFormattedTextSelection(selection))
            assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(selection, "new"))
        }
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }
    @Test fun visiblePipePasteEscapesDelimiterAndKeepsOtherCells() {
        val source = "| aXb | H |\n| --- | --- |\n| keep | still |"
        val selection = selected(source, cell(0, 0, 0, 1), cell(0, 0, 0, 2))
        val editor = MarkdownEditorController(source)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selection, "|"))
        assertEquals("| a\\|b | H |\n| --- | --- |\n| keep | still |", editor.text)
        assertEquals("a\\|b", editor.semanticTable("block-0")?.headers?.first())
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        val alreadyEscaped = MarkdownEditorController(source)
        assertTrue(alreadyEscaped.replaceFormattedTextSelectionWithMarkdown(selection, "\\|"))
        assertEquals("| a\\|b | H |\n| --- | --- |\n| keep | still |", alreadyEscaped.text)
    }

    @Test fun backslashParityThatWouldChangeTableStructureIsRejectedAtomically() {
        val source = "| a\\|b | H |\n| --- | --- |\n| keep | still |"
        val selection = selected(source, cell(0, 0, 0, 0), cell(0, 0, 0, 1))
        val editor = MarkdownEditorController(source)
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(selection, "\\"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }

}
