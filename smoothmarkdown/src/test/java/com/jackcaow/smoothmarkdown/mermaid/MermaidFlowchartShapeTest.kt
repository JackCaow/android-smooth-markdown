package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidFlowchartShapeTest {
    @Test fun flutterGalleryShapeFixtureKeepsSubroutineAndAsymmetricNodes() {
        // Mermaid gallery #6: graph LR; ... D --> E>标签]; E --> F[[子程序]].
        val graph = MermaidParser.parse("""
            graph LR
            A[矩形] --> B(圆角矩形)
            B --> C{菱形}
            C --> D((圆形))
            D --> E>标签]
            E --> F[[子程序]]
        """.trimIndent())!!
        assertEquals(MermaidShape.Diamond, graph.node("C")?.shape)
        assertEquals(MermaidShape.Asymmetric, graph.node("E")?.shape)
        assertEquals(MermaidShape.Subroutine, graph.node("F")?.shape)
        val layout = MermaidLayout.compute(graph)
        assertEquals(5, layout.edges.size)
        assertTrue(layout.nodes.getValue("F").width > layout.nodes.getValue("A").width)
    }

    @Test fun mirroredPolygonsRemainDistinctFromBaseShapes() {
        val graph = MermaidParser.parse("""
            flowchart LR
            A[/Normal/] --> B[\Mirror\]
            B --> C[/Trapezoid\]
            C --> D[\MirrorTrap/]
            D --> E[(Database)]
        """.trimIndent())!!
        assertEquals(MermaidShape.Parallelogram, graph.node("A")?.shape)
        assertEquals(MermaidShape.ParallelogramAlt, graph.node("B")?.shape)
        assertEquals(MermaidShape.Trapezoid, graph.node("C")?.shape)
        assertEquals(MermaidShape.TrapezoidAlt, graph.node("D")?.shape)
        assertEquals(MermaidShape.Cylinder, graph.node("E")?.shape)
        val layout = MermaidLayout.compute(graph)
        assertEquals(4, layout.edges.size)
        assertEquals(layout.nodes.getValue("A").height, layout.nodes.getValue("B").height)
        assertTrue(layout.nodes.getValue("E").height > layout.nodes.getValue("D").height)

        val rect = MermaidRect(0f, 0f, 100f, 60f)
        val normal = flowchartPolygon(MermaidShape.Parallelogram, rect)!!
        val mirror = flowchartPolygon(MermaidShape.ParallelogramAlt, rect)!!
        assertEquals(15f, normal.first().x, .001f)
        assertEquals(0f, mirror.first().x, .001f)
        assertEquals(10f, flowchartPolygon(MermaidShape.Trapezoid, rect)!!.first().x, .001f)
        assertEquals(0f, flowchartPolygon(MermaidShape.TrapezoidAlt, rect)!!.first().x, .001f)
        assertEquals(15f, flowchartPolygon(MermaidShape.Hexagon, rect)!!.first().x, .001f)
        assertEquals(90f, flowchartPolygon(MermaidShape.Asymmetric, rect)!![1].x, .001f)
        assertNotNull(flowchartPolygon(MermaidShape.Diamond, rect))
    }
}
