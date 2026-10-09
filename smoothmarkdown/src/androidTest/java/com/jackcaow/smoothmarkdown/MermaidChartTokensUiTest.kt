package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jackcaow.smoothmarkdown.mermaid.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MermaidChartTokensUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun everySpecialtyChartUpdatesHostPalette() {
        val source = mutableStateOf("pie\n\"One\" : 40\n\"Two\" : 60")
        val style = mutableStateOf(MarkdownMermaidTokens())
        compose.setContent { MaterialTheme {
            MermaidDiagramView(source.value, Modifier.testTag("chart"), style = style.value)
        } }
        val sources = listOf(
            "pie\n\"One\" : 40\n\"Two\" : 60",
            "timeline\n2024 : Launch",
            "gantt\nTask :crit, a, 2024-06-01, 3d",
            "kanban\n  todo[Todo]\n    task[Fix] @{ priority: \"High\" }",
            "radar-beta\naxis A, B, C\ncurve c{4, 7, 6}",
            "xychart-beta\nx-axis [A, B]\nbar [3, 8]\nline [5, 7]",

        )
        fun magentaPixels(): Int {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("chart").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.8f && color.green < 0.3f && color.blue > 0.8f) count++
            }
            return count
        }
        for (fixture in sources) {
            compose.runOnIdle { source.value = fixture; style.value = MarkdownMermaidTokens() }
            val normal = magentaPixels()
            compose.runOnIdle { style.value = style.value.copy(charts = MarkdownMermaidChartTokens(
                seriesColors = listOf(Color.Magenta),
                ganttStatusColors = mapOf(MermaidGanttStatus.Critical to Color.Magenta),
                kanbanPriorityColors = mapOf(MermaidKanbanPriority.High to Color.Magenta),
            )) }
            assertTrue("Chart ignored host palette: $fixture", magentaPixels() > normal + 100)
        }
    }
}
