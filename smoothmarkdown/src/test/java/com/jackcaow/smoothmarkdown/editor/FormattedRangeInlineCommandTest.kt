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

    @Test fun tableRectangleWrapsCompleteItalicButKeepsOtherCellsAndOneUndo() {
        val source = "| A | B |\n| --- | --- |\n| x | *y* |"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedTableCellDrag("block-0", 1, 0))
        assertTrue(controller.extendFormattedTableCellDrag("block-0", 1, 1))
        assertTrue(controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.BOLD))
        assertEquals("| A | B |\n| --- | --- |\n| **x** | __*y*__ |", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun blockBatchBoldsCompleteLinkAndItalicWithoutChangingUntouchedSource() {
        val source = "*one*\n\n# [two](https://example.com/path)\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedBlockDrag("block-0"))
        assertTrue(controller.extendFormattedBlockDrag("block-1"))
        assertTrue(controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertEquals("__*one*__\n\n# __[two](https://example.com/path)__\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun listBatchItalicizesCompleteBoldAndLeavesChildAndSiblingUntouched() {
        val source = "- **first**\n- second\n  - child\n- outside"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.beginFormattedListItemDrag("block-0", listOf(0)))
        assertTrue(controller.extendFormattedListItemDrag("block-0", listOf(1)))
        assertTrue(controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.ITALIC))
        assertEquals("- _**first**_\n- *second*\n  - child\n- outside", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun nestedBatchRefusesPartialMarkAtomically() {
        val source = "| A | B |\n| --- | --- |\n| *safe* | part *mixed* |"
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

    @Test fun strikethroughAndCodeRangeCommandsRoundTripWithOneUndo() {
        val blocks = MarkdownEditorController("First\n\n# Second")
        assertTrue(blocks.beginFormattedBlockDrag("block-0"))
        assertTrue(blocks.extendFormattedBlockDrag("block-1"))
        assertTrue(blocks.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.STRIKETHROUGH))
        assertEquals("~~First~~\n\n# ~~Second~~", blocks.text)
        assertTrue(blocks.undo())
        assertEquals("First\n\n# Second", blocks.text)

        val table = MarkdownEditorController("| A | B |\n| --- | --- |\n| x | y |")
        assertTrue(table.beginFormattedTableCellDrag("block-0", 1, 0))
        assertTrue(table.extendFormattedTableCellDrag("block-0", 1, 1))
        assertTrue(table.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.INLINE_CODE))
        assertEquals("| A | B |\n| --- | --- |\n| `x` | `y` |", table.text)
        assertTrue(table.undo())
        assertEquals("| A | B |\n| --- | --- |\n| x | y |", table.text)
    }

    @Test fun selectedListItemsSupportStrikethroughWithoutChangingMarkers() {
        val original = "- first\n- second\n- third"
        val controller = MarkdownEditorController(original)
        assertTrue(controller.beginFormattedListItemDrag("block-0", listOf(0)))
        assertTrue(controller.extendFormattedListItemDrag("block-0", listOf(1)))
        assertTrue(controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.STRIKETHROUGH))
        assertEquals("- ~~first~~\n- ~~second~~\n- third", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }
}
