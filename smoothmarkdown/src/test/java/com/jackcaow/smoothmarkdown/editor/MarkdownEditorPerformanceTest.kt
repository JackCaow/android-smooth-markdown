package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorPerformanceTest {
    @Test fun snapshotReportsAvailableNativeStateAndLeavesUnknownMetricsEmpty() {
        val controller = MarkdownEditorController("# 😀\n\nfind find")
        controller.mode = MarkdownEditorMode.FORMATTED
        val source = controller.text
        controller.updateFromInput(TextFieldValue(source, TextRange(source.length), TextRange(10, 14)))

        val snapshot = MarkdownEditorPerformanceReporter().capture(
            controller, searchQuery = "find", searchOpen = true,
            slashSuggestionsVisible = true, timestampMillis = 123L,
        )

        assertEquals(15, snapshot.sourceLength) // The emoji occupies two UTF-16 code units.
        assertEquals(2, snapshot.blockCount)
        assertEquals(MarkdownEditorMode.FORMATTED, snapshot.mode)
        assertTrue(snapshot.isComposing)
        assertEquals(2, snapshot.searchMatchCount)
        assertTrue(snapshot.slashSuggestionsVisible)
        assertEquals(123L, snapshot.timestampMillis)
        assertNull(snapshot.formattedSegmentCount)
        assertNull(snapshot.formattedSegmentCacheHit)
        assertNull(snapshot.retainedFormattedSegmentKeyCount)
        assertNull(snapshot.wikilinkSuggestionsVisible)
    }

    @Test fun snapshotsRefreshBlockCountOnSourceChangeAndIgnoreClosedSearch() {
        val controller = MarkdownEditorController("One")
        val reporter = MarkdownEditorPerformanceReporter()
        assertEquals(1, reporter.capture(controller, "One", true, false).blockCount)

        controller.text = "One\n\nTwo"
        val next = reporter.capture(controller, "One", searchOpen = false,
            slashSuggestionsVisible = false)
        assertEquals(2, next.blockCount)
        assertEquals(0, next.searchMatchCount)
        assertFalse(next.isComposing)
        assertFalse(next.slashSuggestionsVisible)
    }

    @Test fun sourceFocusReportsOnlyTransitions() {
        val tracker = MarkdownEditorSourceFocusTracker()
        val transitions = mutableListOf<Boolean>()
        tracker.setFocused(false) { transitions += it }
        tracker.setFocused(true) { transitions += it }
        tracker.setFocused(true) { transitions += it }
        tracker.setFocused(false) { transitions += it }
        tracker.setFocused(false) { transitions += it }
        assertEquals(listOf(true, false), transitions)
    }
}
