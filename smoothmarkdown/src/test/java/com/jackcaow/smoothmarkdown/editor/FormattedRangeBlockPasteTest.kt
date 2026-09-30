package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedRangeBlockPasteTest {
    private fun selection(source: String, first: Int, start: Int, last: Int, end: Int) =
        MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-$first", start),
            MarkdownFormattedTextPosition("block-$last", end))

    @Test fun reversedRangePastesHeadingAndListPreservingCrLfNeighborsAndUndo() {
        val source = "Prelude\r\n\r\n# Alpha **bold**\r\n\r\nBeta tail\r\n\r\nNeighbor `exact`"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, 1, 6, 2, 5).let { it.copy(anchor = it.focus, focus = it.anchor) }
        val paste = "## Inserted\n\n- one\n- two"
        assertTrue(editor.canReplaceFormattedTextSelectionWithMarkdownBlocks(selected, paste))
        assertFalse(editor.canUndo)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdownBlocks(selected, paste))
        assertEquals("Prelude\r\n\r\n# Alpha \r\n\r\n## Inserted\n\n- one\n- two\r\n\r\ntail\r\n\r\nNeighbor `exact`", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
    }

    @Test fun sameHeadingRangeMakesUnselectedSuffixParagraph() {
        val source = "# BeforeAfter\n\nOutside"
        val editor = MarkdownEditorController(source)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdownBlocks(selection(source, 0, 3, 0, 6), "## New"))
        assertEquals("# Bef\n\n## New\n\nAfter\n\nOutside", editor.text)
    }

    @Test fun partialInlineMarksOutsideRangeKeepTheirMarkdown() {
        val source = "A **bold** X\n\nY [link](https://example.com/a) end"
        val editor = MarkdownEditorController(source)
        val selected = selection(source, 0, 4, 1, 3)
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdownBlocks(selected, "## New"))
        assertEquals("A **bo**\n\n## New\n\n[ink](https://example.com/a) end", editor.text)
    }

    @Test fun rejectsStaleStructuredOrUnsafeRangesWithoutHistory() {
        val source = "First **bold**\n\n- item\n\nLast"
        val editor = MarkdownEditorController(source)
        val throughList = selection(source, 0, 1, 2, 2)
        val stale = throughList.copy(source = "old")
        val splitEmojiSource = "😀 First\n\nLast"
        val emoji = MarkdownEditorController(splitEmojiSource)
        val halfEmoji = selection(splitEmojiSource, 0, 1, 1, 1)
        listOf(stale, throughList).forEach { selected ->
            assertFalse(editor.canReplaceFormattedTextSelectionWithMarkdownBlocks(selected, "## New"))
            assertFalse(editor.replaceFormattedTextSelectionWithMarkdownBlocks(selected, "## New"))
        }
        assertFalse(editor.canReplaceFormattedTextSelectionWithMarkdownBlocks(selection(source, 0, 1, 0, 3), ""))
        assertFalse(emoji.replaceFormattedTextSelectionWithMarkdownBlocks(halfEmoji, "## New"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertEquals(splitEmojiSource, emoji.text)
        assertFalse(emoji.canUndo)
    }
}
