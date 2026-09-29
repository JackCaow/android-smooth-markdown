package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.ParserPluginRegistry

/** UTF-16 offsets in the text a formatted GFM cell actually displays. */
internal class TableCellTextSpan(val block: MarkdownDocumentBlock, val cell: MarkdownTableCellPosition) {
    val range: TextRange = sourceTableCellContentRange(block.source, cell.rowIndex, cell.columnIndex)
        ?: error("Invalid table cell source")
    val raw: String = block.source.substring(range.min, range.max)
    private val sourceBoundaries: List<Int> = buildList {
        add(0)
        var index = 0
        while (index < raw.length) {
            index += if (raw[index] == '\\' && index + 1 < raw.length && raw[index + 1] == '|') 2 else 1
            add(index)
        }
    }
    val visible: String = raw.replace("\\|", "|")

    fun sourceOffset(visibleOffset: Int): Int? = sourceBoundaries.getOrNull(visibleOffset)

    fun validOffset(offset: Int): Boolean = offset in 0..visible.length &&
        safeUtf16Boundary(visible, offset) == offset && sourceOffset(offset) != null

    fun slice(start: Int, end: Int): String? {
        val from = sourceOffset(start) ?: return null
        val to = sourceOffset(end) ?: return null
        return raw.substring(from, to)
    }

    fun patch(start: Int, end: Int, replacement: String): String? {
        val from = sourceOffset(start) ?: return null
        val to = sourceOffset(end) ?: return null
        return block.source.replaceRange(range.min + from, range.min + to, replacement)
    }

    fun validPatch(replacement: String): Boolean {
        val before = MarkdownSourceTable.parse(block.source) ?: return false
        val after = MarkdownSourceTable.parse(replacement) ?: return false
        if (before.columnCount != after.columnCount || before.rows.size != after.rows.size ||
            before.alignments != after.alignments) return false
        if (before.headers.indices.any { column ->
                (cell.rowIndex != 0 || column != cell.columnIndex) && before.headers[column] != after.headers[column]
            }) return false
        if (before.rows.indices.any { row -> before.rows[row].indices.any { column ->
                (cell.rowIndex != row + 1 || cell.columnIndex != column) && before.rows[row][column] != after.rows[row][column]
            } }) return false
        return true
    }
}

