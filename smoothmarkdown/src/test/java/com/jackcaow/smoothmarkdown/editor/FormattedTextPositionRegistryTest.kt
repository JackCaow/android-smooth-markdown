package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattedTextPositionRegistryTest {
    @Test fun mapsWindowPointsToTheCorrectBlockAndVisibleUtf16Offset() {
        val source = "Alpha\n\n# Beta"
        val registry = FormattedTextPositionRegistry()
        registry.register(FormattedTextPositionTarget("block-0", source, "Alpha",
            { Rect(10f, 20f, 110f, 40f) }, { point -> ((point.x - 10f) / 20f).toInt() }))
        registry.register(FormattedTextPositionTarget("block-1", source, "Beta",
            { Rect(10f, 50f, 110f, 70f) }, { point -> ((point.x - 10f) / 20f).toInt() }))

        assertEquals(MarkdownFormattedTextPosition("block-0", 2), registry.positionAt(Offset(55f, 30f), source))
        assertEquals(MarkdownFormattedTextPosition("block-1", 3), registry.positionAt(Offset(75f, 60f), source))
        assertNull(registry.positionAt(Offset(55f, 45f), source))
        assertNull(registry.positionAt(Offset(55f, 30f), "stale source"))
    }

    @Test fun resolvesLiveBoundsAfterScrollAndDropsDisposedTargets() {
        val registry = FormattedTextPositionRegistry()
        var bounds = Rect(0f, 100f, 200f, 140f)
        val target = FormattedTextPositionTarget("block-0", "Text", "Text", { bounds }, { 2 })
        registry.register(target)
        assertEquals(2, registry.positionAt(Offset(30f, 120f), "Text")?.offset)
        bounds = Rect(0f, 20f, 200f, 60f)
        assertNull(registry.positionAt(Offset(30f, 120f), "Text"))
        assertEquals(2, registry.positionAt(Offset(30f, 40f), "Text")?.offset)
        registry.unregister(target)
        assertNull(registry.positionAt(Offset(30f, 40f), "Text"))
    }

    @Test fun keepsBidiLayoutOffsetsAndRejectsHalfOfAnEmoji() {
        val registry = FormattedTextPositionRegistry()
        registry.register(FormattedTextPositionTarget("rtl", "rtl source", "אבגדה",
            { Rect(0f, 0f, 100f, 20f) }, { point -> if (point.x < 50f) 5 else 0 }))
        assertEquals(5, registry.positionAt(Offset(10f, 10f), "rtl source")?.offset)
        assertEquals(0, registry.positionAt(Offset(90f, 10f), "rtl source")?.offset)

        registry.register(FormattedTextPositionTarget("emoji", "emoji source", "😀 end",
            { Rect(0f, 30f, 100f, 50f) }, { point -> if (point.x < 50f) 1 else 2 }))
        assertEquals(0, registry.positionAt(Offset(10f, 40f), "emoji source")?.offset)
        assertEquals(2, registry.positionAt(Offset(90f, 40f), "emoji source")?.offset)
        assertEquals(0, safeUtf16Boundary("😀", -1))
        assertEquals(2, safeUtf16Boundary("😀", 3))
    }

    @Test fun lateDisposeCannotRemoveReplacementForTheSameBlock() {
        val registry = FormattedTextPositionRegistry()
        val old = FormattedTextPositionTarget("block-0", "old", "Old", { Rect(0f, 0f, 10f, 10f) }, { 1 })
        val replacement = FormattedTextPositionTarget("block-0", "new", "New", { Rect(0f, 0f, 10f, 10f) }, { 2 })
        registry.register(old)
        registry.register(replacement)
        registry.unregister(old)
        assertEquals(2, registry.positionAt(Offset(5f, 5f), "new")?.offset)
    }
    @Test fun distinctTableCellsInOneBlockKeepSeparateCaretTargets() {
        val source = "| A | B |\n| --- | --- |"
        val registry = FormattedTextPositionRegistry()
        val leftCell = MarkdownTableCellPosition(0, 0)
        val rightCell = MarkdownTableCellPosition(0, 1)
        val left = FormattedTextPositionTarget("block-0", source, "A",
            { Rect(0f, 0f, 40f, 20f) }, { 1 }, { Offset(20f, 10f) }, leftCell)
        val right = FormattedTextPositionTarget("block-0", source, "B",
            { Rect(50f, 0f, 90f, 20f) }, { 0 }, { Offset(60f, 10f) }, rightCell)
        registry.register(left)
        registry.register(right)
        assertEquals(MarkdownFormattedTextPosition("block-0", 1, tableCell = leftCell),
            registry.positionAt(Offset(20f, 10f), source))
        assertEquals(MarkdownFormattedTextPosition("block-0", 0, tableCell = rightCell),
            registry.positionAt(Offset(60f, 10f), source))
        assertEquals(Offset(60f, 10f), registry.cursorWindowPoint(
            MarkdownFormattedTextPosition("block-0", 0, tableCell = rightCell), source))
        registry.unregister(left)
        assertEquals(rightCell, registry.positionAt(Offset(60f, 10f), source)?.tableCell)
    }

}
