package com.jackcaow.smoothmarkdown.mermaid

/** Parses Flutter radar-beta fixture syntax, declining malformed lines for fenced-code fallback. */
class MermaidRadarParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        if (!lines.firstOrNull().equals("radar-beta", true)) return null
        var title: String? = null
        val axes = mutableListOf<MermaidRadarAxis>()
        val curves = mutableListOf<MermaidRadarCurve>()
        var showLegend = true
        var max: Double? = null
        var min: Double? = null
        var graticule = MermaidRadarGraticule.Polygon
        var ticks = 5
        for (line in lines.drop(1)) {
            val value = line.substringAfter(' ', "").trim()
            when (line.substringBefore(' ').lowercase()) {
                "title" -> { title = value.takeIf(String::isNotEmpty) ?: return null }
                "axis" -> {
                    val parsed = splitQuoted(value).map { parseAxis(it) ?: return null }
                    if (parsed.isEmpty() || parsed.any { axis -> axes.any { it.id == axis.id } }) return null
                    axes += parsed
                }
                "curve" -> {
                    val open = value.indexOf('{')
                    if (open <= 0 || !value.endsWith('}')) return null
                    val identity = value.substring(0, open).trim()
                    val labeled = Regex("^(.+?)\\[\"([^\"]+)\"\\]$").matchEntire(identity)
                    val id = (labeled?.groupValues?.get(1) ?: identity).trim()
                    val label = labeled?.groupValues?.get(2) ?: id
                    if (id.isEmpty() || curves.any { it.id == id }) return null
                    val values = value.substring(open + 1, value.length - 1).split(',').map { part ->
                        val raw = part.substringAfter(':', part).trim()
                        raw.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                    }
                    if (values.isEmpty()) return null
                    curves += MermaidRadarCurve(id, label, values)
                }
                "showlegend" -> showLegend = when (value.lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> return null
                }
                "max" -> max = value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                "min" -> min = value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                "graticule" -> graticule = when (value.lowercase()) {
                    "polygon" -> MermaidRadarGraticule.Polygon
                    "circle" -> MermaidRadarGraticule.Circle
                    else -> return null
                }
                "ticks" -> ticks = value.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
                else -> return null
            }
        }
        if (axes.size < 3 || curves.isEmpty() || curves.any { it.values.size != axes.size }) return null
        if (max != null && min != null && max <= min) return null
        val data = MermaidRadarData(title, axes, curves, showLegend, max, min, graticule, ticks)
        if (data.effectiveMax <= data.effectiveMin) return null
        return MermaidDiagram(MermaidKind.Radar, MermaidDirection.TB, emptyList(), emptyList(), radar = data)
    }

    private fun parseAxis(text: String): MermaidRadarAxis? {
        val trimmed = text.trim()
        val labeled = Regex("^(.+?)\\[\"([^\"]+)\"\\]$").matchEntire(trimmed)
        val id = (labeled?.groupValues?.get(1) ?: trimmed).trim()
        if (id.isEmpty() || '[' in id || ']' in id) return null
        return MermaidRadarAxis(id, labeled?.groupValues?.get(2) ?: id)
    }
}

/** Parses categorical/numeric Mermaid xychart series and axes without accepting partial data. */
class MermaidXYChartParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        val header = lines.firstOrNull()?.lowercase() ?: return null
        val orientation = when (header) {
            "xychart", "xychart-beta" -> MermaidXYOrientation.Vertical
            "xychart horizontal", "xychart-beta horizontal" -> MermaidXYOrientation.Horizontal
            else -> return null
        }
        var title: String? = null
        var xTitle: String? = null
        var yTitle: String? = null
        var categories: List<String> = emptyList()
        var xMin: Double? = null
        var xMax: Double? = null
        var yMin: Double? = null
        var yMax: Double? = null
        val series = mutableListOf<MermaidXYSeries>()
        for (line in lines.drop(1)) {
            val value = line.substringAfter(' ', "").trim()
            when (line.substringBefore(' ').lowercase()) {
                "title" -> title = unquote(value).takeIf(String::isNotEmpty) ?: return null
                "x-axis", "y-axis" -> {
                    val axis = parseAxis(value) ?: return null
                    if (line.startsWith("x-axis", true)) {
                        xTitle = axis.title; categories = axis.categories; xMin = axis.min; xMax = axis.max
                    } else { yTitle = axis.title; yMin = axis.min; yMax = axis.max }
                }
                "bar", "line" -> {
                    val values = parseNumbers(value) ?: return null
                    series += MermaidXYSeries(if (line.startsWith("bar", true)) MermaidXYSeriesType.Bar
                        else MermaidXYSeriesType.Line, values)
                }
                else -> return null
            }
        }
        if (series.isEmpty() || categories.isNotEmpty() && series.any { it.values.size != categories.size }) return null
        if (xMin != null && xMax != null && xMax <= xMin) return null
        if (yMin != null && yMax != null && yMax <= yMin) return null
        val data = MermaidXYData(title, xTitle, yTitle, categories, xMin, xMax, yMin, yMax, orientation, series)
        if (data.effectiveMax <= data.effectiveMin) return null
        return MermaidDiagram(MermaidKind.XYChart, MermaidDirection.LR, emptyList(), emptyList(), xyChart = data)
    }

    private data class Axis(val title: String?, val categories: List<String>, val min: Double?, val max: Double?)

    private fun parseAxis(text: String): Axis? {
        var remaining = text
        var title: String? = null
        if (remaining.startsWith('"')) {
            val end = remaining.indexOf('"', 1)
            if (end < 0) return null
            title = remaining.substring(1, end)
            remaining = remaining.substring(end + 1).trim()
        }
        if (remaining.contains('[')) {
            val open = remaining.indexOf('[')
            if (!remaining.endsWith(']') || open >= remaining.lastIndex) return null
            if (title == null && open > 0) title = remaining.substring(0, open).trim()
            val cats = splitQuoted(remaining.substring(open + 1, remaining.length - 1))
                .map(::unquote).map(String::trim)
            if (cats.isEmpty() || cats.any(String::isEmpty)) return null
            return Axis(title, cats, null, null)
        }
        val range = Regex("^(.+?)\\s*-->\\s*(.+)$").matchEntire(remaining)
        if (range != null) {
            val left = range.groupValues[1].trim()
            val first = left.substringAfterLast(' ')
            if (title == null && first != left) title = left.dropLast(first.length).trim()
            val min = first.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
            val max = range.groupValues[2].trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
            return Axis(title, emptyList(), min, max)
        }
        if (remaining.isNotBlank() && title == null) title = remaining
        if (title.isNullOrBlank()) return null
        return Axis(title, emptyList(), null, null)
    }

    private fun parseNumbers(text: String): List<Double>? {
        if (!text.startsWith('[') || !text.endsWith(']')) return null
        val parts = text.substring(1, text.length - 1).split(',')
        if (parts.isEmpty()) return null
        return parts.map { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
    }
}

private fun unquote(value: String): String = value.removeSurrounding("\"")

/** Commas inside quoted labels do not split an axis or category. */
private fun splitQuoted(value: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (char in value) {
        when {
            char == '"' -> { quoted = !quoted; current.append(char) }
            char == ',' && !quoted -> { parts += current.toString().trim(); current.clear() }
            else -> current.append(char)
        }
    }
    parts += current.toString().trim()
    return if (quoted) emptyList() else parts
}
