package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSourceListTest {
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
