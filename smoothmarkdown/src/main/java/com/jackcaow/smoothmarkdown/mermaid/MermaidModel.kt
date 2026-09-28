package com.jackcaow.smoothmarkdown.mermaid

/** Parsed subset of Mermaid supported by the native Compose prototype. */
enum class MermaidKind { Flowchart, Sequence }
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

data class MermaidDiagram(
    val kind: MermaidKind,
    val direction: MermaidDirection,
    val nodes: List<MermaidNode>,
    val edges: List<MermaidEdge>,
    val subgraphs: List<MermaidSubgraph> = emptyList(),
) {
    fun node(id: String): MermaidNode? = nodes.firstOrNull { it.id == id }
}
