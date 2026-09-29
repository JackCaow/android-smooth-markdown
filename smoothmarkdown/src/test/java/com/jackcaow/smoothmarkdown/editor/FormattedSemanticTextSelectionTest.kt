package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedSemanticTextSelectionTest {
    private fun selection(source: String, fromBlock: Int, from: Int, toBlock: Int, to: Int) =
        MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-$fromBlock", from),
            MarkdownFormattedTextPosition("block-$toBlock", to))

    @Test fun crossBlockCopyKeepsInlineSyntaxAndSelectedHeading() {
        val source = "before\n\nStart **bold** end\n\n# Has [link](https://example.com/a) end\n\nafter"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, 1, 6, 2, 8)
        assertEquals("**bold** end\n\n# Has [link](https://example.com/a)",
            editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertEquals(editor.copyFormattedTextSelectionAsMarkdown(selected),
            editor.copyFormattedTextSelectionAsMarkdown(selected.copy(anchor = selected.focus, focus = selected.anchor)))
    }

    @Test fun deleteAcrossParagraphsMergesVisibleTextWithOneUndoAndRedo() {
        val source = "before\n\nBeforeX\n\nYAfter\n\nafter"
        val editor = MarkdownEditorController(source)
        assertTrue(editor.deleteFormattedTextSelection(selection(source, 1, 6, 2, 1)))
        assertEquals("before\n\nBeforeAfter\n\nafter", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals("before\n\nBeforeAfter\n\nafter", editor.text)
    }

    @Test fun markdownReplacementAcrossBlocksKeepsOutsideSourceAndOneUndo() {
        val source = "outer\r\n\r\nBeforeX\r\n\r\nYAfter\r\n\r\nend"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, 1, 6, 2, 1)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selected, "# Inserted\n\nBody"))
        assertEquals("outer\r\n\r\nBefore\r\n\r\n# Inserted\n\nBody\r\n\r\nAfter\r\n\r\nend", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
    }

    @Test fun reverseSelectionDeletesNestedMarksWithoutChangingUntouchedMarkdown() {
        val source = "Start **bold *deep*** tail\n\n# *Next* end\n\noutside"
        val selected = selection(source, 0, 6, 1, 5)
        val editor = MarkdownEditorController(source)
        assertTrue(editor.deleteFormattedTextSelection(selected.copy(anchor = selected.focus, focus = selected.anchor)))
        assertEquals("Start end\n\noutside", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun validEmojiBoundaryCopiesAndReplacesAcrossBlocks() {
        val source = "😀 Alpha\n\n# Beta"
        val selected = selection(source, 0, 0, 1, 2)
        val editor = MarkdownEditorController(source)
        assertEquals("😀 Alpha\n\n# Be", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selected, "New"))
        assertEquals("New\n\n# ta", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun sameHeadingRangePasteTurnsTrailingTextIntoParagraph() {
        val source = "# BeforeAfter\n\noutside"
        val editor = MarkdownEditorController(source)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selection(source, 0, 3, 0, 6), "## New"))
        assertEquals("# Bef\n\n## New\n\nAfter\n\noutside", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun selectionRejectsUnsupportedInterveningBlockStaleSourceAndHalfEmojiAtomically() {
        val source = "😀 first\n\n- item\n\n# Last"
        val editor = MarkdownEditorController(source)
        val acrossList = selection(source, 0, 2, 2, 2)
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(acrossList))
        assertFalse(editor.deleteFormattedTextSelection(acrossList))
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(acrossList, "Body"))
        val halfEmoji = selection(source, 0, 1, 0, 3)
        assertFalse(editor.deleteFormattedTextSelection(halfEmoji))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(halfEmoji))
        val stale = selection("older", 0, 0, 0, 2)
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(stale, "Body"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }
}
