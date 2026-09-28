package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentBlockSelectionTest {
    @Test fun copiesExactSourceAcrossDifferentBlocksInEitherDirection() {
        val source = "before\r\n\r\n# 😀 Title\r\n\r\n> quoted\r\n\r\nafter"
        val controller = MarkdownEditorController(source)
        val blocks = controller.semanticDocument().blocks
        assertTrue(controller.selectFormattedBlock(blocks[2].id))
        assertTrue(controller.selectFormattedBlock(blocks[1].id))
        assertEquals("# 😀 Title\r\n\r\n> quoted", controller.copyFormattedBlockSelectionAsMarkdown())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun deletesContiguousBlocksAndRestoresSourceWithOneUndoStep() {
        val source = "before\n\n# Title\n\n> quote\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedBlock("block-1"))
        assertTrue(controller.selectFormattedBlock("block-2"))
        assertTrue(controller.deleteFormattedBlockSelection())
        assertEquals("before\n\n\n\nafter", controller.text)
        assertNull(controller.formattedBlockSelection)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("before\n\n\n\nafter", controller.text)
    }

    @Test fun replacesOneBlockWithMultipleParsedBlocksWithoutTouchingNeighbors() {
        val source = "before\n\n# Old\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedBlock("block-1"))
        assertTrue(controller.replaceFormattedBlockSelectionWithMarkdown("# New\n\n> quote"))
        assertEquals("before\n\n# New\n\n> quote\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun refusesReplacementThatWouldMergeWithUntouchedParagraph() {
        val source = "# A\n# B\nnext"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedBlock("block-1"))
        assertFalse(controller.replaceFormattedBlockSelectionWithMarkdown("plain"))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
        assertEquals("# B", controller.copyFormattedBlockSelectionAsMarkdown())
    }

    @Test fun sourceEditInvalidatesStaleBlockSelection() {
        val controller = MarkdownEditorController("one\n\n# two")
        assertTrue(controller.selectFormattedBlock("block-0"))
        controller.replaceRange(0, 0, "start\n\n")
        assertNull(controller.copyFormattedBlockSelectionAsMarkdown())
        assertFalse(controller.deleteFormattedBlockSelection())
    }
}
