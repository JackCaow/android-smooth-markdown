package com.jackcaow.smoothmarkdown.mermaid

/** A bounded spoken summary for Canvas diagrams, whose shapes are otherwise invisible to TalkBack. */
internal fun MermaidDiagram.accessibilitySummary(): String {
    fun <T> describe(items: List<T>, limit: Int = 12, render: (T) -> String): String {
        val visible = items.take(limit).joinToString("; ", transform = render)
        val more = if (items.size > limit) "; and ${items.size - limit} more" else ""
        return visible + more
    }
    return when (kind) {
        MermaidKind.Flowchart, MermaidKind.Sequence, MermaidKind.ClassDiagram, MermaidKind.StateDiagram -> {
            val title = when (kind) {
                MermaidKind.Flowchart -> "Flowchart"
                MermaidKind.Sequence -> "Sequence diagram"
                MermaidKind.ClassDiagram -> "Class diagram"
                else -> "State diagram"
            }
            "$title. ${nodes.size} nodes, ${edges.size} connections. " +
                (if (subgraphs.isEmpty()) "" else "Subgraphs: ${describe(subgraphs) { group ->
                    "${group.label}, ${group.nodeIds.size} nodes${group.parentId?.let { ", inside ${subgraphs.firstOrNull { it.id == group.parentId }?.label ?: it}" }.orEmpty()}"
                }}. ") +
                "Nodes: ${describe(nodes) { it.label.ifBlank { it.id } }}. " +
                "Connections: ${describe(edges) { edge ->
                    "${edge.from} to ${edge.to}${edge.label?.let { ", $it" }.orEmpty()}"
                }}."
        }
        MermaidKind.Pie -> "Pie chart${pie?.title?.let { ", $it" }.orEmpty()}. " +
            describe(pie?.slices.orEmpty()) { "${it.label}, ${it.value}" }
        MermaidKind.Timeline -> "Timeline${timeline?.title?.let { ", $it" }.orEmpty()}. " +
            describe(timeline?.sections.orEmpty()) { section ->
                "${section.title}: ${section.events.joinToString(", ") { it.title }}"
            }
        MermaidKind.Gantt -> "Gantt chart${gantt?.title?.let { ", $it" }.orEmpty()}. " +
            describe(gantt?.tasks.orEmpty()) { "${it.name}, ${it.status.name.lowercase()}" }
        MermaidKind.Kanban -> "Kanban board${kanban?.title?.let { ", $it" }.orEmpty()}. " +
            describe(kanban?.columns.orEmpty()) { column ->
                "${column.title}, ${column.tasks.size} tasks: ${describe(column.tasks, 6) { it.description }}"
            }
        MermaidKind.Radar -> "Radar chart${radar?.title?.let { ", $it" }.orEmpty()}. " +
            "Axes: ${describe(radar?.axes.orEmpty()) { it.label }}. " +
            "Curves: ${describe(radar?.curves.orEmpty()) { it.label }}."
        MermaidKind.XYChart -> "XY chart${xyChart?.title?.let { ", $it" }.orEmpty()}. " +
            "${xyChart?.pointCount ?: 0} points, ${xyChart?.series?.size ?: 0} series. " +
            "Categories: ${describe(xyChart?.categories.orEmpty()) { it }}."
        MermaidKind.ERDiagram -> "Entity relationship diagram. " +
            "Entities: ${describe(er?.entities.orEmpty()) { it.label }}. " +
            "Relationships: ${describe(er?.relationships.orEmpty()) { "${it.from} ${it.label} ${it.to}" }}."
    }
}
