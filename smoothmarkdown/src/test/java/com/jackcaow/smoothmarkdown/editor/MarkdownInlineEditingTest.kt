package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineEditingTest {
    @Test fun displaysFourMarksAndKeepsUnknownSourceUntouched() {
        val source = "A **bold** and *italic*, [site](https://example.com), `code`, and ~~raw~~."
        val model = MarkdownInlineEditing.parse(source)
        assertEquals("A bold and italic, site, code, and ~~raw~~.", model.visible)
        assertEquals(listOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK, InlineMarkKind.CODE),
            model.marks.map { it.kind })
        assertEquals("https://example.com", model.marks.first { it.kind == InlineMarkKind.LINK }.destination)
        assertEquals(FontWeight.Bold, model.annotated(Color.Blue).spanStyles.first().item.fontWeight)
        assertEquals(source, model.replaceVisible(model.visible))
    }

    @Test fun editsInsideExistingMarksPreserveDelimitersDestinationAndUndo() {
        val original = "Before **bold** and [site](https://example.com/path). After"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.single()
        val model = MarkdownFormattedBlock.inline(block)!!
        assertTrue(controller.replaceFormattedInlineText(block.id, model.visible.replace("bold", "bold!"), TextRange(12)))
        assertEquals("Before **bold!** and [site](https://example.com/path). After", controller.text)
        val next = MarkdownFormattedBlock.inline(controller.semanticDocument().blocks.single())!!
        assertTrue(controller.replaceFormattedInlineText(block.id, next.visible.replace("site", "docs"), TextRange(23)))
        assertEquals("Before **bold!** and [docs](https://example.com/path). After", controller.text)
        assertTrue(controller.undo())
        assertEquals("Before **bold!** and [site](https://example.com/path). After", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertTrue(controller.redo())
    }

    @Test fun formattedToolbarCreatesBoldItalicLinkAndCodeInSelectedText() {
        val controller = MarkdownEditorController("alpha beta gamma delta")
        controller.mode = MarkdownEditorMode.FORMATTED
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedSelection(id, TextRange(0, 5))
        controller.applyCommand(MarkdownEditorCommand.BOLD)
        assertEquals("**alpha** beta gamma delta", controller.text)
        controller.setFormattedSelection(id, TextRange(6, 10))
        controller.applyCommand(MarkdownEditorCommand.ITALIC)
        assertEquals("**alpha** *beta* gamma delta", controller.text)
        controller.setFormattedSelection(id, TextRange(11, 16))
        controller.applyCommand(MarkdownEditorCommand.LINK, "https://example.com")
        assertEquals("**alpha** *beta* [gamma](https://example.com) delta", controller.text)
        controller.setFormattedSelection(id, TextRange(17, 22))
        controller.applyCommand(MarkdownEditorCommand.INLINE_CODE)
        assertEquals("**alpha** *beta* [gamma](https://example.com) `delta`", controller.text)
        assertEquals("alpha beta gamma delta", MarkdownFormattedBlock.inline(controller.semanticDocument().blocks.single())!!.visible)
        repeat(4) { assertTrue(controller.undo()) }
        assertEquals("alpha beta gamma delta", controller.text)
    }

    @Test fun headingAndEscapedLiteralRoundTripWithoutTouchingNeighborBlocks() {
        val original = "first\n\n## Hello **world** ##\n\nlast"
        val controller = MarkdownEditorController(original)
        val block = controller.semanticDocument().blocks[1]
        val model = MarkdownFormattedBlock.inline(block)!!
        assertEquals("Hello world", model.visible)
        assertTrue(controller.replaceFormattedInlineText(block.id, "Hello w*orld", TextRange(11)))
        assertEquals("first\n\n## Hello **w\\*orld** ##\n\nlast", controller.text)
        assertEquals("Hello w*orld", MarkdownFormattedBlock.inline(controller.semanticDocument().blocks[1])!!.visible)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun deletingAnEntireMarkRemovesItsSyntaxAndCanUndo() {
        val controller = MarkdownEditorController("A **bold** word")
        val block = controller.semanticDocument().blocks.single()
        assertTrue(controller.replaceFormattedInlineText(block.id, "A  word", TextRange(2)))
        assertEquals("A  word", controller.text)
        assertTrue(controller.undo())
        assertEquals("A **bold** word", controller.text)
    }

    @Test fun crossMarkReplacementFallsBackToValidPlainSource() {
        val model = MarkdownInlineEditing.parse("**bold** and *italic*")
        assertEquals("plain", model.replaceVisible("plain"))
    }

    @Test fun applyingSameMarkToItsFullRangeTogglesItOff() {
        val controller = MarkdownEditorController("**bold** and *italic*")
        controller.mode = MarkdownEditorMode.FORMATTED
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedSelection(id, TextRange(0, 4))
        controller.applyCommand(MarkdownEditorCommand.BOLD)
        assertEquals("bold and *italic*", controller.text)
        assertTrue(controller.undo())
        assertEquals("**bold** and *italic*", controller.text)
    }

    @Test fun unmatchedMarkupAndIntrawordUnderscoresStayLiteral() {
        val source = "a_b_c ![mark](asset.png) and *unfinished"
        val model = MarkdownInlineEditing.parse(source)
        assertEquals(source, model.visible)
        assertTrue(model.marks.isEmpty())
    }

    @Test fun unsafeCrossBlockEditIsRejectedWithoutHistory() {
        val controller = MarkdownEditorController("one\n\ntwo")
        val block = controller.semanticDocument().blocks.first()
        assertFalse(controller.replaceFormattedInlineText(block.id, "one\n\nnew", TextRange(8)))
        assertEquals("one\n\ntwo", controller.text)
        assertFalse(controller.canUndo)
    }
}
