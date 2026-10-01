package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTest {
    @Test fun inlineMathCoexistsWithTextAndMultipleFormulas() {
        val paragraph = parseMarkdown("Einstein: \$E=mc^2\$ and \$x + y\$ done").firstChild as Paragraph
        val children = paragraph.children().toList()
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
        val nodes = document.children().toList()
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

    @Test fun backslashDelimitersReachReaderAndCoreProjection() {
        val source = "😀 Math \\(x_1\\) and \$y_2\$"
        val paragraph = parseMarkdown(source).firstChild as Paragraph
        assertEquals(listOf("x_1", "y_2"), paragraph.children().filterIsInstance<InlineMathNode>().map { it.latex }.toList())
        assertEquals(2, inlineRender(paragraph, false).math.size)
        val projected = MarkdownCoreParser().parseAST(source, enableExtensions = true)
        val formulas = projected.children[0].children.filter { it.kind == com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode.Kind.INLINE_MATH }
        assertEquals("\\(x_1\\)", formulas[0].source)
        assertEquals("x_1", formulas[0].literalText)
        assertEquals(8, formulas[0].sourceRange.offset)
        assertTrue(MarkdownCoreParser().parseAST(source).children[0].children.none { it.kind == com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode.Kind.INLINE_MATH })
        assertEquals("x", (parseMarkdown("\\[x\\]").firstChild as BlockMathNode).latex)
        assertEquals("\\frac{x}{y}", (parseMarkdown("\\[\n\\frac{x}{y}\n\\]").firstChild as BlockMathNode).latex)
        assertTrue(!streamingNativeEligible("\\(x\\)", null, false))
        assertTrue(!streamingNativeEligible("\\[x\\]", null, false))
    }
    @Test fun backslashEscapesCodeAndPartialMathStayLiteral() {
        val paragraph = parseMarkdown("`\\(code\\)` \\\\(escaped\\) \\(open").firstChild as Paragraph
        assertTrue(paragraph.children().none { it is InlineMathNode })
    }

    @Test fun backslashDisplayKeepsTrailerAndEscapedBrackets() {
        for (source in listOf("\\[x\\] trailing", "\\[\nx\n\\] trailing")) {
            val nodes = parseMarkdown(source).children().toList()
            assertEquals("x", (nodes[0] as BlockMathNode).latex)
            assertEquals("trailing", inlineText(nodes[1] as Paragraph, false).text.trim())
            val ast = MarkdownCoreParser().parseAST(source, enableExtensions = true)
            assertEquals("x", ast.children[0].literalText)
            assertEquals(" trailing", ast.children[1].source)
            assertEquals(source.indexOf(" trailing"), ast.children[1].sourceRange.offset)
        }
        for (source in listOf("\\[^escaped] and [^real]", "\\[ordinary] text")) {
            assertTrue(parseMarkdown(source).firstChild is Paragraph)
        }
        assertEquals("x[0]", (parseMarkdown("\\[x[0]\\]").firstChild as BlockMathNode).latex)
        assertEquals("x[0]", (parseMarkdown("\\[\nx[0]").firstChild as BlockMathNode).latex)
        assertEquals("x[0]", (parseMarkdown("\\[x[0]").firstChild as BlockMathNode).latex)
        assertEquals("x[0]\n+1", (parseMarkdown("\\[x[0]\n+1\n\\]").firstChild as BlockMathNode).latex)
    }

}
