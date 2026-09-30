package com.jackcaow.smoothmarkdown.mermaid

import kotlin.math.max
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class MermaidPoint(val x: Float, val y: Float)
data class MermaidRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2
}
data class MermaidPlacedEdge(
    val edge: MermaidEdge,
    val start: MermaidPoint,
    val end: MermaidPoint,
    val curveControls: Pair<MermaidPoint, MermaidPoint>? = null,
    val labelBounds: MermaidRect? = null,
)
data class MermaidPieSlicePlacement(val slice: MermaidPieSlice, val startAngle: Float, val sweepAngle: Float, val index: Int)
data class MermaidPiePlacement(
    val center: MermaidPoint, val radius: Float, val legendY: Float,
    val slices: List<MermaidPieSlicePlacement>,
)
data class MermaidTimelineSectionPlacement(val section: MermaidTimelineSection, val card: MermaidRect, val marker: MermaidPoint, val index: Int)
data class MermaidTimelinePlacement(val axisY: Float, val sections: List<MermaidTimelineSectionPlacement>)
data class MermaidGanttTaskPlacement(val task: MermaidGanttTask, val bar: MermaidRect, val rowY: Float)
data class MermaidGanttPlacement(
    val chartX: Float, val chartWidth: Float, val headerY: Float, val minDay: Long, val maxDay: Long,
    val tasks: List<MermaidGanttTaskPlacement>,
)

/** Flutter draws the marker only when today falls inside the chart's date range. */
internal fun MermaidGanttPlacement.todayMarkerX(todayDay: Long, enabled: Boolean): Float? {
    if (!enabled || todayDay !in minDay..maxDay) return null
    val dayCount = (maxDay - minDay + 1).coerceAtLeast(1)
    return chartX + (todayDay - minDay).toFloat() * chartWidth / dayCount.toFloat()
}

data class MermaidKanbanColumnPlacement(
    val column: MermaidKanbanColumn, val box: MermaidRect, val cards: List<MermaidRect>,
)
data class MermaidKanbanPlacement(val columns: List<MermaidKanbanColumnPlacement>)
data class MermaidRadarPlacement(
    val center: MermaidPoint, val radius: Float, val axisEnds: List<MermaidPoint>,
    val rings: List<List<MermaidPoint>>, val curves: List<List<MermaidPoint>>, val legendY: Float,
)
data class MermaidXYBarPlacement(val rect: MermaidRect, val seriesIndex: Int)
data class MermaidXYPlacement(
    val plot: MermaidRect, val bars: List<MermaidXYBarPlacement>,
    val lines: List<Pair<Int, List<MermaidPoint>>>, val categoryCenters: List<Float>,
    val baseline: Float,
)
data class MermaidERPlacedRelationship(
    val relationship: MermaidERRelationship, val points: List<MermaidPoint>, val labelAt: MermaidPoint,
)
data class MermaidERPlacement(
    val entities: Map<String, MermaidRect>, val relationships: List<MermaidERPlacedRelationship>,
)
data class MermaidLayoutResult(
    val width: Float,
    val height: Float,
    val nodes: Map<String, MermaidRect>,
    val edges: List<MermaidPlacedEdge>,
    val subgraphs: Map<String, MermaidRect>,
    val pie: MermaidPiePlacement? = null,
    val timeline: MermaidTimelinePlacement? = null,
    val gantt: MermaidGanttPlacement? = null,
    val kanban: MermaidKanbanPlacement? = null,
    val radar: MermaidRadarPlacement? = null,
    val xyChart: MermaidXYPlacement? = null,
    val er: MermaidERPlacement? = null,
)

/** Deterministic layered layout for the supported flowchart and sequence subset. Units are dp. */
object MermaidLayout {
    fun compute(diagram: MermaidDiagram): MermaidLayoutResult = when (diagram.kind) {
        MermaidKind.Flowchart -> flowchart(diagram)
        MermaidKind.Sequence -> sequence(diagram)
        MermaidKind.Pie -> pie(diagram)
        MermaidKind.Timeline -> timeline(diagram)
        MermaidKind.Gantt -> gantt(diagram)
        MermaidKind.Kanban -> kanban(diagram)
        MermaidKind.Radar -> radar(diagram)
        MermaidKind.XYChart -> xyChart(diagram)
        MermaidKind.ClassDiagram, MermaidKind.StateDiagram -> flowchart(diagram)
        MermaidKind.ERDiagram -> erDiagram(diagram)
    }

