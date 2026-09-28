package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun mixedDocumentRoundTripsWithOriginalSpacingAndCrLf() {
        val source = "# Title\r\n\r\n😀 paragraph\r\n\r\n- one\r\n- two\r\n\r\n```kotlin\r\nval x = 1\r\n```\r\n\r\n<div>raw</div>\r\n"
        val document = MarkdownDocumentCodec.parse(source)
        assertEquals(source, document.toMarkdown())
        assertEquals(
            listOf(MarkdownBlockKind.HEADING, MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.CODE, MarkdownBlockKind.RAW),
            document.blocks.map { it.kind },
        )
        assertEquals(1, document.blocks.first().headingLevel)
        assertEquals("kotlin", document.blocks[3].language)
        assertEquals(TextRange(source.indexOf("😀"), source.indexOf("😀") + "😀 paragraph".length), document.blocks[1].range)
        document.blocks.forEach { assertEquals(it.source, source.substring(it.range.min, it.range.max)) }
    }

    @Test fun recognizesTableImageAndRuleWithoutNormalizingTheirSource() {
        val source = "![alt](photo.png)\n\n| A | B |\n| --- | ---: |\n| 1 | 2 |\n\n---"
        val document = MarkdownDocumentCodec.parse(source)
        assertEquals(listOf(MarkdownBlockKind.IMAGE, MarkdownBlockKind.TABLE, MarkdownBlockKind.RULE), document.blocks.map { it.kind })
        assertEquals(source, document.toMarkdown())
        assertEquals("| A | B |\n| --- | ---: |\n| 1 | 2 |", document.blocks[1].source)
    }

    @Test fun rejectsSetextLevelRewriteWithoutChangingDocument() {
        val source = "Title\n=====\n\nnext"
        val editor = MarkdownDocumentEditor(source)
        assertEquals(MarkdownBlockKind.HEADING, editor.document.blocks.first().kind)
        assertFalse(editor.setHeadingLevel(editor.document.blocks.first().id, 2))
        assertEquals(source, editor.document.toMarkdown())
        assertFalse(editor.canUndo)
    }

    @Test fun editingHeadingPreservesEveryOtherSourceCharacterAndHistory() {
        val source = "before\n\n## Title  \n\nafter\n"
        val editor = MarkdownDocumentEditor(source)
        val heading = editor.document.blocks[1]
        assertTrue(editor.setHeadingLevel(heading.id, 3))
        assertEquals("before\n\n### Title  \n\nafter\n", editor.document.toMarkdown())
        assertEquals(heading.id, editor.document.blocks[1].id)
        assertTrue(editor.undo())
        assertEquals(source, editor.document.toMarkdown())
        assertTrue(editor.redo())
        assertEquals("before\n\n### Title  \n\nafter\n", editor.document.toMarkdown())
    }

    @Test fun replacementRejectsMultipleBlocksAndControllerKeepsUndo() {
        val controller = MarkdownEditorController("before\n\n# Title\n\nafter")
        val headingId = controller.semanticDocument().blocks[1].id
        assertFalse(controller.replaceSemanticBlock(headingId, "# First\n\nSecond"))
        assertEquals("before\n\n# Title\n\nafter", controller.text)
        assertTrue(controller.replaceSemanticBlock(headingId, "## New"))
        assertEquals("before\n\n## New\n\nafter", controller.text)
        assertTrue(controller.undo())
        assertEquals("before\n\n# Title\n\nafter", controller.text)
    }
}
