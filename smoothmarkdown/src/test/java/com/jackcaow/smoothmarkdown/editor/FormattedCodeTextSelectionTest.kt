package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedCodeTextSelectionTest {
    private fun position(block: Int, offset: Int) = MarkdownFormattedTextPosition("block-$block", offset)
    private fun selected(source: String, firstBlock: Int, first: Int, lastBlock: Int, last: Int) =
        MarkdownFormattedTextSelection(source, position(firstBlock, first), position(lastBlock, last))

    @Test fun partialCodeCopyDeleteReplacePreserveFenceAndUndo() {
        val source = "before\r\n\r\n~~~ kotlin\r\nBeforeX\r\nYAfter\r\n~~~  \r\n\r\nafter"
        val selection = selected(source, 1, 6, 1, 10)
        val editor = MarkdownEditorController(source)
        assertEquals("~~~ kotlin\r\nX\r\nY\r\n~~~  ", editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertFalse(editor.canUndo)
        assertTrue(editor.deleteFormattedTextSelection(selection))
        assertEquals("before\r\n\r\n~~~ kotlin\r\nBeforeAfter\r\n~~~  \r\n\r\nafter", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals("before\r\n\r\n~~~ kotlin\r\nBeforeAfter\r\n~~~  \r\n\r\nafter", editor.text)
        val replacement = MarkdownEditorController(source)
        assertTrue(replacement.replaceFormattedTextSelectionWithMarkdown(selection, "new"))
        assertEquals("before\r\n\r\n~~~ kotlin\r\nBefore\r\n~~~  \r\n\r\nnew\r\n\r\n~~~ kotlin\r\nAfter\r\n~~~  \r\n\r\nafter", replacement.text)
        assertTrue(replacement.undo())
        assertEquals(source, replacement.text)
    }

    @Test fun codeToProseAndProseToCodePreserveUntouchedSource() {
        val source = "top\n\n```js\nBeforeX\n```\n\n# YAfter\n\nbottom"
        val selection = selected(source, 1, 6, 2, 1)
        val editor = MarkdownEditorController(source)
        assertEquals("```js\nX\n```\n\n# Y", editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertEquals(editor.copyFormattedTextSelectionAsMarkdown(selection),
            editor.copyFormattedTextSelectionAsMarkdown(selection.copy(anchor = selection.focus, focus = selection.anchor)))
        assertTrue(editor.deleteFormattedTextSelection(selection))
        assertEquals("top\n\n```js\nBefore\n```\n\n# After\n\nbottom", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)

        val reverseKinds = selected(source, 0, 2, 1, 2)
        val other = MarkdownEditorController(source)
        assertEquals("p\n\n```js\nBe\n```", other.copyFormattedTextSelectionAsMarkdown(reverseKinds))
        assertTrue(other.deleteFormattedTextSelection(reverseKinds))
        assertEquals("to\n\n```js\nforeX\n```\n\n# YAfter\n\nbottom", other.text)
        assertTrue(other.undo())
        assertEquals(source, other.text)
    }

    @Test fun completeCodeAndInterveningTableKeepExactCrLf() {
        val source = "```\r\nBeforeX\r\n```\r\n\r\n| A | B |\r\n| --- | --- |\r\n| C | D |\r\n\r\n```\r\nYAfter\r\n```"
        val selection = selected(source, 0, 6, 2, 1)
        val editor = MarkdownEditorController(source)
        assertEquals("```\r\nX\r\n```\r\n\r\n| A | B |\r\n| --- | --- |\r\n| C | D |\r\n\r\n```\r\nY\r\n```",
            editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertTrue(editor.deleteFormattedTextSelection(selection))
        assertEquals("```\r\nBefore\r\n```\r\n\r\n```\r\nAfter\r\n```", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun invalidCodeEndpointsCannotMutateOrCreateUndo() {
        val source = "```\n😀\r\nnext\n```\n\nlast"
        val editor = MarkdownEditorController(source)
        val halfEmoji = selected(source, 0, 1, 0, 4)
        val halfCrLf = selected(source, 0, 3, 0, 5)
        val stale = selected("older", 0, 0, 0, 2)
        for (selection in listOf(halfEmoji, halfCrLf, stale)) {
            assertNull(editor.copyFormattedTextSelectionAsMarkdown(selection))
            assertFalse(editor.deleteFormattedTextSelection(selection))
        }
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(selected(source, 0, 0, 0, 2), "\uD83D"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }
    @Test fun fullCodeBodyCopyUsesExactSourceAndReplaceWithMarkdownIsAtomic() {
        val source = "intro\n\n~~~lang\r\n**literal**\r\n~~~  \n\noutro"
        val body = "**literal**"
        val selection = selected(source, 1, 0, 1, body.length)
        val editor = MarkdownEditorController(source)
        assertEquals("~~~lang\r\n**literal**\r\n~~~  ", editor.copyFormattedTextSelectionAsMarkdown(selection))
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selection, "# Replacement"))
        assertEquals("intro\n\n# Replacement\n\noutro", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }

    @Test fun mixedLineEndingsKeepOriginalCodeBodyTerminator() {
        val source = "```js\r\nBeforeX\n```\n\nafter"
        val editor = MarkdownEditorController(source)
        assertTrue(editor.deleteFormattedTextSelection(selected(source, 0, 6, 0, 7)))
        assertEquals("```js\r\nBefore\n```\n\nafter", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

}
