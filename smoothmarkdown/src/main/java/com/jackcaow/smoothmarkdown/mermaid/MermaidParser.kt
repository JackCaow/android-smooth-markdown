package com.jackcaow.smoothmarkdown.mermaid

/** Dispatches supported native diagram syntaxes. */
object MermaidParser {
    fun parse(source: String): MermaidDiagram? {
        val rawLines = source.lineSequence().toList()
        val lines = source.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("%%") }.toList()
        val header = if (lines.firstOrNull() == "---") {
            val end = lines.drop(1).indexOf("---")
            if (end < 0) return null
            lines.getOrNull(end + 2) ?: return null
        } else lines.firstOrNull() ?: return null
        return when {
            Regex("^(graph|flowchart)\\s+(TD|TB|BT|LR|RL)$", RegexOption.IGNORE_CASE).matches(header) ->
                MermaidFlowchartParser().parse(lines)
            header.equals("sequenceDiagram", ignoreCase = true) -> MermaidSequenceParser().parse(lines)
            Regex("^pie(?:\\s+showData)?$", RegexOption.IGNORE_CASE).matches(header) -> MermaidPieParser().parse(lines)
            header.equals("timeline", ignoreCase = true) -> MermaidTimelineParser().parse(lines)
            header.equals("gantt", ignoreCase = true) -> MermaidGanttParser().parse(rawLines)
            header.equals("kanban", ignoreCase = true) -> MermaidKanbanParser().parse(rawLines)
            header.equals("radar-beta", ignoreCase = true) -> MermaidRadarParser().parse(lines)
            header.equals("classDiagram", ignoreCase = true) ->
                MermaidStructuredParser(MermaidKind.ClassDiagram).parse(lines)
            Regex("^stateDiagram(?:-v2)?$", RegexOption.IGNORE_CASE).matches(header) ->
                MermaidStructuredParser(MermaidKind.StateDiagram).parse(lines)
            Regex("^xychart(?:-beta)?(?:\\s+horizontal)?$", RegexOption.IGNORE_CASE).matches(header) ->
                MermaidXYChartParser().parse(lines)
            header.equals("erDiagram", ignoreCase = true) -> MermaidERParser().parse(lines)
            else -> null
        }
    }
}

/** Flowchart subset, mirroring Flutter's FlowchartParser node/edge/class/subgraph semantics. */
class MermaidFlowchartParser {
    private val nodes = linkedMapOf<String, MermaidNode>()
    private val edges = mutableListOf<MermaidEdge>()
    private val subgraphs = mutableListOf<MermaidSubgraph>()
    private val groups = mutableListOf<GroupState>()
    private val groupIds = mutableSetOf<String>()
    private val classDefs = mutableMapOf<String, MermaidNodeStyle>()
    private val assignments = mutableMapOf<String, String>()
    private val inlineStyles = mutableMapOf<String, MermaidNodeStyle>()

    fun parse(lines: List<String>): MermaidDiagram? {
        val header = lines.firstOrNull()?.trim() ?: return null
        val match = Regex("^(?:graph|flowchart)\\s+(TD|TB|BT|LR|RL)$", RegexOption.IGNORE_CASE).matchEntire(header)
            ?: return null
        nodes.clear(); edges.clear(); subgraphs.clear(); groups.clear(); groupIds.clear()
        classDefs.clear(); assignments.clear(); inlineStyles.clear()
        val direction = MermaidDirection.valueOf(match.groupValues[1].uppercase().replace("TD", "TB"))
        lines.drop(1).forEach { parseLine(it.trim()) }
        while (groups.isNotEmpty()) closeGroup()
        val styledNodes = nodes.values.map { node ->
            val className = assignments[node.id]
            node.copy(className = className, style = className?.let(classDefs::get) ?: inlineStyles[node.id])
        }
        return MermaidDiagram(MermaidKind.Flowchart, direction, styledNodes, edges.toList(), subgraphs.toList())
    }

