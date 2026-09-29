package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedListSourcePasteTest {
    @Test fun blankLinePasteKeepsSuffixAndSiblingAsEditableSourceWithOneUndoStep() {
        val original = "- Before target after\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id

        assertTrue(controller.replaceFormattedListLineWithSourcePaste(id, listOf(0), 0,
            "Before one\n\ntwo after", TextRange(7, 13)))

        assertEquals("- Before one\n\n  two after\n- Keep", controller.text)
        assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
        assertEquals(controller.text.indexOf("two after") + 3, controller.selection.start)
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals(2, list.items.size)
        assertEquals("- Keep", list.copySiblingItems(emptyList(), 1, 1))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("- Before one\n\n  two after\n- Keep", controller.text)
    }

    @Test fun nestedTaskPastePreservesMarkersAndCrLf() {
        val original = "1. Parent\r\n   - [x] Before target after\r\n   - Sibling\r\n2. Next"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id

        assertTrue(controller.replaceFormattedListLineWithSourcePaste(id, listOf(0, 0), 0,
            "Before first\n\nsecond after", TextRange(7, 13)))

        assertEquals("1. Parent\r\n   - [x] Before first\r\n\r\n     second after\r\n   - Sibling\r\n2. Next", controller.text)
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertTrue(list.item(listOf(0, 0))!!.checked)
        assertEquals("   - Sibling", list.copySiblingItems(listOf(0), 1, 1))
        assertEquals("2. Next", list.copySiblingItems(emptyList(), 1, 1))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun trailingBlankLinesKeepUntouchedSuffixInParentSource() {
        val original = "- Before target after\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id

        assertTrue(controller.replaceFormattedListLineWithSourcePaste(id, listOf(0), 0,
            "Before one\n\n after", TextRange(7, 13)))
        assertEquals("- Before one\n\n   after\n- Keep", controller.text)
        assertEquals(controller.text.indexOf("   after") + 2, controller.selection.start)
    }

    @Test fun sourceFallbackKeepsExistingInlineSyntaxOutsidePaste() {
        val original = "- **Before** target [after](url)\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id

        assertTrue(controller.replaceFormattedListLineWithSourcePaste(id, listOf(0), 0,
            "Before first\n\nsecond after", TextRange(7, 13)))
        assertEquals("- **Before** first\n\n  second [after](url)\n- Keep", controller.text)
        assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
    }

    @Test fun sourceFallbackClosesAndReopensBoldAroundSelectedText() {
        val original = "- **Before target after**\n- Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val id = controller.semanticDocument().blocks.single().id

        assertTrue(controller.replaceFormattedListLineWithSourcePaste(id, listOf(0), 0,
            "Before first\n\nsecond after", TextRange(7, 13)))
        assertEquals("- **Before **first\n\n  second** after**\n- Keep", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }
}
