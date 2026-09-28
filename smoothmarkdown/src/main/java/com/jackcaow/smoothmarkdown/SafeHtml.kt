package com.jackcaow.smoothmarkdown

/** The Flutter package's bounded, opt-in HTML tag and attribute policy. */
internal object SafeHtml {
    const val MAX_TAG_LENGTH = 512
    val voidTags = setOf("br", "hr", "img")
    private val allowedSchemes = setOf("http", "https", "mailto", "tel")

    data class Tag(
        val name: String,
        val attributes: Map<String, String>,
        val isClosing: Boolean,
        val isSelfClosing: Boolean,
        val end: Int,
    )

    sealed interface Block {
        data object Rule : Block
        data class Container(val name: String, val content: String, val alignment: String?, val trailing: String) : Block
    }

    fun parseBlock(source: String): Block? {
        val trimmed = source.trim()
        val open = lexTag(trimmed) ?: return null
        if (open.name == "hr" && !open.isClosing && open.end == trimmed.length) return Block.Rule
        if (open.isClosing || open.name !in setOf("div", "p", "center", "blockquote")) return null
        val alignment = if (open.name == "center") "center"
            else open.attributes["align"]?.lowercase()?.takeIf { it in setOf("left", "center", "right") }
        if (open.isSelfClosing) return Block.Container(open.name, "", alignment, "")
        var depth = 1
        var cursor = open.end
        while (cursor < trimmed.length) {
            val next = trimmed.indexOf('<', cursor)
            if (next < 0) break
            val tag = lexTag(trimmed, next)
            if (tag == null) { cursor = next + 1; continue }
            if (tag.name == open.name) {
                if (tag.isClosing) depth-- else if (!tag.isSelfClosing) depth++
                if (depth == 0) {
                    return Block.Container(open.name, trimmed.substring(open.end, next), alignment, trimmed.substring(tag.end))
                }
            }
            cursor = tag.end
        }
        return Block.Container(open.name, trimmed.substring(open.end), alignment, "")
    }

    fun lexTag(source: String, start: Int = 0): Tag? {
        val limit = minOf(source.length, start + MAX_TAG_LENGTH)
        if (start !in source.indices || source[start] != '<') return null
        var index = start + 1
        val isClosing = index < limit && source[index] == '/'
        if (isClosing) index++
        if (index >= limit || !source[index].isAsciiLetter()) return null
        val nameStart = index++
        while (index < limit && source[index].isNameChar()) index++
        val name = source.substring(nameStart, index).lowercase()
        val attributes = linkedMapOf<String, String>()
        while (true) {
            while (index < limit && source[index].isHtmlSpace()) index++
            if (index >= limit) return null
            val current = source[index]
            if (current == '>' || current == '/') {
                val isSelfClosing = current == '/'
                if (isSelfClosing && (index + 1 >= limit || source[index + 1] != '>')) return null
                return Tag(name, attributes, isClosing, isSelfClosing, index + if (isSelfClosing) 2 else 1)
            }
            if (!current.isAsciiLetter()) return null
            val attributeStart = index++
            while (index < limit && source[index].isNameChar()) index++
            val attribute = source.substring(attributeStart, index).lowercase()
            while (index < limit && source[index].isHtmlSpace()) index++
            var value = ""
            if (index < limit && source[index] == '=') {
                index++
                while (index < limit && source[index].isHtmlSpace()) index++
                if (index >= limit) return null
                val quote = source[index]
                if (quote == '"' || quote == '\'') {
                    index++
                    val valueStart = index
                    while (index < limit && source[index] != quote) index++
                    if (index >= limit) return null
                    value = source.substring(valueStart, index++)
                } else {
                    val valueStart = index
                    while (index < limit && !source[index].isHtmlSpace() && source[index] != '>') index++
                    value = source.substring(valueStart, index)
                }
            }
            attributes.putIfAbsent(attribute, value)
        }
    }

    fun isSafeLink(url: String): Boolean {
        val cleaned = url.filter { it.code > 0x20 }
        if (cleaned.isEmpty()) return false
        for (index in cleaned.indices) {
            when (cleaned[index]) {
                '/', '?', '#' -> return true
                ':' -> return cleaned.substring(0, index).lowercase() in allowedSchemes
            }
        }
        return true
    }

