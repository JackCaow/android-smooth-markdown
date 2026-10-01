package com.jackcaow.smoothmarkdown

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionStateTest {
    @Test fun reverseRangesKeepVisibleSourceOrderAcrossParagraphsAndTableCells() {
        val state = ReaderSelectionState()
        val before = target("before", 0f)
        val left = target("left", 50f)
        val right = target("right", 50f, 100f)
        val after = target("after", 100f)
        // Registration order need not match the document: table cells and recycled layouts can
        // arrive in a different order. Range offsets must follow their positioned source order.
        listOf(after, right, before, left).forEach(state::track)
        state.select(TextRange(18, 3))
        assertEquals(listOf("ore", "left", "right", "aft"), state.selectedTexts.map { it.text })
        assertEquals(TextRange(18, 3), state.range)
    }

    @Test fun partialRangeRetainsRegisteredAnchorAnnotationForCopyFiltering() {
        val state = ReaderSelectionState()
        val anchor = "smd" + "a".repeat(497)
        val tagged = buildAnnotatedString {
            append(anchor)
            addStringAnnotation(nonTextAnchorAnnotationTag, anchor, 0, length)
        }
        state.track(target("before", 0f))
        state.track(target("", 50f).copy(text = tagged))
        state.track(target("after", 100f))
        state.select(TextRange(6 + 249, 6 + 500 + 3))
        val selected = state.selectedTexts
        assertFalse(selected.first().getStringAnnotations(nonTextAnchorAnnotationTag, 0, selected.first().length).isEmpty())
        assertEquals("aft", visibleSelectedText(selected, setOf(anchor)).text)
    }

    @Test fun updatedAndRemovedTargetsDoNotLeaveStaleCopyText() {
        val state = ReaderSelectionState()
        val first = target("alpha", 0f)
        val second = target("beta", 50f)
        state.track(first)
        state.track(second)
        state.selectAll()
        assertEquals(listOf("alpha", "beta"), state.selectedTexts.map { it.text })
        state.track(first.copy(text = AnnotatedString("omega")))
        state.remove(second.key)
        state.selectAll()
        assertEquals(listOf("omega"), state.selectedTexts.map { it.text })
        state.clear()
        assertTrue(state.selectedTexts.isEmpty())
    }

    @Test fun hitMappingUsesTableCellPositionAndClampsOutsideDocument() {
        val state = ReaderSelectionState()
        state.track(target("left", 0f).copy(offsetAtWindowPosition = { 2 }))
        state.track(target("right", 0f, 100f).copy(offsetAtWindowPosition = { 3 }))
        assertEquals(7, state.offsetAt(Offset(120f, 10f)))
        state.select(TextRange(0, 100))
        assertEquals(listOf("left", "right"), state.selectedTexts.map { it.text })
    }

    private fun target(text: String, top: Float, left: Float = 0f) = MarkdownSelectionTarget(
        Any(), Rect(left, top, left + 80f, top + 30f), AnnotatedString(text),
    )
}
