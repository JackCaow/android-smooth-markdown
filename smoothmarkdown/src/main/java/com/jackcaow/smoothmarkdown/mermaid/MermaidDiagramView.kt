package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** Standalone native Mermaid prototype. Embed this view for a Mermaid fenced block. */
@Composable
fun MermaidDiagramView(source: String, modifier: Modifier = Modifier) {
    val diagram = remember(source) { MermaidParser.parse(source) }
    if (diagram == null) {
        Text(source, modifier = modifier)
        return
    }
    val description = remember(diagram) { diagram.accessibilitySummary() }
    val accessibleModifier = modifier.clearAndSetSemantics { contentDescription = description }
    val layout = remember(diagram) { MermaidLayout.compute(diagram) }
    if (diagram.kind == MermaidKind.Pie) {
        MermaidPieView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.Timeline) {
        MermaidTimelineView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.Gantt) {
        MermaidGanttView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.Kanban) {
        MermaidKanbanView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.Radar) {
        MermaidRadarView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.XYChart) {
        MermaidXYChartView(diagram, layout, accessibleModifier)
        return
    }
    if (diagram.kind == MermaidKind.ERDiagram) {
        MermaidERView(diagram, layout, accessibleModifier)
        return
    }
    val foreground = MaterialTheme.colorScheme.onSurface
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Box(accessibleModifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.coerceAtLeast(1f).dp, layout.height.coerceAtLeast(1f).dp)) {
            Canvas(Modifier.fillMaxSize()) {
                layout.subgraphs.forEach { (_, rect) ->
                    drawRoundRect(surface, Offset(rect.x * density, rect.y * density),
                        Size(rect.width * density, rect.height * density),
                        CornerRadius(8.dp.toPx()))
                    drawRoundRect(foreground.copy(alpha = 0.45f), Offset(rect.x * density, rect.y * density),
                        Size(rect.width * density, rect.height * density),
                        CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
                if (diagram.kind == MermaidKind.Sequence) {
                    layout.nodes.values.forEach { rect ->
                        drawLine(foreground.copy(alpha = 0.5f),
                            Offset(rect.centerX * density, (rect.y + rect.height) * density),
                            Offset(rect.centerX * density, (layout.height - 18f) * density),
                            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    }
                }
                layout.edges.forEach { drawEdge(it, foreground) }
                diagram.nodes.forEach { node ->
                    layout.nodes[node.id]?.let { drawNode(it, node, nodeFill, foreground) }
                }
            }
            diagram.subgraphs.forEach { group ->
                layout.subgraphs[group.id]?.let { rect ->
                    Text(group.label, modifier = Modifier.offset(rect.x.dp + 8.dp, rect.y.dp + 3.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
            }
            diagram.nodes.forEach { node ->
                layout.nodes[node.id]?.let { rect ->
                    if (node.shape != MermaidShape.StateStart && node.shape != MermaidShape.StateEnd) {
                        if (node.compartments.isNotEmpty()) {
                            androidx.compose.foundation.layout.Column(Modifier.offset(rect.x.dp, rect.y.dp)
                                .width(rect.width.dp).height(rect.height.dp).padding(5.dp)) {
                                Text(node.label, color = foreground, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(rect.width.dp), textAlign = TextAlign.Center, maxLines = 1)
                                node.compartments.forEach { section ->
                                    if (section.isNotEmpty()) {
                                        androidx.compose.material3.HorizontalDivider()
                                        section.forEach { row ->
                                            Text(row, color = foreground, style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1)
                                        }
                                    }
                                }
                            }
                        } else Box(Modifier.offset(rect.x.dp, rect.y.dp).width(rect.width.dp).height(rect.height.dp)
                            .padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                            Text(node.label, color = node.style?.text?.let(::Color) ?: foreground,
                                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3)
                        }
                    }
                }
            }
            layout.edges.forEach { placed ->
                placed.edge.sourceLabel?.let { label ->
                    Text(label, modifier = Modifier.offset(placed.start.x.dp + 5.dp, placed.start.y.dp - 19.dp)
                        .background(surface).padding(horizontal = 2.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
                placed.edge.targetLabel?.let { label ->
                    Text(label, modifier = Modifier.offset(placed.end.x.dp + 5.dp, placed.end.y.dp - 19.dp)
                        .background(surface).padding(horizontal = 2.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
                placed.edge.label?.takeIf(String::isNotBlank)?.let { label ->
                    val x = (placed.start.x + placed.end.x) / 2
                    val y = (placed.start.y + placed.end.y) / 2
                    Text(label,
                        modifier = Modifier.offset((x - label.length * 3.5f).dp, (y - 24f).dp)
                            .background(surface).padding(horizontal = 3.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun DrawScope.drawNode(rect: MermaidRect, node: MermaidNode, defaultFill: Color, defaultStroke: Color) {
    val left = rect.x * density
    val top = rect.y * density
    val width = rect.width * density
    val height = rect.height * density
    val fill = node.style?.fill?.let(::Color) ?: defaultFill
    val stroke = node.style?.stroke?.let(::Color) ?: defaultStroke
    val strokeWidth = (node.style?.strokeWidth ?: 1f) * density
    val actualShape = if (node.participantType == MermaidParticipantType.Actor) MermaidShape.Circle else node.shape
    if (actualShape == MermaidShape.StateStart || actualShape == MermaidShape.StateEnd) {
        drawCircle(stroke, minOf(width, height) / 2, Offset(left + width / 2, top + height / 2))
        if (actualShape == MermaidShape.StateEnd) {
            drawCircle(fill, minOf(width, height) / 3, Offset(left + width / 2, top + height / 2))
        }
        return
    }
    val polygon: Path? = when (actualShape) {
        MermaidShape.Diamond -> polygon(left + width / 2 to top, left + width to top + height / 2,
            left + width / 2 to top + height, left to top + height / 2)
        MermaidShape.Hexagon -> polygon(left + width * 0.2f to top, left + width * 0.8f to top,
            left + width to top + height / 2, left + width * 0.8f to top + height,
            left + width * 0.2f to top + height, left to top + height / 2)
        MermaidShape.Parallelogram -> polygon(left + width * 0.15f to top, left + width to top,
            left + width * 0.85f to top + height, left to top + height)
        MermaidShape.Trapezoid -> polygon(left + width * 0.18f to top, left + width * 0.82f to top,
            left + width to top + height, left to top + height)
        MermaidShape.Asymmetric -> polygon(left + width * 0.14f to top, left + width to top,
            left + width to top + height, left to top + height, left + width * 0.14f to top + height / 2)
        else -> null
    }
    if (polygon != null) {
        drawPath(polygon, fill)
        drawPath(polygon, stroke, style = Stroke(strokeWidth))
    } else if (actualShape == MermaidShape.Circle) {
        drawOval(fill, Offset(left, top), Size(width, height))
        drawOval(stroke, Offset(left, top), Size(width, height), style = Stroke(strokeWidth))
    } else {
        val radius = when (actualShape) {
            MermaidShape.Stadium -> height / 2
            MermaidShape.Rounded -> 10.dp.toPx()
            else -> 4.dp.toPx()
        }
        drawRoundRect(fill, Offset(left, top), Size(width, height), CornerRadius(radius))
        drawRoundRect(stroke, Offset(left, top), Size(width, height), CornerRadius(radius), style = Stroke(strokeWidth))
        if (actualShape == MermaidShape.Subroutine) {
            drawLine(stroke, Offset(left + 8.dp.toPx(), top), Offset(left + 8.dp.toPx(), top + height), strokeWidth)
            drawLine(stroke, Offset(left + width - 8.dp.toPx(), top), Offset(left + width - 8.dp.toPx(), top + height), strokeWidth)
        }
        if (actualShape == MermaidShape.Cylinder) {
            drawOval(stroke, Offset(left, top - 4.dp.toPx()), Size(width, 12.dp.toPx()), style = Stroke(strokeWidth))
        }
    }
}

private fun DrawScope.drawEdge(placed: MermaidPlacedEdge, color: Color) {
    val start = Offset(placed.start.x * density, placed.start.y * density)
    val end = Offset(placed.end.x * density, placed.end.y * density)
    val width = if (placed.edge.line == MermaidLine.Thick) 3.dp.toPx() else 1.5.dp.toPx()
    val dash = if (placed.edge.line == MermaidLine.Dotted)
        PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
    if (start == end) return
    drawLine(color, start, end, width, pathEffect = dash)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val size = 8.dp.toPx()
    fun marker(at: Offset, toward: Offset, type: MermaidEdgeMarker) {
        val vx = toward.x - at.x; val vy = toward.y - at.y
        val len = hypot(vx, vy).coerceAtLeast(1f)
        val ax = vx / len; val ay = vy / len
        val tip = Offset(at.x + ax * size * 1.3f, at.y + ay * size * 1.3f)
        val left = Offset(at.x - ay * size * 0.7f, at.y + ax * size * 0.7f)
        val right = Offset(at.x + ay * size * 0.7f, at.y - ax * size * 0.7f)
        when (type) {
            MermaidEdgeMarker.Inheritance -> {
                val triangle = Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
                drawPath(triangle, color, style = Stroke(width))
            }
            MermaidEdgeMarker.Composition, MermaidEdgeMarker.Aggregation -> {
                val far = Offset(at.x - ax * size * 1.3f, at.y - ay * size * 1.3f)
                val diamond = Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y)
                    lineTo(far.x, far.y); lineTo(right.x, right.y); close() }
                if (type == MermaidEdgeMarker.Composition) drawPath(diamond, color)
                else drawPath(diamond, color, style = Stroke(width))
            }
        }
    }
    placed.edge.sourceMarker?.let { marker(start, end, it) }
    placed.edge.targetMarker?.let { marker(end, start, it) }
    when (placed.edge.arrow) {
        MermaidArrow.Arrow -> {
            drawLine(color, end, Offset(end.x - ux * size - uy * size * 0.5f,
                end.y - uy * size + ux * size * 0.5f), width)
            drawLine(color, end, Offset(end.x - ux * size + uy * size * 0.5f,
                end.y - uy * size - ux * size * 0.5f), width)
        }
        MermaidArrow.Cross -> {
            drawLine(color, Offset(end.x - uy * size * 0.5f, end.y + ux * size * 0.5f),
                Offset(end.x + uy * size * 0.5f, end.y - ux * size * 0.5f), width)
            drawLine(color, Offset(end.x - ux * size * 0.5f, end.y - uy * size * 0.5f),
                Offset(end.x + ux * size * 0.5f, end.y + uy * size * 0.5f), width)
        }
        MermaidArrow.Circle -> drawCircle(color, size / 3, end, style = Stroke(width))
        MermaidArrow.None -> Unit
    }
}

private fun polygon(vararg points: Pair<Float, Float>): Path = Path().apply {
    points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) }
    close()
}
