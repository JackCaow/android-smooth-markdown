package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedTableTest {
    @Test fun blockScopedCellEditPreservesSurroundingMarkdownAndUndo() {
        val original = "Before\n\n| Name | Note |\n| :--- | ---: |\n| Ada | **bold** |\n\nAfter"
        val controller = MarkdownEditorController(original)
        val id = controller.semanticDocument().blocks.first { it.kind == MarkdownBlockKind.TABLE }.id
        assertEquals(MarkdownTableAlignment.LEFT, controller.semanticTable(id)?.alignments?.first())
        assertTrue(controller.editSemanticTable(id) { it.replaceCell(0, 0, "Ada | Lovelace") })
        assertTrue(controller.text.contains("Ada \\| Lovelace | **bold**"))
        assertTrue(controller.text.startsWith("Before\n\n"))
        assertTrue(controller.text.endsWith("\n\nAfter"))
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
    }

    @Test fun rowAndColumnChangesKeepTableParseable() {
        val controller = MarkdownEditorController("| A | B |\n| --- | --- |\n| x | y |")
        val id = controller.semanticDocument().blocks.single().id
        assertTrue(controller.editSemanticTable(id) { it.insertRowAfter(0) })
        assertTrue(controller.editSemanticTable(id) { it.insertColumnAfter(1) })
        assertEquals(2, controller.semanticTable(id)?.rows?.size)
        assertEquals(3, controller.semanticTable(id)?.columnCount)
        assertFalse(controller.editSemanticTable(id) { it })
    }
}
