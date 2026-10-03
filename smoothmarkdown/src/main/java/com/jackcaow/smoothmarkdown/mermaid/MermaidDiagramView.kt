package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.jackcaow.smoothmarkdown.MarkdownMermaidEdgeRouting
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import com.jackcaow.smoothmarkdown.LocalMarkdownStyleSheet
import com.jackcaow.smoothmarkdown.MarkdownMermaidTokens
import com.jackcaow.smoothmarkdown.normalized

/** Native Mermaid diagram. [onNodeTap] receives the source node ID, never its display label. */
@Composable
fun MermaidDiagramView(
    source: String,
    modifier: Modifier = Modifier,
    onNodeTap: ((String) -> Unit)? = null,
    /** Flutter-compatible fenced `theme=dark|forest|neutral|default` override. */
    theme: String? = null,
    /** Host colors take precedence over [theme]; null fields inherit the surrounding MaterialTheme. */
    style: MarkdownMermaidTokens = LocalMarkdownStyleSheet.current.designTokens.mermaid,
) {
    val resolvedStyle = style.normalized()
    val colors = resolvedStyle.colors ?: theme?.let(::mermaidThemeColors)
    MaterialTheme(
        colorScheme = colors?.let { MaterialTheme.colorScheme.withMermaidTheme(it) } ?: MaterialTheme.colorScheme,
        typography = resolvedStyle.typography?.let { roles ->
            val inherited = MaterialTheme.typography
            inherited.copy(
                bodySmall = roles.bodySmall ?: inherited.bodySmall,
                bodyMedium = roles.bodyMedium ?: inherited.bodyMedium,
                bodyLarge = roles.bodyLarge ?: inherited.bodyLarge,
                labelSmall = roles.labelSmall ?: inherited.labelSmall,
                labelMedium = roles.labelMedium ?: inherited.labelMedium,
                labelLarge = roles.labelLarge ?: inherited.labelLarge,
                titleSmall = roles.titleSmall ?: inherited.titleSmall,
                titleMedium = roles.titleMedium ?: inherited.titleMedium,
                titleLarge = roles.titleLarge ?: inherited.titleLarge,
                headlineSmall = roles.headlineSmall ?: inherited.headlineSmall,
                headlineMedium = roles.headlineMedium ?: inherited.headlineMedium,
                headlineLarge = roles.headlineLarge ?: inherited.headlineLarge,
                displaySmall = roles.displaySmall ?: inherited.displaySmall,
                displayMedium = roles.displayMedium ?: inherited.displayMedium,
                displayLarge = roles.displayLarge ?: inherited.displayLarge
            )
        } ?: MaterialTheme.typography,
    ) {
        MermaidDiagramContent(source, colors?.let { modifier.background(it.background) } ?: modifier,
            onNodeTap, explicitTheme = colors != null, style = resolvedStyle)
    }
}

