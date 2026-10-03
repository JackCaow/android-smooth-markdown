package com.jackcaow.smoothmarkdown.mermaid

/** Flutter-compatible pie syntax for positive decimal slices and optional title/showData. */
class MermaidPieParser {
    private val quoted = Regex("^\"([^\"]+)\"\\s*:\\s*([0-9.]+)$")
    private val singleQuoted = Regex("^'([^']+)'\\s*:\\s*([0-9.]+)$")
    private val unquoted = Regex("^([^:]+):\\s*([0-9.]+)$")

    fun parse(lines: List<String>): MermaidDiagram? {
        val header = lines.firstOrNull()?.trim() ?: return null
        val declaration = Regex("^pie(?:\\s+(showData))?(?:\\s+title\\s+(.+))?$", RegexOption.IGNORE_CASE).matchEntire(header) ?: return null
        var title: String? = declaration.groupValues[2].ifBlank { null }
        val slices = mutableListOf<MermaidPieSlice>()
        for (line in lines.drop(1).map(String::trim)) {
            if (line.startsWith("title ", ignoreCase = true)) {
                title = line.drop(6).trim()
                continue
            }
            val match = quoted.matchEntire(line) ?: singleQuoted.matchEntire(line) ?: unquoted.matchEntire(line)
            val label = match?.groupValues?.get(1)?.trim().orEmpty()
            val value = match?.groupValues?.get(2)?.toDoubleOrNull()
            if (label.isNotEmpty() && value != null && value.isFinite() && value > 0) slices += MermaidPieSlice(label, value)
        }
        if (slices.isEmpty()) return null
        return MermaidDiagram(
            kind = MermaidKind.Pie, direction = MermaidDirection.TB, nodes = emptyList(), edges = emptyList(),
            pie = MermaidPieData(title, slices, declaration.groupValues[1].isNotEmpty()),
        )
    }
}

/** Period/event timeline syntax, including `: event` continuation and event descriptions. */
class MermaidTimelineParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        if (!lines.firstOrNull().equals("timeline", ignoreCase = true)) return null
        var title: String? = null
        val sections = mutableListOf<MermaidTimelineSection>()
        var currentPeriod: String? = null
        val events = mutableListOf<MermaidTimelineEvent>()
        fun finishSection() {
            val period = currentPeriod
            if (period != null && events.isNotEmpty()) sections += MermaidTimelineSection(period, events.toList())
            events.clear()
        }
        for (line in lines.drop(1)) {
            if (line.startsWith("title ", ignoreCase = true)) {
                title = line.drop(6).trim()
                continue
            }
            val colon = line.indexOf(':')
            if (colon >= 0) {
                val period = line.substring(0, colon).trim()
                val event = line.substring(colon + 1).trim()
                when {
                    period.isEmpty() && event.isNotEmpty() -> if (currentPeriod != null) events += MermaidTimelineEvent(event)
                    period.isNotEmpty() -> {
                        finishSection()
                        currentPeriod = period
                        if (event.isNotEmpty()) events += MermaidTimelineEvent(event)
                    }
                }
            } else if (events.isNotEmpty()) {
                events[events.lastIndex] = events.last().copy(description = line)
            }
        }
        finishSection()
        if (sections.isEmpty()) return null
        return MermaidDiagram(
            kind = MermaidKind.Timeline, direction = MermaidDirection.LR, nodes = emptyList(), edges = emptyList(),
            timeline = MermaidTimelineData(title, sections),
        )
    }
}
