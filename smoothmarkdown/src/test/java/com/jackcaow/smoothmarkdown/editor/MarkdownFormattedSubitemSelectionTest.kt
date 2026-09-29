package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFormattedSubitemSelectionTest {
    @Test fun deletesSiblingListSubtreesWithoutRewritingNeighborsAndUndoesInOneStep() {
        val source = "before\n\n- One\n- [x] Two\n  - Nested\n- Three\n- Four\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedListItem("block-1", listOf(2)))
        assertTrue(controller.selectFormattedListItem("block-1", listOf(1)))
        assertEquals("- [x] Two\n  - Nested\n- Three", controller.copyFormattedListItemSelectionAsMarkdown())
        assertTrue(controller.deleteFormattedListItemSelection())
        assertEquals("before\n\n- One\n- Four\n\nafter", controller.text)
        assertNull(controller.formattedListItemSelection)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("before\n\n- One\n- Four\n\nafter", controller.text)
    }

    @Test fun deletesNestedSiblingItemsButKeepsParentAndFollowingRootItem() {
        val source = "- Parent\n  - One\n  - Two\n- End"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedListItem("block-0", listOf(0, 0)))
        assertTrue(controller.selectFormattedListItem("block-0", listOf(0, 1)))
        assertEquals("- One\n- Two", controller.copyFormattedListItemSelectionAsMarkdown())
        assertTrue(controller.deleteFormattedListItemSelection())
        assertEquals("- Parent\n- End", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
    }

    @Test fun deletesLastOrAllRootItemsWithoutLeavingAListMarker() {
        val last = MarkdownEditorController("- One\n- Two")
        assertTrue(last.selectFormattedListItem("block-0", listOf(1)))
        assertTrue(last.deleteFormattedListItemSelection())
        assertEquals("- One", last.text)
        assertTrue(last.undo())
        assertEquals("- One\n- Two", last.text)

        val all = MarkdownEditorController("- One\n- Two")
        assertTrue(all.selectFormattedListItem("block-0", listOf(0)))
        assertTrue(all.selectFormattedListItem("block-0", listOf(1)))
        assertTrue(all.deleteFormattedListItemSelection())
        assertEquals("", all.text)
        assertTrue(all.undo())
        assertEquals("- One\n- Two", all.text)
    }

    @Test fun rejectsInvalidOrStaleListItemSelectionWithoutHistory() {
        val controller = MarkdownEditorController("- One\n- Two\n- Three")
        assertFalse(controller.selectFormattedListItem("block-0", listOf(9)))
        assertTrue(controller.selectFormattedListItem("block-0", listOf(0)))
        controller.replaceRange(0, 0, "- Zero\n")
        assertNull(controller.copyFormattedListItemSelectionAsMarkdown())
        assertFalse(controller.deleteFormattedListItemSelection())
        assertEquals("- Zero\n- One\n- Two\n- Three", controller.text)
    }

    @Test fun formatsSelectedPrimaryLinesAndPreservesTaskMarkersAndNestedChildren() {
        val source = "- [ ] One\n- [x] Two\n  - Child\n- Three"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedListItem("block-0", listOf(0)))
        assertTrue(controller.selectFormattedListItem("block-0", listOf(1)))
        assertTrue(controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.BOLD))
        assertEquals("- [ ] **One**\n- [x] **Two**\n  - Child\n- Three", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun clearsTableRectangleWhileRetainingEveryUnselectedCellAndSourceStyle() {
        val source = "before\n\n| A | **B** | C |\n| :--- | ---: | --- |\n| x | y | z |\n| q | r | s |\n\nafter"
        val controller = MarkdownEditorController(source)
        assertTrue(controller.selectFormattedTableCell("block-1", 2, 1))
        assertTrue(controller.selectFormattedTableCell("block-1", 1, 0))
        assertEquals("x\ty\nq\tr", controller.copyFormattedTableCellSelectionAsTsv())
        assertTrue(controller.clearFormattedTableCellSelection())
        val updated = "before\n\n| A | **B** | C |\n| :--- | ---: | --- |\n|  |  | z |\n|  |  | s |\n\nafter"
        assertEquals(updated, controller.text)
        assertNull(controller.formattedTableCellSelection)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals(updated, controller.text)
    }

    @Test fun copiesHeaderAndEscapedPipeAndRejectsStaleTableSelection() {
        val source = "| A \\| B | **C** |\n| --- | --- |\n| one | two |"
        val controller = MarkdownEditorController(source)
        assertFalse(controller.selectFormattedTableCell("block-0", 3, 0))
        assertTrue(controller.selectFormattedTableCell("block-0", 1, 1))
        assertTrue(controller.selectFormattedTableCell("block-0", 0, 0))
        assertEquals("A \\| B\t**C**\none\ttwo", controller.copyFormattedTableCellSelectionAsTsv())
        controller.replaceRange(0, 0, "intro\n\n")
        assertNull(controller.copyFormattedTableCellSelectionAsTsv())
        assertFalse(controller.clearFormattedTableCellSelection())
    }
}
