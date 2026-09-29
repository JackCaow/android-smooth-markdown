package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** Editable GFM table source. Cells retain their inline Markdown when structure changes. */
data class MarkdownSourceTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val alignments: List<MarkdownTableAlignment?>,
) {
    val columnCount: Int get() = headers.size

    /** Keeps the untouched table source when a formatted edit changes just one cell. */
    internal fun sourcePatchForCell(source: String, updated: MarkdownSourceTable): MarkdownSourceCellPatch? {
        return sourcePatchesForCells(source, updated)?.singleOrNull()
    }

    /** Creates independent patches for cell contents, leaving pipes, padding and alignment untouched. */
    internal fun sourcePatchesForCells(source: String, updated: MarkdownSourceTable): List<MarkdownSourceCellPatch>? {
        if (headers.size != updated.headers.size || rows.size != updated.rows.size ||
            alignments != updated.alignments || rows.indices.any { rows[it].size != updated.rows[it].size }) return null

        val changes = mutableListOf<Triple<Int, Int, String>>()
        headers.indices.forEach { column ->
            if (headers[column] != updated.headers[column]) changes += Triple(0, column, updated.headers[column])
        }
        rows.indices.forEach { row ->
            rows[row].indices.forEach { column ->
                if (rows[row][column] != updated.rows[row][column]) changes += Triple(row + 2, column, updated.rows[row][column])
            }
        }
        val lines = source.split('\n')
        val starts = mutableListOf<Int>()
        var offset = 0
        lines.forEach { line -> starts += offset; offset += line.length + 1 }
        val patches = changes.map { (lineIndex, columnIndex, replacement) ->
            if ('\n' in replacement || '\r' in replacement) return null
            val line = lines.getOrNull(lineIndex) ?: return null
            val cell = sourceCellRanges(line).getOrNull(columnIndex) ?: return null
            val raw = line.substring(cell.start, cell.end)
            val old = if (lineIndex == 0) headers[columnIndex] else rows[lineIndex - 2][columnIndex]
            if (raw.trim() != old) return null
            val contentStart = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length / 2 else it }
            val contentEnd = raw.indexOfLast { !it.isWhitespace() }.let { if (it < 0) contentStart else it + 1 }
            MarkdownSourceCellPatch(starts[lineIndex] + cell.start + contentStart, starts[lineIndex] + cell.start + contentEnd, replacement)
        }
        val patched = patches.sortedByDescending { it.start }.fold(source) { current, patch ->
            current.replaceRange(patch.start, patch.end, patch.replacement)
        }
        return patches.takeIf { parse(patched) == updated }
    }

    fun toMarkdown(): String {
        fun line(cells: List<String>) = "| ${cells.joinToString(" | ")} |"
        val markers = headers.indices.map { index ->
            when (alignments.getOrNull(index)) {
                MarkdownTableAlignment.LEFT -> ":---"
                MarkdownTableAlignment.CENTER -> ":---:"
                MarkdownTableAlignment.RIGHT -> "---:"
                null -> "---"
            }
        }
        return (listOf(line(headers), line(markers)) + rows.map(::line)).joinToString("\n")
    }

    fun replaceCell(rowIndex: Int, columnIndex: Int, text: String, header: Boolean = false): MarkdownSourceTable {
        if (columnIndex !in headers.indices) return this
        val escaped = text.replace("|", "\\|")
        if (header) return copy(headers = headers.toMutableList().also { it[columnIndex] = escaped })
        if (rowIndex !in rows.indices) return this
        return copy(rows = rows.toMutableList().also { list ->
            list[rowIndex] = list[rowIndex].toMutableList().also { it[columnIndex] = escaped }
        })
    }

    fun insertRowBefore(index: Int): MarkdownSourceTable = insertRow(index.coerceIn(0, rows.size))
    fun insertRowAfter(index: Int): MarkdownSourceTable = insertRow((index + 1).coerceIn(0, rows.size))
    private fun insertRow(index: Int) = copy(rows = rows.toMutableList().also { it.add(index, List(columnCount) { "" }) })
    fun deleteRow(index: Int): MarkdownSourceTable =
        if (index !in rows.indices) this else copy(rows = rows.toMutableList().also { it.removeAt(index) })

    fun insertColumnBefore(index: Int): MarkdownSourceTable = insertColumn(index.coerceIn(0, columnCount))
    fun insertColumnAfter(index: Int): MarkdownSourceTable = insertColumn((index + 1).coerceIn(0, columnCount))
    private fun insertColumn(index: Int) = copy(
        headers = headers.toMutableList().also { it.add(index, "") },
        alignments = alignments.toMutableList().also { it.add(index, null) },
        rows = rows.map { row -> row.toMutableList().also { it.add(index, "") } },
    )
    fun deleteColumn(index: Int): MarkdownSourceTable =
        if (index !in headers.indices || columnCount == 1) this else copy(
            headers = headers.toMutableList().also { it.removeAt(index) },
            alignments = alignments.toMutableList().also { it.removeAt(index) },
            rows = rows.map { row -> row.toMutableList().also { it.removeAt(index) } },
        )

    fun setColumnAlignment(index: Int, alignment: MarkdownTableAlignment?): MarkdownSourceTable =
        if (index !in headers.indices) this else copy(alignments = alignments.toMutableList().also { it[index] = alignment })

    companion object {
        fun parse(source: String): MarkdownSourceTable? {
            val lines = source.split('\n')
            if (lines.size < 2) return null
            val headers = splitCells(lines[0])
            val markers = splitCells(lines[1])
            if (headers.isEmpty() || headers.size != markers.size) return null
            if (markers.any { !Regex(":?-{3,}:?").matches(it) }) return null
            val alignments = markers.map { marker ->
                when {
                    marker.startsWith(':') && marker.endsWith(':') -> MarkdownTableAlignment.CENTER
                    marker.startsWith(':') -> MarkdownTableAlignment.LEFT
                    marker.endsWith(':') -> MarkdownTableAlignment.RIGHT
                    else -> null
                }
            }
            val rows = lines.drop(2).map { line ->
                splitCells(line).let { cells -> List(headers.size) { cells.getOrElse(it) { "" } } }
            }
            return MarkdownSourceTable(headers, rows, alignments)
        }

        private fun splitCells(line: String): List<String> {
            val parts = mutableListOf<String>()
            var start = 0
            var slashCount = 0
            for (index in line.indices) {
                val char = line[index]
                if (char == '|' && slashCount % 2 == 0) {
                    parts += line.substring(start, index).trim()
                    start = index + 1
                }
                slashCount = if (char == '\\') slashCount + 1 else 0
            }
            parts += line.substring(start).trim()
            if (line.trimStart().startsWith('|') && parts.firstOrNull() == "") parts.removeAt(0)
            if (line.trimEnd().endsWith('|') && parts.lastOrNull() == "") parts.removeAt(parts.lastIndex)
            return parts
        }
    }
}

