package com.jackcaow.smoothmarkdown.mermaid

/** Bounded port of Flutter's structured graph parser. Unknown statements reject the whole diagram. */
class MermaidStructuredParser(private val kind: MermaidKind) {
    fun parse(lines: List<String>): MermaidDiagram? {
        if (kind != MermaidKind.ClassDiagram && kind != MermaidKind.StateDiagram) return null
        val nodes = linkedMapOf<String, MermaidNode>()
        val edges = mutableListOf<MermaidEdge>()
        var direction = MermaidDirection.TB
        var block: String? = null
        val rows = mutableListOf<String>()
        fun add(id: String): MermaidNode = nodes.getOrPut(id) {
            MermaidNode(id, shape = if (kind == MermaidKind.StateDiagram) MermaidShape.Rounded else MermaidShape.Rectangle,
                compartments = if (kind == MermaidKind.ClassDiagram) listOf(emptyList(), emptyList()) else emptyList())
        }
        fun append(id: String, row: String) {
            val node = add(id)
            val parts = node.compartments.map { it.toMutableList() }
            parts[if ('(' in row) 1 else 0].add(row)
            nodes[id] = node.copy(compartments = parts)
        }
        for (original in lines.drop(1)) {
            val line = original.trim().removeSuffix(";")
            if (line.isBlank() || line.startsWith("%%")) continue
            if (block != null) {
                if (line == "}") {
                    if (kind == MermaidKind.ClassDiagram) rows.forEach { append(block!!, it) }
                    else return null // composite states need nested layout
                    block = null; rows.clear()
                } else {
                    if ('{' in line || '}' in line) return null
                    rows += line
                }
                continue
            }
            val directionMatch = Regex("^direction\\s+(TB|TD|BT|LR|RL)$", RegexOption.IGNORE_CASE).matchEntire(line)
            if (directionMatch != null) {
                direction = MermaidDirection.valueOf(directionMatch.groupValues[1].uppercase().replace("TD", "TB"))
                continue
            }
            if (kind == MermaidKind.StateDiagram) {
                val transition = STATE_TRANSITION.matchEntire(line)
                if (transition != null) {
                    fun endpoint(value: String, source: Boolean): String {
                        if (value != "[*]") { add(value); return value }
                        val id = if (source) "\$state:start" else "\$state:end"
                        nodes.putIfAbsent(id, MermaidNode(id, "", if (source) MermaidShape.StateStart else MermaidShape.StateEnd))
                        return id
                    }
                    edges += MermaidEdge(endpoint(transition.groupValues[1], true),
                        endpoint(transition.groupValues[2], false), transition.groupValues[3].ifBlank { null })
                    continue
                }
                val alias = STATE_ALIAS.matchEntire(line)
                if (alias != null) {
                    val id = alias.groupValues[2]; nodes[id] = add(id).copy(label = alias.groupValues[1]); continue
                }
                val declaration = STATE_DECLARATION.matchEntire(line)
                if (declaration != null) {
                    val id = declaration.groupValues[1]
                    nodes[id] = add(id).copy(shape = if (line.endsWith("<<choice>>")) MermaidShape.Diamond else MermaidShape.Rounded,
                        label = if (line.endsWith("<<choice>>")) "" else add(id).label)
                    continue
                }
                val description = MEMBER.matchEntire(line)
                if (description != null) {
                    val id = description.groupValues[1]; nodes[id] = add(id).copy(label = description.groupValues[2]); continue
                }
                if (IDENTIFIER.matches(line)) { add(line); continue }
                return null
            }
            val declaration = CLASS_DECLARATION.matchEntire(line)
            if (declaration != null) {
                val id = declaration.groupValues[1]
                nodes[id] = add(id).copy(label = declaration.groupValues[2].ifBlank { id })
                if (declaration.groupValues[3].isNotBlank()) block = id
                continue
            }
            val relation = CLASS_RELATION.matchEntire(line)
            if (relation != null) {
                val from = relation.groupValues[1]; val to = relation.groupValues[5]
                val op = relation.groupValues[3]
                add(from); add(to)
                val marker = when {
                    '|' in op -> MermaidEdgeMarker.Inheritance
                    '*' in op -> MermaidEdgeMarker.Composition
                    'o' in op -> MermaidEdgeMarker.Aggregation
                    else -> null
                }
                val atSource = op.startsWith('<') || op.startsWith('*') || op.startsWith('o')
                val reverse = marker == null && op.startsWith('<')
                edges += MermaidEdge(if (reverse) to else from, if (reverse) from else to,
                    relation.groupValues[6].ifBlank { null },
                    line = if ('.' in op) MermaidLine.Dotted else MermaidLine.Solid,
                    arrow = if (marker == null && ('<' in op || '>' in op)) MermaidArrow.Arrow else MermaidArrow.None,
                    sourceMarker = if (atSource) marker else null,
                    targetMarker = if (atSource) null else marker,
                    sourceLabel = relation.groupValues[if (reverse) 4 else 2].ifBlank { null },
                    targetLabel = relation.groupValues[if (reverse) 2 else 4].ifBlank { null })
                continue
            }
            val member = MEMBER.matchEntire(line)
            if (member != null) { append(member.groupValues[1], member.groupValues[2]); continue }
            return null
        }
        if (block != null || nodes.isEmpty()) return null
        return MermaidDiagram(kind, direction, nodes.values.toList(), edges)
    }

    private companion object {
        const val ID = """[\w\u0080-\uFFFF-]+"""
        val IDENTIFIER = Regex("^$ID$")
        val STATE_TRANSITION = Regex("^(\\[\\*]|$ID)\\s*-->\\s*(\\[\\*]|$ID)(?:\\s*:\\s*(.*))?$")
        val STATE_ALIAS = Regex("^state\\s+\"(.*)\"\\s+as\\s+($ID)$")
        val STATE_DECLARATION = Regex("^state\\s+($ID)(?:\\s+<<choice>>)?$")
        val MEMBER = Regex("^($ID)\\s*:\\s*(.+)$")
        val CLASS_DECLARATION = Regex("^class\\s+($ID)(?:\\s*\\[\"(.*)\"\\])?\\s*(\\{)?$")
        val CLASS_RELATION = Regex("^($ID)(?:\\s+\"([^\"]*)\")?\\s*(<\\|--|--\\|>|<\\|\\.\\.|\\.\\.\\|>|\\*--|--\\*|o--|--o|<--|-->|<\\.\\.|\\.\\.>|--|\\.\\.)(?:\\s+\"([^\"]*)\")?\\s*($ID)(?:\\s*:\\s*(.*))?$")
    }
}
