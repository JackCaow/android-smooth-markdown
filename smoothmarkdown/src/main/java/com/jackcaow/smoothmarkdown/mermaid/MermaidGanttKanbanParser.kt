package com.jackcaow.smoothmarkdown.mermaid

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Flutter Gantt fixture syntax; invalid or unknown task lines retain the fenced-code fallback. */
class MermaidGanttParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        val body = lines.filterNot { it.trim().isEmpty() || it.trim().startsWith("%%") }
        if (!body.firstOrNull().equals("gantt", true)) return null
        var title: String? = null
        var dateFormat = "YYYY-MM-DD"
        var axisFormat: String? = null
        var excludes: String? = null
        var todayMarker = true
        var section: String? = null
        val sections = linkedSetOf<String>()
        val tasks = mutableListOf<MermaidGanttTask>()
        // Flutter defaults undated tasks to today; UTC days make the native layout time-zone stable.
        var nextDay = todayDay()
        for (raw in body.drop(1)) {
            val line = raw.trim()
            val directive = line.substringBefore(' ').lowercase()
            val value = line.substringAfter(' ', "").trim()
            when (directive) {
                "title" -> { if (value.isEmpty()) return null; title = value }
                "dateformat" -> { if (value.isEmpty()) return null; dateFormat = value }
                "axisformat" -> { if (value.isEmpty()) return null; axisFormat = value }
                "excludes" -> { if (value.isEmpty()) return null; excludes = value }
                "todaymarker" -> { if (value !in listOf("on", "off")) return null; todayMarker = value == "on" }
                "section" -> { if (value.isEmpty()) return null; section = value; sections += value }
                else -> {
                    val colon = line.indexOf(':')
                    if (colon < 1) return null
                    val name = line.substring(0, colon).trim().takeIf(String::isNotEmpty) ?: return null
                    val parts = line.substring(colon + 1).split(',').map(String::trim)
                    if (parts.any(String::isEmpty)) return null
                    var status = MermaidGanttStatus.Normal
                    val remainder = parts.toMutableList()
                    status = when (remainder.first().lowercase()) {
                        "done" -> MermaidGanttStatus.Done
                        "active" -> MermaidGanttStatus.Active
                        "crit", "critical" -> MermaidGanttStatus.Critical
                        "milestone" -> MermaidGanttStatus.Milestone
                        else -> status
                    }
                    if (status != MermaidGanttStatus.Normal) remainder.removeAt(0)
                    if (remainder.isEmpty() || remainder.size > 3) return null
                    val generatedId = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    var id = generatedId
                    var startSpec: String? = null
                    var endSpec: String
                    when (remainder.size) {
                        1 -> endSpec = remainder[0]
                        2 -> {
                            endSpec = remainder[1]
                            val first = remainder[0]
                            when {
                                first.startsWith("after ", true) -> startSpec = first
                                parseDay(first) != null -> startSpec = first
                                else -> id = first
                            }
                        }
                        else -> { id = remainder[0]; startSpec = remainder[1]; endSpec = remainder[2] }
                    }
                    if (id.isEmpty() || tasks.any { it.id == id }) return null
                    val dependencies = mutableListOf<String>()
                    val start = when {
                        startSpec == null -> nextDay
                        startSpec.startsWith("after ", true) -> {
                            val predecessor = startSpec.substring(6).trim()
                            dependencies += predecessor
                            (tasks.firstOrNull { it.id == predecessor } ?: return null).endDay + 1
                        }
                        else -> parseDay(startSpec) ?: return null
                    }
                    val end = parseDay(endSpec) ?: durationDays(endSpec)?.let { start + it - 1 } ?: return null
                    if (end < start && status != MermaidGanttStatus.Milestone) return null
                    tasks += MermaidGanttTask(id, name, start, if (status == MermaidGanttStatus.Milestone) start else end,
                        section, status, dependencies)
                    nextDay = tasks.last().endDay + 1
                }
            }
        }
        if (tasks.isEmpty()) return null
        return MermaidDiagram(MermaidKind.Gantt, MermaidDirection.LR, emptyList(), emptyList(),
            gantt = MermaidGanttData(title, tasks, sections.toList(), dateFormat, axisFormat, excludes, todayMarker))
    }

    private fun todayDay(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis / DAY_MS

    private fun parseDay(value: String): Long? {
        val parts = when {
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value) -> value.split('-').map(String::toInt)
            Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(value) -> value.split('/').map(String::toInt).let { listOf(it[2], it[1], it[0]) }
            Regex("^\\d{2}-\\d{2}-\\d{4}$").matches(value) -> value.split('-').map(String::toInt).let { listOf(it[2], it[0], it[1]) }
            else -> return null
        }
        return try {
            val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                isLenient = false
                clear()
                set(parts[0], parts[1] - 1, parts[2])
            }
            calendar.timeInMillis / DAY_MS
        } catch (_: IllegalArgumentException) { null }
    }

    private fun durationDays(value: String): Long? {
        val match = Regex("^(\\d+)([dDwWmMyY]?)$").matchEntire(value) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        return if (amount > 100000) null else amount * when (match.groupValues[2].lowercase()) {
            "w" -> 7L; "m" -> 30L; "y" -> 365L; else -> 1L
        }
    }

    private companion object { const val DAY_MS = 86_400_000L }
}

