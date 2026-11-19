package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class MermaidChartTokensTest {
    @Test fun invalidGeometryAndUnspecifiedPaletteRemainSafeAfterCopy() {
        val host = MarkdownMermaidChartTokens(seriesColors = listOf(Color.Unspecified),
            pointRadius = (-8).dp, gridWidth = Float.NaN.dp, fillOpacity = 4f)
        val style = MarkdownMermaidTokens(charts = host).normalized().copy(maxHeight = 500.dp)
        assertEquals(Color.Blue, style.charts.seriesColor(5, Color.Blue))
        assertEquals(0.dp, style.charts.pointRadius)
        assertEquals(0.dp, style.charts.gridWidth)
        assertEquals(1f, style.charts.fillOpacity)
        assertEquals(listOf(Color.Unspecified), host.seriesColors)
    }
    @Test fun seriesPaletteCyclesAcrossChartKinds() {
        val charts = MarkdownMermaidChartTokens(seriesColors = listOf(Color.Red, Color.Green))
        assertEquals(Color.Red, charts.seriesColor(2, Color.Blue))
        assertEquals(Color.Green, charts.seriesColor(3, Color.Blue))
    }
}
