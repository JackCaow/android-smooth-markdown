package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidParserTest {
    @Test fun chineseFlowchartFixtureMatchesFlutterNodesShapesAndEdges() {
        val diagram = MermaidParser.parse("""
            graph TD
            A[开始] --> B{判断}
            B -->|是| C[处理A]
            B -->|否| D[处理B]
            C --> E[结束]
            D --> E
        """.trimIndent())!!
        assertEquals(MermaidDirection.TB, diagram.direction)
        assertEquals(5, diagram.nodes.size)
        assertEquals(5, diagram.edges.size)
        assertEquals("开始", diagram.node("A")?.label)
        assertEquals(MermaidShape.Diamond, diagram.node("B")?.shape)
        assertEquals("判断", diagram.node("B")?.label)
        assertEquals("是", diagram.edges[1].label)
        assertEquals("否", diagram.edges[2].label)
        assertEquals("结束", diagram.node("E")?.label)
    }

    @Test fun flowShapesChainsStylesAndNestedSubgraphs() {
        val diagram = MermaidParser.parse("""
            flowchart LR
            subgraph outer [Outer]
              A[Rect] --> B(Round) ==> C{{Hex}}
              subgraph inner [Inner]
                D((Circle)) -.-> E[[Subroutine]]
              end
            end
            classDef hot fill:#f9f,stroke:#333,color:white,stroke-width:4px
            class B,C hot
            style A fill:#123456,stroke:blue
            C --> D
        """.trimIndent())!!
        assertEquals(MermaidDirection.LR, diagram.direction)
        assertEquals(5, diagram.nodes.size)
        assertEquals(4, diagram.edges.size)
        assertEquals(MermaidShape.Rounded, diagram.node("B")?.shape)
        assertEquals(MermaidShape.Hexagon, diagram.node("C")?.shape)
        assertEquals(MermaidShape.Circle, diagram.node("D")?.shape)
        assertEquals(MermaidShape.Subroutine, diagram.node("E")?.shape)
        assertEquals(MermaidLine.Thick, diagram.edges[1].line)
        assertEquals(MermaidLine.Dotted, diagram.edges[2].line)
        assertEquals(0xFFFF99FF.toInt(), diagram.node("B")?.style?.fill)
        assertEquals(0xFF123456.toInt(), diagram.node("A")?.style?.fill)
        assertEquals(2, diagram.subgraphs.size)
        assertTrue(diagram.subgraphs.first { it.id == "outer" }.nodeIds.containsAll(listOf("A", "B", "C", "D", "E")))
        assertEquals(listOf("D", "E"), diagram.subgraphs.first { it.id == "inner" }.nodeIds)
    }

    @Test fun flutterFlowchartDottedConnectorWithoutArrowKeepsLabelsAndChain() {
        // Flutter's FlowchartParser accepts `...` as a dotted edge without a tip.
        val diagram = MermaidParser.parse("""
            graph LR
            A[Draft] ...|review| B[Approved] -.-> C[Published]
            B --- D[Archived]
            C === E[Done]
        """.trimIndent())!!
        assertEquals(5, diagram.nodes.size)
        assertEquals(4, diagram.edges.size)
        assertEquals("review", diagram.edges[0].label)
        assertEquals(MermaidLine.Dotted, diagram.edges[0].line)
        assertEquals(MermaidArrow.None, diagram.edges[0].arrow)
        assertEquals(MermaidLine.Dotted, diagram.edges[1].line)
        assertEquals(MermaidArrow.Arrow, diagram.edges[1].arrow)
        assertEquals(MermaidLine.Solid, diagram.edges[2].line)
        assertEquals(MermaidArrow.None, diagram.edges[2].arrow)
        assertEquals(MermaidLine.Thick, diagram.edges[3].line)
        assertEquals(MermaidArrow.None, diagram.edges[3].arrow)
        val layout = MermaidLayout.compute(diagram)
        assertEquals(4, layout.edges.size)
        assertTrue(layout.nodes.getValue("A").x < layout.nodes.getValue("B").x)
        assertEquals("review", layout.edges.first().edge.label)
    }

    @Test fun directionsAndParserReuseDoNotLeakState() {
        val parser = MermaidFlowchartParser()
        val first = parser.parse(listOf("graph BT", "A --> B"))!!
        val second = parser.parse(listOf("graph RL", "X --> Y"))!!
        assertEquals(MermaidDirection.BT, first.direction)
        assertEquals(MermaidDirection.RL, second.direction)
        assertEquals(listOf("X", "Y"), second.nodes.map { it.id })
        assertFalse(second.nodes.any { it.id == "A" })
        assertEquals(MermaidKind.Pie, MermaidParser.parse("pie\n  \"A\": 5")?.kind)
    }

    @Test fun subgraphEndpointUsesGroupBoundsWithoutPhantomNode() {
        val diagram = MermaidParser.parse("""
            graph LR
            subgraph cluster [Cluster]
              A[Inside]
            end
            cluster --> B[Outside]
        """.trimIndent())!!
        assertNull(diagram.node("cluster"))
        assertTrue(diagram.edges.single().subgraphEdge)
        val placement = MermaidLayout.compute(diagram)
        assertNotNull(placement.subgraphs["cluster"])
        assertEquals(1, placement.edges.size)
    }

    @Test fun sequenceParticipantsAliasesAndMessages() {
        val diagram = MermaidParser.parse("""
            sequenceDiagram
            participant A as Alice
            actor B as Bob
            A->>B: Hello
            B-->>A: Hi
            A-xB: Cancel
            B-)A: Async
        """.trimIndent())!!
        assertEquals(MermaidKind.Sequence, diagram.kind)
        assertEquals(listOf("Alice", "Bob"), diagram.nodes.map { it.label })
        assertEquals(MermaidParticipantType.Actor, diagram.node("B")?.participantType)
        assertEquals(4, diagram.edges.size)
        assertEquals(MermaidArrow.Arrow, diagram.edges[0].arrow)
        assertEquals(MermaidLine.Dotted, diagram.edges[1].line)
        assertEquals(MermaidArrow.Cross, diagram.edges[2].arrow)
        assertEquals("Async", diagram.edges[3].label)
    }

    @Test fun layoutFollowsDirectionAndSequencesHaveOrderedRows() {
        val flow = MermaidParser.parse("graph LR\nA --> B --> C")!!
        val placement = MermaidLayout.compute(flow)
        assertTrue(placement.nodes.getValue("A").x < placement.nodes.getValue("B").x)
        assertTrue(placement.nodes.getValue("B").x < placement.nodes.getValue("C").x)
        assertEquals(2, placement.edges.size)
        assertTrue(placement.width > 0)

        val reversed = MermaidLayout.compute(MermaidParser.parse("graph RL\nA --> B")!!)
        assertTrue(reversed.nodes.getValue("A").x > reversed.nodes.getValue("B").x)

        val sequence = MermaidLayout.compute(MermaidParser.parse("sequenceDiagram\nA->>B: one\nB-->>A: two")!!)
        assertNotNull(sequence.nodes["A"])
        assertTrue(sequence.edges[0].start.y < sequence.edges[1].start.y)
    }
}