    private fun erDiagram(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.er)
        val horizontal = data.direction == MermaidDirection.LR || data.direction == MermaidDirection.RL
        val reverse = data.direction == MermaidDirection.RL || data.direction == MermaidDirection.BT
        val ordered = if (reverse) data.entities.reversed() else data.entities
        val boxes = linkedMapOf<String, MermaidRect>()
        var cursor = 24f
        ordered.forEach { entity ->
            val height = 42f + entity.attributes.size * 25f + if (entity.attributes.isEmpty()) 0f else 8f
            boxes[entity.id] = if (horizontal) MermaidRect(cursor, 80f, 190f, height)
                else MermaidRect(108f, cursor, 204f, height)
            cursor += if (horizontal) 270f else height + 106f
        }
        val width = if (horizontal) cursor - 270f + 214f else 420f
        val height = if (horizontal) (boxes.values.maxOf { it.y + it.height } + 110f) else cursor - 106f + 24f
        val indices = ordered.mapIndexed { index, entity -> entity.id to index }.toMap()
        val relations = data.relationships.mapIndexed { edgeIndex, edge ->
            val from = boxes.getValue(edge.from)
            val to = boxes.getValue(edge.to)
            val distance = kotlin.math.abs(indices.getValue(edge.from) - indices.getValue(edge.to))
            val points = if (from == to) {
                val x = from.x + from.width
                val y = from.y + from.height / 2
                listOf(MermaidPoint(x, y - 14f), MermaidPoint(x + 42f, y - 14f),
                    MermaidPoint(x + 42f, y + 14f), MermaidPoint(x, y + 14f))
            } else if (horizontal) {
                val rightward = to.x > from.x
                val start = MermaidPoint(if (rightward) from.x + from.width else from.x, from.centerY)
                val end = MermaidPoint(if (rightward) to.x else to.x + to.width, to.centerY)
                if (distance <= 1) listOf(start, end) else {
                    val viaY = 38f - edgeIndex * 12f
                    listOf(start, MermaidPoint(start.x + if (rightward) 18f else -18f, viaY),
                        MermaidPoint(end.x + if (rightward) -18f else 18f, viaY), end)
                }
            } else {
                val downward = to.y > from.y
                val start = MermaidPoint(from.centerX, if (downward) from.y + from.height else from.y)
                val end = MermaidPoint(to.centerX, if (downward) to.y else to.y + to.height)
                if (distance <= 1) listOf(start, end) else {
                    val viaX = 354f + edgeIndex * 12f
                    listOf(start, MermaidPoint(viaX, start.y + if (downward) 18f else -18f),
                        MermaidPoint(viaX, end.y + if (downward) -18f else 18f), end)
                }
            }
            val mid = points[points.size / 2]
            val labelAt = if (points.size == 2) MermaidPoint((points[0].x + points[1].x) / 2,
                (points[0].y + points[1].y) / 2) else mid
            MermaidERPlacedRelationship(edge, points, labelAt)
        }
        return MermaidLayoutResult(width, height, emptyMap(), emptyList(), emptyMap(),
            er = MermaidERPlacement(boxes, relations))
    }

    private fun radar(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.radar)
        val center = MermaidPoint(210f, if (data.title == null) 174f else 198f)
        val radius = 118f
        fun point(index: Int, ratio: Double): MermaidPoint {
            val angle = -PI / 2 + index * 2 * PI / data.axes.size
            return MermaidPoint(center.x + cos(angle).toFloat() * radius * ratio.toFloat(),
                center.y + sin(angle).toFloat() * radius * ratio.toFloat())
        }
        val axes = data.axes.indices.map { point(it, 1.0) }
        val rings = (1..data.ticks).map { tick ->
            data.axes.indices.map { point(it, tick.toDouble() / data.ticks) }
        }
        val span = data.effectiveMax - data.effectiveMin
        val curves = data.curves.map { curve ->
            curve.values.mapIndexed { index, value ->
                point(index, ((value - data.effectiveMin) / span).coerceIn(0.0, 1.0))
            }
        }
        val legendY = center.y + radius + 52f
        val height = legendY + if (data.showLegend) data.curves.size * 26f + 20f else 16f
        return MermaidLayoutResult(420f, height, emptyMap(), emptyList(), emptyMap(),
            radar = MermaidRadarPlacement(center, radius, axes, rings, curves, legendY))
    }

    private fun xyChart(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.xyChart)
        val horizontal = data.orientation == MermaidXYOrientation.Horizontal
        val pointCount = data.pointCount
        val top = if (data.title == null) 28f else 68f
        val plot = if (horizontal) MermaidRect(116f, top, 320f, (pointCount * 58f).coerceAtLeast(190f))
            else MermaidRect(64f, top, (pointCount * 76f).coerceAtLeast(310f), 250f)
        val min = data.effectiveMin
        val span = data.effectiveMax - min
        fun scaled(value: Double): Float = ((value - min) / span).coerceIn(0.0, 1.0).toFloat()
        val baseline = if (horizontal) plot.x + plot.width * scaled(0.0)
            else plot.y + plot.height * (1f - scaled(0.0))
        val bars = mutableListOf<MermaidXYBarPlacement>()
        val lines = mutableListOf<Pair<Int, List<MermaidPoint>>>()
        val barSeries = data.series.withIndex().filter { it.value.type == MermaidXYSeriesType.Bar }
        val step = if (horizontal) plot.height / pointCount else plot.width / pointCount
        val centers = List(pointCount) { index ->
            if (horizontal) plot.y + (index + 0.5f) * step else plot.x + (index + 0.5f) * step
        }
        val barWidth = (step * 0.7f / barSeries.size.coerceAtLeast(1)).coerceAtMost(24f)
        barSeries.forEachIndexed { barOrder, indexed ->
            indexed.value.values.forEachIndexed { index, value ->
                val offset = (barOrder - (barSeries.size - 1) / 2f) * barWidth
                val end = if (horizontal) plot.x + plot.width * scaled(value)
                    else plot.y + plot.height * (1f - scaled(value))
                val rect = if (horizontal) MermaidRect(minOf(baseline, end), centers[index] + offset - barWidth / 2,
                    kotlin.math.abs(end - baseline).coerceAtLeast(1f), barWidth)
                else MermaidRect(centers[index] + offset - barWidth / 2, minOf(baseline, end),
                    barWidth, kotlin.math.abs(end - baseline).coerceAtLeast(1f))
                bars += MermaidXYBarPlacement(rect, indexed.index)
            }
        }
        data.series.forEachIndexed { seriesIndex, series ->
            if (series.type == MermaidXYSeriesType.Line) {
                lines += seriesIndex to series.values.mapIndexed { index, value ->
                    if (horizontal) MermaidPoint(plot.x + plot.width * scaled(value), centers[index])
                    else MermaidPoint(centers[index], plot.y + plot.height * (1f - scaled(value)))
                }
            }
        }
        val width = plot.x + plot.width + 24f
        val height = plot.y + plot.height + if (horizontal) 42f else 82f
        return MermaidLayoutResult(width, height, emptyMap(), emptyList(), emptyMap(),
            xyChart = MermaidXYPlacement(plot, bars, lines, centers, baseline))
    }

    private fun gantt(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.gantt)
        val chartX = 190f
        val chartWidth = ((data.maxDay - data.minDay + 1).coerceAtMost(180) * 16f).coerceIn(400f, 1000f)
        val headerY = if (data.title.isNullOrBlank()) 18f else 56f
        val rowStart = headerY + 54f
        val dayWidth = chartWidth / (data.maxDay - data.minDay + 1).coerceAtLeast(1)
        val tasks = data.tasks.mapIndexed { index, task ->
            val x = chartX + (task.startDay - data.minDay) * dayWidth
            val width = if (task.status == MermaidGanttStatus.Milestone) 16f
                else ((task.endDay - task.startDay + 1) * dayWidth).toFloat().coerceAtLeast(6f)
            val y = rowStart + index * 44f
            MermaidGanttTaskPlacement(task, MermaidRect(x.toFloat(), y + 12f, width, 20f), y)
        }
        return MermaidLayoutResult(chartX + chartWidth + 24f, rowStart + tasks.size * 44f + 24f,
            emptyMap(), emptyList(), emptyMap(),
            gantt = MermaidGanttPlacement(chartX, chartWidth, headerY, data.minDay, data.maxDay, tasks))
    }

    private fun kanban(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.kanban)
        val top = if (data.title.isNullOrBlank()) 16f else 58f
        val columns = data.columns.mapIndexed { index, column ->
            val x = 16f + index * 236f
            val height = 68f + column.tasks.size * 92f + 12f
            MermaidKanbanColumnPlacement(column, MermaidRect(x, top, 220f, height),
                column.tasks.indices.map { card -> MermaidRect(x + 8f, top + 60f + card * 92f, 204f, 82f) })
        }
        return MermaidLayoutResult(16f + data.columns.size * 236f,
            columns.maxOf { it.box.y + it.box.height } + 16f,
            emptyMap(), emptyList(), emptyMap(), kanban = MermaidKanbanPlacement(columns))
    }

    private fun pie(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.pie)
        val titleHeight = if (data.title.isNullOrBlank()) 0f else 22f
        val center = MermaidPoint(180f, 138f + titleHeight)
        val legendY = 268f + titleHeight
        val height = legendY + data.slices.size * 30f + 20f
        var angle = -90f
        val slices = data.slices.mapIndexed { index, slice ->
            val sweep = (slice.value / data.totalValue * 360).toFloat()
            MermaidPieSlicePlacement(slice, angle, sweep, index).also { angle += sweep }
        }
        return MermaidLayoutResult(360f, height, emptyMap(), emptyList(), emptyMap(),
            pie = MermaidPiePlacement(center, 108f, legendY, slices))
    }

    private fun timeline(diagram: MermaidDiagram): MermaidLayoutResult {
        val data = requireNotNull(diagram.timeline)
        val axisY = if (data.title.isNullOrBlank()) 72f else 102f
        val cardY = axisY + 50f
        val sections = data.sections.mapIndexed { index, section ->
            val x = 24f + index * 192f
            val height = 36f + section.events.size * 30f + section.events.count { !it.description.isNullOrBlank() } * 20f
            MermaidTimelineSectionPlacement(section, MermaidRect(x, cardY, 168f, height),
                MermaidPoint(x + 84f, axisY), index)
        }
        val height = sections.maxOf { it.card.y + it.card.height } + 24f
        return MermaidLayoutResult(48f + sections.size * 192f, height, emptyMap(), emptyList(), emptyMap(),
            timeline = MermaidTimelinePlacement(axisY, sections))
    }

    private fun flowchart(diagram: MermaidDiagram): MermaidLayoutResult {
        if (diagram.nodes.isEmpty()) return MermaidLayoutResult(0f, 0f, emptyMap(), emptyList(), emptyMap())
        if (diagram.kind == MermaidKind.Flowchart && diagram.subgraphs.isNotEmpty()) return groupedFlowchart(diagram)
        val nodeIds = diagram.nodes.mapTo(mutableSetOf()) { it.id }
        // Match Flutter's Dagre layout: keep cycle-closing edges for drawing, but
        // exclude DFS back edges while assigning ranks so a single loop does not
        // collapse every node in the cycle into the same layer.
        val successors = diagram.nodes.associate { it.id to mutableListOf<String>() }
        diagram.edges.forEach { edge ->
            if (edge.from in nodeIds && edge.to in nodeIds && edge.from != edge.to) {
                successors.getValue(edge.from) += edge.to
            }
        }
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        val backEdges = mutableSetOf<Pair<String, String>>()
        fun findBackEdges(id: String) {
            if (!visited.add(id)) return
            inStack += id
            successors.getValue(id).forEach { next ->
                if (next in inStack) backEdges += id to next
                else if (next !in visited) findBackEdges(next)
            }
            inStack -= id
        }
        diagram.nodes.forEach { findBackEdges(it.id) }
        val outgoing = diagram.nodes.associate { it.id to mutableListOf<String>() }
        val indegree = diagram.nodes.associate { it.id to 0 }.toMutableMap()
        diagram.edges.forEach { edge ->
            if (edge.from in nodeIds && edge.to in nodeIds && edge.from != edge.to &&
                (edge.from to edge.to) !in backEdges) {
                outgoing.getValue(edge.from) += edge.to
                indegree[edge.to] = indegree.getValue(edge.to) + 1
            }
        }
        val rank = diagram.nodes.associate { it.id to 0 }.toMutableMap()
        val queue = ArrayDeque(diagram.nodes.filter { indegree.getValue(it.id) == 0 }.map { it.id })
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (to in outgoing.getValue(from)) {
                rank[to] = max(rank.getValue(to), rank.getValue(from) + 1)
                indegree[to] = indegree.getValue(to) - 1
                if (indegree.getValue(to) == 0) queue.addLast(to)
            }
        }
        val layers = diagram.nodes.groupBy { rank.getValue(it.id) }.toSortedMap()
        val horizontal = diagram.direction == MermaidDirection.LR || diagram.direction == MermaidDirection.RL
        val selfLoops = if (diagram.kind == MermaidKind.StateDiagram)
            diagram.edges.filter { it.from == it.to } else emptyList()
        fun labelWidth(label: String): Float = label.sumOf { if (it.code > 127) 16.0 else 8.0 }.toFloat() + 8f
        val loopCrossGap = if (selfLoops.isEmpty()) 40f else if (horizontal) 72f else
            max(40f, (selfLoops.maxOfOrNull { labelWidth(it.label.orEmpty()) } ?: 0f) + 72f)
        val rankGap = if (horizontal && selfLoops.isNotEmpty())
            max(64f, (selfLoops.maxOfOrNull { labelWidth(it.label.orEmpty()) } ?: 0f) + 24f)
            else 64f
        val positions = linkedMapOf<String, MermaidRect>()
        var main = 24f
        var maxCross = 0f
        for (layer in layers.values) {
            val mainSize = layer.maxOf { if (horizontal) nodeWidth(it) else nodeHeight(it) }
            var cross = if (horizontal && selfLoops.isNotEmpty()) 84f else 24f
            for (node in layer) {
                val width = nodeWidth(node)
                val height = nodeHeight(node)
                positions[node.id] = if (horizontal) MermaidRect(main + (mainSize - width) / 2, cross, width, height)
                else MermaidRect(cross, main + (mainSize - height) / 2, width, height)
                cross += (if (horizontal) height else width) + loopCrossGap
            }
            maxCross = max(maxCross, cross - loopCrossGap + 24f)
            main += mainSize + rankGap
        }
        val mainExtent = main - rankGap + 24f
        var width = if (horizontal) mainExtent else maxCross
        val height = if (horizontal) maxCross else mainExtent
        if (diagram.direction == MermaidDirection.RL || diagram.direction == MermaidDirection.BT) {
            positions.replaceAll { _, box ->
                if (horizontal) box.copy(x = width - box.x - box.width)
                else box.copy(y = height - box.y - box.height)
            }
        }
        val groupBoxes = diagram.subgraphs.mapNotNull { group ->
            val contained = group.nodeIds.mapNotNull(positions::get)
            if (contained.isEmpty()) null else {
                val left = contained.minOf { it.x } - 18f
                val top = (contained.minOf { it.y } - 32f).coerceAtLeast(2f)
                val right = contained.maxOf { it.x + it.width } + 18f
                val bottom = contained.maxOf { it.y + it.height } + 18f
                group.id to MermaidRect(left, top, right - left, bottom - top)
            }
        }.toMap()
        val placedEdges = diagram.edges.mapNotNull { edge ->
            val from = positions[edge.from] ?: groupBoxes[edge.from] ?: return@mapNotNull null
            val to = positions[edge.to] ?: groupBoxes[edge.to] ?: return@mapNotNull null
            if (edge.from == edge.to && diagram.kind == MermaidKind.StateDiagram) {
                val estimatedWidth = labelWidth(edge.label.orEmpty())
                val start = if (horizontal) MermaidPoint(from.x + from.width * .3f, from.y)
                    else MermaidPoint(from.x + from.width, from.y + from.height * .3f)
                val end = if (horizontal) MermaidPoint(from.x + from.width * .7f, from.y)
                    else MermaidPoint(from.x + from.width, from.y + from.height * .7f)
                val controls = if (horizontal)
                    MermaidPoint(start.x - 25f, start.y - 45f) to MermaidPoint(end.x + 25f, end.y - 45f)
                    else MermaidPoint(start.x + 45f, start.y - 25f) to MermaidPoint(end.x + 45f, end.y + 25f)
                val label = if (edge.label.isNullOrBlank()) null else if (horizontal)
                    MermaidRect(from.x, from.y - 58f, estimatedWidth, 18f)
                    else MermaidRect(from.x + from.width + 48f, from.centerY - 9f, estimatedWidth, 18f)
                if (!horizontal) width = max(width, from.x + from.width + 64f)
                if (label != null) width = max(width, label.x + label.width + 24f)
                return@mapNotNull MermaidPlacedEdge(edge, start, end, controls, label)
            }
            val start: MermaidPoint
            val end: MermaidPoint
            when (diagram.direction) {
                MermaidDirection.TB -> {
                    start = MermaidPoint(from.centerX, from.y + from.height)
                    end = MermaidPoint(to.centerX, to.y)
                }
                MermaidDirection.BT -> {
                    start = MermaidPoint(from.centerX, from.y)
                    end = MermaidPoint(to.centerX, to.y + to.height)
                }
                MermaidDirection.LR -> {
                    start = MermaidPoint(from.x + from.width, from.centerY)
                    end = MermaidPoint(to.x, to.centerY)
                }
                MermaidDirection.RL -> {
                    start = MermaidPoint(from.x, from.centerY)
                    end = MermaidPoint(to.x + to.width, to.centerY)
                }
            }
            MermaidPlacedEdge(edge, start, end)
        }
        return MermaidLayoutResult(width, height, positions, placedEdges, groupBoxes)
    }

    /** Layout each nested subgraph from its own edges before placing it as a unit in its parent. */
    private fun groupedFlowchart(diagram: MermaidDiagram): MermaidLayoutResult {
        data class UnitBox(
            val id: String, val group: Boolean, var width: Float, var height: Float,
            val children: List<UnitBox> = emptyList(), var x: Float = 0f, var y: Float = 0f,
        )
        val horizontal = diagram.direction == MermaidDirection.LR || diagram.direction == MermaidDirection.RL
        val groups = diagram.subgraphs.associateBy { it.id }
        val nodes = diagram.nodes.associateBy { it.id }
        val nodeOrder = diagram.nodes.mapIndexed { index, node -> node.id to index }.toMap()
        val directOwner = diagram.subgraphs.flatMap { group -> group.directNodeIds.map { it to group.id } }.toMap()
        // An edge between descendants ranks their immediate containers too. An edge
        // inside one container ranks its immediate children instead of being ignored.
        fun immediateChild(endpoint: String, parentId: String?): String? {
            var id = endpoint
            if (id !in nodes && id !in groups) return null
            while (true) {
                val owner = if (id in groups) groups.getValue(id).parentId else directOwner[id]
                if (owner == parentId) return id
                if (owner == null) return null
                id = owner
            }
        }
        fun arrange(units: List<UnitBox>, parentId: String?, marginX: Float, marginY: Float): Pair<Float, Float> {
            if (units.isEmpty()) return marginX * 2 to marginY * 2
            val ids = units.mapTo(mutableSetOf()) { it.id }
            val successors = units.associate { it.id to linkedSetOf<String>() }
            diagram.edges.forEach { edge ->
                val from = immediateChild(edge.from, parentId)
                val to = immediateChild(edge.to, parentId)
                if (from != null && to != null && from != to && from in ids && to in ids) {
                    successors.getValue(from) += to
                }
            }
            // Mermaid graphs commonly close a path with a back edge. Keep that edge
            // for drawing while preventing it from collapsing the forward ranks.
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()
            val backEdges = mutableSetOf<Pair<String, String>>()
            fun visit(id: String) {
                if (!visited.add(id)) return
                inStack += id
                successors.getValue(id).forEach { next ->
                    if (next in inStack) backEdges += id to next
                    else if (next !in visited) visit(next)
                }
                inStack -= id
            }
            // The first declared connection establishes the forward path when a
            // cycle has no natural root; node declarations may appear in any order.
            diagram.edges.mapNotNull { immediateChild(it.from, parentId) }
                .filter { it in ids }.forEach(::visit)
            units.forEach { visit(it.id) }
            val indegree = units.associate { it.id to 0 }.toMutableMap()
            successors.forEach { (from, destinations) ->
                destinations.forEach { to -> if ((from to to) !in backEdges) indegree[to] = indegree.getValue(to) + 1 }
            }
            val rank = units.associate { it.id to 0 }.toMutableMap()
            val queue = ArrayDeque(units.filter { indegree.getValue(it.id) == 0 }.map { it.id })
            while (queue.isNotEmpty()) {
                val from = queue.removeFirst()
                successors.getValue(from).forEach { to ->
                    if ((from to to) !in backEdges) {
                        rank[to] = max(rank.getValue(to), rank.getValue(from) + 1)
                        indegree[to] = indegree.getValue(to) - 1
                        if (indegree.getValue(to) == 0) queue.addLast(to)
                    }
                }
            }
            var main = if (horizontal) marginX else marginY
            var crossExtent = 0f
            units.groupBy { rank.getValue(it.id) }.toSortedMap().values.forEach { layer ->
                val mainSize = layer.maxOf { if (horizontal) it.width else it.height }
                var cross = if (horizontal) marginY else marginX
                layer.forEach { unit ->
                    if (horizontal) { unit.x = main + (mainSize - unit.width) / 2; unit.y = cross }
                    else { unit.x = cross; unit.y = main + (mainSize - unit.height) / 2 }
                    cross += (if (horizontal) unit.height else unit.width) + 40f
                }
                crossExtent = max(crossExtent, cross - 40f)
                main += mainSize + 64f
            }
            return if (horizontal) (main - 64f + marginX) to (crossExtent + marginY)
                else (crossExtent + marginX) to (main - 64f + marginY)
        }
        fun children(parentId: String?): List<UnitBox> {
            val direct = if (parentId == null) diagram.nodes.filter { it.id !in directOwner }
                else diagram.nodes.filter { directOwner[it.id] == parentId }
            val childGroups = diagram.subgraphs.filter { it.parentId == parentId }
            val entries = direct.map { UnitBox(it.id, false, nodeWidth(it), nodeHeight(it)) } +
                childGroups.map { group ->
                    val nested = children(group.id)
                    val size = arrange(nested, group.id, 18f, 34f)
                    UnitBox(group.id, true, max(size.first, group.label.length * 8f + 36f),
                        max(size.second, 76f), nested)
                }
            return entries.sortedWith(compareBy<UnitBox> {
                if (it.group) groups.getValue(it.id).nodeIds.mapNotNull(nodeOrder::get).minOrNull() ?: Int.MAX_VALUE
                else nodeOrder[it.id] ?: Int.MAX_VALUE
            }.thenBy { it.id })
        }
        val roots = children(null)
        arrange(roots, null, 24f, 24f)
        val positions = linkedMapOf<String, MermaidRect>()
        val groupBoxes = linkedMapOf<String, MermaidRect>()
        fun place(unit: UnitBox, offsetX: Float, offsetY: Float) {
            val x = offsetX + unit.x
            val y = offsetY + unit.y
            val rect = MermaidRect(x, y, unit.width, unit.height)
            if (!unit.group) positions[unit.id] = rect
            else {
                groupBoxes[unit.id] = rect
                unit.children.forEach { place(it, x, y) }
            }
        }
        roots.forEach { place(it, 0f, 0f) }
        val allBoxes = positions.values + groupBoxes.values
        val width = (allBoxes.maxOfOrNull { it.x + it.width } ?: 0f) + 24f
        val height = (allBoxes.maxOfOrNull { it.y + it.height } ?: 0f) + 24f
        if (diagram.direction == MermaidDirection.RL || diagram.direction == MermaidDirection.BT) {
            positions.replaceAll { _, box -> if (horizontal) box.copy(x = width - box.x - box.width)
                else box.copy(y = height - box.y - box.height) }
            groupBoxes.replaceAll { _, box -> if (horizontal) box.copy(x = width - box.x - box.width)
                else box.copy(y = height - box.y - box.height) }
        }
        val placedEdges = diagram.edges.mapNotNull { edge ->
            val from = positions[edge.from] ?: groupBoxes[edge.from] ?: return@mapNotNull null
            val to = positions[edge.to] ?: groupBoxes[edge.to] ?: return@mapNotNull null
            val start: MermaidPoint
            val end: MermaidPoint
            when (diagram.direction) {
                MermaidDirection.TB -> { start = MermaidPoint(from.centerX, from.y + from.height); end = MermaidPoint(to.centerX, to.y) }
                MermaidDirection.BT -> { start = MermaidPoint(from.centerX, from.y); end = MermaidPoint(to.centerX, to.y + to.height) }
                MermaidDirection.LR -> { start = MermaidPoint(from.x + from.width, from.centerY); end = MermaidPoint(to.x, to.centerY) }
                MermaidDirection.RL -> { start = MermaidPoint(from.x, from.centerY); end = MermaidPoint(to.x + to.width, to.centerY) }
            }
            MermaidPlacedEdge(edge, start, end)
        }
        return MermaidLayoutResult(width, height, positions, placedEdges, groupBoxes)
    }

    private fun sequence(diagram: MermaidDiagram): MermaidLayoutResult {
        if (diagram.nodes.isEmpty()) return MermaidLayoutResult(0f, 0f, emptyMap(), emptyList(), emptyMap())
        val positions = diagram.nodes.mapIndexed { index, node ->
            node.id to MermaidRect(24f + index * 156f, 20f, 116f, 44f)
        }.toMap()
        val edges = diagram.edges.mapIndexedNotNull { index, edge ->
            val from = positions[edge.from] ?: return@mapIndexedNotNull null
            val to = positions[edge.to] ?: return@mapIndexedNotNull null
            val y = 106f + index * 68f
            MermaidPlacedEdge(edge, MermaidPoint(from.centerX, y), MermaidPoint(to.centerX, y))
        }
        return MermaidLayoutResult(positions.values.maxOf { it.x + it.width } + 24f,
            max(142f, 106f + edges.size * 68f), positions, edges, emptyMap())
    }

    private fun nodeWidth(node: MermaidNode): Float {
        if (node.shape == MermaidShape.StateStart || node.shape == MermaidShape.StateEnd) return 24f
        if (node.compartments.isNotEmpty()) {
            val longest = (listOf(node.label) + node.compartments.flatten()).maxOf { it.length }
            return (longest * 8f + 32f).coerceIn(120f, 320f)
        }
        val base = (node.label.length * 8f + 28f).coerceIn(88f, 280f)
        return when (node.shape) {
            MermaidShape.Diamond, MermaidShape.Hexagon -> base + 30f
            MermaidShape.Parallelogram, MermaidShape.ParallelogramAlt -> base + 22f
            MermaidShape.Trapezoid, MermaidShape.TrapezoidAlt, MermaidShape.Subroutine -> base + 12f
            MermaidShape.Circle -> max(base, 80f)
            else -> base
        }
    }

    private fun nodeHeight(node: MermaidNode): Float = when (node.shape) {
        MermaidShape.StateStart, MermaidShape.StateEnd -> 24f
        MermaidShape.Diamond, MermaidShape.Hexagon, MermaidShape.Circle -> 76f
        MermaidShape.Cylinder -> 64f
        else -> if (node.compartments.isNotEmpty()) 46f + node.compartments.sumOf { it.size }.toFloat() * 22f + 12f else 48f
    }
}