/** Indentation-sensitive Kanban parser, including the Flutter ticketBaseUrl frontmatter fixture. */
class MermaidKanbanParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        var index = lines.indexOfFirst { it.isNotBlank() }
        if (index < 0) return null
        var ticketBaseUrl: String? = null
        if (lines[index].trim() == "---") {
            val end = (index + 1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return null
            ticketBaseUrl = lines.subList(index + 1, end).firstNotNullOfOrNull { line ->
                Regex("ticketBaseUrl:\\s*['\"]([^'\"]+)['\"]").find(line)?.groupValues?.get(1)
            }
            index = end + 1
        }
        while (index < lines.size && lines[index].isBlank()) index++
        if (!lines.getOrNull(index).orEmpty().trim().equals("kanban", true)) return null
        var title: String? = null
        val columns = mutableListOf<MermaidKanbanColumn>()
        var currentId: String? = null
        var currentTitle = ""
        var currentLimit: Int? = null
        val tasks = mutableListOf<MermaidKanbanTask>()
        fun flush() {
            currentId?.let { columns += MermaidKanbanColumn(it, currentTitle, tasks.toList(), currentLimit) }
            tasks.clear()
        }
        for (raw in lines.drop(index + 1)) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("%%")) continue
            if (line.startsWith("title ", true)) {
                if (title != null || columns.isNotEmpty() || currentId != null) return null
                title = line.substring(6).trim().takeIf(String::isNotEmpty) ?: return null
                continue
            }
            val indentChars = raw.takeWhile { it == ' ' || it == '\t' }
            val indent = indentChars.count { it == ' ' } + indentChars.count { it == '\t' } * 4
            val column = Regex("^(\\w+)\\[([^\\]]+)\\](?:\\s+wip:(\\d+))?$").matchEntire(line)
            if (indent <= 2 && column != null) {
                flush()
                currentId = column.groupValues[1]
                if (columns.any { it.id == currentId }) return null
                currentTitle = column.groupValues[2]
                currentLimit = column.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
                continue
            }
            if (indent < 4 || currentId == null) return null
            val taskMatch = Regex("^(\\w+)\\[([^\\]]+)\\](?:\\s+@\\{([^}]+)\\})?$").matchEntire(line) ?: return null
            val id = taskMatch.groupValues[1]
            if (tasks.any { it.id == id } || columns.any { col -> col.tasks.any { it.id == id } }) return null
            val metadata = linkedMapOf<String, String>()
            val rawMetadata = taskMatch.groupValues[3]
            if (rawMetadata.isNotEmpty()) {
                for (pair in rawMetadata.split(',')) {
                    val fields = pair.split(':', limit = 2)
                    if (fields.size != 2) return null
                    metadata[fields[0].trim()] = fields[1].trim().trim('"', '\'')
                }
            }
            val priority = when (metadata["priority"]?.lowercase()) {
                null, "normal" -> MermaidKanbanPriority.Normal
                "very high" -> MermaidKanbanPriority.VeryHigh
                "high" -> MermaidKanbanPriority.High
                "low" -> MermaidKanbanPriority.Low
                "very low" -> MermaidKanbanPriority.VeryLow
                else -> return null
            }
            tasks += MermaidKanbanTask(id, taskMatch.groupValues[2], metadata["assigned"], metadata["ticket"],
                priority, metadata.filterKeys { it !in setOf("assigned", "ticket", "priority") })
        }
        flush()
        if (columns.isEmpty()) return null
        return MermaidDiagram(MermaidKind.Kanban, MermaidDirection.LR, emptyList(), emptyList(),
            kanban = MermaidKanbanData(title, columns, ticketBaseUrl))
    }
}