/** Source-checked same-cell edits and safe table-edge ranges into neighboring prose. */
internal class MarkdownTableTextSelection private constructor(
    private val source: String,
    private val document: MarkdownDocument,
    private val firstIndex: Int,
    private val lastIndex: Int,
    private val first: Endpoint,
    private val last: Endpoint,
    private val plugins: ParserPluginRegistry?,
) {
    private data class Endpoint(
        val block: MarkdownDocumentBlock,
        val offset: Int,
        val table: TableCellTextSpan?,
        val inline: MarkdownInlineEditing?,
    ) {
        val visibleLength: Int get() = table?.visible?.length ?: inline!!.visible.length

        fun fragment(start: Int, end: Int): String? {
            if (start == end) return ""
            if (table != null) return table.slice(start, end)
            val body = inline?.sliceVisibleRange(TextRange(start, end)) ?: return null
            return MarkdownFormattedBlock.markdown(block, body)
        }

        fun left(): String? = if (table != null) table.patch(offset, visibleLength, "")
            else inline?.splitVisibleRange(TextRange(offset, visibleLength))?.before?.let {
                if (it.isEmpty()) "" else MarkdownFormattedBlock.markdown(block, it)
            }

        fun right(): String? = if (table != null) table.patch(0, offset, "")
            else inline?.splitVisibleRange(TextRange(0, offset))?.after?.let {
                if (it.isEmpty()) "" else MarkdownFormattedBlock.markdown(block, it)
            }
    }

    data class Edit(val range: TextRange, val replacement: String, val caret: Int)

    /** Copies selected cell source text and intervening block trivia, without unselected table structure. */
    fun copy(): String? {
        if (firstIndex == lastIndex) return first.table?.slice(first.offset, last.offset)?.takeIf { it.isNotEmpty() }
        val fragments = (firstIndex..lastIndex).mapNotNull { index ->
            val block = document.blocks[index]
            val markdown = when (index) {
                firstIndex -> first.fragment(first.offset, first.visibleLength)
                lastIndex -> last.fragment(0, last.offset)
                else -> block.source
            } ?: return null
            if (markdown.isEmpty()) null else block to markdown
        }
        if (fragments.isEmpty()) return null
        return buildString {
            fragments.forEachIndexed { index, (block, markdown) ->
                if (index > 0) append(source.substring(fragments[index - 1].first.range.max, block.range.min))
                append(markdown)
            }
        }
    }

    fun edit(markdown: String): Edit? {
        if (!wellFormedUtf16(markdown)) return null
        if (firstIndex == lastIndex) {
            if ('\r' in markdown || '\n' in markdown) return null
            val span = first.table ?: return null
            val escaped = escapeUnescapedPipes(markdown)
            val replacement = span.patch(first.offset, last.offset, escaped) ?: return null
            if (replacement == first.block.source || !span.validPatch(replacement)) return null
            val range = first.block.range
            if (!validCandidate(range, replacement, listOf(first.block.kind to replacement))) return null
            val caret = span.range.min + (span.sourceOffset(first.offset) ?: return null) + escaped.length
            return Edit(range, replacement, caret)
        }
        val inserted = if (markdown.isEmpty()) emptyList() else MarkdownDocumentCodec.parse(markdown, plugins).blocks
        if (markdown.isNotEmpty() && (inserted.isEmpty() || inserted.first().range.min != 0 ||
                inserted.last().range.max != markdown.length)) return null
        val left = first.left() ?: return null
        val right = last.right() ?: return null
        if (first.table != null && !first.table.validPatch(left)) return null
        if (last.table != null && !last.table.validPatch(right)) return null
        val expected = mutableListOf<Pair<MarkdownBlockKind, String>>()
        val pieces = mutableListOf<String>()
        if (left.isNotEmpty()) { expected += first.block.kind to left; pieces += left }
        if (inserted.isNotEmpty()) { expected += inserted.map { it.kind to it.source }; pieces += markdown }
        if (right.isNotEmpty()) { expected += last.block.kind to right; pieces += right }
        val separator = if (source.substring(first.block.range.max, last.block.range.min).contains("\r\n")) "\r\n\r\n" else "\n\n"
        val replacement = pieces.joinToString(separator)
        val range = TextRange(first.block.range.min, last.block.range.max)
        if (replacement == source.substring(range.min, range.max) || !validCandidate(range, replacement, expected)) return null
        val caret = if (markdown.isNotEmpty()) (if (left.isNotEmpty()) left.length + separator.length else 0) + markdown.length
            else left.length
        return Edit(range, replacement, caret.coerceIn(0, replacement.length))
    }

    private fun validCandidate(range: TextRange, replacement: String,
                               expected: List<Pair<MarkdownBlockKind, String>>): Boolean {
        val parsed = MarkdownDocumentCodec.parse(source.replaceRange(range.min, range.max, replacement), plugins).blocks
        val before = document.blocks.take(firstIndex)
        val after = document.blocks.drop(lastIndex + 1)
        return parsed.size == before.size + expected.size + after.size &&
            before.zip(parsed).all { (old, next) -> old.kind == next.kind && old.source == next.source } &&
            expected.zip(parsed.drop(before.size)).all { (want, next) -> want.first == next.kind && want.second == next.source } &&
            after.zip(parsed.takeLast(after.size)).all { (old, next) -> old.kind == next.kind && old.source == next.source }
    }

    companion object {
        fun resolve(source: String, document: MarkdownDocument, selected: MarkdownFormattedTextSelection,
                    enableWikilinks: Boolean, plugins: ParserPluginRegistry?): MarkdownTableTextSelection? {
            if (selected.source != source || selected.anchor.listPath != null || selected.focus.listPath != null ||
                selected.anchor.listLineIndex != 0 || selected.focus.listLineIndex != 0) return null
            val anchorIndex = document.blocks.indexOfFirst { it.id == selected.anchor.blockId }
            val focusIndex = document.blocks.indexOfFirst { it.id == selected.focus.blockId }
            if (anchorIndex < 0 || focusIndex < 0) return null
            val ordered = listOf(anchorIndex to selected.anchor, focusIndex to selected.focus).sortedWith(
                compareBy({ it.first }, { it.second.tableCell?.rowIndex ?: -1 },
                    { it.second.tableCell?.columnIndex ?: -1 }, { it.second.offset }))
            val firstIndex = ordered[0].first
            val lastIndex = ordered[1].first
            val firstPosition = ordered[0].second
            val lastPosition = ordered[1].second
            if (firstIndex == lastIndex && firstPosition == lastPosition) return null
            if (firstIndex == lastIndex && (firstPosition.tableCell == null ||
                    firstPosition.tableCell != lastPosition.tableCell)) return null
            if (firstIndex != lastIndex && (firstPosition.tableCell == null && lastPosition.tableCell == null)) return null
            if ((firstIndex + 1 until lastIndex).any { document.blocks[it].kind !in setOf(
                    MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING, MarkdownBlockKind.CODE,
                    MarkdownBlockKind.TABLE, MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST,
                ) }) return null
            fun endpoint(index: Int, position: MarkdownFormattedTextPosition): Endpoint? {
                val block = document.blocks[index]
                val cell = position.tableCell
                if (cell != null) {
                    if (block.kind != MarkdownBlockKind.TABLE || cell.rowIndex < 0 || cell.columnIndex < 0) return null
                    if (sourceTableCellContentRange(block.source, cell.rowIndex, cell.columnIndex) == null) return null
                    val span = TableCellTextSpan(block, cell)
                    if (!span.validOffset(position.offset)) return null
                    return Endpoint(block, position.offset, span, null)
                }
                val inline = MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return null
                if (position.offset !in 0..inline.visible.length ||
                    inline.splitVisibleRange(TextRange(0, position.offset)) == null ||
                    inline.splitVisibleRange(TextRange(position.offset, inline.visible.length)) == null) return null
                return Endpoint(block, position.offset, null, inline)
            }
            val first = endpoint(firstIndex, firstPosition) ?: return null
            val last = endpoint(lastIndex, lastPosition) ?: return null
            if (firstIndex != lastIndex) {
                // A prefix of a table can remain a table only when it ends in the last cell;
                // a suffix can remain a table only when it starts in the first header cell.
                first.table?.let { span ->
                    val table = MarkdownSourceTable.parse(span.block.source) ?: return null
                    if (span.cell.rowIndex != table.rows.size || span.cell.columnIndex != table.columnCount - 1) return null
                }
                last.table?.let { span ->
                    if (span.cell.rowIndex != 0 || span.cell.columnIndex != 0) return null
                }
            }
            return MarkdownTableTextSelection(source, document, firstIndex, lastIndex, first, last, plugins)
        }

        /** A visible pipe is cell content only with an odd number of source backslashes before it. */
        private fun escapeUnescapedPipes(value: String): String = buildString {
            var slashes = 0
            value.forEach { char ->
                if (char == '|' && slashes % 2 == 0) append('\\')
                append(char)
                slashes = if (char == '\\') slashes + 1 else 0
            }
        }

        private fun wellFormedUtf16(value: String): Boolean {
            var index = 0
            while (index < value.length) {
                if (Character.isHighSurrogate(value[index])) {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
                    index++
                } else if (Character.isLowSurrogate(value[index])) return false
                index++
            }
            return true
        }
    }
}
