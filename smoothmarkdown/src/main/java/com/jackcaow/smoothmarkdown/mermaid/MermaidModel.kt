package com.jackcaow.smoothmarkdown.mermaid

/** Parsed subset of Mermaid supported by the native Compose prototype. */
enum class MermaidKind { Flowchart, Sequence, Pie, Timeline }
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

data class MermaidDiagram(
    val kind: MermaidKind,
    val direction: MermaidDirection,
    val nodes: List<MermaidNode>,
    val edges: List<MermaidEdge>,
    val subgraphs: List<MermaidSubgraph> = emptyList(),
    val pie: MermaidPieData? = null,
    val timeline: MermaidTimelineData? = null,
) {
    fun node(id: String): MermaidNode? = nodes.firstOrNull { it.id == id }
}