    fun isSafeImageSource(url: String): Boolean {
        val cleaned = url.filter { it.code > 0x20 }
        if (cleaned.isEmpty()) return false
        for (index in cleaned.indices) {
            when (cleaned[index]) {
                '/', '?', '#' -> break
                ':' -> return cleaned.substring(0, index).lowercase() in setOf("http", "https")
            }
        }
        return !cleaned.startsWith("//")
    }

    fun color(value: String): Int? {
        val normalized = value.trim().lowercase()
        val named = mapOf(
            "aqua" to 0x00FFFF, "black" to 0x000000, "blue" to 0x0000FF,
            "brown" to 0xA52A2A, "cyan" to 0x00FFFF, "fuchsia" to 0xFF00FF,
            "gold" to 0xFFD700, "gray" to 0x808080, "green" to 0x008000,
            "grey" to 0x808080, "indigo" to 0x4B0082, "lime" to 0x00FF00,
            "magenta" to 0xFF00FF, "maroon" to 0x800000, "navy" to 0x000080,
            "olive" to 0x808000, "orange" to 0xFFA500, "pink" to 0xFFC0CB,
            "purple" to 0x800080, "red" to 0xFF0000, "silver" to 0xC0C0C0,
            "teal" to 0x008080, "violet" to 0xEE82EE, "white" to 0xFFFFFF,
            "yellow" to 0xFFFF00,
        )
        val rgb = if (normalized.startsWith('#')) {
            val hex = normalized.drop(1).let { if (it.length == 3) it.map { digit -> "$digit$digit" }.joinToString("") else it }
            if (hex.length != 6) return null
            hex.toIntOrNull(16)
        } else named[normalized]
        return rgb?.let { 0xFF000000.toInt() or it }
    }

    fun fontSize(value: String): Float? {
        val normalized = value.trim().lowercase()
        val size = when {
            normalized.endsWith("px") -> normalized.dropLast(2).trim().toFloatOrNull()
            normalized.endsWith("pt") -> normalized.dropLast(2).trim().toFloatOrNull()?.times(4f / 3f)
            else -> normalized.toFloatOrNull()
        }
        return size?.takeIf { it in 4f..128f }
    }

    fun legacyFontSize(value: String): Float? {
        if (value.trim().startsWith('+') || value.trim().startsWith('-')) return null
        val sizes = listOf(10f, 13f, 16f, 18f, 24f, 32f, 48f)
        val index = value.trim().toIntOrNull()?.minus(1) ?: return null
        return sizes.getOrNull(index)
    }

    fun cssDeclarations(style: String): Map<String, String> = buildMap {
        style.split(';').forEach { item ->
            val separator = item.indexOf(':')
            if (separator > 0) {
                val key = item.substring(0, separator).trim().lowercase()
                val value = item.substring(separator + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) putIfAbsent(key, value)
            }
        }
    }

    /** Withholds a trailing partial tag outside Markdown code until `>` arrives. */
    fun safeRenderPrefix(full: String): String {
        val lastGreaterThan = full.lastIndexOf('>')
        if (full.indexOf('<', lastGreaterThan + 1) < 0) return full
        var lineStart = 0
        var fenceMarker: Char? = null
        var fenceLength = 0
        while (lineStart <= full.length) {
            val newline = full.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) full.length else newline
            val line = full.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            val marker = trimmed.firstOrNull()
            val run = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker } else ""
            if (fenceMarker != null) {
                if (marker == fenceMarker && run.length >= fenceLength && trimmed.drop(run.length).isEmpty()) {
                    fenceMarker = null
                    fenceLength = 0
                }
            } else if (run.length >= 3) {
                fenceMarker = marker
                fenceLength = run.length
            } else {
                val spans = mutableListOf<IntRange>()
                var open = -1
                var cursor = 0
                while (cursor < line.length) {
                    if (line[cursor] == '\\') cursor += 2
                    else if (line[cursor] == '`') {
                        if (open < 0) open = cursor else { spans += open..cursor; open = -1 }
                        cursor++
                    } else cursor++
                }
                line.indices.forEach { index ->
                    val absolute = lineStart + index
                    if (absolute > lastGreaterThan && line[index] == '<' &&
                        spans.none { index > it.first && index < it.last } && mayStartTag(full, absolute)) {
                        return full.substring(0, absolute)
                    }
                }
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        return full
    }

    private fun mayStartTag(text: String, index: Int): Boolean =
        index + 1 >= text.length || text[index + 1].isAsciiLetter() || text[index + 1] == '/'

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
    private fun Char.isNameChar() = isAsciiLetter() || this in '0'..'9' || this == '-'
    private fun Char.isHtmlSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r'
}
