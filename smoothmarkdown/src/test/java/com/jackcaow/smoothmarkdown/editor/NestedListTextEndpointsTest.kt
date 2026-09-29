package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedListTextEndpointsTest {
    private fun at(block: Int, path: List<Int>, line: Int, offset: Int) =
        MarkdownFormattedTextPosition("block-$block", offset, path, line)

    private fun between(source: String, from: MarkdownFormattedTextPosition, to: MarkdownFormattedTextPosition) =
        MarkdownFormattedTextSelection(source, from, to)

    @Test fun nestedSiblingCharactersCopyDeleteAndUndoWithoutFlatteningParentOrRootSibling() {
        val source = "- parent\r\n  - NestedX\r\n  - YTail\r\n- keep"
        val selected = between(source, at(0, listOf(0, 0), 0, 6), at(0, listOf(0, 1), 0, 1))
        val editor = MarkdownEditorController(source)
        assertEquals("  - X\r\n  - Y", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.deleteFormattedTextSelection(selected))
        assertEquals("- parent\r\n  - NestedTail\r\n- keep", editor.text)
        assertEquals(1, MarkdownSourceList.parse(editor.semanticDocument().blocks.single())?.item(listOf(0))
            ?.parts?.filterIsInstance<MarkdownSourceList.NestedList>()?.single()?.items?.size)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
        assertTrue(editor.redo())
    }

    @Test fun continuationCharactersCopyDeleteReplaceAndUndoKeepIndentAndOtherItems() {
        val source = "- one\r\n  BeforeX\r\n  YAfter\r\n- keep"
        val selected = between(source, at(0, listOf(0), 1, 6), at(0, listOf(0), 2, 1))
        val editor = MarkdownEditorController(source)
        assertEquals("  X\r\n  Y", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.deleteFormattedTextSelection(selected))
        assertEquals("- one\r\n  BeforeAfter\r\n- keep", editor.text)
        assertTrue(editor.undo())
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(selected, "# New"))
        assertTrue(editor.text.contains("# New"))
        assertTrue(editor.text.endsWith("- keep"))
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun nestedInlineMarkdownAndEmojiRespectVisibleUtf16Boundaries() {
        val source = "- parent\n  - Left **bold** 😀 tail\n- keep"
        val editor = MarkdownEditorController(source)
        val selected = between(source, at(0, listOf(0, 0), 0, 6), at(0, listOf(0, 0), 0, 9))
        assertEquals("  - **old**", editor.copyFormattedTextSelectionAsMarkdown(selected))
        assertTrue(editor.deleteFormattedTextSelection(selected))
        assertEquals("- parent\n  - Left **b** 😀 tail\n- keep", editor.text)
        assertTrue(editor.undo())
        val halfEmoji = between(source, at(0, listOf(0, 0), 0, 11), at(0, listOf(0, 0), 0, 12))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(halfEmoji))
        assertFalse(editor.deleteFormattedTextSelection(halfEmoji))
        assertEquals(source, editor.text)
        val replace = between(source, at(0, listOf(0, 0), 0, 5), at(0, listOf(0, 0), 0, 9))
        assertTrue(editor.replaceFormattedTextSelectionWithMarkdown(replace, "**new**"))
        assertEquals("- parent\n  - Left **new** 😀 tail\n- keep", editor.text)
        assertTrue(editor.undo())
        assertEquals(source, editor.text)
    }

    @Test fun highlightsNestedAndContinuationLinesBySourceOrderInEitherDirection() {
        val source = "- parent\n  FirstX\n  - Nested\n    tail\n- keep"
        val blocks = MarkdownEditorController(source).semanticDocument().blocks
        val selected = FormattedTextEndpoints(source, at(0, listOf(0), 1, 5))
            .withFocus(at(0, listOf(0, 0), 1, 1))
        val reversed = selected.copy(anchor = selected.focus!!, focus = selected.anchor)
        for (range in listOf(selected, reversed)) {
            assertEquals(TextRange(5, 6), range.listVisibleRange(blocks, "block-0", listOf(0), 1, 6))
            assertEquals(TextRange(0, 6), range.listVisibleRange(blocks, "block-0", listOf(0, 0), 0, 6))
            assertEquals(TextRange(0, 1), range.listVisibleRange(blocks, "block-0", listOf(0, 0), 1, 4))
            assertNull(range.listVisibleRange(blocks, "block-0", listOf(1), 0, 4))
        }
    }

    @Test fun rejectsStaleAndMissingNestedPathWithoutMutation() {
        val source = "- root\n  - child\n- keep"
        val editor = MarkdownEditorController(source)
        val bad = between(source, at(0, listOf(0, 9), 0, 1), at(0, listOf(1), 0, 2))
        assertNull(editor.copyFormattedTextSelectionAsMarkdown(bad))
        assertFalse(editor.deleteFormattedTextSelection(bad))
        val stale = between("older", at(0, listOf(0, 0), 0, 1), at(0, listOf(1), 0, 2))
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(stale, "new"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }

    @Test fun completeNestedSiblingCopyRemovesOnlySharedIndentAndFinalNewline() {
        val source = "- Parent\r\n\r\n  3. One\r\n     continuation\r\n  4. Two\r\n- End"
        val editor = MarkdownEditorController(source)
        assertTrue(editor.selectFormattedListItem("block-0", listOf(0, 0)))
        assertTrue(editor.selectFormattedListItem("block-0", listOf(0, 1)))
        assertEquals("3. One\r\n   continuation\r\n4. Two",
            editor.copyFormattedListItemSelectionAsMarkdown())
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }

    @Test fun partialRangeAcrossOpaqueListChildFailsClosed() {
        val source = "- BeforeX\n\n  ```text\n  code\n  ```\n\n  YAfter\n- keep"
        val selected = between(source, at(0, listOf(0), 0, 6), at(0, listOf(0), 1, 1))
        val editor = MarkdownEditorController(source)
        assertFalse(editor.deleteFormattedTextSelection(selected))
        assertFalse(editor.replaceFormattedTextSelectionWithMarkdown(selected, "New"))
        assertEquals(source, editor.text)
        assertFalse(editor.canUndo)
    }
}
