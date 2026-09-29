package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedBlockTransformsTest {
    private fun selected(source: String, first: Int, last: Int): MarkdownEditorController =
        MarkdownEditorController(source).also { controller ->
            assertTrue(controller.selectFormattedBlock("block-$first"))
            if (last != first) assertTrue(controller.selectFormattedBlock("block-$last"))
        }

    @Test fun groupsTwoBlocksIntoOneBulletOrderedTaskOrQuoteWithOneUndo() {
        val source = "Before\n\n# **One**\n\nTwo [link](https://example.com)\n\nAfter"
        val expected = mapOf(
            MarkdownEditorCommand.UNORDERED_LIST to
                "Before\n\n- **One**\n- Two [link](https://example.com)\n\nAfter",
            MarkdownEditorCommand.ORDERED_LIST to
                "Before\n\n1. **One**\n2. Two [link](https://example.com)\n\nAfter",
            MarkdownEditorCommand.TASK_LIST to
                "Before\n\n- [ ] **One**\n- [ ] Two [link](https://example.com)\n\nAfter",
            MarkdownEditorCommand.BLOCKQUOTE to
                "Before\n\n> **One**\n>\n> Two [link](https://example.com)\n\nAfter",
        )
        expected.forEach { (command, result) ->
            val controller = selected(source, 1, 2)
            assertTrue(command.name, controller.applyBlockCommandToFormattedBlockSelection(command))
            assertEquals(command.name, result, controller.text)
            assertNull(controller.formattedBlockSelection)
            assertTrue(controller.undo())
            assertEquals(source, controller.text)
            assertFalse(controller.canUndo)
            assertTrue(controller.redo())
            assertEquals(result, controller.text)
        }
    }

    @Test fun paragraphFromAdjacentHeadingsAddsRequiredBlankLine() {
        val source = "# **One**\n## Two\n\nAfter"
        val controller = selected(source, 0, 1)
        assertTrue(controller.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.PARAGRAPH))
        assertEquals("**One**\n\nTwo\n\nAfter", controller.text)
        assertEquals(listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.PARAGRAPH,
            MarkdownBlockKind.PARAGRAPH), controller.semanticDocument().blocks.map { it.kind })
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
    }

    @Test fun allHeadingLevelsConvertEachSelectedBlockAndKeepCrLfAndInlineSource() {
        val source = "Before\r\n\r\n**One**\r\n\r\nTwo [link](https://example.com)\r\n\r\nAfter"
        listOf(MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3, MarkdownEditorCommand.HEADING4,
            MarkdownEditorCommand.HEADING5, MarkdownEditorCommand.HEADING6).forEachIndexed { index, command ->
            val controller = selected(source, 1, 2)
            assertTrue(command.name, controller.applyBlockCommandToFormattedBlockSelection(command))
            val marker = "#".repeat(index + 1)
            assertEquals("Before\r\n\r\n$marker **One**\r\n\r\n$marker Two [link](https://example.com)\r\n\r\nAfter",
                controller.text)
            assertTrue(controller.undo())
            assertEquals(source, controller.text)
        }
    }

    @Test fun reverseSelectionAndSoftWrappedParagraphKeepItemText() {
        val source = "Before\n\nFirst line\nsecond line\n\n# Last\n\nAfter"
        val controller = selected(source, 2, 1)
        assertTrue(controller.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.UNORDERED_LIST))
        assertEquals("Before\n\n- First line\n  second line\n- Last\n\nAfter", controller.text)
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks[1])!!
        assertEquals(listOf("First line", "second line"), list.items[0].lines.map { part ->
            controller.semanticDocument().blocks[1].source.substring(part.start, part.end)
        })
    }

    @Test fun unsupportedOrUnsafeSelectionsFailWithoutChangingSourceOrHistory() {
        val complex = "Before\n\n# One\n\n| A |\n| --- |\n| B |\n\nAfter"
        val tableRange = selected(complex, 1, 2)
        assertFalse(tableRange.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.BLOCKQUOTE))
        assertEquals(complex, tableRange.text)
        assertFalse(tableRange.canUndo)

        val setext = "Title\n=====\n\nAfter"
        val setextRange = selected(setext, 0, 0)
        assertFalse(setextRange.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.PARAGRAPH))
        assertEquals(setext, setextRange.text)
        assertFalse(setextRange.canUndo)

        val boundary = "Before\n# One"
        val boundaryRange = selected(boundary, 1, 1)
        assertFalse(boundaryRange.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.PARAGRAPH))
        assertEquals(boundary, boundaryRange.text)
        assertFalse(boundaryRange.canUndo)

        val noOp = selected("One\n\nTwo", 0, 1)
        assertFalse(noOp.applyBlockCommandToFormattedBlockSelection(MarkdownEditorCommand.PARAGRAPH))
        assertFalse(noOp.canUndo)
    }
}
