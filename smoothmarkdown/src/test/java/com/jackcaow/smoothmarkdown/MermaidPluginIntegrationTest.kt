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
        assertTrue(parseMarkdown("```mermaid\ngantt\n  title Roadmap\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```mermaid\npie\n  title Empty\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```mermaid\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```kotlin\ngraph LR\nA --> B\n```", plugins).firstChild is FencedCodeBlock)
    }

    @Test fun pieAndTimelineFencesBecomePluginNodes() {
        val pie = parseMarkdown("```mermaid\npie showData\n  \"A\": 30\n  \"B\": 70\n```", plugins)
            .firstChild as MermaidDiagramNode
        assertEquals("pie showData\n  \"A\": 30\n  \"B\": 70", pie.code)
        val timeline = parseMarkdown("~~~mermaid\ntimeline\n  2024 : Launch\n~~~", plugins)
            .firstChild as MermaidDiagramNode
        assertEquals("~~~", timeline.fence)
    }

    @Test fun ganttAndKanbanFencesBecomePluginNodesOnlyWhenValid() {
        val gantt = parseMarkdown("```mermaid\ngantt\nTask A :a, 2024-01-01, 3d\n```", plugins)
        assertTrue(gantt.firstChild is MermaidDiagramNode)
        val kanban = parseMarkdown("```mermaid\nkanban\n  todo[To Do]\n    task1[Ship]\n```", plugins)
        assertTrue(kanban.firstChild is MermaidDiagramNode)
        assertTrue(parseMarkdown("```mermaid\nkanban\n title Empty\n```", plugins)
            .firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```mermaid\nradar-beta\n axis A\n```", plugins)
            .firstChild is FencedCodeBlock)
    }

    @Test fun radarAndXYChartFencesUseOptInPluginAndInvalidChartsStayCode() {
        val radar = "```mermaid\nradar-beta\naxis A, B, C\ncurve c{1, 2, 3}\n```"
        val xy = "```mermaid\nxychart-beta\nx-axis [A, B]\nbar [10, 20]\n```"
        assertTrue(parseMarkdown(radar).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown(xy).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown(radar, plugins).firstChild is MermaidDiagramNode)
        assertTrue(parseMarkdown(xy, plugins).firstChild is MermaidDiagramNode)
        assertTrue(parseMarkdown("```mermaid\nradar-beta\naxis A, B\n```", plugins)
            .firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("```mermaid\nxychart-beta\nbar [oops]\n```", plugins)
            .firstChild is FencedCodeBlock)
    }

    @Test fun nestedFenceInQuoteIsTransformed() {
        val document = parseMarkdown("> ```mermaid\n> graph TB\n> A --> B\n> ```", plugins)
        assertTrue(document.firstChild.firstChild is MermaidDiagramNode)
    }

    @Test fun classAndStateFencesRequireOptInAndUnsupportedSyntaxStaysCode() {
        val klass = "```mermaid\nclassDiagram\nAnimal <|-- Duck\n```"
        val state = "```mermaid\nstateDiagram-v2\n[*] --> 待支付\n```"
        assertTrue(parseMarkdown(klass).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown(state).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown(klass, plugins).firstChild is MermaidDiagramNode)
        assertTrue(parseMarkdown(state, plugins).firstChild is MermaidDiagramNode)
        assertTrue(parseMarkdown("```mermaid\nclassDiagram\nclass A {\n+int x\n```", plugins)
            .firstChild is FencedCodeBlock)
    }
}
