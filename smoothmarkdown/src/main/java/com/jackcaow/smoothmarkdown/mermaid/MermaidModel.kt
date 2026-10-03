package com.jackcaow.smoothmarkdown.mermaid

/** Parsed subset of Mermaid supported by the native Compose prototype. */
enum class MermaidKind { Flowchart, Sequence, Pie, Timeline, Gantt, Kanban, Radar, XYChart, ClassDiagram, StateDiagram, ERDiagram, GitGraph, Mindmap }
enum class MermaidDirection { TB, BT, LR, RL }
enum class MermaidShape {
    Rectangle, Rounded, Stadium, Diamond, Hexagon, Circle, Subroutine,
    Cylinder, Asymmetric, Parallelogram, ParallelogramAlt, Trapezoid, TrapezoidAlt, StateStart, StateEnd,
}
enum class MermaidEdgeMarker { Inheritance, Composition, Aggregation }
enum class MermaidLine { Solid, Dotted, Thick }
enum class MermaidArrow { None, Arrow, Cross, Circle }
enum class MermaidParticipantType { Participant, Actor }

data class MermaidNodeStyle(
    val fill: Int? = null,
    val stroke: Int? = null,
    val strokeWidth: Float = 1f,
    val text: Int? = null,
)

data class MermaidNode(
    val id: String,
    val label: String = id,
    val shape: MermaidShape = MermaidShape.Rectangle,
    val style: MermaidNodeStyle? = null,
    val className: String? = null,
    val participantType: MermaidParticipantType = MermaidParticipantType.Participant,
    val compartments: List<List<String>> = emptyList(),
)

data class MermaidEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val line: MermaidLine = MermaidLine.Solid,
    val arrow: MermaidArrow = MermaidArrow.Arrow,
    val subgraphEdge: Boolean = false,
    val sourceMarker: MermaidEdgeMarker? = null,
    val targetMarker: MermaidEdgeMarker? = null,
    val sourceLabel: String? = null,
    val targetLabel: String? = null,
)

data class MermaidSubgraph(
    val id: String,
    val label: String,
    val nodeIds: List<String>,
    val parentId: String? = null,
    val directNodeIds: List<String> = nodeIds,
)

data class MermaidPieSlice(val label: String, val value: Double)
data class MermaidPieData(
    val title: String?,
    val slices: List<MermaidPieSlice>,
    val showValuesInLegend: Boolean,
) {
    val totalValue: Double get() = slices.sumOf { it.value }
    fun percentage(slice: MermaidPieSlice): Double = if (totalValue > 0) slice.value * 100 / totalValue else 0.0
}

data class MermaidTimelineEvent(val title: String, val description: String? = null)
data class MermaidTimelineSection(val title: String, val events: List<MermaidTimelineEvent>)
data class MermaidTimelineData(val title: String?, val sections: List<MermaidTimelineSection>)

enum class MermaidGanttStatus { Normal, Done, Active, Critical, Milestone }
data class MermaidGanttTask(
    val id: String, val name: String, val startDay: Long, val endDay: Long,
    val section: String?, val status: MermaidGanttStatus, val dependencies: List<String>,
)
data class MermaidGanttData(
    val title: String?, val tasks: List<MermaidGanttTask>, val sections: List<String>,
    val dateFormat: String, val axisFormat: String?, val excludes: String?, val todayMarker: Boolean,
) {
    val minDay: Long get() = tasks.minOf { it.startDay }
    val maxDay: Long get() = tasks.maxOf { it.endDay }
    fun task(id: String): MermaidGanttTask? = tasks.firstOrNull { it.id == id }
}

enum class MermaidKanbanPriority { VeryHigh, High, Normal, Low, VeryLow }
data class MermaidKanbanTask(
    val id: String, val description: String, val assigned: String?, val ticket: String?,
    val priority: MermaidKanbanPriority, val metadata: Map<String, String>,
)
data class MermaidKanbanColumn(
    val id: String, val title: String, val tasks: List<MermaidKanbanTask>, val wipLimit: Int?,
) { val isOverLimit: Boolean get() = wipLimit != null && tasks.size > wipLimit }
data class MermaidKanbanData(
    val title: String?, val columns: List<MermaidKanbanColumn>, val ticketBaseUrl: String?,
) {
    val allTasks: List<MermaidKanbanTask> get() = columns.flatMap { it.tasks }
    fun task(id: String): MermaidKanbanTask? = allTasks.firstOrNull { it.id == id }
}

enum class MermaidRadarGraticule { Polygon, Circle }
data class MermaidRadarAxis(val id: String, val label: String)
data class MermaidRadarCurve(val id: String, val label: String, val values: List<Double>)
data class MermaidRadarData(
    val title: String?, val axes: List<MermaidRadarAxis>, val curves: List<MermaidRadarCurve>,
    val showLegend: Boolean = true, val max: Double? = null, val min: Double? = null,
    val graticule: MermaidRadarGraticule = MermaidRadarGraticule.Polygon, val ticks: Int = 5,
) {
    val effectiveMin: Double get() = min ?: 0.0
    val effectiveMax: Double get() = max ?: (curves.flatMap { it.values }.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
}

enum class MermaidXYOrientation { Vertical, Horizontal }
enum class MermaidXYSeriesType { Bar, Line }
data class MermaidXYSeries(val type: MermaidXYSeriesType, val values: List<Double>)
data class MermaidXYData(
    val title: String?, val xAxisTitle: String?, val yAxisTitle: String?,
    val categories: List<String>, val xAxisMin: Double?, val xAxisMax: Double?,
    val yAxisMin: Double?, val yAxisMax: Double?, val orientation: MermaidXYOrientation,
    val series: List<MermaidXYSeries>,
) {
    val pointCount: Int get() = if (categories.isNotEmpty()) categories.size else series.maxOf { it.values.size }
    val effectiveMin: Double get() = yAxisMin ?: minOf(0.0, series.flatMap { it.values }.minOrNull() ?: 0.0)
    val effectiveMax: Double get() = yAxisMax ?: maxOf(0.0, series.flatMap { it.values }.maxOrNull() ?: 0.0)
}

enum class MermaidERCardinality { ExactlyOne, ZeroOrOne, OneOrMore, ZeroOrMore }
data class MermaidEREntity(val id: String, val label: String, val attributes: List<String>)
data class MermaidERRelationship(
    val from: String, val to: String, val label: String,
    val sourceCardinality: MermaidERCardinality, val targetCardinality: MermaidERCardinality,
    val dotted: Boolean,
)
data class MermaidERData(
    val entities: List<MermaidEREntity>, val relationships: List<MermaidERRelationship>,
    val direction: MermaidDirection,
) {
    fun entity(id: String): MermaidEREntity? = entities.firstOrNull { it.id == id }
}

data class MermaidDiagram(
    val kind: MermaidKind,
    val direction: MermaidDirection,
    val nodes: List<MermaidNode>,
    val edges: List<MermaidEdge>,
    val subgraphs: List<MermaidSubgraph> = emptyList(),
    val pie: MermaidPieData? = null,
    val timeline: MermaidTimelineData? = null,
    val gantt: MermaidGanttData? = null,
    val kanban: MermaidKanbanData? = null,
    val radar: MermaidRadarData? = null,
    val xyChart: MermaidXYData? = null,
    val er: MermaidERData? = null,
) {
    fun node(id: String): MermaidNode? = nodes.firstOrNull { it.id == id }
}
