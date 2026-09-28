package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MermaidNodeHitTestTest {
    @Test fun flowchartUsesStableIdsAndIgnoresEmptyAreaAndSubgraphBorder() {
        val diagram = MermaidParser.parse("""
            flowchart LR
            subgraph group [A group]
              first[Visible label] --> second{Next step}
            end
        """.trimIndent())!!
        val layout = MermaidLayout.compute(diagram)
        val first = layout.nodes.getValue("first")
        assertEquals("first", diagram.nodeIdAt(layout, first.centerX, first.centerY))
        val second = layout.nodes.getValue("second")
        assertEquals("second", diagram.nodeIdAt(layout, second.centerX, second.centerY))
        assertNull(diagram.nodeIdAt(layout, 0f, 0f))
        val group = layout.subgraphs.getValue("group")
        assertNull(diagram.nodeIdAt(layout, group.x + 2f, group.y + 2f))
        assertNull(diagram.nodeIdAt(layout, first.x + first.width, first.centerY))
    }

    @Test fun erEntityReceivesItsIdButRelationshipDoesNot() {
        val diagram = MermaidParser.parse("""
            erDiagram
            CUSTOMER ||--o{ ORDER : places
            CUSTOMER {
              string name
            }
        """.trimIndent())!!
        val layout = MermaidLayout.compute(diagram)
        val customer = layout.er!!.entities.getValue("CUSTOMER")
        assertEquals("CUSTOMER", diagram.nodeIdAt(layout, customer.centerX, customer.centerY))
        assertNull(diagram.nodeIdAt(layout, 0f, 0f))
    }

    @Test fun sequenceParticipantReceivesSourceId() {
        val diagram = MermaidParser.parse("sequenceDiagram\nparticipant Alice as A\nAlice->>Bob: Hello")!!
        val layout = MermaidLayout.compute(diagram)
        val participant = diagram.nodes.first()
        val box = layout.nodes.getValue(participant.id)
        assertEquals(participant.id, diagram.nodeIdAt(layout, box.centerX, box.centerY))
        assertNull(diagram.nodeIdAt(layout, box.centerX, box.y + box.height + 1f))
    }
}
