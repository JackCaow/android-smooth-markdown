package com.jackcaow.smoothmarkdown

import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlKbdTest {
    @Test fun inlineKeyCapsKeepTheCompleteVisibleTextAndSelectionOffsets() {
        val paragraph = parseMarkdown("Press <kbd>Ctrl</kbd>+<kbd>C</kbd> now.", enableHtml = true).firstChild as Paragraph
        val keys = paragraph.children().filterIsInstance<HtmlKbdNode>()
        assertEquals(listOf("Ctrl", "C"), keys.map { it.label })
        val rendered = inlineRender(paragraph, enableHtml = true)
        assertEquals(listOf("Ctrl", "C"), rendered.kbds.values.toList())
        assertEquals("Press Ctrl+C now.", rendered.text.text)
        assertEquals("Ctrl+C", rendered.text.subSequence(6, 12).text)
        assertEquals("Ctrl+C", visibleSelectedText(listOf(rendered.text.subSequence(6, 12)), emptySet()).text)
    }

    @Test fun nestedFormattingAndUnclosedKeyCapsRetainPlainKeyText() {
        val nested = parseMarkdown("<kbd><b>Ctrl</b> + <kbd>K</kbd></kbd> next", enableHtml = true).firstChild as Paragraph
        val rendered = inlineRender(nested, enableHtml = true)
        assertEquals(listOf("Ctrl + K"), rendered.kbds.values.toList())
        assertEquals("Ctrl + K next", rendered.text.text)

        val unclosed = parseMarkdown("Press <kbd>Escape", enableHtml = true).firstChild as Paragraph
        assertEquals("Press Escape", inlineRender(unclosed, enableHtml = true).text.text)
    }

    @Test fun htmlModeIsIsolatedFromLiteralSourceAndCache() {
        val source = "Use <kbd>Enter</kbd>"
        val plain = parseMarkdown(source).firstChild as Paragraph
        val html = parseMarkdown(source, enableHtml = true).firstChild as Paragraph
        assertEquals("Use <kbd>Enter</kbd>", inlineRender(plain, enableHtml = false).text.text)
        assertEquals("Use Enter", inlineRender(html, enableHtml = true).text.text)
        assertTrue(inlineRender(plain, enableHtml = false).kbds.isEmpty())
        assertSame(html, parseMarkdown(source, enableHtml = true).firstChild)
    }

    private fun org.commonmark.node.Node.children(): List<org.commonmark.node.Node> = buildList {
        var child = firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }
}
