package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedStructuredInlineMarksTest {
    private fun list(block: Int, path: List<Int>, line: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, listPath = path, listLineIndex = line)
    private fun quote(block: Int, line: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, quoteLineIndex = line)
    private fun cell(block: Int, row: Int, column: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, tableCell = MarkdownTableCellPosition(row, column))
    private fun selection(source: String, a: MarkdownFormattedTextPosition, b: MarkdownFormattedTextPosition) =
        MarkdownFormattedTextSelection(source, a, b)

    @Test fun partialListPhysicalLinesKeepMarkersCrLfAndUndoAsOneEdit() {
        val source = "before\r\n\r\n- First line\r\n  second line\r\n- untouched\r\n\r\nafter"
        val controller = MarkdownEditorController(source)
        val selected = selection(source, list(1, listOf(0), 1, 6), list(1, listOf(0), 0, 2))
        assertTrue(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.BOLD))
        assertEquals("before\r\n\r\n- Fi**rst line**\r\n  **second** line\r\n- untouched\r\n\r\nafter", controller.text)
        assertEquals(MarkdownBlockKind.BULLET_LIST, controller.semanticDocument().blocks[1].kind)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun partialQuoteRowsRetainPrefixesAndUndo() {
        val source = "before\n\n> first text\n> second row\n> third\n\nafter"
        val controller = MarkdownEditorController(source)
        val selected = selection(source, quote(1, 0, 2), quote(1, 1, 6))
        assertTrue(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.BOLD))
        assertEquals("before\n\n> fi**rst text**\n> **second** row\n> third\n\nafter", controller.text)
        assertEquals(MarkdownBlockKind.QUOTE, controller.semanticDocument().blocks[1].kind)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun partialTableCellsUseRowMajorSelectionAndKeepPipesPaddingAndUndo() {
        val source = "before\n\n| Alpha | Beta |\n| --- | --- |\n| One | Two |\n\nafter"
        val controller = MarkdownEditorController(source)
        val selected = selection(source, cell(1, 1, 0, 2), cell(1, 0, 0, 2))
        assertTrue(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.BOLD))
        assertEquals("before\n\n| Al**pha** | **Beta** |\n| --- | --- |\n| **On**e | Two |\n\nafter", controller.text)
        assertEquals(MarkdownBlockKind.TABLE, controller.semanticDocument().blocks[1].kind)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)

        val endpoints = FormattedTextEndpoints(source, selected.anchor, selected.focus)
        val blocks = MarkdownDocumentCodec.parse(source).blocks
        assertEquals(TextRange(2, 5), endpoints.tableVisibleRange(blocks, "block-1", MarkdownTableCellPosition(0, 0), 5))
        assertEquals(TextRange(0, 4), endpoints.tableVisibleRange(blocks, "block-1", MarkdownTableCellPosition(0, 1), 4))
        assertEquals(TextRange(0, 2), endpoints.tableVisibleRange(blocks, "block-1", MarkdownTableCellPosition(1, 0), 3))
        assertEquals(null, endpoints.tableVisibleRange(blocks, "block-1", MarkdownTableCellPosition(1, 1), 3))
    }

    @Test fun staleMixedAndUnsafeSelectionsFailWithoutChangingSource() {
        val source = "- first\n- second\n\n| A | B |\n| --- | --- |\n| C | D |\n\n> quote"
        val controller = MarkdownEditorController(source)
        val mixed = selection(source, list(0, listOf(0), 0, 1), cell(1, 0, 0, 1))
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(mixed, MarkdownEditorCommand.BOLD))
        val table = selection(source, cell(1, 0, 0, 0), cell(1, 0, 1, 1))
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(table, MarkdownEditorCommand.LINK))
        val stale = selection(source + " ", quote(2, 0, 0), quote(2, 0, 2))
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(stale, MarkdownEditorCommand.BOLD))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun tableCellsWithHiddenInlineDelimitersRemainUnchanged() {
        val source = "| **A** | B |\n| --- | --- |\n| C | D |"
        val controller = MarkdownEditorController(source)
        val selected = selection(source, cell(0, 0, 0, 0), cell(0, 0, 1, 1))
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.ITALIC))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }
}