internal data class MarkdownSourceCellPatch(val start: Int, val end: Int, val replacement: String)

/** Ranges between unescaped pipes, in the original line rather than normalized table text. */
private fun sourceCellRanges(line: String): List<TextRange> {
    val cells = mutableListOf<TextRange>()
    var start = 0
    var slashes = 0
    line.forEachIndexed { index, char ->
        if (char == '|' && slashes % 2 == 0) {
            cells += TextRange(start, index)
            start = index + 1
        }
        slashes = if (char == '\\') slashes + 1 else 0
    }
    cells += TextRange(start, line.length)
    if (line.trimStart().startsWith('|') && cells.firstOrNull()?.let { line.substring(it.start, it.end).isBlank() } == true) cells.removeAt(0)
    if (line.trimEnd().endsWith('|') && cells.lastOrNull()?.let { line.substring(it.start, it.end).isBlank() } == true) cells.removeAt(cells.lastIndex)
    return cells
}

enum class MarkdownTableAlignment { LEFT, CENTER, RIGHT }

data class MarkdownSourceTableAtRange(val range: TextRange, val table: MarkdownSourceTable)

/** Locates a GFM table containing a UTF-16 source offset, excluding fenced code blocks. */
internal fun findSourceTable(source: String, offset: Int): MarkdownSourceTableAtRange? {
    val lines = source.split('\n')
    val starts = mutableListOf<Int>()
    var position = 0
    lines.forEach { line -> starts += position; position += line.length + 1 }
    var fence: Char? = null
    var fenceLength = 0
    var index = 0
    while (index + 1 < lines.size) {
        val indentation = lines[index].takeWhile { it == ' ' }.length
        val trimmed = lines[index].drop(indentation)
        val marker = trimmed.firstOrNull()
        val fenceRun = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker } else ""
        if (fence != null) {
            if (indentation <= 3 && marker == fence && fenceRun.length >= fenceLength && trimmed.drop(fenceRun.length).isBlank()) {
                fence = null
                fenceLength = 0
            }
            index++
            continue
        }
        if (indentation <= 3 && fenceRun.length >= 3) {
            fence = marker
            fenceLength = fenceRun.length
            index++
            continue
        }
        val candidate = MarkdownSourceTable.parse(lines.subList(index, index + 2).joinToString("\n"))
        if (candidate == null) { index++; continue }
        var endLine = index + 1
        while (endLine + 1 < lines.size && lines[endLine + 1].contains('|') && lines[endLine + 1].isNotBlank()) endLine++
        val range = TextRange(starts[index], starts[endLine] + lines[endLine].length)
        if (offset in range.min..range.max) {
            val table = MarkdownSourceTable.parse(lines.subList(index, endLine + 1).joinToString("\n"))
            if (table != null) return MarkdownSourceTableAtRange(range, table)
        }
        index = endLine + 1
    }
    return null
}
