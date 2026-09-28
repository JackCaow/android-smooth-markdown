package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSourceListTest {
    @Test fun splitsFormattedTaskAtVisibleCaretAndRestoresUndoSelection() {
        val original = "before\n\n- [x] firstsecond\n- [ ] untouched\n\nafter"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.first { it.kind == MarkdownBlockKind.BULLET_LIST }.id
        controller.setFormattedListSelection(id, listOf(0), 0, androidx.compose.ui.text.TextRange(5))
        assertTrue(controller.splitFormattedListLine(id, listOf(0), 0, 5))
        assertEquals("before\n\n- [x] first\n- [ ] second\n- [ ] untouched\n\nafter", controller.text)
        assertEquals(listOf(1), controller.activeFormattedListPath)
        assertEquals(androidx.compose.ui.text.TextRange.Zero, controller.formattedListSelection)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertTrue(controller.redo())
        assertEquals("before\n\n- [x] first\n- [ ] second\n- [ ] untouched\n\nafter", controller.text)
    }

    @Test fun splitsNestedFormattedItemWithoutChangingNeighbors() {
        val original = "- parent\r\n  - **childnext**\r\n  - sibling\r\n- outside"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.splitFormattedListLine(id, listOf(0, 0), 0, 5))
        assertEquals("- parent\r\n  - **child**\r\n  - **next**\r\n  - sibling\r\n- outside", controller.text)
        assertEquals(listOf(0, 1), controller.activeFormattedListPath)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun indentsAndOutdentsSubtreeAsSingleUndoableSourceEdits() {
        val original = "- parent\n- **child**\n  - grandchild\n- untouched"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedListSelection(id, listOf(1), 0, androidx.compose.ui.text.TextRange(3))
        assertTrue(controller.indentFormattedListItem(id, listOf(1)))
        assertEquals("- parent\n  - **child**\n    - grandchild\n- untouched", controller.text)
        assertEquals(listOf(0, 0), controller.activeFormattedListPath)
        assertEquals(androidx.compose.ui.text.TextRange(3), controller.formattedListSelection)
        assertTrue(controller.outdentFormattedListItem(id, listOf(0, 0)))
        assertEquals(original, controller.text)
        assertEquals(listOf(1), controller.activeFormattedListPath)
        repeat(2) { assertTrue(controller.undo()) }
        assertEquals(original, controller.text)
        repeat(2) { assertTrue(controller.redo()) }
        assertEquals(original, controller.text)
    }

    @Test fun structuralListEditsRejectInvalidTargetsWithoutTouchingSource() {
        val original = "- parent\n  - first\n  - second\n- tail"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        assertFalse(controller.indentFormattedListItem(id, listOf(0)))
        assertFalse(controller.splitFormattedListLine(id, listOf(0, 0), 0, 100))
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun outdentsFirstNestedItemAfterRemainingChildren() {
        val original = "- parent\n  - first\n  - second\n- tail"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.outdentFormattedListItem(id, listOf(0, 0)))
        assertEquals("- parent\n  - second\n- first\n- tail", controller.text)
        assertEquals(listOf(1), controller.activeFormattedListPath)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun outdentsSimpleRootItemToParagraphAndKeepsBothListFragments() {
        val original = "- first\n- **second**\n- third"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedListSelection(id, listOf(1), 0, androidx.compose.ui.text.TextRange(3))
        assertTrue(controller.outdentFormattedListItem(id, listOf(1)))
        assertEquals("- first\n\n**second**\n\n- third", controller.text)
        assertEquals(3, controller.semanticDocument().blocks.size)
        assertEquals(MarkdownBlockKind.PARAGRAPH, controller.semanticDocument().blocks[1].kind)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertEquals(listOf(1), controller.activeFormattedListPath)
        assertEquals(androidx.compose.ui.text.TextRange(3), controller.formattedListSelection)
        assertTrue(controller.redo())
        assertEquals("- first\n\n**second**\n\n- third", controller.text)
    }

    @Test fun enterOnEmptyNestedItemOutdentsInsteadOfAddingAnotherBlankItem() {
        listOf(
            "- parent\n  - " to "- parent\n- ",
            "- parent\n  - [ ] " to "- parent\n- [ ] ",
            "1. parent\n   1. " to "1. parent\n1. ",
        ).forEach { (original, expected) ->
            val controller = MarkdownEditorController(original)
            val id = controller.semanticDocument().blocks.single().id
            assertTrue("$original should outdent", controller.splitFormattedListLine(id, listOf(0, 0), 0, 0))
            assertEquals(expected, controller.text)
            assertEquals(listOf(1), controller.activeFormattedListPath)
            assertTrue(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun enterOnEmptyRootItemLeavesAnEditableParagraphAndRestoresUndo() {
        val cases = listOf(
            Triple("- first\n- ", listOf(1), "- first\n\n"),
            Triple("- first\n- \n- next", listOf(1), "- first\n\n\n\n- next"),
            Triple("- \n- next", listOf(0), "\n\n- next"),
            Triple("- [ ] ", listOf(0), ""),
            Triple("7) first\n8) ", listOf(1), "7) first\n\n"),
            Triple("- first\r\n- ", listOf(1), "- first\r\n\r\n"),
        )
        cases.forEach { (original, path, blank) ->
            val controller = MarkdownEditorController(original)
            val id = controller.semanticDocument().blocks.single().id
            assertTrue("$original should exit", controller.splitFormattedListLine(id, path, 0, 0))
            assertEquals(blank, controller.text)
            assertEquals(path.single(), controller.pendingListExit?.beforeItemCount)
            assertTrue(controller.completePendingListExit("paragraph", androidx.compose.ui.text.TextRange(9)))
            assertTrue(controller.semanticDocument().blocks.any { it.kind == MarkdownBlockKind.PARAGRAPH && it.source == "paragraph" })
            assertTrue(controller.undo())
            assertEquals(blank, controller.text)
            assertTrue(controller.pendingListExit != null)
            assertTrue(controller.undo())
            assertEquals(original, controller.text)
            assertEquals(null, controller.pendingListExit)
            assertTrue(controller.redo())
            assertEquals(blank, controller.text)
            assertTrue(controller.redo())
            assertTrue(controller.text.contains("paragraph"))
        }
    }

    @Test fun liftsRootItemWithContinuationAndNestedSubtreeAsOneUndoableEdit() {
        val original = "- first\n- **parent**\n  continuation\n  - child\n    - grandchild\n- last"
        val expected = "- first\n\n**parent**\ncontinuation\n- child\n  - grandchild\n\n- last"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        controller.setFormattedListSelection(id, listOf(1), 0, androidx.compose.ui.text.TextRange(3))
        assertTrue(controller.outdentFormattedListItem(id, listOf(1)))
        assertEquals(expected, controller.text)
        assertEquals(4, controller.semanticDocument().blocks.size)
        assertEquals(MarkdownBlockKind.PARAGRAPH, controller.semanticDocument().blocks[1].kind)
        assertEquals(MarkdownBlockKind.BULLET_LIST, controller.semanticDocument().blocks[2].kind)
        assertEquals(androidx.compose.ui.text.TextRange(3), controller.formattedSelection)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertEquals(listOf(1), controller.activeFormattedListPath)
        assertTrue(controller.redo())
        assertEquals(expected, controller.text)
    }

    @Test fun liftsRootOrderedItemWithChildAndKeepsCrLfAndNeighborMarkers() {
        val original = "7) first\r\n8) parent\r\n   1) child\r\n9) last"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.outdentFormattedListItem(id, listOf(1)))
        assertEquals("7) first\r\n\r\nparent\r\n1) child\r\n\r\n9) last", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun rootLiftRefusesRawOrUnderindentedContentWithoutCreatingUndoEntry() {
        listOf(
            "- parent\n\n  ```text\n  code\n  ```\n- last",
            "- parent\ncontinuation\n  - child\n- last",
        ).forEach { original ->
            val controller = MarkdownEditorController(original)
            val id = controller.semanticDocument().blocks.single().id
            assertFalse(controller.outdentFormattedListItem(id, listOf(0)))
            assertEquals(original, controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun editsBulletItemWithoutRewritingMarkerNestedContentOrNeighbors() {
        val original = "before\r\n\r\n+  **first**\r\n  continuation\r\n+ second\r\n\r\nafter"
        val controller = MarkdownEditorController(original)
        val block = controller.semanticDocument().blocks.first { it.kind == MarkdownBlockKind.BULLET_LIST }
        val list = MarkdownSourceList.parse(block)!!
        assertEquals(2, list.items.size)
        assertEquals("**first**", list.content(0))
        assertTrue(controller.replaceFormattedListItemText(block.id, 0, "first!"))
        assertEquals(original.replace("**first**", "**first!**"), controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertTrue(controller.redo())
        assertEquals(original.replace("**first**", "**first!**"), controller.text)
    }

    @Test fun editsOrderedItemAndPreservesNumbersIndentAndOtherLines() {
        val original = "p\n\n7) apple\n8) banana\n   - nested\n\nend"
        val controller = MarkdownEditorController(original)
        val block = controller.semanticDocument().blocks.first { it.kind == MarkdownBlockKind.ORDERED_LIST }
        assertEquals(listOf("7)", "8)"), MarkdownSourceList.parse(block)!!.items.map { it.marker })
        assertTrue(controller.replaceFormattedListItemText(block.id, 1, "banana ripe"))
        assertEquals(original.replace("8) banana", "8) banana ripe"), controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun togglesOnlyRequestedTaskAndRetainsUnrelatedMarkerCase() {
        val original = "- [X] done\n- [ ] pending\n- [x] also done"
        val controller = MarkdownEditorController(original)
        val block = controller.semanticDocument().blocks.single()
        val list = MarkdownSourceList.parse(block)!!
        assertEquals(listOf(true, false, true), list.items.map { it.checked })
        assertTrue(controller.setFormattedTaskChecked(block.id, 1, true))
        assertEquals("- [X] done\n- [x] pending\n- [x] also done", controller.text)
        assertFalse(controller.setFormattedTaskChecked(block.id, 1, true))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertTrue(controller.setFormattedTaskChecked(block.id, 0, false))
        assertEquals("- [ ] done\n- [ ] pending\n- [x] also done", controller.text)
    }

    @Test fun rejectsMultilineTextAndNonTaskToggle() {
        val controller = MarkdownEditorController("- alpha\n- beta")
        val id = controller.semanticDocument().blocks.single().id
        assertFalse(controller.replaceFormattedListItemText(id, 0, "alpha\nother"))
        assertFalse(controller.setFormattedTaskChecked(id, 0, true))
        assertEquals("- alpha\n- beta", controller.text)
    }

    @Test fun editsNestedItemsAndContinuationLinesWithoutChangingUntouchedSource() {
        val original = "- parent\r\n  continuation\r\n  - **child**\r\n    child continued\r\n  - [ ] task child\r\n- sibling"
        val controller = MarkdownEditorController(original)
        val blockId = controller.semanticDocument().blocks.single().id
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals(2, list.items.size)
        assertEquals("continuation", list.lineContent(listOf(0), 1))
        assertEquals("**child**", list.lineContent(listOf(0, 0), 0))
        assertEquals("child continued", list.lineContent(listOf(0, 0), 1))
        assertEquals("task child", list.lineContent(listOf(0, 1), 0))

        assertTrue(controller.replaceFormattedListLineText(blockId, listOf(0, 0), 0, "child!"))
        assertEquals(original.replace("**child**", "**child!**"), controller.text)
        assertTrue(controller.replaceFormattedListLineText(blockId, listOf(0, 0), 1, "child continues"))
        assertEquals(original.replace("**child**", "**child!**").replace("child continued", "child continues"), controller.text)
        assertTrue(controller.replaceFormattedListLineText(blockId, listOf(0), 1, "parent continues"))
        assertTrue(controller.setFormattedTaskChecked(blockId, listOf(0, 1), true))
        val final = original.replace("continuation", "parent continues")
            .replace("**child**", "**child!**")
            .replace("child continued", "child continues")
            .replace("- [ ] task child", "- [x] task child")
        assertEquals(final, controller.text)
        repeat(4) { assertTrue(controller.undo()) }
        assertEquals(original, controller.text)
        repeat(4) { assertTrue(controller.redo()) }
        assertEquals(final, controller.text)
    }

    @Test fun nestedListInsideFencedCodeIsNotEditableAsListItem() {
        val original = "- parent\n\n  ```text\n  - literal code\n  ```\n\n  - child"
        val controller = MarkdownEditorController(original)
        val blockId = controller.semanticDocument().blocks.single().id
        val list = MarkdownSourceList.parse(controller.semanticDocument().blocks.single())!!
        assertEquals("child", list.lineContent(listOf(0, 0), 0))
        assertEquals(null, list.lineContent(listOf(0, 1), 0))
        assertTrue(list.items.first().parts.filterIsInstance<MarkdownSourceList.Raw>().any {
            list.rawContent(it).contains("- literal code")
        })
        assertEquals(original, controller.text)
    }
}
