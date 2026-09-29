package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** Explicitly marked quote lines, with offsets into the original top-level source block. */
internal class MarkdownSourceQuote private constructor(
    val block: MarkdownDocumentBlock,
    val lines: List<Line>,
) {
    data class Line(
        val index: Int,
        val start: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val end: Int,
        val prefix: String,
        val content: String,
        val terminator: String,
    ) {
        val depth: Int get() = prefix.count { it == '>' }
        fun inline(enableWikilinks: Boolean): MarkdownInlineEditing =
            MarkdownInlineEditing.parse(content, enableWikilinks)
    }

    /** Replace one physical line's rendered content, preserving its quote markers and CR/LF. */
    fun replaceVisibleLine(index: Int, visible: String, enableWikilinks: Boolean): String? {
        val line = lines.getOrNull(index) ?: return null
        if (visible.any { it == '\n' || it == '\r' } || !wellFormedUtf16(visible)) return null
        val next = line.inline(enableWikilinks).replaceVisible(visible) ?: return null
        return replaceLineContent(index, next)
    }

    fun replaceLineContent(index: Int, content: String): String? {
        val line = lines.getOrNull(index) ?: return null
        if (content.any { it == '\n' || it == '\r' } || !wellFormedUtf16(content) ||
            content == line.content) return null
        val candidate = block.source.replaceRange(line.contentStart, line.contentEnd, content)
        val parsed = MarkdownDocumentCodec.parse(candidate).blocks.singleOrNull() ?: return null
        if (parsed.kind != MarkdownBlockKind.QUOTE || parsed.range != TextRange(0, candidate.length)) return null
        val after = parse(parsed) ?: return null
        if (after.lines.size != lines.size || after.lines.indices.any { it != index &&
                (after.lines[it].prefix != lines[it].prefix || after.lines[it].content != lines[it].content ||
                    after.lines[it].terminator != lines[it].terminator) }) return null
        return candidate
    }

    companion object {
        private val marker = Regex("^(?: {0,3}>[ \\t]?)+")

        fun parse(block: MarkdownDocumentBlock): MarkdownSourceQuote? {
            if (block.kind != MarkdownBlockKind.QUOTE || block.source.isEmpty()) return null
            val lines = mutableListOf<Line>()
            var start = 0
            while (start < block.source.length) {
                var end = start
                while (end < block.source.length && block.source[end] != '\n' && block.source[end] != '\r') end++
                val breakEnd = when {
                    end >= block.source.length -> end
                    block.source[end] == '\r' && end + 1 < block.source.length && block.source[end + 1] == '\n' -> end + 2
                    else -> end + 1
                }
                val raw = block.source.substring(start, end)
                val prefix = marker.find(raw)?.value ?: return null
                lines += Line(lines.size, start, start + prefix.length, end, breakEnd, prefix,
                    raw.substring(prefix.length), block.source.substring(end, breakEnd))
                start = breakEnd
            }
            return lines.takeIf { it.isNotEmpty() }?.let { MarkdownSourceQuote(block, it) }
        }

        private fun wellFormedUtf16(value: String): Boolean {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (Character.isHighSurrogate(char)) {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
                    index++
                } else if (Character.isLowSurrogate(char)) return false
                index++
            }
            return true
        }
    }
}
