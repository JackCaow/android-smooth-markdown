package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** Source-preserving inline marks for character endpoints inside one structured block. */
internal object FormattedStructuredInlineMarks {
    fun list(block: MarkdownDocumentBlock, first: MarkdownFormattedTextPosition,
             last: MarkdownFormattedTextPosition, kind: InlineMarkKind, destination: String?,
             enableWikilinks: Boolean): String? {
        if (first.blockId != block.id || last.blockId != block.id ||
            first.listPath == null || last.listPath == null ||
            first.tableCell != null || last.tableCell != null ||
            first.quoteLineIndex != null || last.quoteLineIndex != null ||
            block.kind !in setOf(MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST)) return null
        val list = MarkdownSourceList.parse(block) ?: return null
        fun location(position: MarkdownFormattedTextPosition): Int? {
            val path = position.listPath ?: return null
            return list.item(path)?.lines?.getOrNull(position.listLineIndex)?.start
        }
        val firstStart = location(first) ?: return null
        val lastStart = location(last) ?: return null
        val (start, end) = if (firstStart < lastStart || firstStart == lastStart && first.offset <= last.offset)
            first to last else last to first
        val startLine = location(start) ?: return null
        val endLine = location(end) ?: return null
        if (startLine == endLine && start.offset == end.offset) return null
        val lines = list.sourceLines().sortedBy { it.start }
        val chosen = lines.filter { it.start + it.prefix.length in startLine..endLine }
        if (chosen.isEmpty() || chosen.first().start + chosen.first().prefix.length != startLine ||
            chosen.last().start + chosen.last().prefix.length != endLine) return null
        if (list.rawSpans().any { it.min < endLine && it.max > startLine }) return null
        val patches = mutableListOf<Patch>()
        for (line in chosen) {
            val contentStart = line.start + line.prefix.length
            val inline = MarkdownInlineEditing.parse(line.content, enableWikilinks)
            val lower = if (contentStart == startLine) start.offset else 0
            val upper = if (contentStart == endLine) end.offset else inline.visible.length
            if (lower !in 0..inline.visible.length || upper !in lower..inline.visible.length) return null
            if (lower == upper) continue
            val wrapped = wrapped(inline, TextRange(lower, upper), kind, destination, enableWikilinks) ?: return null
            patches += Patch(contentStart, contentStart + line.content.length, wrapped)
        }
        val candidate = patch(block.source, patches) ?: return null
        val parsed = MarkdownDocumentCodec.parse(candidate).blocks.singleOrNull() ?: return null
        if (parsed.kind != block.kind || parsed.range != TextRange(0, candidate.length)) return null
        val next = MarkdownSourceList.parse(parsed) ?: return null
        val nextLines = next.sourceLines().sortedBy { it.start }
        if (nextLines.size != lines.size || lines.zip(nextLines).any { (old, new) ->
                old.prefix != new.prefix || old.depth != new.depth || old.firstInItem != new.firstInItem ||
                    MarkdownInlineEditing.parse(old.content, enableWikilinks).visible !=
                    MarkdownInlineEditing.parse(new.content, enableWikilinks).visible
            }) return null
        return candidate
    }

    fun quote(block: MarkdownDocumentBlock, first: MarkdownFormattedTextPosition,
              last: MarkdownFormattedTextPosition, kind: InlineMarkKind, destination: String?,
              enableWikilinks: Boolean): String? {
        if (first.blockId != block.id || last.blockId != block.id ||
            first.quoteLineIndex == null || last.quoteLineIndex == null ||
            first.listPath != null || last.listPath != null || first.tableCell != null || last.tableCell != null) return null
        val quote = MarkdownSourceQuote.parse(block) ?: return null
        val (start, end) = if (first.quoteLineIndex < last.quoteLineIndex ||
            first.quoteLineIndex == last.quoteLineIndex && first.offset <= last.offset) first to last else last to first
        if (start.quoteLineIndex == end.quoteLineIndex && start.offset == end.offset) return null
        val patches = mutableListOf<Patch>()
        for (index in start.quoteLineIndex!!..end.quoteLineIndex!!) {
            val line = quote.lines.getOrNull(index) ?: return null
            val inline = line.inline(enableWikilinks)
            val lower = if (index == start.quoteLineIndex) start.offset else 0
            val upper = if (index == end.quoteLineIndex) end.offset else inline.visible.length
            if (lower !in 0..inline.visible.length || upper !in lower..inline.visible.length) return null
            if (lower == upper) continue
            val wrapped = wrapped(inline, TextRange(lower, upper), kind, destination, enableWikilinks) ?: return null
            patches += Patch(line.contentStart, line.contentEnd, wrapped)
        }
        val candidate = patch(block.source, patches) ?: return null
        val parsed = MarkdownDocumentCodec.parse(candidate).blocks.singleOrNull() ?: return null
        if (parsed.kind != MarkdownBlockKind.QUOTE || parsed.range != TextRange(0, candidate.length)) return null
        val next = MarkdownSourceQuote.parse(parsed) ?: return null
        if (next.lines.size != quote.lines.size || quote.lines.zip(next.lines).any { (old, new) ->
                old.prefix != new.prefix || old.terminator != new.terminator ||
                    old.inline(enableWikilinks).visible != new.inline(enableWikilinks).visible
            }) return null
        return candidate
    }

