package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidSubgraphTest {
    private fun overlaps(a: MermaidRect, b: MermaidRect): Boolean =
        a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

    private fun contains(outer: MermaidRect, inner: MermaidRect): Boolean =
        inner.x >= outer.x && inner.y >= outer.y &&
            inner.x + inner.width <= outer.x + outer.width && inner.y + inner.height <= outer.y + outer.height

    @Test fun issue46FixtureRendersSubgraphBesideUnrelatedNodes() {
        val source = """
            graph LR
                %% 节点定义
                A[矩形] --> B(圆角矩形)
                B --> C{菱形}
                C -->|条件| D[(圆柱形)]
                %% 连接样式
                E==>|粗线|F
                F-.->|虚线|G
                %% 方向控制
                subgraph 子图
                    H[内部节点]
                end
        """.trimIndent()
        val diagram = MermaidParser.parse(source)!!
        val group = diagram.subgraphs.single()
        assertEquals("子图", group.label)
        assertEquals(listOf("H"), group.nodeIds)
        val layout = MermaidLayout.compute(diagram)
        val box = layout.subgraphs.getValue(group.id)
        assertTrue(contains(box, layout.nodes.getValue("H")))
        diagram.nodes.filter { it.id != "H" }.forEach { assertFalse(overlaps(box, layout.nodes.getValue(it.id))) }
        assertTrue(box.x + box.width <= layout.width)
        assertTrue(box.y + box.height <= layout.height)
    }

    @Test fun nestedGroupsHaveDistinctBoundsAndGroupEndpointUsesBorder() {
        val source = """
            flowchart LR
            subgraph outer [Outer]
              A[First]
              subgraph inner [Inner]
                B[Second]
              end
            end
            outer --> C[After]
        """.trimIndent()
        val diagram = MermaidParser.parse(source)!!
        assertEquals("outer", diagram.subgraphs.first { it.id == "inner" }.parentId)
        assertEquals(listOf("A"), diagram.subgraphs.first { it.id == "outer" }.directNodeIds)
        assertNull(diagram.node("outer"))
        val layout = MermaidLayout.compute(diagram)
        val outer = layout.subgraphs.getValue("outer")
        val inner = layout.subgraphs.getValue("inner")
        assertTrue(contains(outer, inner))
        assertTrue(contains(inner, layout.nodes.getValue("B")))
        assertFalse(overlaps(outer, layout.nodes.getValue("C")))
        val edge = layout.edges.single()
        assertTrue(edge.edge.subgraphEdge)
        assertEquals(outer.x + outer.width, edge.start.x)
        assertEquals(layout.nodes.getValue("C").x, edge.end.x)
        assertTrue(diagram.accessibilitySummary().contains("Subgraphs: Inner, 1 nodes, inside Outer"))
    }

    @Test fun malformedUnclosedAndStrayEndFallBack() {
        assertNull(MermaidParser.parse("flowchart TB\nsubgraph A\nX[inside]"))
        assertNull(MermaidParser.parse("flowchart TB\nX[inside]\nend"))
        assertNull(MermaidParser.parse("flowchart TB\nsubgraph\nX[inside]\nend"))
        assertNotNull(MermaidParser.parse("flowchart TB\nsubgraph A\nX[inside]\nend"))
    }

    @Test fun incomingEdgeCanTargetNamedAndUnicodeSubgraph() {
        val diagram = MermaidParser.parse("""
            graph LR
            A[Start] --> 子图
            subgraph 子图
              B[Inside]
            end
        """.trimIndent())!!
        assertNull(diagram.node("子图"))
        val layout = MermaidLayout.compute(diagram)
        val target = layout.subgraphs.getValue("子图")
        assertEquals(target.x, layout.edges.single().end.x)
        assertTrue(layout.edges.single().edge.subgraphEdge)
    }
}
