package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.commonmark.node.BulletList

class FormattedStructuredPasteTest {
    @Test fun pastesNestedListInsideFormattedItemAsOneUndoStep() {
        val original = "- Before target after\n- Keep this sibling"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0,
            "Before - child A\n- child B after"))
        assertEquals("- Before \n  - child A\n  - child B\n\n   after\n- Keep this sibling", controller.text)
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals(2, list.items.size)
        assertEquals("- Keep this sibling", list.copySiblingItems(emptyList(), 1, 1))
        assertEquals("child A", list.lineContent(listOf(0, 0), 0))
        assertEquals("child B", list.lineContent(listOf(0, 1), 0))
        assertEquals("after", list.lineContent(listOf(0), 1)?.trim())
        assertEquals(listOf(0, 1), controller.activeFormattedListPath)
        assertEquals(0, controller.activeFormattedListLine)
        assertEquals(TextRange("child B".length), controller.formattedListSelection)
        assertEquals(listOf(0, 1) to 0, controller.formattedListFocusTarget)
        assertEquals(MarkdownEditorMode.FORMATTED, controller.mode)
        assertEquals(controller.text.indexOf("- child B") + "- child B".length, controller.selection.start)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("- Before \n  - child A\n  - child B\n\n   after\n- Keep this sibling", controller.text)
    }

    @Test fun nestedItemPasteKeepsOrderedMarkersAndUntouchedInlineSource() {
        val original = "1. Parent\n   - **boldtarget** tail\n   - Sibling\n2. Next"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0, 0), 0,
            "bold- first\n- second tail"))
        assertTrue(controller.text.startsWith("1. Parent\n   - **bold**"))
        assertTrue(controller.text.contains("     - first\n     - second"))
        assertTrue(controller.text.endsWith("   - Sibling\n2. Next"))
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals("Sibling", list.lineContent(listOf(0, 1), 0))
        assertEquals("Next", list.lineContent(listOf(1), 0))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun listPasteRejectsPlainMultilineAndBrokenUtf16WithoutMutation() {
        val controller = MarkdownEditorController("- 😀 target\n- Keep").apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertFalse(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0, "😀 one\ntwo"))
        assertFalse(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0,
            "\uD83D- child\n- other\uDE00 target"))
        assertEquals("- 😀 target\n- Keep", controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun pastesNestedBlocksFromListContinuationWithoutChangingNextItem() {
        val original = "- first\n  continuation target\n- next"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 1,
            "continuation - child\n- child 2 target"))
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals(2, list.items.size)
        assertEquals("child", list.lineContent(listOf(0, 0), 0))
        assertEquals("child 2", list.lineContent(listOf(0, 1), 0)?.trim())
        assertEquals("- next", list.copySiblingItems(emptyList(), 1, 1))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun taskListPasteCreatesChildListInsteadOfIndentedCode() {
        val original = "- [x] Parent target\n- [ ] Keep"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0,
            "Parent - child A\n- child B"))
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertTrue(list.item(listOf(0))!!.checked)
        assertEquals("child A", list.lineContent(listOf(0, 0), 0))
        assertEquals("child B", list.lineContent(listOf(0, 1), 0))
        assertEquals("- [ ] Keep", list.copySiblingItems(emptyList(), 1, 1))
        assertTrue(controller.text.contains("\n  - child A\n  - child B"))
    }

    @Test fun suffixFreeChildListPasteKeepsTightListSpacing() {
        val controller = MarkdownEditorController("- Parent target\n- Keep").apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue((parseMarkdown(controller.text).firstChild as BulletList).isTight)
        assertTrue(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0,
            "Parent - child A\n- child B"))
        assertEquals("- Parent \n  - child A\n  - child B\n- Keep", controller.text)
        assertTrue((parseMarkdown(controller.text).firstChild as BulletList).isTight)
    }

    @Test fun rejectsPasteThatWouldReinterpretUntouchedSuffixAsHeading() {
        for (suffix in listOf("# Heading", "> quote", "---")) {
            val original = "- Before target $suffix\n- Keep"
            val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
            val block = controller.semanticDocument().blocks.single()
            assertFalse(controller.replaceFormattedListLineWithBlocks(block.id, listOf(0), 0,
                "Before - child A\n- child B $suffix"))
            assertEquals(original, controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun insertsParsedBlocksBetweenParagraphFragmentsWithoutChangingNeighbors() {
        val original = "Preface\r\n\r\nHello target world\r\n\r\nTail"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks[1]
        assertTrue(controller.replaceFormattedTextWithBlocks(block.id, "Hello # Heading\n\n- A\n- B world"))
        assertEquals("Preface\r\n\r\nHello \n\n# Heading\n\n- A\n- B\n\n world\r\n\r\nTail", controller.text)
        assertEquals(listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.PARAGRAPH,
            MarkdownBlockKind.HEADING, MarkdownBlockKind.BULLET_LIST,
            MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.PARAGRAPH),
            controller.semanticDocument().blocks.map { it.kind })
        assertEquals(controller.text.indexOf("- B") + 3, controller.selection.start)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("Preface\r\n\r\nHello \n\n# Heading\n\n- A\n- B\n\n world\r\n\r\nTail", controller.text)
    }

    @Test fun splitsActiveInlineMarksAndKeepsUntouchedLinkSource() {
        val original = "**boldword** then [linktext](https://example.com)"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        val inline = MarkdownFormattedBlock.inline(block)!!
        val next = inline.visible.replaceRange(2, 6, "# Title\n\n- item")
        assertTrue(controller.replaceFormattedTextWithBlocks(block.id, next))
        assertEquals("**bo**\n\n# Title\n\n- item\n\n**rd** then [linktext](https://example.com)", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun usesUtf16SelectionOffsetsAndDoesNotTreatPlainTextAsBlocks() {
        val original = "👩‍💻 Alpha beta"
        val controller = MarkdownEditorController(original).apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        val inline = MarkdownFormattedBlock.inline(block)!!
        val start = inline.visible.indexOf("Alpha")
        assertTrue(start > 2)
        val next = inline.visible.replaceRange(start, start + 5, "## Heading\n\n> quote")
        assertTrue(controller.replaceFormattedTextWithBlocks(block.id, next))
        assertEquals("👩‍💻 \n\n## Heading\n\n> quote\n\n beta", controller.text)

        val plain = MarkdownEditorController("Hello world").apply { mode = MarkdownEditorMode.FORMATTED }
        val plainBlock = plain.semanticDocument().blocks.single()
        assertFalse(plain.replaceFormattedTextWithBlocks(plainBlock.id, "Hello friend"))
        assertTrue(plain.replaceFormattedInlineText(plainBlock.id, "Hello friend", TextRange(12)))
        assertEquals("Hello friend", plain.text)
    }

    @Test fun headingFragmentsKeepTheirMarkerAndInvalidUtf16BoundaryIsRejected() {
        val controller = MarkdownEditorController("## before target after ##").apply { mode = MarkdownEditorMode.FORMATTED }
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedTextWithBlocks(block.id, "before # New\n\n> quote after"))
        assertEquals("## before  ##\n\n# New\n\n> quote\n\n##  after ##", controller.text)

        val emoji = MarkdownEditorController("😀").apply { mode = MarkdownEditorMode.FORMATTED }
        val emojiBlock = emoji.semanticDocument().blocks.single()
        assertFalse(emoji.replaceFormattedTextWithBlocks(emojiBlock.id, "\uD83D# H\n\n- item\uDE00"))
        assertEquals("😀", emoji.text)
    }
}
