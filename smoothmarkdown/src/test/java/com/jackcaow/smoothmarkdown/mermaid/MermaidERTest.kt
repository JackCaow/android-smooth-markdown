package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidERTest {
    @Test fun issue46EntitiesAttributesAndRelationshipsRemainIntact() {
        val diagram = MermaidParser.parse("""
            erDiagram
                CUSTOMER ||--o{ ORDER : places
                ORDER ||--|{ LINE_ITEM : contains
                CUSTOMER {
                    int id PK
                    string name
                }
                ORDER {
                    int id PK
                    int customer_id FK
                }
                LINE_ITEM {
                    int id PK
                    int order_id FK
                    string product
                }
        """.trimIndent())!!
        assertEquals(MermaidKind.ERDiagram, diagram.kind)
        val data = diagram.er!!
        assertEquals(listOf("CUSTOMER", "ORDER", "LINE_ITEM"), data.entities.map { it.id })
        assertEquals(listOf("int id PK", "int customer_id FK"), data.entity("ORDER")!!.attributes)
        assertEquals(MermaidERCardinality.ExactlyOne, data.relationships.first().sourceCardinality)
        assertEquals(MermaidERCardinality.ZeroOrMore, data.relationships.first().targetCardinality)
        assertEquals(MermaidERCardinality.OneOrMore, data.relationships.last().targetCardinality)
        val layout = MermaidLayout.compute(diagram)
        val boxes = layout.er!!.entities.values.toList()
        assertEquals(3, boxes.size)
        assertTrue(boxes[0].y + boxes[0].height < boxes[1].y)
        assertTrue(boxes[1].y + boxes[1].height < boxes[2].y)
        assertEquals(2, layout.er.relationships.size)
    }

    @Test fun allSourceAndTargetCardinalitiesPreservedForDottedRelations() {
        val sources = mapOf("||" to MermaidERCardinality.ExactlyOne,
            "|o" to MermaidERCardinality.ZeroOrOne,
            "}|" to MermaidERCardinality.OneOrMore,
            "}o" to MermaidERCardinality.ZeroOrMore)
        val targets = mapOf("||" to MermaidERCardinality.ExactlyOne,
            "o|" to MermaidERCardinality.ZeroOrOne,
            "|{" to MermaidERCardinality.OneOrMore,
            "o{" to MermaidERCardinality.ZeroOrMore)
        for ((source, sourceKind) in sources) for ((target, targetKind) in targets) {
            val data = MermaidParser.parse("erDiagram\nA $source..$target B : \"has\"")!!.er!!
            val relation = data.relationships.single()
            assertEquals(sourceKind, relation.sourceCardinality)
            assertEquals(targetKind, relation.targetCardinality)
            assertEquals("has", relation.label)
            assertTrue(relation.dotted)
        }
    }

    @Test fun quotedNamesAliasesDirectionAndSemicolons() {
        val diagram = MermaidParser.parse("""
            erDiagram
            direction LR
            "Customer Account"["Customer"] {
              int id PK "identifier"
            }
            "Customer Account" ||--o{ "Order Line" : has;
        """.trimIndent())!!
        assertEquals(MermaidDirection.LR, diagram.direction)
        val data = diagram.er!!
        assertEquals("Customer", data.entity("Customer Account")!!.label)
        assertEquals(listOf("int id PK \"identifier\""), data.entity("Customer Account")!!.attributes)
        assertEquals("Order Line", data.relationships.single().to)
        val boxes = MermaidLayout.compute(diagram).er!!.entities
        assertTrue(boxes.getValue("Customer Account").x < boxes.getValue("Order Line").x)
    }

    @Test fun unsupportedAndIncompleteSyntaxFallsBackInsteadOfDroppingLines() {
        assertNull(MermaidParser.parse("erDiagram"))
        assertNull(MermaidParser.parse("erDiagram\nA ||--o{ B : has\ninvalid syntax"))
        assertNull(MermaidParser.parse("erDiagram\nA {\nint id PK"))
        assertNull(MermaidParser.parse("erDiagram\nA ||--o{ B"))
        assertNull(MermaidParser.parse("erDiagram\ndirection sideways\nA"))
    }
}
