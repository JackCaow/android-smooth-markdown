package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.*
import org.junit.Test

class MermaidGeometryParityTest {
    @Test fun diamondBranchesAndMergesUseSeparatePortsAndCurves() {
        val diagram = MermaidParser.parse("flowchart TD\nA{判断} -->|是| B[处理 A]\nA -->|否| C[处理 B]\nB --> D[结束]\nC --> D")!!
        val result = MermaidLayout.compute(diagram)
        val branches = result.edges.filter { it.edge.from == "A" }
        val merge = result.edges.filter { it.edge.to == "D" }
        assertNotEquals(branches[0].start, branches[1].start)
        assertNotEquals(merge[0].end, merge[1].end)
        assertTrue(result.edges.all { it.curveControls != null })
        assertTrue(result.edges.all { it.labelBounds == null || inside(it.labelBounds!!, result) })
    }

    @Test fun sequenceSelfMessagesAndLongMeasuredLabelsFitCanvas() {
        val diagram = MermaidParser.parse("sequenceDiagram\nparticipant A\nA->>A: 检查完整内容并重试")!!
        val result = MermaidLayout.compute(diagram, MermaidLayoutMetrics(
            nodeSizes = mapOf("A" to (180f to 72f)),
            labelSizes = mapOf("检查完整内容并重试" to (360f to 42f))))
        val edge = result.edges.single()
        assertNotEquals(edge.start, edge.end)
        assertNotNull(edge.curveControls)
        assertTrue(inside(edge.labelBounds!!, result))
        assertTrue(result.width >= 360f)
        assertEquals(72f, result.nodes.getValue("A").height)
    }

    @Test fun denseErRelationshipsAndChineseLabelsStayWithinBounds() {
        listOf("LR", "TB").forEach { direction ->
            val source = "erDiagram\ndirection $direction\n" +
                (0..8).joinToString("\n") { "A ||--o{ E$it : 包含完整订单关系名称" }
            val result = MermaidLayout.compute(MermaidParser.parse(source)!!)
            result.er!!.relationships.forEach { relationship ->
                relationship.points.forEach { assertTrue(it.x >= 0 && it.x <= result.width &&
                    it.y >= 0 && it.y <= result.height) }
                val label = relationship.labelAt
                assertTrue(label.x - 70f >= 0 && label.x + 70f <= result.width)
            }
        }
    }

    @Test fun measuredNodesReserveRealWidthWithoutCharacterCountCaps() {
        val diagram = MermaidParser.parse("flowchart LR\nA[长文字] --> B[完成]")!!
        val result = MermaidLayout.compute(diagram, MermaidLayoutMetrics(
            nodeSizes = mapOf("A" to (680f to 100f), "B" to (160f to 100f))))
        val a = result.nodes.getValue("A"); val b = result.nodes.getValue("B")
        assertEquals(680f, a.width)
        assertTrue(a.x + a.width < b.x)
        assertTrue(result.width >= 888f)
    }

    @Test fun gitCommitOrderFollowsEveryAcceptedDirection() {
        listOf("LR", "TB", "BT").forEach { direction ->
            val diagram = MermaidParser.parse("gitGraph $direction:\ncommit id: \"first\"\ncommit id: \"second\"")!!
            val layout = MermaidLayout.compute(diagram)
            val first = layout.nodes.getValue("first"); val second = layout.nodes.getValue("second")
            when (direction) {
                "LR" -> assertTrue(first.x < second.x)
                "RL" -> assertTrue(first.x > second.x)
                "TB" -> assertTrue(first.y < second.y)
                "BT" -> assertTrue(first.y > second.y)
            }
            assertTrue(layout.edges.all { it.start != it.end })
        }
    }

    @Test fun mindmapParentsAreCenteredOnTheirOwnChildren() {
        val diagram = MermaidParser.parse("mindmap\n  root((应用))\n    前端\n      页面\n      组件\n    后端\n      API\n      数据库")!!
        val layout = MermaidLayout.compute(diagram)
        diagram.nodes.filter { node -> diagram.edges.any { it.from == node.id } }.forEach { parent ->
            val children = diagram.edges.filter { it.from == parent.id }.map { layout.nodes.getValue(it.to) }
            val top = children.minOf { it.y }; val bottom = children.maxOf { it.y + it.height }
            assertEquals((top + bottom) / 2, layout.nodes.getValue(parent.id).centerY, 1f)
        }
    }

    @Test fun longCircleLabelsGetEqualAxesForTheirMeasuredDiameter() {
        val diagram = MermaidParser.parse("mindmap\nroot((Very long root label))\n  child")!!
        val root = diagram.nodes.first()
        val layout = MermaidLayout.compute(diagram, MermaidLayoutMetrics(nodeSizes = mapOf(root.id to (460f to 70f))))
        val circle = layout.nodes.getValue(root.id)
        assertEquals(460f, circle.width)
        assertEquals(circle.width, circle.height)
    }

    private fun inside(rect: MermaidRect, layout: MermaidLayoutResult): Boolean =
        rect.x >= 0 && rect.y >= 0 && rect.x + rect.width <= layout.width && rect.y + rect.height <= layout.height
}
