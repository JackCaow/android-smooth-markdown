package com.jackcaow.smoothmarkdown

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionRangeTest {
    @Test fun wordAndParagraphMapToRenderedTextOffsets() {
        val first = target("alpha beta\ngamma delta", 0f, offset = 7, word = TextRange(6, 10))
        val second = target("later paragraph", 100f, offset = 2, word = TextRange(0, 5))
        val texts = listOf(first.text, second.text)
        assertEquals(TextRange(6, 10), readerSelectionRangeAt(
            Offset(20f, 20f), listOf(first, second), texts, ReaderSelectionGranularity.WORD))
        assertEquals(TextRange(0, 10), readerSelectionRangeAt(
            Offset(20f, 20f), listOf(first, second), texts, ReaderSelectionGranularity.PARAGRAPH))
        assertEquals(TextRange(first.text.length, first.text.length + 15), readerSelectionRangeAt(
            Offset(20f, 120f), listOf(first, second), texts, ReaderSelectionGranularity.PARAGRAPH))
    }

    @Test fun repeatedRenderedParagraphUsesVisualOccurrence() {
        val first = target("repeat", 0f, offset = 2, word = TextRange(0, 6))
        val second = target("repeat", 100f, offset = 2, word = TextRange(0, 6))
        assertEquals(TextRange(6, 12), readerSelectionRangeAt(
            Offset(20f, 120f), listOf(second, first), listOf(first.text, second.text),
            ReaderSelectionGranularity.WORD))
    }

    @Test fun paddingPressReturnsNoRangeInsteadOfLeavingSelectAll() {
        val first = target("alpha beta", 20f, offset = 2, word = TextRange(0, 5))
        assertNull(readerSelectionRangeAt(Offset(20f, 5f), listOf(first), listOf(first.text),
            ReaderSelectionGranularity.WORD))
    }

    @Test fun fullWidthBoundsOutsideTextGlyphsReturnsNoRange() {
        val first = target("alpha beta", 0f, offset = 2, word = TextRange(0, 5))
            .copy(containsTextAtWindowPosition = { false })
        assertNull(readerSelectionRangeAt(Offset(190f, 20f), listOf(first), listOf(first.text),
            ReaderSelectionGranularity.WORD))
    }

    private fun target(text: String, top: Float, offset: Int, word: TextRange) = MarkdownSelectionTarget(
        key = Any(),
        boundsInWindow = Rect(0f, top, 200f, top + 50f),
        text = AnnotatedString(text),
        offsetAtWindowPosition = { offset },
        wordBoundaryAtWindowPosition = { word },
    )
}