    private fun parseLine(line: String) {
        if (line.isBlank() || line.startsWith("%%")) return
        when {
            line.startsWith("classDef ") -> {
                Regex("^classDef\\s+(\\w+)\\s+(.+)$").matchEntire(line)?.let {
                    classDefs[it.groupValues[1]] = parseStyle(it.groupValues[2])
                }
            }
            line.startsWith("class ") -> {
                Regex("^class\\s+([^\\s]+)\\s+(\\w+)$").matchEntire(line)?.let { match ->
                    match.groupValues[1].split(',').forEach { assignments[it.trim()] = match.groupValues[2] }
                }
            }
            line.startsWith("style ") -> {
                Regex("^style\\s+(\\w+)\\s+(.+)$").matchEntire(line)?.let {
                    inlineStyles[it.groupValues[1]] = parseStyle(it.groupValues[2])
                }
            }
            line.startsWith("subgraph ") -> openGroup(line.removePrefix("subgraph ").trim())
            line == "end" -> closeGroup()
            else -> parseNodeOrEdge(line)
        }
    }

    private fun openGroup(value: String) {
        val idAndLabel = Regex("^(\\w+)\\s*\\[(.+)]$").matchEntire(value)
        val id = idAndLabel?.groupValues?.get(1) ?: value.substringBefore(' ').ifBlank { "subgraph_${subgraphs.size}" }
        val label = idAndLabel?.groupValues?.get(2) ?: value
        groups += GroupState(id, label)
        groupIds += id
    }

    private fun closeGroup() {
        if (groups.isEmpty()) return
        val closed = groups.removeAt(groups.lastIndex)
        subgraphs += MermaidSubgraph(closed.id, closed.label, closed.nodeIds.toList())
        groups.lastOrNull()?.nodeIds?.addAll(closed.nodeIds)
    }

    private fun parseNodeOrEdge(line: String) {
        val matches = arrowPattern.findAll(line).toList()
        if (matches.isEmpty()) {
            parseNode(line)?.let(::saveNode)
            return
        }
        val parts = mutableListOf<String>()
        var cursor = 0
        for (match in matches) {
            parts += line.substring(cursor, match.range.first).trim()
            cursor = match.range.last + 1
        }
        parts += line.substring(cursor).trim()
        if (parts.size != matches.size + 1 || parts.any(String::isEmpty)) return
        parts.mapNotNull(::parseNode).forEach(::saveNode)
        for (index in matches.indices) {
            val from = extractId(parts[index]) ?: continue
            val to = extractId(parts[index + 1]) ?: continue
            val token = matches[index].groupValues[1]
            val label = matches[index].groupValues[2].removeSurrounding("|").ifBlank { null }
            edges += MermaidEdge(
                from, to, label,
                line = when {
                    '=' in token -> MermaidLine.Thick
                    '.' in token -> MermaidLine.Dotted
                    else -> MermaidLine.Solid
                },
                arrow = when {
                    'x' in token -> MermaidArrow.Cross
                    'o' in token -> MermaidArrow.Circle
                    '>' in token -> MermaidArrow.Arrow
                    else -> MermaidArrow.None
                },
                subgraphEdge = from in groupIds || to in groupIds,
            )
        }
    }

    private fun saveNode(node: MermaidNode) {
        if (node.id in groupIds) return
        val previous = nodes[node.id]
        if (previous == null || (previous.label == previous.id && node.label != node.id) ||
            (previous.shape == MermaidShape.Rectangle && node.shape != MermaidShape.Rectangle)) nodes[node.id] = node
        groups.lastOrNull()?.nodeIds?.add(node.id)
    }

    private fun parseNode(value: String): MermaidNode? {
        val text = value.trim()
        if (text in groupIds) return null
        for ((pattern, shape) in shapePatterns) {
            pattern.matchEntire(text)?.let { match ->
                return MermaidNode(match.groupValues[1], unescape(match.groupValues[2]), shape)
            }
        }
        return if (Regex("^\\w+$").matches(text)) MermaidNode(text) else null
    }

    private fun extractId(value: String): String? = Regex("^\\w+").find(value)?.value

    private fun parseStyle(value: String): MermaidNodeStyle {
        val properties = value.split(',').mapNotNull {
            val pieces = it.split(':', limit = 2)
            if (pieces.size == 2) pieces[0].trim() to pieces[1].trim() else null
        }.toMap()
        return MermaidNodeStyle(
            fill = color(properties["fill"]), stroke = color(properties["stroke"]),
            strokeWidth = properties["stroke-width"]?.removeSuffix("px")?.toFloatOrNull()?.coerceIn(0.1f, 20f) ?: 1f,
            text = color(properties["color"]),
        )
    }

    private fun color(value: String?): Int? {
        if (value == null) return null
        val hex = value.removePrefix("#")
        if (value.startsWith('#')) {
            val expanded = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
            return when (expanded.length) {
                6 -> expanded.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
                8 -> expanded.toLongOrNull(16)?.toInt()
                else -> null
            }
        }
        return namedColors[value.lowercase()]
    }

