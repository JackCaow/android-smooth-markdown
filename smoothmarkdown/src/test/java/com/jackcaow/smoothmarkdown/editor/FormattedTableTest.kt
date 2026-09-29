package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedTableTest {
    @Test fun singleCellEditKeepsComplexDocumentSourceSelectionAndHistoryAcrossModes() {
        val original = "Intro\r\n\r\n- [x] parent\r\n  - nested **keep**\r\n\r\n" +
            "> quoted `keep`\r\n\r\n" +
            "| Feature  | Status |\r\n|:---------|-------:|\r\n" +
            "| Raw  | **Done** |\r\n| Next  | Later |\r\n\r\nTail"
        val controller = MarkdownEditorController(original)
        assertEquals(
            listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.BULLET_LIST,
                MarkdownBlockKind.QUOTE, MarkdownBlockKind.TABLE, MarkdownBlockKind.PARAGRAPH),
            controller.semanticDocument().blocks.map { it.kind },
        )
        val tableId = controller.semanticDocument().blocks.first { it.kind == MarkdownBlockKind.TABLE }.id
        val quotedSelection = original.indexOf("quoted")
        controller.setSelection(quotedSelection, quotedSelection + "quoted".length)
        controller.mode = MarkdownEditorMode.FORMATTED

        assertTrue(controller.editSemanticTable(tableId) { it.replaceCell(0, 0, "Revised") })
        val edited = original.replace("| Raw  |", "| Revised  |")
        assertEquals(edited, controller.text)
        assertEquals(quotedSelection, controller.selection.start)
        assertEquals(quotedSelection + "quoted".length, controller.selection.end)
        assertEquals("| Feature  | Status |\r\n|:---------|-------:|", controller.text.substring(
            controller.text.indexOf("| Feature"), controller.text.indexOf("| Revised"),
        ).trimEnd('\r', '\n'))

        controller.mode = MarkdownEditorMode.PREVIEW
        assertEquals(edited, controller.text)
        assertTrue(controller.undo())
        controller.mode = MarkdownEditorMode.SOURCE
        assertEquals(original, controller.text)
        assertEquals(quotedSelection, controller.selection.start)
        assertTrue(controller.redo())
        controller.mode = MarkdownEditorMode.FORMATTED
        assertEquals(edited, controller.text)
    }

    @Test fun sourceTableCellEditPreservesEscapedPipesWhitespaceAndMovesCaretWithPatch() {
        val original = "Before\n\n| Name \\| Alias  | Value |\n|:--------------|------:|\n" +
            "| Ada  | 1 |\n| Bob  | 2 |\n\nAfter"
        val controller = MarkdownEditorController(original)
        val caret = original.indexOf("Bob")
        controller.setSelection(caret)
        assertTrue(controller.replaceTableCellText(0, 0, "Ada Lovelace"))
        assertEquals(original.replace("| Ada  |", "| Ada Lovelace  |"), controller.text)
        assertEquals(caret + " Lovelace".length, controller.selection.start)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertEquals(caret, controller.selection.start)
    }

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