    fun table(block: MarkdownDocumentBlock, first: MarkdownFormattedTextPosition,
              last: MarkdownFormattedTextPosition, kind: InlineMarkKind, destination: String?,
              enableWikilinks: Boolean): String? {
        if (first.blockId != block.id || last.blockId != block.id ||
            first.tableCell == null || last.tableCell == null ||
            first.listPath != null || last.listPath != null ||
            first.quoteLineIndex != null || last.quoteLineIndex != null || block.kind != MarkdownBlockKind.TABLE) return null
        val table = MarkdownSourceTable.parse(block.source) ?: return null
        fun key(position: MarkdownFormattedTextPosition): Int {
            val cell = position.tableCell!!
            return cell.rowIndex * table.columnCount + cell.columnIndex
        }
        val firstKey = key(first)
        val lastKey = key(last)
        val count = (table.rows.size + 1) * table.columnCount
        if (firstKey !in 0 until count || lastKey !in 0 until count) return null
        val (start, end) = if (firstKey < lastKey || firstKey == lastKey && first.offset <= last.offset)
            first to last else last to first
        if (key(start) == key(end) && start.offset == end.offset) return null
        var updated = table
        var changed = false
        for (index in key(start)..key(end)) {
            val row = index / table.columnCount
            val column = index % table.columnCount
            val raw = if (row == 0) table.headers[column] else table.rows[row - 1][column]
            val cell = MarkdownTableCellPosition(row, column)
            if (sourceTableCellContentRange(block.source, row, column) == null) return null
            val span = TableCellTextSpan(block, cell)
            val inline = MarkdownInlineEditing.parse(raw, enableWikilinks)
            // The current table field displays Markdown source, apart from escaped pipes.
            // Character endpoints cannot safely address hidden delimiters in an existing mark.
            if (inline.visible != span.visible) return null
            val lower = if (index == key(start)) start.offset else 0
            val upper = if (index == key(end)) end.offset else span.visible.length
            if (!span.validOffset(lower) || !span.validOffset(upper) || upper < lower) return null
            if (lower == upper) continue
            val wrapped = wrapped(inline, TextRange(lower, upper), kind, destination, enableWikilinks) ?: return null
            if (wrapped == raw) continue
            changed = true
            updated = if (row == 0) updated.copy(headers = updated.headers.toMutableList().also { it[column] = wrapped })
                else updated.copy(rows = updated.rows.toMutableList().also { rows ->
                    rows[row - 1] = rows[row - 1].toMutableList().also { it[column] = wrapped }
                })
        }
        if (!changed) return null
        val patches = table.sourcePatchesForCells(block.source, updated) ?: return null
        return patches.sortedByDescending { it.start }.fold(block.source) { source, patch ->
            source.replaceRange(patch.start, patch.end, patch.replacement)
        }
    }

    private data class Patch(val start: Int, val end: Int, val replacement: String)

    private fun patch(source: String, patches: List<Patch>): String? {
        if (patches.isEmpty() || patches.all { source.substring(it.start, it.end) == it.replacement }) return null
        return patches.sortedByDescending { it.start }.fold(source) { current, item ->
            current.replaceRange(item.start, item.end, item.replacement)
        }
    }

    private fun wrapped(inline: MarkdownInlineEditing, range: TextRange, kind: InlineMarkKind,
                        destination: String?, enableWikilinks: Boolean): String? {
        val result = if (range.min == 0 && range.max == inline.visible.length)
            inline.wrapComplete(kind, destination, allowMixed = true)
        else inline.wrap(range, kind, destination)
        return result?.takeIf { MarkdownInlineEditing.parse(it, enableWikilinks).visible == inline.visible }
    }
}
