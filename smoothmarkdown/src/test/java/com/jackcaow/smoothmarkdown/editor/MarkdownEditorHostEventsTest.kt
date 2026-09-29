package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditorHostEventsTest {
    @Test fun initialStateAndRepeatedSelectionDoNotNotify() {
        val initial = TextFieldValue("Hello", TextRange(5))
        val tracker = MarkdownEditorHostEvents(initial)
        val source = mutableListOf<String>()
        val selections = mutableListOf<TextRange>()

        tracker.accept(initial, { source += it }, { selections += it })
        tracker.accept(initial.copy(selection = TextRange(1, 3)), { source += it }, { selections += it })
        tracker.accept(initial.copy(selection = TextRange(1, 3)), { source += it }, { selections += it })

        assertEquals(emptyList<String>(), source)
        assertEquals(listOf(TextRange(1, 3)), selections)
    }

    @Test fun sourceChangesAndUndoReportDistinctCommittedSnapshots() {
        val tracker = MarkdownEditorHostEvents(TextFieldValue("First"))
        val source = mutableListOf<String>()
        tracker.accept(TextFieldValue("Second"), { source += it }, null)
        tracker.accept(TextFieldValue("Second", TextRange(2)), { source += it }, null)
        tracker.accept(TextFieldValue("First"), { source += it }, null)
        assertEquals(listOf("Second", "First"), source)
    }

    @Test fun imeCompositionNotifiesOnlyAfterCommitButStillReportsSelection() {
        val tracker = MarkdownEditorHostEvents(TextFieldValue("A", TextRange(1)))
        val source = mutableListOf<String>()
        val selections = mutableListOf<TextRange>()
        val composing = TextFieldValue("A中", TextRange(2), TextRange(1, 2))
        tracker.accept(composing, { source += it }, { selections += it })
        tracker.accept(composing.copy(text = "A中文", selection = TextRange(3), composition = TextRange(1, 3)),
            { source += it }, { selections += it })
        tracker.accept(TextFieldValue("A中文", TextRange(3)), { source += it }, { selections += it })
        assertEquals(listOf("A中文"), source)
        assertEquals(listOf(TextRange(2), TextRange(3)), selections)
    }
}
