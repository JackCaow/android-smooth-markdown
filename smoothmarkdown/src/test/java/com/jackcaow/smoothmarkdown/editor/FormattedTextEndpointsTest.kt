package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattedTextEndpointsTest {
    @Test fun paintsOnlySelectedUtf16CharactersAcrossParagraphAndHeadingInEitherDirection() {
        val source = "BeforeX\n\n# YAfter\n\nOutside"
        val blocks = MarkdownEditorController(source).semanticDocument().blocks
        val forward = FormattedTextEndpoints(source, MarkdownFormattedTextPosition("block-0", 6))
            .withFocus(MarkdownFormattedTextPosition("block-1", 1))
        assertEquals(TextRange(6, 7), forward.visibleRange(blocks, "block-0", 7))
        assertEquals(TextRange(0, 1), forward.visibleRange(blocks, "block-1", 6))
        assertNull(forward.visibleRange(blocks, "block-2", 7))
        val reverse = FormattedTextEndpoints(source, MarkdownFormattedTextPosition("block-1", 1))
            .withFocus(MarkdownFormattedTextPosition("block-0", 6))
        assertEquals(forward.visibleRange(blocks, "block-0", 7), reverse.visibleRange(blocks, "block-0", 7))
        assertEquals(forward.visibleRange(blocks, "block-1", 6), reverse.visibleRange(blocks, "block-1", 6))
    }

    @Test fun sameBlockAndUnfinishedRangesDoNotPaintUnselectedText() {
        val source = "😀 Alpha\n\nAfter"
        val blocks = MarkdownEditorController(source).semanticDocument().blocks
        val started = FormattedTextEndpoints(source, MarkdownFormattedTextPosition("block-0", 3))
        assertNull(started.visibleRange(blocks, "block-0", 8))
        assertEquals(TextRange(3, 5), started.withFocus(MarkdownFormattedTextPosition("block-0", 5))
            .visibleRange(blocks, "block-0", 8))
        assertEquals(TextRange(3, 5), started.copy(anchor = MarkdownFormattedTextPosition("block-0", 5))
            .withFocus(MarkdownFormattedTextPosition("block-0", 3)).visibleRange(blocks, "block-0", 8))
    }

    @Test fun completeInterveningListCodeAndTableAreMarkedInEitherDirection() {
        val source = "First\n\n- item\n\n```\ncode\n```\n\n| A |\n| --- |\n| B |\n\nLast"
        val blocks = MarkdownEditorController(source).semanticDocument().blocks
        val forward = FormattedTextEndpoints(source, MarkdownFormattedTextPosition("block-0", 2))
            .withFocus(MarkdownFormattedTextPosition("block-4", 2))
        val reverse = forward.copy(anchor = forward.focus!!, focus = forward.anchor)
        for (selected in listOf(forward, reverse)) {
            for (index in 1..3) assertEquals(true, selected.containsCompleteBlock(blocks, "block-$index"))
            assertEquals(false, selected.containsCompleteBlock(blocks, "block-0"))
            assertEquals(false, selected.containsCompleteBlock(blocks, "block-4"))
        }
        assertEquals(false, forward.copy(focus = null).containsCompleteBlock(blocks, "block-1"))
    }
}
