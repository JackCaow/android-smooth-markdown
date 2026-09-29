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

    @Test fun listCodeAndTableBetweenCharacterEndpointsCopyDeleteAndReplaceAsCompleteSourceBlocks() {
        val middleBlocks = listOf(
            "- one\n  - nested\n- two",
            "1. one\n2. two",
            "```kotlin\nval answer = 42\n```",
            "| Key | Value |\n| --- | --- |\n| A | B |",
        )
        middleBlocks.forEach { middle ->
            val source = "outside before\n\nLeft**bold**\n\n$middle\n\nRightTail\n\noutside after"
            val selected = selection(source, 1, 4, 3, 5)
            val copyEditor = MarkdownEditorController(source)
            assertEquals("**bold**\n\n$middle\n\nRight", copyEditor.copyFormattedTextSelectionAsMarkdown(selected))
            assertEquals(source, copyEditor.text)
            assertFalse(copyEditor.canUndo)
            val deleteEditor = MarkdownEditorController(source)
            assertTrue("delete across $middle", deleteEditor.deleteFormattedTextSelection(selected))
            assertEquals("outside before\n\nLeftTail\n\noutside after", deleteEditor.text)
            assertTrue(deleteEditor.undo())
            assertEquals(source, deleteEditor.text)
            assertFalse(deleteEditor.canUndo)
            assertTrue(deleteEditor.redo())
            assertEquals("outside before\n\nLeftTail\n\noutside after", deleteEditor.text)
            val replaceEditor = MarkdownEditorController(source)
            assertTrue("replace across $middle", replaceEditor.replaceFormattedTextSelectionWithMarkdown(selected, "# Inserted"))
            assertEquals("outside before\n\nLeft\n\n# Inserted\n\nTail\n\noutside after", replaceEditor.text)
            assertTrue(replaceEditor.undo())
            assertEquals(source, replaceEditor.text)
            assertFalse(replaceEditor.canUndo)
        }
    }

    @Test fun reverseAndBoundaryOnlySelectionsKeepInterveningSourceExact() {
        val source = "😀 first\r\n\r\n```js\r\nconst n = 1\r\n```\r\n\r\n# Last"
        val selected = selection(source, 0, 8, 2, 0)
        val editor = MarkdownEditorController(source)
        val reversed = selected.copy(anchor = selected.focus, focus = selected.anchor)
        assertEquals("```js\r\nconst n = 1\r\n```", editor.copyFormattedTextSelectionAsMarkdown(reversed))
        assertTrue(editor.deleteFormattedTextSelection(reversed))
        assertEquals("😀 firstLast", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun selectionRejectsUnsupportedInterveningBlockStaleSourceAndHalfEmojiAtomically() {
        val source = "😀 first\n\n> quote\n\n# Last"
        val editor = MarkdownEditorController(source)
        val acrossQuote = selection(source, 0, 2, 2, 2)
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(acrossQuote))
        assertFalse(editor.deleteFormattedTextSelection(acrossQuote))
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(acrossQuote, "Body"))
        val endpointInQuote = selection(source, 0, 2, 1, 2)
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(endpointInQuote))
        assertFalse(editor.deleteFormattedTextSelection(endpointInQuote))
        val halfEmoji = selection(source, 0, 1, 0, 3)
        assertFalse(editor.deleteFormattedTextSelection(halfEmoji))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(halfEmoji))
        val stale = selection("older", 0, 0, 0, 2)
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(stale, "Body"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }
}
