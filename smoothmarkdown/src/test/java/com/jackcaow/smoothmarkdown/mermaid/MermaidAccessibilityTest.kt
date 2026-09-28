package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidAccessibilityTest {
    @Test fun flowchartNamesNodesAndConnections() {
        val diagram = MermaidParser.parse("graph LR\nA[Start] -->|yes| B[Finish]")!!
        val summary = diagram.accessibilitySummary()
        assertTrue(summary.contains("Flowchart. 2 nodes, 1 connections"))
        assertTrue(summary.contains("Start; Finish"))
        assertTrue(summary.contains("A to B, yes"))
    }

    @Test fun longDiagramIsBounded() {
        val source = "graph LR\n" + (0..15).joinToString("\n") { "N$it[Node $it]" }
        val summary = MermaidParser.parse(source)!!.accessibilitySummary()
        assertTrue(summary.contains("and 4 more"))
        assertFalse(summary.contains("Node 15"))
    }

    @Test fun pieAndEntityRelationshipDataAreSpoken() {
        val pie = MermaidParser.parse("pie\ntitle Sales\n\"North\" : 30\n\"South\" : 70")!!
        assertTrue(pie.accessibilitySummary().contains("North, 30.0"))
        val er = MermaidParser.parse("erDiagram\nCUSTOMER ||--o{ ORDER : places")!!
        assertTrue(er.accessibilitySummary().contains("CUSTOMER places ORDER"))
    }
}
