package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedRangeInlineCommandTest {
    @Test fun boldsReverseSelectedParagraphAndHeadingWithoutChangingNeighborsOrLineEndings() {
        val source = "before\r\n\r\nFirst 😀\r\n\r\n# Title\r\n\r\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedBlockDrag("block-2"))
        assertTrue(controller.extendFormattedBlockDrag("block-1"))

        assertTrue(controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertEquals("before\r\n\r\n**First 😀**\r\n\r\n# **Title**\r\n\r\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun selectedBlocksToggleCompleteExistingMarksAndRejectMixedSyntaxAtomically() {
        val source = "**First**\n\n# **Second**\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedBlockDrag("block-0"))
        assertTrue(controller.extendFormattedBlockDrag("block-1"))
        assertTrue(controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertEquals("First\n\n# Second\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)

        val mixed = MarkdownEditorController("Plain\n\n# Part *italic*\n\nafter")
        assertTrue(mixed.beginFormattedBlockDrag("block-0"))
        assertTrue(mixed.extendFormattedBlockDrag("block-1"))
        assertFalse(mixed.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertEquals("Plain\n\n# Part *italic*\n\nafter", mixed.text)
        assertFalse(mixed.canUndo)
    }

    @Test fun tableRectangleFormattingPreservesPipesAlignmentAndOutsideCells() {
        val source = "| A | B | C |\n| :--- | ---: | --- |\n| x | y | z |\n| q | r | s |"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedTableCellDrag("block-0", 2, 1))
        assertTrue(controller.extendFormattedTableCellDrag("block-0", 1, 0))

        assertTrue(controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.ITALIC))
        assertEquals("| A | B | C |\n| :--- | ---: | --- |\n| *x* | *y* | z |\n| *q* | *r* | s |", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun tableRectangleRefusesMixedMarksWithoutPartialMutation() {
        val source = "| A | B |\n| --- | --- |\n| x | *y* |"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedTableCellDrag("block-0", 1, 0))
        assertTrue(controller.extendFormattedTableCellDrag("block-0", 1, 1))
        assertFalse(controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.BOLD))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun tableRectangleSkipsEmptyCellsLikeFlutter() {
        val source = "| A | B |\n| --- | --- |\n| x |  |"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedTableCellDrag("block-0", 1, 0))
        assertTrue(controller.extendFormattedTableCellDrag("block-0", 1, 1))
        assertTrue(controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.BOLD))
        assertEquals("| A | B |\n| --- | --- |\n| **x** |  |", controller.text)
    }
}
