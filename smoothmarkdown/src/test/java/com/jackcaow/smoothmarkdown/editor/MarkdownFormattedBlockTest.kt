package com.jackcaow.smoothmarkdown.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFormattedBlockTest {
    @Test fun formattedHeadingKeepsMarkersSpacingAndUndo() {
        val source = "before\n\n## Old ##\n\nafter"
        val controller = MarkdownEditorController(source)
        val block = controller.semanticDocument().blocks[1]
        assertEquals("Old", MarkdownFormattedBlock.text(block))
        assertTrue(controller.replaceFormattedBlockText(block.id, "New"))
        assertEquals("before\n\n## New ##\n\nafter", controller.text)
        assertTrue(controller.setSemanticHeadingLevel(block.id, 3))
        assertEquals("before\n\n### New ##\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals("before\n\n## New ##\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
        assertTrue(controller.redo())
        assertEquals("before\n\n## New ##\n\nafter", controller.text)
    }

    @Test fun paragraphTextEditRetainsSurroundingCrLfAndUtf16() {
        val source = "# Header\r\n\r\n😀 old\r\n\r\nend"
        val controller = MarkdownEditorController(source)
        val block = controller.semanticDocument().blocks[1]
        assertEquals("😀 old", MarkdownFormattedBlock.text(block))
        assertTrue(controller.replaceFormattedBlockText(block.id, "😀 new"))
        assertEquals("# Header\r\n\r\n😀 new\r\n\r\nend", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
    }

    @Test fun fencedCodeEditPreservesFenceLanguageAndCrLf() {
        val source = "before\r\n\r\n```kotlin\r\nval old = 1\r\n```\r\n\r\nafter"
        val controller = MarkdownEditorController(source)
        val block = controller.semanticDocument().blocks[1]
        assertEquals("val old = 1", MarkdownFormattedBlock.text(block))
        assertTrue(controller.replaceFormattedBlockText(block.id, "val new = 2"))
        assertEquals("before\r\n\r\n```kotlin\r\nval new = 2\r\n```\r\n\r\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals(source, controller.text)
    }

    @Test fun unsupportedAndInvalidBlockEditsLeaveSourceUntouched() {
        val source = "- one\n- two\n\nparagraph"
        val controller = MarkdownEditorController(source)
        val list = controller.semanticDocument().blocks.first()
        assertEquals(null, MarkdownFormattedBlock.text(list))
        assertFalse(controller.replaceFormattedBlockText(list.id, "change"))
        val paragraph = controller.semanticDocument().blocks.last()
        assertFalse(controller.replaceFormattedBlockText(paragraph.id, "first\n\nsecond"))
        assertEquals(source, controller.text)
    }
}
