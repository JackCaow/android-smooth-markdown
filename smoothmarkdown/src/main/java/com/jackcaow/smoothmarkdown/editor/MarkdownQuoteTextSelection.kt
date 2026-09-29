package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.ParserPluginRegistry

/** Source-backed endpoints for explicitly marked quote lines and adjacent prose. */
internal class MarkdownQuoteTextSelection private constructor(
    private val source: String,
    private val document: MarkdownDocument,
    private val first: Endpoint,
    private val last: Endpoint,
    private val enableWikilinks: Boolean,
    private val plugins: ParserPluginRegistry?,
) {
    private data class Endpoint(
        val block: MarkdownDocumentBlock,
        val blockIndex: Int,
        val line: MarkdownSourceQuote.Line?,
        val inline: MarkdownInlineEditing,
        val offset: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val lineStart: Int,
    ) {
        fun fragment(start: Int, end: Int): String? {
            if (start == end) return ""
            val body = inline.sliceVisibleRange(TextRange(start, end)) ?: return null
            return if (line != null) line.prefix + body else MarkdownFormattedBlock.markdown(block, body)
        }
    }

    data class Edit(val range: TextRange, val replacement: String, val caret: Int)

    fun copy(): String? {
        if (first.contentStart == last.contentStart) {
            return first.fragment(first.offset, last.offset)?.takeIf(String::isNotEmpty)
        }
        val start = first.fragment(first.offset, first.inline.visible.length) ?: return null
        val end = last.fragment(0, last.offset) ?: return null
        val middleStart = if (first.line != null) first.contentEnd else first.block.range.max
        val middleEnd = if (last.line != null) last.lineStart else last.block.range.min
        if (middleStart > middleEnd) return null
        val middle = source.substring(middleStart, middleEnd)
        return (start + middle + end).takeIf(String::isNotEmpty)
    }

    /** Within one quote block, edit quote prose without changing its marker or untouched lines. */
    fun edit(markdown: String): Edit? {
        if (first.blockIndex != last.blockIndex || first.line == null || last.line == null ||
            first.line.depth != last.line.depth || markdown.any { it == '\n' || it == '\r' } ||
            !wellFormedUtf16(markdown)) return null
        val left = first.inline.splitVisibleRange(TextRange(first.offset, first.inline.visible.length))?.before ?: return null
        val right = last.inline.splitVisibleRange(TextRange(0, last.offset))?.after ?: return null
        val body = left + markdown + right
        if (MarkdownInlineEditing.parse(body, enableWikilinks).visible !=
            first.inline.visible.substring(0, first.offset) +
                MarkdownInlineEditing.parse(markdown, enableWikilinks).visible + last.inline.visible.substring(last.offset)) return null
        val range = TextRange(first.contentStart, last.contentEnd)
        val candidate = source.replaceRange(range.min, range.max, body)
        if (candidate == source) return null
        val parsed = MarkdownDocumentCodec.parse(candidate, plugins).blocks
        if (parsed.size != document.blocks.size || parsed.indices.any { index ->
                index != first.blockIndex &&
                    (parsed[index].kind != document.blocks[index].kind || parsed[index].source != document.blocks[index].source)
            }) return null
        val changed = parsed[first.blockIndex]
        if (changed.kind != MarkdownBlockKind.QUOTE) return null
        val next = MarkdownSourceQuote.parse(changed) ?: return null
        val previous = MarkdownSourceQuote.parse(first.block) ?: return null
        val removedLines = last.line.index - first.line.index
        if (next.lines.size != previous.lines.size - removedLines ||
            next.lines.take(first.line.index).map { Triple(it.prefix, it.content, it.terminator) } !=
                previous.lines.take(first.line.index).map { Triple(it.prefix, it.content, it.terminator) } ||
            next.lines.drop(first.line.index + 1).map { Triple(it.prefix, it.content, it.terminator) } !=
                previous.lines.drop(last.line.index + 1).map { Triple(it.prefix, it.content, it.terminator) } ||
            next.lines[first.line.index].prefix != first.line.prefix ||
            next.lines[first.line.index].content != body ||
            next.lines[first.line.index].terminator != last.line.terminator) return null
        val caret = left.length + markdown.length
        return Edit(range, body, caret.coerceIn(0, body.length))
    }

    companion object {
        fun resolve(source: String, document: MarkdownDocument, selected: MarkdownFormattedTextSelection,
                    enableWikilinks: Boolean, plugins: ParserPluginRegistry?): MarkdownQuoteTextSelection? {
            if (selected.source != source ||
                selected.anchor.quoteLineIndex == null && selected.focus.quoteLineIndex == null ||
                selected.anchor.listPath != null || selected.focus.listPath != null ||
                selected.anchor.tableCell != null || selected.focus.tableCell != null) return null
            fun endpoint(position: MarkdownFormattedTextPosition): Endpoint? {
                val index = document.blocks.indexOfFirst { it.id == position.blockId }
                val block = document.blocks.getOrNull(index) ?: return null
                val line = if (position.quoteLineIndex != null) {
                    MarkdownSourceQuote.parse(block)?.lines?.getOrNull(position.quoteLineIndex)
                        ?: return null
                } else null
                val inline = if (line != null) line.inline(enableWikilinks)
                    else MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return null
                if (position.offset !in 0..inline.visible.length ||
                    inline.splitVisibleRange(TextRange(0, position.offset)) == null ||
                    inline.splitVisibleRange(TextRange(position.offset, inline.visible.length)) == null) return null
                val headingPrefix = if (block.kind == MarkdownBlockKind.HEADING)
                    Regex("^ {0,3}#{1,6}[ \\t]+").find(block.source)?.value?.length ?: return null else 0
                val start = if (line != null) block.range.min + line.contentStart else
                    block.range.min + headingPrefix
                return Endpoint(block, index, line, inline, position.offset, start,
                    start + inline.source.length,
                    if (line != null) block.range.min + line.start else block.range.min)
            }
            val a = endpoint(selected.anchor) ?: return null
            val b = endpoint(selected.focus) ?: return null
            val ordered = listOf(a, b).sortedWith(compareBy({ it.blockIndex }, { it.contentStart }, { it.offset }))
            val first = ordered[0]
            val last = ordered[1]
            if (first.contentStart == last.contentStart && first.offset == last.offset) return null
            return MarkdownQuoteTextSelection(source, document, first, last, enableWikilinks, plugins)
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
