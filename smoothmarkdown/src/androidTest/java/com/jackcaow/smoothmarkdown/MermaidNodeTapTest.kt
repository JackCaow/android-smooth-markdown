package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.jackcaow.smoothmarkdown.mermaid.MermaidDiagramView
import com.jackcaow.smoothmarkdown.mermaid.MermaidLayout
import com.jackcaow.smoothmarkdown.mermaid.MermaidParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MermaidNodeTapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publicCallbackReceivesNodeIdOnlyForNodeTaps() {
        val source = "graph LR\nA[Start] --> B[Finish]"
        val diagram = MermaidParser.parse(source)!!
        val first = MermaidLayout.compute(diagram).nodes.getValue("A")
        val taps = mutableListOf<String>()
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            MaterialTheme {
                MermaidDiagramView(source, Modifier.size(300.dp, 180.dp), onNodeTap = { taps += it })
            }
        }
        val rendered = compose.onNodeWithContentDescription(
            "Flowchart. 2 nodes, 1 connections. Nodes: Start; Finish. Connections: A to B.",
        )
        rendered.performTouchInput { click(Offset(first.centerX * pixelsPerDp, first.centerY * pixelsPerDp)) }
        compose.runOnIdle { assertEquals(listOf("A"), taps) }
        rendered.performTouchInput { click(Offset(2f * pixelsPerDp, 2f * pixelsPerDp)) }
        compose.runOnIdle { assertEquals(listOf("A"), taps) }
    }
}
