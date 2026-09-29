package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineEditingTest {
    @Test fun displaysFiveMarksAndKeepsSourceUntouched() {
        val source = "A **bold** and *italic*, [site](https://example.com), `code`, and ~~raw~~."
        val model = MarkdownInlineEditing.parse(source)
        assertEquals("A bold and italic, site, code, and raw.", model.visible)
        assertEquals(listOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK, InlineMarkKind.CODE,
            InlineMarkKind.STRIKETHROUGH),
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

    @Test fun formattedStrikethroughUsesVisibleUtf16RangeAndPreservesNeighborSource() {
        val original = "before\n\n## 😀 alpha beta ##\n\nafter"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val heading = controller.semanticDocument().blocks[1]
        // The source cursor deliberately points at the preceding paragraph.
        controller.setSelection(0)
        controller.setFormattedSelection(heading.id, TextRange(3, 8))
        controller.applyCommand(MarkdownEditorCommand.STRIKETHROUGH)
        assertEquals("before\n\n## 😀 ~~alpha~~ beta ##\n\nafter", controller.text)
        assertEquals(TextRange(3, 8), controller.formattedSelection)
        assertEquals("😀 alpha beta", MarkdownFormattedBlock.inline(controller.semanticDocument().blocks[1])!!.visible)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("before\n\n## 😀 ~~alpha~~ beta ##\n\nafter", controller.text)
    }

    @Test fun formattedStrikethroughTogglesOffAndDoesNotEditDuringImeComposition() {
        val original = "~~alpha~~ beta"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedSelection(id, TextRange(0, 5), TextRange(0, 5))
        controller.applyCommand(MarkdownEditorCommand.STRIKETHROUGH)
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)

        controller.setFormattedSelection(id, TextRange(0, 5))
        controller.applyCommand(MarkdownEditorCommand.STRIKETHROUGH)
        assertEquals("alpha beta", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertTrue(controller.redo())
        assertEquals("alpha beta", controller.text)
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

    @Test fun partialVisibleRangeCanNestInsideExistingBoldItalicAndLink() {
        val cases = listOf(
            Triple("**bold**", InlineMarkKind.ITALIC, InlineMarkKind.BOLD),
            Triple("*bold*", InlineMarkKind.BOLD, InlineMarkKind.ITALIC),
            Triple("[bold](https://example.com/path)", InlineMarkKind.ITALIC, InlineMarkKind.LINK),
        )
        cases.forEach { (source, addedKind, originalKind) ->
            val model = MarkdownInlineEditing.parse(source)
            val wrapped = model.wrap(TextRange(1, 3), addedKind)
            assertTrue("$source must support its inner visible range", wrapped != null)
            val after = MarkdownInlineEditing.parse(wrapped!!)
            assertEquals("bold", after.visible)
            assertTrue(after.marks.any { it.kind == originalKind && it.range == TextRange(0, 4) })
            assertTrue(after.marks.any { it.kind == addedKind && it.range == TextRange(1, 3) })
            if (originalKind == InlineMarkKind.LINK) {
                assertEquals("https://example.com/path", after.marks.first { it.kind == InlineMarkKind.LINK }.destination)
            }
        }
    }

    @Test fun nestedAsteriskDelimitersKeepOuterEmphasisAndInnerStrong() {
        val model = MarkdownInlineEditing.parse("*b**ol**d*")
        assertEquals("bold", model.visible)
        assertTrue(model.marks.any { it.kind == InlineMarkKind.ITALIC && it.range == TextRange(0, 4) })
        assertTrue(model.marks.any { it.kind == InlineMarkKind.BOLD && it.range == TextRange(1, 3) })
    }

    @Test fun headingPartialVisibleFormattingPreservesMarkerNeighborsAndOneUndo() {
        val original = "before\r\n\r\n## [bold](https://example.com/path) ##\r\n\r\nafter"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val heading = controller.semanticDocument().blocks[1]
        controller.setFormattedSelection(heading.id, TextRange(1, 3))
        assertTrue(controller.applyFormattedInlineMark(MarkdownEditorCommand.BOLD))
        assertTrue(controller.text.startsWith("before\r\n\r\n## "))
        assertTrue(controller.text.endsWith(" ##\r\n\r\nafter"))
        assertTrue(controller.text.contains("https://example.com/path"))
        val next = MarkdownFormattedBlock.inline(controller.semanticDocument().blocks[1])!!
        assertEquals("bold", next.visible)
        assertTrue(next.marks.any { it.kind == InlineMarkKind.BOLD && it.range == TextRange(1, 3) })
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun partialLinkInsideExistingLinkIsRejectedWithoutHistory() {
        val original = "[bold](https://example.com/path)"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedSelection(id, TextRange(1, 3))
        assertFalse(controller.applyFormattedInlineMark(MarkdownEditorCommand.LINK, "https://another.example"))
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun unsafeLinkDestinationAndHalfEmojiSelectionAreRejected() {
        val model = MarkdownInlineEditing.parse("A **bold** 😀")
        val selection = TextRange(2, 4)
        assertEquals(null, model.wrap(selection, InlineMarkKind.LINK, "javascript:alert(1)"))
        assertEquals(null, model.wrap(selection, InlineMarkKind.LINK, "   "))
        assertEquals(null, model.wrap(selection, InlineMarkKind.LINK, "https://example.com/) **injected**"))
        assertEquals(null, model.wrap(selection, InlineMarkKind.LINK, "https://example.com/(broken)"))
        val emoji = model.visible.indexOf("😀")
        assertEquals(null, model.wrap(TextRange(emoji + 1, emoji + 2), InlineMarkKind.BOLD))
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
