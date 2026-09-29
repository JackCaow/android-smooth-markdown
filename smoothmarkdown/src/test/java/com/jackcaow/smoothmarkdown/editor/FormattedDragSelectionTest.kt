package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedDragSelectionTest {
    @Test fun reverseDragAcrossProseCopiesExactMarkdownAndDeletesInOneUndoStep() {
        val source = "before\r\n\r\n# 😀 **Title**\r\n\r\nLast *line*\r\n\r\nafter"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val heading = FormattedDragTarget.Block("block-1")
        val paragraph = FormattedDragTarget.Block("block-2")
        drag.register(heading, Rect(0f, 0f, 200f, 40f))
        drag.register(paragraph, Rect(0f, 50f, 200f, 90f))
        assertTrue(drag.begin(paragraph))
        assertTrue(drag.update(Offset(20f, 20f)))
        drag.end()
        assertEquals("# 😀 **Title**\r\n\r\nLast *line*", controller.copyFormattedBlockSelectionAsMarkdown())
        assertTrue(controller.deleteFormattedBlockSelection())
        assertEquals("before\r\n\r\n\r\n\r\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun documentDragWillNotSilentlyIncludeAListOrCrossUnregisteredContent() {
        val source = "A\n\n- item\n\nB"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val first = FormattedDragTarget.Block("block-0")
        val last = FormattedDragTarget.Block("block-2")
        drag.register(first, Rect(0f, 0f, 100f, 30f))
        drag.register(last, Rect(0f, 80f, 100f, 110f))
        assertTrue(drag.begin(first))
        assertFalse(drag.update(Offset(20f, 90f)))
        assertEquals("A", controller.copyFormattedBlockSelectionAsMarkdown())
        assertFalse(drag.update(Offset(20f, 50f)))
    }

    @Test fun dragSelectedHeadingsCanBeReplacedWithoutChangingNeighboringSource() {
        val source = "intro\n\n# One\n\n## Two\n\noutro"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val second = FormattedDragTarget.Block("block-2")
        val first = FormattedDragTarget.Block("block-1")
        drag.register(first, Rect(0f, 0f, 100f, 30f))
        drag.register(second, Rect(0f, 40f, 100f, 70f))
        assertTrue(drag.begin(second))
        assertTrue(drag.update(Offset(20f, 15f)))
        assertTrue(controller.replaceFormattedBlockSelectionWithMarkdown("# Replacement"))
        assertEquals("intro\n\n# Replacement\n\noutro", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun listDragSelectsOnlySiblingSubtreesAndKeepsTaskMarkers() {
        val source = "- [ ] One\n- [x] Two\n  - Nested\n- Three"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val first = FormattedDragTarget.ListItem("block-0", listOf(0))
        val second = FormattedDragTarget.ListItem("block-0", listOf(1))
        val nested = FormattedDragTarget.ListItem("block-0", listOf(1, 0))
        drag.register(first, Rect(0f, 0f, 100f, 30f))
        drag.register(second, Rect(0f, 30f, 100f, 60f))
        drag.register(nested, Rect(20f, 60f, 100f, 90f))
        assertTrue(drag.begin(first))
        assertFalse(drag.update(Offset(40f, 70f)))
        assertTrue(drag.update(Offset(10f, 40f)))
        assertEquals("- [ ] One\n- [x] Two\n  - Nested", controller.copyFormattedListItemSelectionAsMarkdown())
        assertTrue(controller.deleteFormattedListItemSelection())
        assertEquals("- Three", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun tableDragCreatesRectangleWithExactCellSourceAndOneUndo() {
        val source = "| A | B |\n| --- | --- |\n| x | y |\n| q | r |"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val first = FormattedDragTarget.TableCell("block-0", 1, 0)
        val last = FormattedDragTarget.TableCell("block-0", 2, 1)
        drag.register(first, Rect(0f, 0f, 100f, 30f))
        drag.register(last, Rect(100f, 30f, 200f, 60f))
        assertTrue(drag.begin(first))
        assertTrue(drag.update(Offset(150f, 45f)))
        assertEquals("x\ty\nq\tr", controller.copyFormattedTableCellSelectionAsTsv())
        assertTrue(controller.clearFormattedTableCellSelection())
        assertEquals("| A | B |\n| --- | --- |\n|  |  |\n|  |  |", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun tableDragCanPasteSameSizedTsvWithoutReformattingOtherCells() {
        val source = "| A | B | C |\n| :--- | ---: | --- |\n| x | y | z |\n| q | r | s |"
        val controller = MarkdownEditorController(source)
        val drag = FormattedDragSelection(controller)
        val first = FormattedDragTarget.TableCell("block-0", 1, 0)
        val last = FormattedDragTarget.TableCell("block-0", 2, 1)
        drag.register(first, Rect(0f, 0f, 100f, 30f))
        drag.register(last, Rect(100f, 30f, 200f, 60f))
        assertTrue(drag.begin(first))
        assertTrue(drag.update(Offset(150f, 45f)))
        assertFalse(controller.replaceFormattedTableCellSelectionFromTsv("one\ttwo"))
        assertEquals(source, controller.text)
        assertTrue(controller.replaceFormattedTableCellSelectionFromTsv("one\ttwo\nthree\tfour"))
        assertEquals("| A | B | C |\n| :--- | ---: | --- |\n| one | two | z |\n| three | four | s |", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun staleSelectionDoesNotGetReusedAfterDocumentChanges() {
        val controller = MarkdownEditorController("One\n\nTwo")
        val drag = FormattedDragSelection(controller)
        assertTrue(drag.begin(FormattedDragTarget.Block("block-0")))
        controller.replaceRange(0, 0, "Ahead\n\n")
        assertNull(controller.copyFormattedBlockSelectionAsMarkdown())
        assertFalse(drag.update(Offset(0f, 0f)))
    }
}
