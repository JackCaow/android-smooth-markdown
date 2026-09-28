package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.style.BaselineShift
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootnotesTest {
    @Test fun parsesMultipleNumericAndNamedReferencesAsSuperscripts() {
        val paragraph = parseMarkdown("Text[^1] and[^note] end").firstChild as Paragraph
        val references = paragraph.children().filterIsInstance<FootnoteReferenceNode>().toList()
        assertEquals(listOf("1", "note"), references.map { it.label })

        val styled = inlineText(paragraph, enableHtml = false)
        assertEquals("Text[1] and[note] end", styled.text)
        assertEquals(2, styled.spanStyles.count { it.item.baselineShift == BaselineShift.Superscript })
    }

    @Test fun parsesDefinitionWithFormattingAndIndentedContinuation() {
        val document = parseMarkdown("[^note]: First **bold** line\n    Second line\n\n    Third line\nAfter")
        val definition = document.firstChild as FootnoteDefinitionNode
        assertEquals("note", definition.label)
        val styled = inlineText(definition, enableHtml = false)
        assertTrue(styled.text.startsWith("First bold line"))
        assertTrue(styled.text.contains("Second line"))
        assertTrue(styled.text.contains("Third line"))
        assertTrue(styled.spanStyles.any { it.item.fontWeight != null && styled.text.substring(it.start, it.end) == "bold" })
        assertTrue(definition.next is Paragraph)
    }

    @Test fun malformedReferencesAndCodeStayLiteral() {
        val paragraph = parseMarkdown("[^] [^open `[^code]`").firstChild as Paragraph
        assertFalse(paragraph.children().any { it is FootnoteReferenceNode })
        assertTrue(paragraph.children().any { it is Code })
        assertEquals("[^] [^open [^code]", inlineText(paragraph, enableHtml = false).text)
    }

    @Test fun escapedOpeningBracketStaysLiteral() {
        val paragraph = parseMarkdown("\\[^escaped] and [^real]").firstChild as Paragraph
        assertEquals(listOf("real"), paragraph.children().filterIsInstance<FootnoteReferenceNode>().map { it.label }.toList())
        assertEquals("[^escaped] and [real]", inlineText(paragraph, enableHtml = false).text)
    }

    @Test fun escapedReferenceInsideDefinitionStaysLiteral() {
        val definition = parseMarkdown("[^note]: \\[^same] then [^same] and \\[^other] then [^other]").firstChild as FootnoteDefinitionNode
        assertEquals(listOf("same", "other"), definition.children().filterIsInstance<FootnoteReferenceNode>().map { it.label }.toList())
        assertEquals("[^same] then [same] and [^other] then [other]", inlineText(definition, enableHtml = false).text)
    }

    @Test fun emptyDefinitionIsNotClaimedAsFootnote() {
        assertTrue(parseMarkdown("[^a]: ").firstChild is Paragraph)
    }

    @Test fun fencedCodeDoesNotCreateFootnoteNodes() {
        val code = parseMarkdown("```markdown\n[^a]: note\n[^a]\n```").firstChild as FencedCodeBlock
        assertTrue(code.literal.contains("[^a]: note"))
        assertTrue(code.firstChild == null)
    }

    private fun org.commonmark.node.Node.children(): Sequence<org.commonmark.node.Node> = sequence {
        var child = firstChild
        while (child != null) {
            yield(child)
            child = child.next
        }
    }
}