@Composable
private fun MermaidDiagramContent(
    source: String,
    modifier: Modifier,
    onNodeTap: ((String) -> Unit)?,
    explicitTheme: Boolean,
    style: MarkdownMermaidTokens,
) {
    val diagram = remember(source) { MermaidParser.parse(source) }
    if (diagram == null) {
        Text(source, modifier = modifier)
        return
    }
    val description = remember(diagram) { diagram.accessibilitySummary() }
    val accessibleModifier = modifier.clearAndSetSemantics { contentDescription = description }
    val hostDensity = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    fun measured(text: String, textStyle: androidx.compose.ui.text.TextStyle): Pair<Float, Float> {
        val size = textMeasurer.measure(text, textStyle, softWrap = false).size
        return size.width / hostDensity.density to size.height / hostDensity.density
    }
    val nodeSizes = diagram.nodes.associate { node ->
        val title = measured(node.label, typography.labelMedium)
        val rows = node.compartments.flatten().map { measured(it, typography.labelSmall) }
        val shapePadding = when (node.shape) {
            MermaidShape.Diamond -> 1.8f; MermaidShape.Hexagon, MermaidShape.Circle -> 1.4f
            else -> 1f
        }
        node.id to if (node.shape == MermaidShape.StateStart || node.shape == MermaidShape.StateEnd) (24f to 24f)
        else (maxOf(88f, (maxOf(title.first, rows.maxOfOrNull { it.first } ?: 0f) + style.nodePadding.value * 2) * shapePadding) to
            maxOf(48f, (title.second + rows.sumOf { it.second.toDouble() }.toFloat() +
                node.compartments.count { it.isNotEmpty() } * 8f + style.nodePadding.value * 2) * shapePadding))
    }
    val labelMeasurements = (diagram.edges.flatMap { listOfNotNull(it.label, it.sourceLabel, it.targetLabel) } + diagram.subgraphs.map { it.label } + diagram.nodes.flatMap { listOf(it.label) + it.compartments.flatten() }).associateWith {
        val size = measured(it, typography.labelSmall)
        size.first + style.labelPadding.value * 2 to size.second
    }
    val labels = labelMeasurements.toMutableMap()
    if (diagram.kind == MermaidKind.GitGraph) diagram.nodes.forEach { node ->
        node.compartments.firstOrNull()?.firstOrNull()?.let { branch ->
            val size = measured(branch, typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            labels[branch] = size.first + style.labelPadding.value * 2 to size.second
        }
        node.compartments.getOrNull(1)?.firstOrNull()?.let { tag ->
            val size = measured(tag, typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            labels[tag] = size.first + style.labelPadding.value * 2 to size.second
        }
    }
    val metrics = MermaidLayoutMetrics(nodeSizes, labels, hostDensity.fontScale,
        style.edgeRouting == MarkdownMermaidEdgeRouting.Curved, style.nodePadding.value,
        style.rankGap.value, style.siblingGap.value, style.arrowSize.value * 1.4f)
    val layout = remember(diagram, metrics) { MermaidLayout.compute(diagram, metrics) }
    if (diagram.kind in setOf(MermaidKind.Pie, MermaidKind.Timeline, MermaidKind.Gantt,
            MermaidKind.Kanban, MermaidKind.Radar, MermaidKind.XYChart, MermaidKind.ERDiagram)) {
        val checks = mutableListOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()
        fun check(text: String?, width: Float, height: Float, textStyle: androidx.compose.ui.text.TextStyle) {
            if (!text.isNullOrBlank()) checks += measured(text, textStyle) to (width to height)
        }
        diagram.pie?.let { data ->
            check(data.title, 312f, 22f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            data.slices.forEach { check(it.label + ": 999 (100.0%)", 292f, 27f, typography.bodySmall) }
        }
        diagram.timeline?.let { data ->
            check(data.title, layout.width - 48f, 30f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            data.sections.forEach { section ->
                check(section.title, 150f, 27f, typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                section.events.forEach { check(it.title, 150f, 20f, typography.bodySmall)
                    check(it.description, 150f, 20f, typography.labelSmall) }
            }
        }
        diagram.gantt?.let { data ->
            check(data.title, layout.width - 32f, 32f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            check("Today", 60f, 16f, typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            data.tasks.forEach { check(it.section?.let { section -> "$section · ${it.name}" } ?: it.name,
                164f, 28f, typography.labelSmall) }
        }
        diagram.kanban?.let { data -> data.columns.forEach { column ->
            check(column.title, 184f, 36f, typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            column.tasks.forEach { check(it.description, 180f, 36f, typography.bodySmall)
                check(it.assigned, 180f, 20f, typography.labelSmall) }
        } }
        diagram.radar?.let { data -> data.axes.forEach { check(it.label, 84f, 24f, typography.labelSmall) }
            data.curves.forEach { check(it.label, 280f, 23f, typography.bodySmall) }
            check(data.title, 364f, 32f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }
        diagram.xyChart?.let { data -> data.categories.forEach { check(it, 64f, 36f, typography.labelSmall) }
            check(data.title, layout.width - 32f, 32f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }
        diagram.er?.let { data -> data.entities.forEach { entity ->
            val width = layout.er?.entities?.get(entity.id)?.width ?: 204f
            check(entity.label, width - 16f, 29f, typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            entity.attributes.forEach { check(it, width - 20f, 24f, typography.labelSmall) }
        }
            data.relationships.forEach { check(it.label, 96f, 24f, typography.labelSmall) } }
        // Grow dp geometry without scaling sp twice. Long labels remain readable through native scrolling.
        val scale = checks.maxOfOrNull { (actual, available) -> maxOf(actual.first / available.first,
            actual.second / available.second) }?.coerceAtLeast(1f) ?: 1f
        Box(accessibleModifier) {
          CompositionLocalProvider(LocalDensity provides Density(hostDensity.density * scale, hostDensity.fontScale / scale)) {
            when (diagram.kind) {
                MermaidKind.Pie -> MermaidPieView(diagram, layout, Modifier)
                MermaidKind.Timeline -> MermaidTimelineView(diagram, layout, Modifier)
                MermaidKind.Gantt -> MermaidGanttView(diagram, layout, Modifier)
                MermaidKind.Kanban -> MermaidKanbanView(diagram, layout, Modifier)
                MermaidKind.Radar -> MermaidRadarView(diagram, layout, Modifier)
                MermaidKind.XYChart -> MermaidXYChartView(diagram, layout, Modifier)
                MermaidKind.ERDiagram -> MermaidERView(diagram, layout, Modifier, onNodeTap, style)
                else -> Unit
            }
          }
        }
        return
    }
    if (diagram.kind == MermaidKind.GitGraph) {
        MermaidGitView(diagram, layout, accessibleModifier, onNodeTap, style)
        return
    }
    val foreground = MaterialTheme.colorScheme.onSurface
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val nodeStroke = if (explicitTheme) MaterialTheme.colorScheme.primary else foreground
    val edgeColor = if (explicitTheme) MaterialTheme.colorScheme.outline else foreground
    val surface = MaterialTheme.colorScheme.surface
    val groupById = diagram.subgraphs.associateBy { it.id }
    fun depth(group: MermaidSubgraph): Int {
        var level = 0
        var parent = group.parentId
        while (parent != null && level < diagram.subgraphs.size) {
            level++
            parent = groupById[parent]?.parentId
        }
        return level
    }
    val groupsForDrawing = diagram.subgraphs.sortedBy(::depth)
    MermaidViewport(layout, accessibleModifier) {
        Box(Modifier.fillMaxSize().mermaidNodeTaps(diagram, layout, onNodeTap)) {
            Canvas(Modifier.fillMaxSize()) {
                groupsForDrawing.forEach { group ->
                    val rect = layout.subgraphs[group.id] ?: return@forEach
                    drawRoundRect(surface, Offset(rect.x * density, rect.y * density),
                        Size(rect.width * density, rect.height * density),
                        CornerRadius(8.dp.toPx()))
                    drawRoundRect(edgeColor.copy(alpha = 0.45f), Offset(rect.x * density, rect.y * density),
                        Size(rect.width * density, rect.height * density),
                        CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
                if (diagram.kind == MermaidKind.Sequence) {
                    layout.nodes.values.forEach { rect ->
                        drawLine(edgeColor.copy(alpha = 0.5f),
                            Offset(rect.centerX * density, (rect.y + rect.height) * density),
                            Offset(rect.centerX * density, (layout.height - 18f) * density),
                            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    }
                }
                layout.edges.forEach { drawEdge(it, edgeColor, style) }
                diagram.nodes.forEach { node ->
                    layout.nodes[node.id]?.let { drawNode(it, node, nodeFill, nodeStroke, style) }
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
                                .width(rect.width.dp).height(rect.height.dp).padding(style.nodePadding)) {
                                Text(node.label, color = foreground, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(rect.width.dp), textAlign = TextAlign.Center, maxLines = Int.MAX_VALUE)
                                node.compartments.forEach { section ->
                                    if (section.isNotEmpty()) {
                                        androidx.compose.material3.HorizontalDivider()
                                        section.forEach { row ->
                                            Text(row, color = foreground, style = MaterialTheme.typography.labelSmall,
                                                maxLines = Int.MAX_VALUE)
                                        }
                                    }
                                }
                            }
                        } else Box(Modifier.offset(rect.x.dp, rect.y.dp).width(rect.width.dp).height(rect.height.dp)
                            .padding(horizontal = style.nodePadding), contentAlignment = Alignment.Center) {
                            Text(node.label, color = node.style?.text?.let(::Color) ?: foreground,
                                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            layout.edges.forEach { placed ->
                placed.edge.sourceLabel?.let { label ->
                    Text(label, modifier = Modifier.offset((placed.sourceLabelBounds?.x ?: (placed.start.x + 5f)).dp,
                        (placed.sourceLabelBounds?.y ?: (placed.start.y - 19f)).dp)
                        .background(surface).padding(horizontal = 2.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
                placed.edge.targetLabel?.let { label ->
                    Text(label, modifier = Modifier.offset((placed.targetLabelBounds?.x ?: (placed.end.x + 5f)).dp,
                        (placed.targetLabelBounds?.y ?: (placed.end.y - 19f)).dp)
                        .background(surface).padding(horizontal = 2.dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
                placed.edge.label?.takeIf(String::isNotBlank)?.let { label ->
                    val x = placed.labelBounds?.x ?: ((placed.start.x + placed.end.x) / 2 - label.length * 3.5f)
                    val y = placed.labelBounds?.y ?: ((placed.start.y + placed.end.y) / 2 - 24f)
                    Text(label,
                        modifier = Modifier.offset(x.dp, y.dp)
                            .background(surface).padding(horizontal = style.labelPadding),
                        color = foreground, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun DrawScope.drawNode(rect: MermaidRect, node: MermaidNode, defaultFill: Color, defaultStroke: Color, tokens: MarkdownMermaidTokens) {
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
    val polygon: Path? = flowchartPolygon(actualShape, rect)?.let { points ->
        polygon(*points.map { it.x * density to it.y * density }.toTypedArray())
    }
    if (polygon != null) {
        drawPath(polygon, fill)
        drawPath(polygon, stroke, style = Stroke(strokeWidth))
    } else if (actualShape == MermaidShape.Circle) {
        drawOval(fill, Offset(left, top), Size(width, height))
        drawOval(stroke, Offset(left, top), Size(width, height), style = Stroke(strokeWidth))
    } else if (actualShape == MermaidShape.Cylinder) {
        val ellipse = height * .15f
        drawRect(fill, Offset(left, top + ellipse / 2), Size(width, height - ellipse))
        drawOval(fill, Offset(left, top + height - ellipse), Size(width, ellipse))
        drawOval(stroke, Offset(left, top + height - ellipse), Size(width, ellipse), style = Stroke(strokeWidth))
        drawOval(fill, Offset(left, top), Size(width, ellipse))
        drawOval(stroke, Offset(left, top), Size(width, ellipse), style = Stroke(strokeWidth))
        drawLine(stroke, Offset(left, top + ellipse / 2), Offset(left, top + height - ellipse / 2), strokeWidth)
        drawLine(stroke, Offset(left + width, top + ellipse / 2),
            Offset(left + width, top + height - ellipse / 2), strokeWidth)
    } else if (actualShape == MermaidShape.Subroutine) {
        drawRect(fill, Offset(left, top), Size(width, height))
        drawRect(stroke, Offset(left, top), Size(width, height), style = Stroke(strokeWidth))
        drawLine(stroke, Offset(left + 8.dp.toPx(), top), Offset(left + 8.dp.toPx(), top + height), strokeWidth)
        drawLine(stroke, Offset(left + width - 8.dp.toPx(), top),
            Offset(left + width - 8.dp.toPx(), top + height), strokeWidth)
    } else {
        val radius = when (actualShape) {
            MermaidShape.Stadium -> height / 2
            MermaidShape.Rounded -> tokens.nodeCornerRadius.toPx()
            else -> tokens.nodeCornerRadius.toPx()
        }
        drawRoundRect(fill, Offset(left, top), Size(width, height), CornerRadius(radius))
        drawRoundRect(stroke, Offset(left, top), Size(width, height), CornerRadius(radius), style = Stroke(strokeWidth))
    }
}

internal fun DrawScope.drawEdge(placed: MermaidPlacedEdge, color: Color, tokens: MarkdownMermaidTokens) {
    val start = Offset(placed.start.x * density, placed.start.y * density)
    val end = Offset(placed.end.x * density, placed.end.y * density)
    val width = if (placed.edge.line == MermaidLine.Thick) tokens.edgeWidth.toPx() * 2 else tokens.edgeWidth.toPx()
    val dash = if (placed.edge.line == MermaidLine.Dotted)
        PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
    if (start == end) return
    placed.curveControls?.let { (first, second) ->
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(first.x * density, first.y * density, second.x * density, second.y * density, end.x, end.y)
        }
        drawPath(path, color, style = Stroke(width, pathEffect = dash))
    } ?: drawLine(color, start, end, width, pathEffect = dash)
    val dx = end.x - (placed.curveControls?.second?.x?.times(density) ?: start.x)
    val dy = end.y - (placed.curveControls?.second?.y?.times(density) ?: start.y)
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val size = tokens.arrowSize.toPx()
    fun marker(at: Offset, toward: Offset, type: MermaidEdgeMarker) {
        val vx = toward.x - at.x; val vy = toward.y - at.y
        val len = hypot(vx, vy).coerceAtLeast(1f)
        val ax = vx / len; val ay = vy / len
        val tip = Offset(at.x + ax * size * 1.3f, at.y + ay * size * 1.3f)
        val left = Offset(at.x - ay * size * 0.7f, at.y + ax * size * 0.7f)
        val right = Offset(at.x + ay * size * 0.7f, at.y - ax * size * 0.7f)
        when (type) {
            MermaidEdgeMarker.Inheritance -> {
                // Inheritance points toward the attached class, opposite the edge shaft.
                val attachedTip = Offset(at.x - ax * size * 1.3f, at.y - ay * size * 1.3f)
                val triangle = Path().apply { moveTo(attachedTip.x, attachedTip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
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
    placed.edge.sourceMarker?.let { marker(start, placed.curveControls?.first?.let { p -> Offset(p.x * density, p.y * density) } ?: end, it) }
    placed.edge.targetMarker?.let { marker(end, placed.curveControls?.second?.let { p -> Offset(p.x * density, p.y * density) } ?: start, it) }
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
