package com.jackcaow.smoothmarkdown

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidPluginIntegrationTest {
    private val plugins = ParserPluginRegistry().also { it.register(MermaidPlugin()) }

    @Test fun defaultReaderKeepsFencedMermaidAsCode() {
        assertTrue(parseMarkdown("```mermaid\ngraph LR\nA --> B\n```").firstChild is FencedCodeBlock)
    }

    @Test fun flowchartFenceBecomesPluginNodeAndKeepsFollowingMarkdown() {
        val document = parseMarkdown("```mermaid\ngraph LR\nA --> B\n```\n\nAfter", plugins)
        val diagram = document.firstChild as MermaidDiagramNode
        assertEquals("graph LR\nA --> B", diagram.code)
        assertEquals("```", diagram.fence)
        assertTrue(diagram.next is Paragraph)
    }

    @Test fun tildeSequenceFencePreservesThemeInfo() {
        val markdown = "~~~mermaid theme=dark\nsequenceDiagram\nAlice->>Bob: Hi\n~~~"
        val diagram = parseMarkdown(markdown, plugins).firstChild as MermaidDiagramNode
        assertEquals("dark", diagram.theme)
        assertEquals("~~~", diagram.fence)
        assertEquals("mermaid theme=dark", diagram.info)
        assertEquals("sequenceDiagram\nAlice->>Bob: Hi", diagram.code)
    }

    @Test fun unsupportedOrEmptyMermaidFallsBackToCode() {
        assertTrue(parseMarkdown("```mermaid\npie\n  \"A\": 5\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```mermaid\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```kotlin\ngraph LR\nA --> B\n```", plugins).firstChild is FencedCodeBlock)
    }

    @Test fun nestedFenceInQuoteIsTransformed() {
        val document = parseMarkdown("> ```mermaid\n> graph TB\n> A --> B\n> ```", plugins)
        assertTrue(document.firstChild.firstChild is MermaidDiagramNode)
    }
}
