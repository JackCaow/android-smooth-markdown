package com.jackcaow.smoothmarkdown.mermaid

import kotlin.math.max

data class MermaidPoint(val x: Float, val y: Float)
data class MermaidRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2
}
data class MermaidPlacedEdge(val edge: MermaidEdge, val start: MermaidPoint, val end: MermaidPoint)
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
data class MermaidKanbanColumnPlacement(
    val column: MermaidKanbanColumn, val box: MermaidRect, val cards: List<MermaidRect>,
)
data class MermaidKanbanPlacement(val columns: List<MermaidKanbanColumnPlacement>)
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
        val nodeIds = diagram.nodes.mapTo(mutableSetOf()) { it.id }
        val outgoing = diagram.nodes.associate { it.id to mutableListOf<String>() }
        val indegree = diagram.nodes.associate { it.id to 0 }.toMutableMap()
        diagram.edges.forEach { edge ->
            if (edge.from in nodeIds && edge.to in nodeIds && edge.from != edge.to) {
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
        // Cycles left after Kahn's pass stay in their original layer rather than recursing forever.
        val layers = diagram.nodes.groupBy { rank.getValue(it.id) }.toSortedMap()
        val horizontal = diagram.direction == MermaidDirection.LR || diagram.direction == MermaidDirection.RL
        val positions = linkedMapOf<String, MermaidRect>()
        var main = 24f
        var maxCross = 0f
        for (layer in layers.values) {
            val mainSize = layer.maxOf { if (horizontal) nodeWidth(it) else nodeHeight(it) }
            var cross = 24f
            for (node in layer) {
                val width = nodeWidth(node)
                val height = nodeHeight(node)
                positions[node.id] = if (horizontal) MermaidRect(main + (mainSize - width) / 2, cross, width, height)
                else MermaidRect(cross, main + (mainSize - height) / 2, width, height)
                cross += (if (horizontal) height else width) + 40f
            }
            maxCross = max(maxCross, cross - 40f + 24f)
            main += mainSize + 64f
        }
        val mainExtent = main - 64f + 24f
        val width = if (horizontal) mainExtent else maxCross
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
        val base = (node.label.length * 8f + 28f).coerceIn(88f, 280f)
        return when (node.shape) {
            MermaidShape.Diamond, MermaidShape.Hexagon -> base + 30f
            MermaidShape.Circle -> max(base, 80f)
            else -> base
        }
    }

    private fun nodeHeight(node: MermaidNode): Float = when (node.shape) {
        MermaidShape.Diamond, MermaidShape.Hexagon, MermaidShape.Circle -> 76f
        else -> 48f
    }
}
