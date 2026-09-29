package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattedTextGestureTest {
    private val source = "First\n\n# Second"

    private fun registry() = FormattedTextPositionRegistry().apply {
        register(FormattedTextPositionTarget("block-0", source, "First",
            { Rect(0f, 0f, 100f, 20f) }, { point -> (point.x / 20f).toInt() },
            { offset -> Offset(offset * 20f, 20f) }))
        register(FormattedTextPositionTarget("block-1", source, "Second",
            { Rect(0f, 30f, 120f, 50f) }, { point -> (point.x / 20f).toInt() },
            { offset -> Offset(offset * 20f, 50f) }))
    }

    @Test fun crossFieldDragStartsAtCharactersAndTracksReverseMovement() {
        val positions = registry()
        val gesture = FormattedTextGesture(positions)
        val anchor = MarkdownFormattedTextPosition("block-0", 2)
        assertNull(gesture.beginCrossFieldDrag(anchor, Offset(60f, 10f), source))
        val selected = gesture.beginCrossFieldDrag(anchor, Offset(60f, 40f), source)!!
        assertEquals(MarkdownFormattedTextPosition("block-1", 3), selected.focus)
        assertEquals(MarkdownFormattedTextPosition("block-0", 1),
            gesture.move(selected, FormattedTextHandle.FOCUS, Offset(20f, 10f), source)?.focus)
        assertNull(gesture.move(selected, FormattedTextHandle.FOCUS, Offset(20f, 10f), "stale"))
    }

    @Test fun handlesHitAtCursorGeometryAndCanMoveAcrossBlocks() {
        val gesture = FormattedTextGesture(registry())
        val selected = FormattedTextEndpoints(source,
            MarkdownFormattedTextPosition("block-0", 2),
            MarkdownFormattedTextPosition("block-1", 3))
        assertEquals(FormattedTextHandle.ANCHOR,
            gesture.hitHandle(selected, Offset(41f, 19f), source, 10f))
        assertEquals(FormattedTextHandle.FOCUS,
            gesture.hitHandle(selected, Offset(60f, 50f), source, 10f))
        assertNull(gesture.hitHandle(selected, Offset(60f, 50f), "stale", 10f))
        val moved = gesture.move(selected, FormattedTextHandle.ANCHOR, Offset(100f, 40f), source)!!
        assertEquals(MarkdownFormattedTextPosition("block-1", 5), moved.anchor)
        assertEquals(selected.focus, moved.focus)
        assertEquals(MarkdownFormattedTextPosition("block-1", 5),
            gesture.move(selected, FormattedTextHandle.ANCHOR, Offset(100f, 55f), source, 6f)?.anchor)
    }
}
