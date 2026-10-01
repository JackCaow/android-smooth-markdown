package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidPieTimelineTest {
    @Test fun pieMatchesFlutterLabelsTitleShowDataDecimalsAndPercentages() {
        val diagram = MermaidParser.parse("""
            pie showData
              title Favorite Pets
              "Dogs" : 33.5
              'Cats' : 51.5
              Rats : 15
        """.trimIndent())!!
        assertEquals(MermaidKind.Pie, diagram.kind)
        val pie = diagram.pie!!
        assertEquals("Favorite Pets", pie.title)
        assertTrue(pie.showValuesInLegend)
        assertEquals(listOf("Dogs", "Cats", "Rats"), pie.slices.map { it.label })
        assertEquals(100.0, pie.totalValue, 0.001)
        assertEquals(33.5, pie.percentage(pie.slices.first()), 0.001)
        val placement = MermaidLayout.compute(diagram)
        assertEquals(3, placement.pie!!.slices.size)
        assertEquals(-90f, placement.pie!!.slices.first().startAngle, 0.001f)
        assertEquals(360f, placement.pie!!.slices.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.01f)
        assertTrue(placement.height > 300f)
    }

    @Test fun pieIgnoresInvalidNonPositiveAndCommentLines() {
        val diagram = MermaidParser.parse("""
            pie
              %% comment
              title Counts
              "Valid": 50
              Missing colon
              "Zero": 0
              "Negative": -10
              "Malformed": 1.2.3
              "Also Valid": 50
        """.trimIndent())!!
        assertEquals(listOf("Valid", "Also Valid"), diagram.pie!!.slices.map { it.label })
        assertTrue(!diagram.pie!!.showValuesInLegend)
        assertNull(MermaidParser.parse("pie\n title Empty"))
    }

    @Test fun timelineMatchesFlutterPeriodsContinuationsAndDescriptions() {
        val diagram = MermaidParser.parse("""
            timeline
              title History of Social Media
              2002 : LinkedIn
              2004 : Facebook
                   : Google
                   : MySpace
              2005 : Youtube
                First video sharing
        """.trimIndent())!!
        assertEquals(MermaidKind.Timeline, diagram.kind)
        val data = diagram.timeline!!
        assertEquals("History of Social Media", data.title)
        assertEquals(listOf("2002", "2004", "2005"), data.sections.map { it.title })
        assertEquals(listOf("Facebook", "Google", "MySpace"), data.sections[1].events.map { it.title })
        assertEquals("First video sharing", data.sections.last().events.single().description)
        val placement = MermaidLayout.compute(diagram)
        assertEquals(3, placement.timeline!!.sections.size)
        assertTrue(placement.timeline!!.sections[0].marker.x < placement.timeline!!.sections[1].marker.x)
        assertTrue(placement.width > 500f)
    }

    @Test fun timelineAllowsPeriodMarkersAndRequiresAnEvent() {
        val diagram = MermaidParser.parse("timeline\nQ1 2024 :\n : Feature A\nQ2 2024 : Feature B")!!
        assertEquals(listOf("Q1 2024", "Q2 2024"), diagram.timeline!!.sections.map { it.title })
        assertNull(MermaidParser.parse("timeline\n title Empty"))
        assertNull(MermaidParser.parse("gantt\n title Unsupported"))
    }
}
