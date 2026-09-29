package com.jackcaow.smoothmarkdown

import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTest {
    @Test fun inlineMathCoexistsWithTextAndMultipleFormulas() {
        val paragraph = parseMarkdown("Einstein: \$E=mc^2\$ and \$x + y\$ done").firstChild as Paragraph
        val children = paragraph.children()
        assertEquals(listOf("E=mc^2", "x + y"), children.filterIsInstance<InlineMathNode>().map { it.latex })
        assertEquals(2, inlineRender(paragraph, false).math.size)
    }

    @Test fun escapedAndPartialDelimitersStayLiteral() {
        val paragraph = parseMarkdown("Price \\\$5 and \$unfinished").firstChild as Paragraph
        assertTrue(paragraph.children().none { it is InlineMathNode })
        assertEquals("Price \$5 and \$unfinished", inlineText(paragraph, false).text)
        assertTrue(parseMarkdown("\$\$").firstChild is BlockMathNode)
    }

    @Test fun singleLineAndMultilineBlocksIncludeUnclosedInput() {
        val document = parseMarkdown("Before\n\$\$E=mc^2\$\$\nAfter\n\$\$\n\\frac{a}{b}\n\$\$\n\$\$unclosed")
        val nodes = document.children()
        assertTrue(nodes[0] is Paragraph)
        assertEquals("E=mc^2", (nodes[1] as BlockMathNode).latex)
        assertTrue(nodes[2] is Paragraph)
        assertEquals("\\frac{a}{b}", (nodes[3] as BlockMathNode).latex)
        assertEquals("unclosed", (nodes[4] as BlockMathNode).latex)
    }

    @Test fun emptyBlockAndDoubleDollarInsideParagraph() {
        assertEquals("", (parseMarkdown("\$\$\n\$\$").firstChild as BlockMathNode).latex)
        val paragraph = parseMarkdown("a \$\$ b").firstChild as Paragraph
        assertTrue(paragraph.children().none { it is InlineMathNode })
    }

    private fun org.commonmark.node.Node.children(): List<org.commonmark.node.Node> = buildList {
        var child = firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }
}
