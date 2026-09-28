package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.*
import org.junit.Test

class MermaidStructuredTest {
    @Test fun flutterClassFixturePreservesMembersAndMarkers() {
        val graph = MermaidParser.parse("""
            classDiagram
            Animal <|-- Duck
            Animal : +int age
            Animal : +isMammal() bool
            class Duck {
              +String beakColor
              +swim()
              +quack()
            }
            Pond o-- Duck : contains
            Duck ..> Food : eats
        """.trimIndent())!!
        assertEquals(MermaidKind.ClassDiagram, graph.kind)
        assertEquals(listOf(listOf("+String beakColor"), listOf("+swim()", "+quack()")), graph.node("Duck")!!.compartments)
        assertEquals(MermaidEdgeMarker.Inheritance, graph.edges[0].sourceMarker)
        assertEquals(MermaidEdgeMarker.Aggregation, graph.edges[1].sourceMarker)
        assertEquals(MermaidLine.Dotted, graph.edges[2].line)
        assertEquals("eats", graph.edges[2].label)
        val layout = MermaidLayout.compute(graph)
        assertTrue(layout.nodes.getValue("Duck").height > 100f)
        assertTrue(layout.width > 0f && layout.height > 0f)
    }

    @Test fun chineseStateFixtureKeepsTerminalsSeparate() {
        val graph = MermaidParser.parse("""
            stateDiagram-v2
            [*] --> 待支付
            待支付 --> 已支付: 支付成功
            已支付 --> 已发货: 发货
            已发货 --> 已完成: 确认收货
            已完成 --> [*]
            待支付 --> 已取消: 超时/取消
            已取消 --> [*]
        """.trimIndent())!!
        assertEquals(MermaidKind.StateDiagram, graph.kind)
        assertEquals(7, graph.nodes.size)
        assertEquals(7, graph.edges.size)
        assertEquals(MermaidShape.StateStart, graph.node("\$state:start")?.shape)
        assertEquals(MermaidShape.StateEnd, graph.node("\$state:end")?.shape)
        assertEquals("超时/取消", graph.edges[5].label)
        assertTrue(MermaidLayout.compute(graph).nodes.values.all { it.width > 0f })
    }

    @Test fun aliasesDirectionsChoiceAndMultiplicity() {
        val state = MermaidParser.parse("""
            stateDiagram
            direction LR
            state "Waiting for payment" as pending
            state choice <<choice>>
            [*] --> pending
            pending --> choice
            choice --> done
            done : Completed
        """.trimIndent())!!
        assertEquals(MermaidDirection.LR, state.direction)
        assertEquals("Waiting for payment", state.node("pending")?.label)
        assertEquals(MermaidShape.Diamond, state.node("choice")?.shape)
        assertEquals("Completed", state.node("done")?.label)
        val klass = MermaidParser.parse("""
            classDiagram
            Animal "1" <-- "many" Food : feeds
        """.trimIndent())!!
        assertEquals("Food", klass.edges.single().from)
        assertEquals("many", klass.edges.single().sourceLabel)
        assertEquals("1", klass.edges.single().targetLabel)
    }

    @Test fun unsupportedStatementsFallBackWithoutPartialDiagram() {
        listOf(
            "stateDiagram-v2\nA --> B\nstate composite {",
            "classDiagram\nclass A {\n+int x",
            "classDiagram\nA --> B\nunsupported token",
            "classDiagram",
            "stateDiagram-v2\nstate A {\nB\n}",
        ).forEach { assertNull(it, MermaidParser.parse(it)) }
    }
}
