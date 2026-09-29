package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedQuoteTextSelectionTest {
    private fun quote(block: Int, line: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, quoteLineIndex = line)

    private fun selection(source: String, from: MarkdownFormattedTextPosition,
                          to: MarkdownFormattedTextPosition) = MarkdownFormattedTextSelection(source, from, to)

    @Test fun quoteLineEditsPreserveNestedMarkersInlineSyntaxAndCrLf() {
        val source = "> Intro\r\n> > Left **bold** 😀 tail\r\n> End\r\n\r\nAfter"
        val editor = MarkdownEditorController(source)
        val block = editor.semanticDocument().blocks.first()
        val quote = MarkdownSourceQuote.parse(block)!!
        assertEquals(listOf(1, 2, 1), quote.lines.map { it.depth })
        assertEquals("Left bold 😀 tail", quote.lines[1].inline(false).visible)
        assertTrue(editor.replaceFormattedQuoteLineText(block.id, 1, "Left bold 😀 friend"))
        assertEquals("> Intro\r\n> > Left **bold** 😀 friend\r\n> End\r\n\r\nAfter", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertTrue(editor.redo())
    }

    @Test fun sameLineCharacterCopyDeleteAndUndoKeepQuoteSource() {
        val source = "> Before **bold** after\n> Keep"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, quote(0, 0, 7), quote(0, 0, 11))
        assertEquals("> **bold**", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.deleteFormattedTextSelection(selected))
        assertEquals("> Before  after\n> Keep", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selected, "friend"))
        assertEquals("> Before friend after\n> Keep", editor.text)
    }

    @Test fun multiLineQuoteEditAndCopyPreserveTrailingLine() {
        val source = "> BeforeX\r\n> Middle\r\n> YAfter\r\n> Keep"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, quote(0, 0, 6), quote(0, 2, 1))
        assertEquals("> X\r\n> Middle\r\n> Y", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.deleteFormattedTextSelection(selected))
        assertEquals("> BeforeAfter\r\n> Keep", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selected, "new"))
        assertEquals("> BeforenewAfter\r\n> Keep", editor.text)
    }

    @Test fun quoteToProseCopyRetainsSourceAndInvalidCrossBlockEditFailsClosed() {
        val source = "> Quoted here\n\nAfter text"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, quote(0, 0, 7), MarkdownFormattedTextPosition("block-1", 5))
        assertEquals("> here\n\nAfter", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertFalse(editor.deleteFormattedTextSelection(selected))
        assertEquals(source, editor.text)
    }

    @Test fun highlightsQuoteLinesAndRejectsStaleSurrogateOrUnmarkedLines() {
        val source = "> A😀X\n> Second\n> End"
        val editor = MarkdownEditorController(source)
        val endpoints = FormattedTextEndpoints(source, quote(0, 0, 3)).withFocus(quote(0, 2, 1))
        val blocks = editor.semanticDocument().blocks
        assertEquals(TextRange(3, 4), endpoints.quoteVisibleRange(blocks, "block-0", 0, 4))
        assertEquals(TextRange(0, 6), endpoints.quoteVisibleRange(blocks, "block-0", 1, 6))
        assertEquals(TextRange(0, 1), endpoints.quoteVisibleRange(blocks, "block-0", 2, 3))
        val halfEmoji = selection(source, quote(0, 0, 2), quote(0, 0, 3))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(halfEmoji))
        assertFalse(editor.deleteFormattedTextSelection(halfEmoji))
        val stale = selection(source + "x", quote(0, 0, 0), quote(0, 0, 1))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(stale))
        assertFalse(editor.deleteFormattedTextSelection(stale))
        assertNull(MarkdownSourceQuote.parse(editor.semanticDocument().blocks.first().copy(source = "> marked\nlazy")))
        assertEquals(source, editor.text)
    }
}
