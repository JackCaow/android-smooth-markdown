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

    @Test fun selfLoopsHaveVisibleRoutesAndLabelsOutsideNodesInEveryDirection() {
        val source = """
            stateDiagram-v2
            [*] --> Idle
            [*] --> Waiting
            Idle --> Idle: RETRY
            Waiting --> Waiting: WAIT
            Idle --> Done
            Waiting --> Done
            Done --> [*]
        """.trimIndent()
        listOf("TB", "BT", "LR", "RL").forEach { direction ->
            val graph = MermaidParser.parse(source.replaceFirst("\n", "\ndirection $direction\n"))!!
            val layout = MermaidLayout.compute(graph)
            val loops = layout.edges.filter { it.edge.from == it.edge.to }
            assertEquals(direction, 2, loops.size)
            assertFalse(direction, loops[0].labelBounds!!.overlaps(loops[1].labelBounds!!))
            loops.forEach { loop ->
                val box = layout.nodes.getValue(loop.edge.from)
                val label = requireNotNull(loop.labelBounds)
                assertNotNull(loop.curveControls)
                assertTrue(direction, loop.start != loop.end)
                assertTrue(direction, label.x >= 0 && label.y >= 0)
                assertTrue(direction, label.x + label.width <= layout.width)
                assertTrue(direction, label.y + label.height <= layout.height)
                assertFalse(direction, label.overlaps(box))
                layout.nodes.values.filter { it != box }.forEach { assertFalse(direction, label.overlaps(it)) }
                if (direction == "TB" || direction == "BT") {
                    assertTrue(direction, loop.start.x == box.x + box.width)
                    assertTrue(direction, label.x > box.x + box.width)
                } else {
                    assertTrue(direction, loop.start.y == box.y)
                    assertTrue(direction, label.y + label.height < box.y)
                }
            }
        }
    }

    @Test fun longChineseSelfLoopLabelReservesCrossAxisLane() {
        val source = """
            stateDiagram-v2
            A --> A: 等待支付结果并重新检查状态
            B --> B: 重试并继续等待
            A --> B
        """.trimIndent()
        listOf("TB", "BT", "LR", "RL").forEach { direction ->
            val graph = MermaidParser.parse(source.replaceFirst("\n", "\ndirection $direction\n"))!!
            val layout = MermaidLayout.compute(graph)
            val loops = layout.edges.filter { it.edge.from == it.edge.to }
            assertFalse(direction, loops[0].labelBounds!!.overlaps(loops[1].labelBounds!!))
            loops.forEach { loop ->
                val label = requireNotNull(loop.labelBounds)
                assertTrue(direction, label.x + label.width <= layout.width)
                assertTrue(direction, label.y + label.height <= layout.height)
                layout.nodes.values.forEach { assertFalse(direction, label.overlaps(it)) }
            }
        }
    }

    @Test fun unlabeledSelfLoopStillFitsInsideCanvas() {
        val graph = MermaidParser.parse("stateDiagram-v2\nIdle --> Idle")!!
        val layout = MermaidLayout.compute(graph)
        val loop = layout.edges.single()
        val rightmost = loop.curveControls!!.let { maxOf(it.first.x, it.second.x) }
        assertTrue(rightmost < layout.width)
        assertNull(loop.labelBounds)
    }

    private fun MermaidRect.overlaps(other: MermaidRect): Boolean =
        x < other.x + other.width && x + width > other.x &&
            y < other.y + other.height && y + height > other.y

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
