package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedListSoftLinePasteTest {
    @Test fun plainSoftLinesKeepParentItemSiblingsCaretAndOneUndoStep() {
        val original = "- Before target after\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedListLineWithPlainLines(block.id, listOf(0), 0,
            "Before first line\nsecond line after"))
        assertEquals("- Before first line\n  second line after\n- Keep", controller.text)
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals(2, list.items.size)
        assertEquals("second line after", list.lineContent(listOf(0), 1))
        assertEquals("- Keep", list.copySiblingItems(emptyList(), 1, 1))
        assertEquals(listOf(0), controller.activeFormattedListPath)
        assertEquals(1, controller.activeFormattedListLine)
        assertEquals(TextRange("second line".length), controller.formattedListSelection)
        assertEquals(listOf(0) to 1, controller.formattedListFocusTarget)
        assertEquals(controller.text.indexOf("second line") + "second line".length, controller.selection.start)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("- Before first line\n  second line after\n- Keep", controller.text)
    }

    @Test fun taskAndOrderedMarkersUseOnlyListIndentAndKeepCrLf() {
        val task = MarkdownEditorController("- [x] Before target\n- [ ] Next").apply { mode = MarkdownEditorMode.FORMATTED }
        assertTrue(task.replaceFormattedListLineWithPlainLines(task.semanticDocument().blocks.single().id,
            listOf(0), 0, "Before alpha\nbeta"))
        assertEquals("- [x] Before alpha\n  beta\n- [ ] Next", task.text)
        assertTrue(MarkdownSourceList.parse(task.semanticDocument().blocks.single())!!.item(listOf(0))!!.checked)

        val ordered = MarkdownEditorController("1. Before target\r\n2. Keep").apply { mode = MarkdownEditorMode.FORMATTED }
        assertTrue(ordered.replaceFormattedListLineWithPlainLines(ordered.semanticDocument().blocks.single().id,
            listOf(0), 0, "Before alpha\nbeta"))
        assertEquals("1. Before alpha\r\n   beta\r\n2. Keep", ordered.text)
    }

    @Test fun markdownBlocksAndBlankLinesDoNotEnterPlainSoftLinePath() {
        val original = "- Before target\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id
        assertFalse(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "Before - child\n- other"))
        assertFalse(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "Before one\n\ntwo"))
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun literalMarkdownCharactersInPlainPasteRemainVisibleText() {
        val controller = MarkdownEditorController("- Before target after\n- Keep")
            .apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "Before a*b\nc_d after"))
        assertEquals("- Before a\\*b\n  c\\_d after\n- Keep", controller.text)
        assertEquals(TextRange("c_d".length), controller.formattedListSelection)
    }

    @Test fun existingInlineMarkupAndTrailingTextStayWithTheirOriginalLine() {
        val controller = MarkdownEditorController("- **Before** target after\n- Keep")
            .apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "Before first\nsecond after"))
        assertEquals("- **Before** first\n  second after\n- Keep", controller.text)
    }

    @Test fun repeatedPrefixUsesPriorFieldSelectionToLocatePastedText() {
        val controller = MarkdownEditorController("- aaaa\n- Keep").apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "a\nbaaaa", TextRange(0)))
        assertEquals("- a\n  baaaa\n- Keep", controller.text)
        assertEquals(TextRange(1), controller.formattedListSelection)
    }

    @Test fun plainInlineMarkupIsPastedLiterallyInsteadOfDropped() {
        val controller = MarkdownEditorController("- target\n- Keep").apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
            "one *two*\nthree", TextRange(0, "target".length)))
        assertEquals("- one \\*two\\*\n  three\n- Keep", controller.text)
        assertEquals(TextRange("three".length), controller.formattedListSelection)
    }

    @Test fun plainHtmlImageAndEntitySyntaxStayLiteral() {
        for (firstLine in listOf("![image](url)", "<span>", "&copy;", "![image](url) <span> &copy;")) {
            val controller = MarkdownEditorController("- target\n- Keep").apply { mode = MarkdownEditorMode.FORMATTED }
            val id = controller.semanticDocument().blocks.single().id
            assertTrue(firstLine, controller.replaceFormattedListLineWithPlainLines(id, listOf(0), 0,
                "$firstLine\nnext", TextRange(0, "target".length)))
            val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
            assertEquals(firstLine, MarkdownInlineEditing.parse(list.lineContent(listOf(0), 0)!!, false).visible)
            assertEquals("- Keep", list.copySiblingItems(emptyList(), 1, 1))
        }
    }
}
