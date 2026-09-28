package com.jackcaow.smoothmarkdown.mermaid

/** Parsed subset of Mermaid supported by the native Compose prototype. */
enum class MermaidKind { Flowchart, Sequence, Pie, Timeline, Gantt, Kanban }
enum class MermaidDirection { TB, BT, LR, RL }
enum class MermaidShape {
    Rectangle, Rounded, Stadium, Diamond, Hexagon, Circle, Subroutine,
    Cylinder, Asymmetric, Parallelogram, Trapezoid,
}
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
)

data class MermaidEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val line: MermaidLine = MermaidLine.Solid,
    val arrow: MermaidArrow = MermaidArrow.Arrow,
    val subgraphEdge: Boolean = false,
)

data class MermaidSubgraph(val id: String, val label: String, val nodeIds: List<String>)

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
) {
    fun node(id: String): MermaidNode? = nodes.firstOrNull { it.id == id }
}