    private fun unescape(value: String) = value.replace("\\\"", "\"").replace("\\'", "'")

    private data class GroupState(val id: String, val label: String, val nodeIds: LinkedHashSet<String> = linkedSetOf())

    private companion object {
        val arrowPattern = Regex("""\s*(====|---->|==>|===|-\.->|-->|---)\s*(\|[^|]*\|)?\s*""")
        val shapePatterns = listOf(
            Regex("""^(\w+)\(\((.+)\)\)$""") to MermaidShape.Circle,
            Regex("""^(\w+)\{\{(.+)\}\}$""") to MermaidShape.Hexagon,
            Regex("""^(\w+)\[\[(.+)\]\]$""") to MermaidShape.Subroutine,
            Regex("""^(\w+)\[\((.+)\)]$""") to MermaidShape.Cylinder,
            Regex("""^(\w+)\(\[(.+)\]\)$""") to MermaidShape.Stadium,
            Regex("""^(\w+)\[/(.+)/\]$""") to MermaidShape.Parallelogram,
            Regex("""^(\w+)\[\\(.+)\\\]$""") to MermaidShape.Parallelogram,
            Regex("""^(\w+)\[/(.+)\\\]$""") to MermaidShape.Trapezoid,
            Regex("""^(\w+)\[\\(.+)/\]$""") to MermaidShape.Trapezoid,
            Regex("""^(\w+)>(.+)\]$""") to MermaidShape.Asymmetric,
            Regex("""^(\w+)\[(.+)\]$""") to MermaidShape.Rectangle,
            Regex("""^(\w+)\((.+)\)$""") to MermaidShape.Rounded,
            Regex("""^(\w+)\{(.+)\}$""") to MermaidShape.Diamond,
        )
        val namedColors = mapOf(
            "red" to 0xFFFF0000.toInt(), "green" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(),
            "white" to 0xFFFFFFFF.toInt(), "black" to 0xFF000000.toInt(), "yellow" to 0xFFFFFF00.toInt(),
            "orange" to 0xFFFFA500.toInt(), "purple" to 0xFF800080.toInt(), "pink" to 0xFFFFC0CB.toInt(),
            "cyan" to 0xFF00FFFF.toInt(), "gray" to 0xFF808080.toInt(), "grey" to 0xFF808080.toInt(),
        )
    }
}

/** Participant declarations and common message arrows from Flutter's SequenceParser. */
class MermaidSequenceParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        if (!lines.firstOrNull().equals("sequenceDiagram", ignoreCase = true)) return null
        val participants = linkedMapOf<String, MermaidNode>()
        val messages = mutableListOf<MermaidEdge>()
        for (line in lines.drop(1).map(String::trim)) {
            val declaration = Regex("^(participant|actor)\\s+(\\w+)(?:\\s+as\\s+(.+))?$", RegexOption.IGNORE_CASE)
                .matchEntire(line)
            if (declaration != null) {
                val id = declaration.groupValues[2]
                participants[id] = MermaidNode(id, declaration.groupValues[3].ifBlank { id },
                    participantType = if (declaration.groupValues[1].equals("actor", true))
                        MermaidParticipantType.Actor else MermaidParticipantType.Participant)
                continue
            }
            val message = messagePattern.matchEntire(line) ?: continue
            val from = message.groupValues[1]
            val token = message.groupValues[2]
            val to = message.groupValues[3]
            participants.putIfAbsent(from, MermaidNode(from))
            participants.putIfAbsent(to, MermaidNode(to))
            messages += MermaidEdge(from, to, message.groupValues[4].ifBlank { null },
                line = if (token.startsWith("--")) MermaidLine.Dotted else MermaidLine.Solid,
                arrow = when {
                    token.endsWith('x') -> MermaidArrow.Cross
                    token.endsWith('>') && token != "->" && token != "-->" -> MermaidArrow.Arrow
                    token.endsWith(')') -> MermaidArrow.Arrow
                    else -> MermaidArrow.None
                })
        }
        return MermaidDiagram(MermaidKind.Sequence, MermaidDirection.LR, participants.values.toList(), messages)
    }

    private companion object {
        val messagePattern = Regex("""^(\w+)(-->>|->>|-->|->|--x|-x|--\)|-\))(\w+)(?::\s*(.*))?$""")
    }
}
