package com.jackcaow.smoothmarkdown.mermaid

/** Dispatches supported native diagram syntaxes. */
object MermaidParser {
    fun parse(source: String): MermaidDiagram? {
        val rawLines = source.lineSequence().map { it.substringBefore("%%").trimEnd() }
            .filter { it.isNotBlank() }.toList()
        val headerIndex = if (rawLines.firstOrNull()?.trim() == "---") {
            val end = rawLines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end < 0) return null
            end + 2
        } else 0
        val body = rawLines.drop(headerIndex)
        val lines = body.map(String::trim)
        val header = lines.firstOrNull() ?: return null
        return when {
            Regex("^(graph|flowchart)\\s+(TD|TB|BT|LR|RL)$", RegexOption.IGNORE_CASE).matches(header) ->
                MermaidFlowchartParser().parse(lines)
            header.equals("sequenceDiagram", true) -> MermaidSequenceParser().parse(lines)
            Regex("^pie(?:\\s+showData)?(?:\\s+title\\s+.+)?$", RegexOption.IGNORE_CASE).matches(header) -> MermaidPieParser().parse(lines)
            header.startsWith("gitGraph", true) -> MermaidNativeTreeParser.gitGraph(body)
            header.equals("mindmap", true) -> MermaidNativeTreeParser.mindmap(body)
            header.equals("timeline", true) -> MermaidTimelineParser().parse(lines)
            header.equals("gantt", true) -> MermaidGanttParser().parse(lines)
            header.equals("kanban", true) -> MermaidKanbanParser().parse(rawLines)
            header.equals("radar-beta", true) -> MermaidRadarParser().parse(lines)
            header.equals("classDiagram", true) -> MermaidStructuredParser(MermaidKind.ClassDiagram).parse(lines)
            Regex("^stateDiagram(?:-v2)?$", RegexOption.IGNORE_CASE).matches(header) ->
                MermaidStructuredParser(MermaidKind.StateDiagram).parse(lines)
            Regex("^xychart(?:-beta)?(?:\\s+horizontal)?$", RegexOption.IGNORE_CASE).matches(header) -> MermaidXYChartParser().parse(lines)
            header.equals("erDiagram", true) -> MermaidERParser().parse(lines)
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
    private var invalid = false

    fun parse(lines: List<String>): MermaidDiagram? {
        val header = lines.firstOrNull()?.trim() ?: return null
        val match = Regex("^(?:graph|flowchart)\\s+(TD|TB|BT|LR|RL)$", RegexOption.IGNORE_CASE).matchEntire(header)
            ?: return null
        nodes.clear(); edges.clear(); subgraphs.clear(); groups.clear(); groupIds.clear()
        classDefs.clear(); assignments.clear(); inlineStyles.clear(); invalid = false
        val direction = MermaidDirection.valueOf(match.groupValues[1].uppercase().replace("TD", "TB"))
        lines.drop(1).forEach { parseLine(it.trim()) }
        if (invalid || groups.isNotEmpty() || nodes.isEmpty()) return null
        val styledNodes = nodes.values.map { node ->
            val className = assignments[node.id]
            node.copy(className = className, style = className?.let(classDefs::get) ?: inlineStyles[node.id])
        }
        val knownGroups = subgraphs.mapTo(mutableSetOf()) { it.id }
        val finalNodes = styledNodes.filterNot { it.id in knownGroups }
        val finalEdges = edges.map { it.copy(subgraphEdge = it.from in knownGroups || it.to in knownGroups) }
        val endpoints = finalNodes.mapTo(mutableSetOf()) { it.id } + knownGroups
        if (finalEdges.any { it.from !in endpoints || it.to !in endpoints }) return null
        return MermaidDiagram(MermaidKind.Flowchart, direction, finalNodes, finalEdges, subgraphs.toList())
    }

    private fun parseLine(line: String) {
        if (line.isBlank() || line.startsWith("%%")) return
        when {
            line.startsWith("classDef ") -> {
                val match = Regex("^classDef\\s+(\\w+)\\s+(.+)$").matchEntire(line)
                if (match == null) invalid = true else classDefs[match.groupValues[1]] = parseStyle(match.groupValues[2])
            }
            line.startsWith("class ") -> {
                val match = Regex("^class\\s+([^\\s]+)\\s+(\\w+)$").matchEntire(line)
                if (match == null) invalid = true else
                    match.groupValues[1].split(',').forEach { assignments[it.trim()] = match.groupValues[2] }
            }
            line.startsWith("style ") -> {
                val match = Regex("^style\\s+(\\w+)\\s+(.+)$").matchEntire(line)
                if (match == null) invalid = true else inlineStyles[match.groupValues[1]] = parseStyle(match.groupValues[2])
            }
            line == "subgraph" || line.startsWith("subgraph ") -> openGroup(line.removePrefix("subgraph").trim())
            line == "end" -> closeGroup()
            else -> parseNodeOrEdge(line)
        }
    }

    private fun openGroup(value: String) {
        if (value.isBlank()) { invalid = true; return }
        val idAndLabel = Regex("^([\\p{L}_][\\p{L}\\p{N}_-]*)\\s*\\[(.+)]$").matchEntire(value)
        if (idAndLabel == null && ('[' in value || ']' in value)) { invalid = true; return }
        val id = idAndLabel?.groupValues?.get(1) ?: value.substringBefore(' ')
        val label = idAndLabel?.groupValues?.get(2) ?: value
        if (id in groupIds || id.isBlank()) { invalid = true; return }
        groups += GroupState(id, label)
        groupIds += id
    }

    private fun closeGroup() {
        if (groups.isEmpty()) { invalid = true; return }
        val closed = groups.removeAt(groups.lastIndex)
        subgraphs += MermaidSubgraph(closed.id, closed.label, closed.nodeIds.toList(),
            parentId = groups.lastOrNull()?.id, directNodeIds = closed.directNodeIds.toList())
        groups.lastOrNull()?.nodeIds?.addAll(closed.nodeIds)
    }

    private fun parseNodeOrEdge(line: String) {
        val matches = arrowPattern.findAll(line).toList()
        if (matches.isEmpty()) {
            val node = parseNode(line)
            if (node == null && line !in groupIds) invalid = true else node?.let(::saveNode)
            return
        }
        val parts = mutableListOf<String>()
        var cursor = 0
        for (match in matches) {
            parts += line.substring(cursor, match.range.first).trim()
            cursor = match.range.last + 1
        }
        parts += line.substring(cursor).trim()
        if (parts.size != matches.size + 1 || parts.any(String::isEmpty) ||
            parts.any { it !in groupIds && parseNode(it) == null }) { invalid = true; return }
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
        groups.lastOrNull()?.directNodeIds?.add(node.id)
    }

    private fun parseNode(value: String): MermaidNode? {
        val text = value.trim()
        if (text in groupIds) return null
        for ((pattern, shape) in shapePatterns) {
            pattern.matchEntire(text)?.let { match ->
                return MermaidNode(match.groupValues[1], unescape(match.groupValues[2]), shape)
            }
        }
        return if (Regex("^[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*$").matches(text)) MermaidNode(text) else null
    }

    private fun extractId(value: String): String? = Regex("^[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*").find(value)?.value

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

    private data class GroupState(
        val id: String, val label: String,
        val nodeIds: LinkedHashSet<String> = linkedSetOf(),
        val directNodeIds: LinkedHashSet<String> = linkedSetOf(),
    )

    private companion object {
        val arrowPattern = Regex("""\s*(====|---->|==>|===|-\.->|\.\.\.|-->|---)\s*(\|[^|]*\|)?\s*""")
        val shapePatterns = listOf(
            Regex("""^(\w+)\(\((.+)\)\)$""") to MermaidShape.Circle,
            Regex("""^(\w+)\{\{(.+)\}\}$""") to MermaidShape.Hexagon,
            Regex("""^(\w+)\[\[(.+)\]\]$""") to MermaidShape.Subroutine,
            Regex("""^(\w+)\[\((.+)\)]$""") to MermaidShape.Cylinder,
            Regex("""^(\w+)\(\[(.+)\]\)$""") to MermaidShape.Stadium,
            Regex("""^(\w+)\[/(.+)/\]$""") to MermaidShape.Parallelogram,
            Regex("""^(\w+)\[\\(.+)\\\]$""") to MermaidShape.ParallelogramAlt,
            Regex("""^(\w+)\[/(.+)\\\]$""") to MermaidShape.Trapezoid,
            Regex("""^(\w+)\[\\(.+)/\]$""") to MermaidShape.TrapezoidAlt,
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
        if (!lines.firstOrNull()?.trim().equals("sequenceDiagram", ignoreCase = true) ||
            lines.size > 501 || lines.sumOf { it.length + 1 } > 50_001) return null
        val participants = linkedMapOf<String, MermaidNode>()
        val messages = mutableListOf<MermaidEdge>()
        for (line in lines.drop(1).map(String::trim)) {
            val declaration = Regex("^(participant|actor)\\s+([\\p{L}_][\\p{L}\\p{M}\\p{N}_]*)(?:\\s+as\\s+(.+))?$", RegexOption.IGNORE_CASE)
                .matchEntire(line)
            if (declaration != null) {
                val id = declaration.groupValues[2]
                participants[id] = MermaidNode(id, declaration.groupValues[3].ifBlank { id },
                    participantType = if (declaration.groupValues[1].equals("actor", true))
                        MermaidParticipantType.Actor else MermaidParticipantType.Participant)
                continue
            }
            if (line.isEmpty() || line.startsWith("%%")) continue
            val message = messagePattern.matchEntire(line) ?: return null
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
        if (participants.isEmpty()) return null
        return MermaidDiagram(MermaidKind.Sequence, MermaidDirection.LR, participants.values.toList(), messages)
    }

    private companion object {
        val messagePattern = Regex("""^([\p{L}_][\p{L}\p{M}\p{N}_]*)\s*(-->>|->>|-->|->|--x|-x|--\)|-\))\s*([\p{L}_][\p{L}\p{M}\p{N}_]*)\s*(?::\s*(.*))?$""")
    }
}

/** Bounded native Git history and indentation trees; unsupported syntax keeps source fallback. */
internal object MermaidNativeTreeParser {
    private fun bounded(lines: List<String>) = lines.size <= 500 && lines.sumOf { it.length + 1 } <= 50_001

    fun gitGraph(lines: List<String>): MermaidDiagram? {
        if (!bounded(lines)) return null
        val declaration = Regex("^gitGraph(?:\\s+(LR|TB|BT))?\\s*:?$", RegexOption.IGNORE_CASE)
            .matchEntire(lines.firstOrNull()?.trim() ?: return null) ?: return null
        val direction = when (declaration.groupValues[1].uppercase()) {
            "TB" -> MermaidDirection.TB
            "BT" -> MermaidDirection.BT
            else -> MermaidDirection.LR
        }
        val nodes = mutableListOf<MermaidNode>()
        val edges = mutableListOf<MermaidEdge>()
        val branches = mutableSetOf("main")
        val heads = mutableMapOf<String, String>()
        val ids = mutableSetOf<String>()
        var current = "main"
        var actions = 0
        var serial = 1
        for (raw in lines.drop(1)) {
            val tokens = tokens(raw.trim()) ?: return null
            val command = tokens.firstOrNull()?.lowercase() ?: return null
            if (command == "init") {
                if (tokens.size != 1 || actions != 0) return null
                actions++
                continue
            }
            actions++
            if (command == "branch") {
                val branch = tokens.getOrNull(1) ?: return null
                if (tokens.size != 2 || branch.any { it == ':' || it == ';' || it == '\n' } || !branches.add(branch)) return null
                heads[current]?.let { heads[branch] = it }
                current = branch
                continue
            }
            if (command == "checkout" || command == "switch") {
                if (tokens.size != 2 || tokens[1] !in branches) return null
                current = tokens[1]
                continue
            }
            if (command != "commit" && command != "merge") return null
            val parents = mutableListOf<String>()
            heads[current]?.let(parents::add)
            val offset = if (command == "merge") {
                val branch = tokens.getOrNull(1) ?: return null
                val own = heads[current] ?: return null
                val other = heads[branch] ?: return null
                if (branch == current || own == other) return null
                parents += other
                2
            } else 1
            val attributes = attributes(tokens.drop(offset)) ?: return null
            val id = attributes["id"] ?: run {
                while ("commit-$serial" in ids) serial++
                "commit-${serial++}"
            }
            if (!ids.add(id)) return null
            val type = attributes["type"] ?: "NORMAL"
            nodes += MermaidNode(id, shape = if (type == "HIGHLIGHT") MermaidShape.Rectangle else MermaidShape.Circle,
                compartments = listOf(listOf(current), attributes["tag"]?.let(::listOf) ?: emptyList(), listOf(type)))
            parents.forEach { edges += MermaidEdge(it, id, arrow = MermaidArrow.None) }
            heads[current] = id
        }
        if (nodes.isEmpty()) return null
        return MermaidDiagram(MermaidKind.GitGraph, direction, nodes, edges)
    }

    fun mindmap(lines: List<String>): MermaidDiagram? {
        if (!bounded(lines) || !lines.firstOrNull()?.trim().equals("mindmap", true)) return null
        val nodes = mutableListOf<MermaidNode>()
        val edges = mutableListOf<MermaidEdge>()
        val ancestors = mutableListOf<Pair<Int, String>>()
        val ids = mutableSetOf<String>()
        var rootIndent: Int? = null
        var serial = 1
        for (raw in lines.drop(1)) {
            var indent = 0
            for (character in raw) {
                when (character) {
                    ' ' -> indent++
                    '\t' -> indent += 4 - indent % 4
                    else -> break
                }
            }
            val declaration = mindmapNode(raw.trim()) ?: return null
            val id = declaration.id ?: run {
                while ("mindmap:$serial" in ids) serial++
                "mindmap:${serial++}"
            }
            if (!ids.add(id)) return null
            if (rootIndent != null) {
                if (indent <= rootIndent) return null
                while (ancestors.lastOrNull()?.first?.let { it >= indent } == true) ancestors.removeAt(ancestors.lastIndex)
                val parent = ancestors.lastOrNull()?.second ?: return null
                edges += MermaidEdge(parent, id, arrow = MermaidArrow.None)
            } else rootIndent = indent
            nodes += declaration.toNode(id)
            ancestors += indent to id
        }
        if (nodes.isEmpty()) return null
        return MermaidDiagram(MermaidKind.Mindmap, MermaidDirection.LR, nodes, edges)
    }

    private fun mindmapNode(text: String): TreeNode? {
        val identifier = "([\\p{L}_][\\p{L}\\p{M}\\p{N}_-]*)?"
        val forms = listOf(
            "\\(\\((.+)\\)\\)" to MermaidShape.Circle,
            "\\((.+)\\)" to MermaidShape.Rounded,
            "\\[(.+)\\]" to MermaidShape.Rectangle,
            "\\{\\{(.+)\\}\\}" to MermaidShape.Hexagon,
        )
        for ((form, shape) in forms) {
            val match = Regex("^$identifier$form$").matchEntire(text) ?: continue
            val label = match.groupValues[2].trim()
            if (label.isEmpty() || (shape == MermaidShape.Rounded && label.startsWith('('))) return null
            return TreeNode(match.groupValues[1].ifBlank { null }, label, shape)
        }
        if (text.isBlank() || text.any { it in "[](){}" } || text.startsWith("::") || "-->" in text) return null
        return TreeNode(null, text, MermaidShape.Rectangle)
    }
    private data class TreeNode(val id: String?, val label: String, val shape: MermaidShape) {
        fun toNode(id: String): MermaidNode = MermaidNode(id, label, shape)
    }

    private fun attributes(tokens: List<String>): Map<String, String>? {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index++]
            val colon = token.indexOf(':')
            if (colon < 0) return null
            val key = token.substring(0, colon).lowercase()
            if (key !in listOf("id", "tag", "type") || key in result) return null
            var value = token.substring(colon + 1)
            if (value.isEmpty()) value = tokens.getOrNull(index++) ?: return null
            if (value.isEmpty()) return null
            if (key == "type") {
                value = value.uppercase()
                if (value !in listOf("NORMAL", "REVERSE", "HIGHLIGHT")) return null
            }
            result[key] = value
        }
        return result
    }

    private fun tokens(text: String): List<String>? {
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            if (text[cursor].isWhitespace()) { cursor++; continue }
            val token = StringBuilder()
            if (text[cursor] == '"') {
                cursor++
                var closed = false
                while (cursor < text.length) {
                    val character = text[cursor++]
                    if (character == '"') { closed = true; break }
                    if (character == '\\') {
                        if (cursor == text.length) return null
                        token.append(text[cursor++])
                    } else token.append(character)
                }
                if (!closed || (cursor < text.length && !text[cursor].isWhitespace())) return null
            } else {
                while (cursor < text.length && !text[cursor].isWhitespace() && text[cursor] != '"') token.append(text[cursor++])
            }
            if (token.isEmpty()) return null
            result += token.toString()
        }
        return result
    }
}
