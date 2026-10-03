package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import com.jackcaow.smoothmarkdown.MarkdownMermaidTokens

/** Branch lanes and commit markers, rather than class-style metadata compartments. */
@Composable
internal fun MermaidGitView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier,
    onNodeTap: ((String) -> Unit)?, tokens: MarkdownMermaidTokens) {
    val branches = diagram.nodes.map { it.compartments.firstOrNull()?.firstOrNull() ?: "main" }.distinct()
    val foreground = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val vertical = diagram.direction == MermaidDirection.TB || diagram.direction == MermaidDirection.BT
    val step = if (vertical) (layout.width - 40f) / branches.size else
        if (diagram.nodes.size > 1) abs(layout.nodes.values.toList()[1].x - layout.nodes.values.first().x) else
            layout.width - layout.nodes.values.first().x
    val textHeight = MaterialTheme.typography.labelSmall.lineHeight.value * LocalDensity.current.fontScale
    fun branchBox(branch: String): MermaidRect = layout.nodes.getValue(diagram.nodes.first {
        (it.compartments.firstOrNull()?.firstOrNull() ?: "main") == branch }.id)
    MermaidViewport(layout, modifier) {
        Box(Modifier.fillMaxSize().mermaidNodeTaps(diagram, layout, onNodeTap)) {
            Canvas(Modifier.fillMaxSize()) {
                branches.forEachIndexed { index, branch ->
                    val rect = branchBox(branch)
                    val start = if (vertical) Offset(rect.centerX.dp.toPx(), 20.dp.toPx()) else Offset(20.dp.toPx(), rect.centerY.dp.toPx())
                    val end = if (vertical) Offset(rect.centerX.dp.toPx(), (layout.height - 20f).dp.toPx()) else Offset((layout.width - 20f).dp.toPx(), rect.centerY.dp.toPx())
                    drawLine(piePalette[index % piePalette.size].copy(alpha = .25f), start, end, tokens.edgeWidth.toPx())
                }
                layout.edges.forEach { placed ->
                    val node = diagram.node(placed.edge.to)
                    val branch = node?.compartments?.firstOrNull()?.firstOrNull() ?: "main"
                    drawEdge(placed, piePalette[branches.indexOf(branch).coerceAtLeast(0) % piePalette.size], tokens)
                }
                diagram.nodes.forEach { node ->
                    val rect = layout.nodes.getValue(node.id)
                    val branch = node.compartments.firstOrNull()?.firstOrNull() ?: "main"
                    val color = piePalette[branches.indexOf(branch).coerceAtLeast(0) % piePalette.size]
                    val center = Offset(rect.centerX.dp.toPx(), rect.centerY.dp.toPx())
                    val type = node.compartments.lastOrNull()?.firstOrNull()
                    drawCircle(surface, 14.dp.toPx(), center)
                    drawCircle(color, 14.dp.toPx(), center, style = Stroke(tokens.edgeWidth.toPx() * 2))
                    if (type == "HIGHLIGHT") drawCircle(color, 8.dp.toPx(), center)
                    if (type == "REVERSE") {
                        val d = 6.dp.toPx()
                        drawLine(color, center + Offset(-d, -d), center + Offset(d, d), tokens.edgeWidth.toPx())
                        drawLine(color, center + Offset(-d, d), center + Offset(d, -d), tokens.edgeWidth.toPx())
                    }
                }
            }
            branches.forEachIndexed { index, branch ->
                val rect = branchBox(branch)
                Text(branch, Modifier.offset(if (vertical) rect.x.dp else 20.dp,
                    if (vertical) (if (diagram.direction == MermaidDirection.BT) layout.height - textHeight - 8f else 8f).dp else (rect.centerY - textHeight - 8f).dp),
                    color = piePalette[index % piePalette.size], style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }
            diagram.nodes.forEach { node ->
                val rect = layout.nodes.getValue(node.id)
                val textX = if (vertical) rect.x + rect.width + 8f else rect.x
                val textY = if (vertical) rect.y else rect.y + rect.height + 8f
                Text(node.label, Modifier.offset(textX.dp, textY.dp).width((step - 42f).coerceAtLeast(28f).dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall)
                node.compartments.getOrNull(1)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tag ->
                    Text(tag, Modifier.offset(textX.dp, (rect.y - textHeight - 8f).dp).width((step - 42f).coerceAtLeast(28f).dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
