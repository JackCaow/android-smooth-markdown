package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** One search hit in the text shown by Formatted mode, with its exact source location. */
internal data class FormattedSearchMatch(
    val target: FormattedSearchTarget,
    val visibleRange: TextRange,
    val sourceRange: TextRange,
)

internal sealed interface FormattedSearchTarget {
    val blockId: String

    data class Text(override val blockId: String) : FormattedSearchTarget
    data class ListLine(override val blockId: String, val path: List<Int>, val lineIndex: Int) : FormattedSearchTarget
    data class QuoteLine(override val blockId: String, val lineIndex: Int) : FormattedSearchTarget
    data class TableCell(override val blockId: String, val row: Int, val column: Int) : FormattedSearchTarget
    data class Raw(override val blockId: String) : FormattedSearchTarget
}

/** Formatted search follows displayed fields; Markdown markers and link destinations are not hits. */
internal class FormattedSearch private constructor(val matches: List<FormattedSearchMatch>) {
    private val matchesByTarget = matches.groupBy { it.target }
    fun ranges(target: FormattedSearchTarget): List<TextRange> =
        matchesByTarget[target].orEmpty().map { it.visibleRange }

    companion object {
        val Empty = FormattedSearch(emptyList())

        fun find(document: MarkdownDocument, query: String, enableWikilinks: Boolean): FormattedSearch {
            if (query.isEmpty()) return Empty
            val matches = mutableListOf<FormattedSearchMatch>()

            fun add(target: FormattedSearchTarget, visible: String, sourceRange: (TextRange) -> TextRange?) {
                if (visible.length < query.length) return
                var cursor = 0
                while (cursor <= visible.length - query.length && matches.size < 500) {
                    val at = visible.indexOf(query, cursor, ignoreCase = true)
                    if (at < 0) break
                    val end = at + query.length
                    if (safeBoundary(visible, at) && safeBoundary(visible, end)) {
                        val visibleRange = TextRange(at, end)
                        val mapped = sourceRange(visibleRange)
                        if (mapped != null && mapped.min < mapped.max && mapped.max <= document.source.length) {
                            matches += FormattedSearchMatch(target, visibleRange, mapped)
                        }
                    }
                    cursor = end // Flutter search uses non-overlapping matches.
                }
            }

            for (block in document.blocks) {
                if (matches.size >= 500) break
                when (block.kind) {
                    MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING -> {
                        val body = MarkdownFormattedBlock.text(block) ?: continue
                        val inline = MarkdownInlineEditing.parse(body, enableWikilinks)
                        val bodyStart = if (block.kind == MarkdownBlockKind.HEADING)
                            Regex("^ {0,3}#{1,6}[ \\t]+").find(block.source)?.value?.length ?: continue else 0
                        add(FormattedSearchTarget.Text(block.id), inline.visible) { range ->
                            inline.sourceRangeForVisible(range)?.let {
                                TextRange(block.range.min + bodyStart + it.min, block.range.min + bodyStart + it.max)
                            }
                        }
                    }
                    MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST -> {
                        val list = MarkdownSourceList.parse(block) ?: continue
                        fun visit(items: List<MarkdownSourceList.Item>, prefix: List<Int>, base: Int) {
                            items.forEachIndexed { index, item ->
                                if (matches.size >= 500) return
                                val path = prefix + (base + index)
                                var nestedBase = 0
                                item.parts.forEach { part ->
                                    when (part) {
                                        is MarkdownSourceList.Line -> {
                                            val lineIndex = item.lines.indexOf(part)
                                            val inline = MarkdownInlineEditing.parse(
                                                list.lineContent(path, lineIndex).orEmpty(), enableWikilinks)
                                            add(FormattedSearchTarget.ListLine(block.id, path, lineIndex), inline.visible) { range ->
                                                inline.sourceRangeForVisible(range)?.let {
                                                    TextRange(block.range.min + part.start + it.min, block.range.min + part.start + it.max)
                                                }
                                            }
                                        }
                                        is MarkdownSourceList.NestedList -> {
                                            visit(part.items, path, nestedBase)
                                            nestedBase += part.items.size
                                        }
                                        is MarkdownSourceList.Raw -> Unit
                                    }
                                }
                            }
                        }
                        visit(list.items, emptyList(), 0)
                    }
                    MarkdownBlockKind.QUOTE -> {
                        val quote = MarkdownSourceQuote.parse(block) ?: continue
                        quote.lines.forEach { line ->
                            val inline = line.inline(enableWikilinks)
                            add(FormattedSearchTarget.QuoteLine(block.id, line.index), inline.visible) { range ->
                                inline.sourceRangeForVisible(range)?.let {
                                    TextRange(block.range.min + line.contentStart + it.min,
                                        block.range.min + line.contentStart + it.max)
                                }
                            }
                        }
                    }
                    MarkdownBlockKind.TABLE -> {
                        val table = MarkdownSourceTable.parse(block.source) ?: continue
                        tableCells@ for (row in 0..table.rows.size) for (column in table.headers.indices) {
                            if (matches.size >= 500) break@tableCells
                            val span = TableCellTextSpan(block, MarkdownTableCellPosition(row, column))
                            add(FormattedSearchTarget.TableCell(block.id, row, column), span.visible) { range ->
                                val start = span.sourceOffset(range.min)
                                val end = span.sourceOffset(range.max)
                                if (start == null || end == null) null else
                                    TextRange(block.range.min + span.range.min + start,
                                        block.range.min + span.range.min + end)
                            }
                        }
                    }
                    MarkdownBlockKind.RAW -> add(FormattedSearchTarget.Raw(block.id), block.source) { range ->
                        TextRange(block.range.min + range.min, block.range.min + range.max)
                    }
                    // Flutter Formatted Find deliberately omits fenced code and Mermaid sources.
                    else -> Unit
                }
            }
            return FormattedSearch(matches.sortedWith(compareBy({ it.sourceRange.min }, { it.sourceRange.max })).take(500))
        }

        private fun safeBoundary(text: String, offset: Int): Boolean = offset == 0 || offset == text.length ||
            !(text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate())
    }
}
