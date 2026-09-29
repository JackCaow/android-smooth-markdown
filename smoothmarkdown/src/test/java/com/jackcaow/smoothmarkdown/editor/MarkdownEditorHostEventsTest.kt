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
        val controller = MarkdownEditorController("First")
        val tracker = MarkdownEditorHostEvents(controller.value)
        val source = mutableListOf<String>()
        val observer: (TextFieldValue) -> Unit = { tracker.accept(it, { text -> source += text }, null) }
        controller.addValueObserver(observer)

        // Both writes occur before Compose could start another frame or collect a snapshot flow.
        controller.text = "Second"
        controller.text = "Third"
        assertEquals(listOf("Second", "Third"), source)
        controller.setSelection(2)
        assertEquals(listOf("Second", "Third"), source)
        check(controller.undo())
        assertEquals(listOf("Second", "Third", "Second"), source)
        controller.removeValueObserver(observer)
        controller.text = "Detached"
        assertEquals(listOf("Second", "Third", "Second"), source)
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

    @Test fun reentrantHostEditKeepsAllObserversInCommitOrder() {
        val controller = MarkdownEditorController("First")
        val seen = mutableListOf<String>()
        controller.addValueObserver { value ->
            seen += "first:${value.text}"
            if (value.text == "Second") controller.text = "Third"
        }
        controller.addValueObserver { value -> seen += "second:${value.text}" }

        controller.text = "Second"

        assertEquals("Third", controller.text)
        assertEquals(listOf("first:Second", "second:Second", "first:Third", "second:Third"), seen)
    }
}
