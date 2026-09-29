package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidRadarXYTest {
    @Test fun radarMatchesFlutterLabeledAxesCurvesAndOptions() {
        val diagram = MermaidParser.parse("""
            radar-beta
              title Skills
              axis A["Label A"], B["Label B"], C["Label C"]
              curve c1["Curve One"]{1, 2, 3}
              curve c2["Curve Two"]{3, 2, 1}
              showLegend false
              max 10
              min 0
              graticule circle
              ticks 10
        """.trimIndent())!!
        assertEquals(MermaidKind.Radar, diagram.kind)
        val data = diagram.radar!!
        assertEquals("Skills", data.title)
        assertEquals(listOf("Label A", "Label B", "Label C"), data.axes.map { it.label })
        assertEquals(listOf("Curve One", "Curve Two"), data.curves.map { it.label })
        assertEquals(listOf(1.0, 2.0, 3.0), data.curves[0].values)
        assertTrue(!data.showLegend)
        assertEquals(10.0, data.effectiveMax, 0.001)
        assertEquals(0.0, data.effectiveMin, 0.001)
        assertEquals(MermaidRadarGraticule.Circle, data.graticule)
        val place = MermaidLayout.compute(diagram).radar!!
        assertEquals(10, place.rings.size)
        assertEquals(2, place.curves.size)
        assertEquals(3, place.curves[0].size)
        assertTrue(place.curves[0][2].x != place.curves[0][1].x)
    }

    @Test fun radarSupportsKeyValueAndChineseLabels() {
        val diagram = MermaidParser.parse("""
            radar-beta
            title 技能评估
            axis 编程, 设计, 沟通
            curve 张三{编程: 5, 设计: 3, 沟通: 4}
        """.trimIndent())!!
        assertEquals("技能评估", diagram.radar!!.title)
        assertEquals(listOf("编程", "设计", "沟通"), diagram.radar.axes.map { it.id })
        assertEquals(listOf(5.0, 3.0, 4.0), diagram.radar.curves[0].values)
        assertEquals("张三", diagram.radar.curves[0].label)
    }

    @Test fun radarFallsBackOnMissingAndMismatchedData() {
        assertNull(MermaidParser.parse("radar-beta\n axis A, B, C"))
        assertNull(MermaidParser.parse("radar-beta\n curve c{1,2,3}"))
        assertNull(MermaidParser.parse("radar-beta\n axis A, B, C\n curve c{1,2}"))
        assertNull(MermaidParser.parse("radar-beta\n axis A, B, C\n curve c{1,2,nope}"))
        assertNull(MermaidParser.parse("radar-beta\n axis A, B, C\n curve c{1,2,3}\n unknown foo"))
    }

    @Test fun xyChartMatchesFlutterMixedCategoricalSeries() {
        val diagram = MermaidParser.parse("""
            xychart-beta
            title "Sales Data"
            x-axis [Q1, Q2, Q3, Q4]
            y-axis "Revenue" 0 --> 100
            bar [23, 45, 67, 89]
            line [20, 50, 60, 85]
        """.trimIndent())!!
        assertEquals(MermaidKind.XYChart, diagram.kind)
        val data = diagram.xyChart!!
        assertEquals("Sales Data", data.title)
        assertEquals(listOf("Q1", "Q2", "Q3", "Q4"), data.categories)
        assertEquals("Revenue", data.yAxisTitle)
        assertEquals(0.0, data.effectiveMin, 0.001)
        assertEquals(100.0, data.effectiveMax, 0.001)
        assertEquals(listOf(MermaidXYSeriesType.Bar, MermaidXYSeriesType.Line), data.series.map { it.type })
        val place = MermaidLayout.compute(diagram).xyChart!!
        assertEquals(4, place.bars.size)
        assertEquals(1, place.lines.size)
        assertTrue(place.bars[3].rect.height > place.bars[0].rect.height)
    }

    @Test fun xyChartSupportsHorizontalQuotedCategoriesDecimalsAndMultipleBars() {
        val diagram = MermaidParser.parse("""
            xychart-beta horizontal
            x-axis ["Q1 2024", "Q2 2024", "Q3 2024"]
            bar [2.3, -3.4, .98]
            bar [1, 2, 3]
        """.trimIndent())!!
        val data = diagram.xyChart!!
        assertEquals(MermaidXYOrientation.Horizontal, data.orientation)
        assertEquals(listOf("Q1 2024", "Q2 2024", "Q3 2024"), data.categories)
        assertEquals(-3.4, data.effectiveMin, 0.001)
        assertEquals(listOf(2.3, -3.4, 0.98), data.series[0].values)
        val place = MermaidLayout.compute(diagram).xyChart!!
        assertEquals(6, place.bars.size)
        assertTrue(place.bars[0].rect.y != place.bars[1].rect.y)
        assertTrue(place.bars[0].rect.width > 0f)
    }

    @Test fun xyChartSupportsPlainHeaderAndFallsBackOnInvalidSeries() {
        assertEquals(MermaidKind.XYChart,
            MermaidParser.parse("xychart\n x-axis [A, B]\n bar [10, 20]")!!.kind)
        assertNull(MermaidParser.parse("xychart-beta\n x-axis [A, B]"))
        assertNull(MermaidParser.parse("xychart-beta\n x-axis [A, B]\n bar [1, 2, 3]"))
        assertNull(MermaidParser.parse("xychart-beta\n x-axis [A, B]\n bar [1, NaN]"))
        assertNull(MermaidParser.parse("xychart-beta\n x-axis [A, B]\n bar [1, 2]\n unsupported"))
    }
}
