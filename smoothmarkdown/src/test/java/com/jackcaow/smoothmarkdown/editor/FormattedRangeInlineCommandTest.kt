package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
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

    @Test fun selectedBlocksToggleCompleteExistingMarksAndHandleMixedSyntaxAtomically() {
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
        assertTrue(mixed.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertTrue(mixed.text.startsWith("**Plain**\n\n# "))
        assertTrue(mixed.text.endsWith("\n\nafter"))
        assertEquals("Part italic", MarkdownFormattedBlock.inline(mixed.semanticDocument().blocks[1])!!.visible)
        assertTrue(mixed.undo())
        assertEquals("Plain\n\n# Part *italic*\n\nafter", mixed.text)
        assertFalse(mixed.canUndo)
    }

    @Test fun selectedParagraphAndHeadingPreserveEmbeddedLinkAndEmphasisInOneUndo() {
        val original = "before\n\nStart [link](https://example.com/a) end\n\n# Has *italics* here\n\nafter"
        val controller = MarkdownEditorController(original)
        assertTrue(controller.beginFormattedBlockDrag("block-1"))
        assertTrue(controller.extendFormattedBlockDrag("block-2"))
        assertTrue(controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD))
        assertTrue(controller.text.startsWith("before\n\n"))
        assertTrue(controller.text.endsWith("\n\nafter"))
        assertTrue(controller.text.contains("https://example.com/a"))
        val blocks = controller.semanticDocument().blocks
        assertEquals("Start link end", MarkdownFormattedBlock.inline(blocks[1])!!.visible)
        assertEquals("Has italics here", MarkdownFormattedBlock.inline(blocks[2])!!.visible)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun partialVisibleSelectionAcrossParagraphAndHeadingPreservesExistingMarks() {
        val original = "before\n\nStart **bold** end\n\n# Has [link](https://example.com/a) end\n\nafter"
        val controller = MarkdownEditorController(original)
        val selected = MarkdownFormattedTextSelection(original,
            MarkdownFormattedTextPosition("block-1", 7),
            MarkdownFormattedTextPosition("block-2", 6))
        assertTrue(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.ITALIC))
        assertTrue(controller.text.startsWith("before\n\nStart "))
        assertTrue(controller.text.endsWith(" end\n\nafter"))
        assertTrue(controller.text.contains("https://example.com/a"))
        val blocks = controller.semanticDocument().blocks
        val paragraph = MarkdownFormattedBlock.inline(blocks[1])!!
        val heading = MarkdownFormattedBlock.inline(blocks[2])!!
        assertEquals("Start bold end", paragraph.visible)
        assertEquals("Has link end", heading.visible)
        assertTrue(paragraph.marks.any { it.kind == InlineMarkKind.ITALIC && it.range == TextRange(7, 14) })
        assertTrue(heading.marks.any { it.kind == InlineMarkKind.ITALIC && it.range == TextRange(0, 6) })
        val forward = controller.text
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)

        val reverse = MarkdownEditorController(original)
        val reversedSelection = selected.copy(anchor = selected.focus, focus = selected.anchor)
        assertTrue(reverse.applyInlineCommandToFormattedTextSelection(reversedSelection, MarkdownEditorCommand.ITALIC))
        assertEquals(forward, reverse.text)
    }

    @Test fun partialRangeRejectsStaleSourceAndInterveningListAtomically() {
        val source = "First\n\n- item\n\nLast"
        val controller = MarkdownEditorController(source)
        val selected = MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-0", 2),
            MarkdownFormattedTextPosition("block-2", 2))
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.BOLD))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)

        val stale = selected.copy(source = "older source")
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(stale, MarkdownEditorCommand.BOLD))
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun crossBlockRangeSkipsEmptyEndpointAndUsesUtf16EmojiOffsets() {
        val source = "😀 First\n\n# **bold** end"
        val first = MarkdownEditorController(source)
        val headingOnly = MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-0", "😀 First".length),
            MarkdownFormattedTextPosition("block-1", 2))
        assertTrue(first.applyInlineCommandToFormattedTextSelection(headingOnly, MarkdownEditorCommand.ITALIC))
        assertTrue(first.text.startsWith("😀 First\n\n# "))
        assertEquals("bold end", MarkdownFormattedBlock.inline(first.semanticDocument().blocks[1])!!.visible)
        assertTrue(first.undo())
        assertEquals(source, first.text)
        assertFalse(first.canUndo)

        val second = MarkdownEditorController(source)
        val paragraphOnly = MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-0", 0),
            MarkdownFormattedTextPosition("block-1", 0))
        assertTrue(second.applyInlineCommandToFormattedTextSelection(paragraphOnly, MarkdownEditorCommand.BOLD))
        assertTrue(second.text.startsWith("**😀 First**\n\n# **bold** end"))
        assertTrue(second.undo())
        assertEquals(source, second.text)

        val halfEmoji = MarkdownFormattedTextSelection(source,
            MarkdownFormattedTextPosition("block-0", 1),
            MarkdownFormattedTextPosition("block-1", 2))
        assertFalse(second.applyInlineCommandToFormattedTextSelection(halfEmoji, MarkdownEditorCommand.BOLD))
        assertEquals(source, second.text)
        assertFalse(second.canUndo)
    }

    @Test fun changedSourceRevisionDoesNotApplyPreviouslyValidRange() {
        val original = "Alpha\n\n# Beta"
        val controller = MarkdownEditorController(original)
        val selected = MarkdownFormattedTextSelection(original,
            MarkdownFormattedTextPosition("block-0", 1),
            MarkdownFormattedTextPosition("block-1", 2))
        controller.replaceRange(0, 0, "Intro\n\n")
        val changed = controller.text
        assertFalse(controller.applyInlineCommandToFormattedTextSelection(selected, MarkdownEditorCommand.BOLD))
        assertEquals(changed, controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
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
