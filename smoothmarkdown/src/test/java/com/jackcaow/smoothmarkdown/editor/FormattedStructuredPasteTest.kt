package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedStructuredPasteTest {
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
