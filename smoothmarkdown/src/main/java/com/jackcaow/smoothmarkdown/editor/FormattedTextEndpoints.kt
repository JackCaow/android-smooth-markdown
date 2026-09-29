package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** A source-bound range assembled from caret positions in separate formatted text fields. */
internal data class FormattedTextEndpoints(
    val source: String,
    val anchor: MarkdownFormattedTextPosition,
    val focus: MarkdownFormattedTextPosition? = null,
) {
    fun withFocus(position: MarkdownFormattedTextPosition): FormattedTextEndpoints = copy(focus = position)

    fun selection(): MarkdownFormattedTextSelection? = focus?.let { MarkdownFormattedTextSelection(source, anchor, it) }

    /** A structured block fully enclosed by the two prose character endpoints. */
    fun containsCompleteBlock(blocks: List<MarkdownDocumentBlock>, blockId: String): Boolean {
        if (focus == null) return false
        val anchorIndex = blocks.indexOfFirst { it.id == anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == focus.blockId }
        val index = blocks.indexOfFirst { it.id == blockId }
        if (anchorIndex < 0 || focusIndex < 0 || index < 0) return false
        if (index !in minOf(anchorIndex, focusIndex) + 1 until maxOf(anchorIndex, focusIndex)) return false
        return blocks[index].kind in setOf(MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST,
            MarkdownBlockKind.CODE, MarkdownBlockKind.TABLE)
    }

    /** Visible UTF-16 range to paint inside a paragraph or ATX heading. */
    fun visibleRange(blocks: List<MarkdownDocumentBlock>, blockId: String, length: Int): TextRange? {
        val end = focus ?: return null
        val anchorIndex = blocks.indexOfFirst { it.id == anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == end.blockId }
        val index = blocks.indexOfFirst { it.id == blockId }
        if (anchorIndex < 0 || focusIndex < 0 || index < 0) return null
        val forward = anchorIndex < focusIndex || anchorIndex == focusIndex && anchor.offset <= end.offset
        val firstIndex = minOf(anchorIndex, focusIndex)
        val lastIndex = maxOf(anchorIndex, focusIndex)
        if (index !in firstIndex..lastIndex) return null
        val start = when (index) {
            firstIndex -> if (forward) anchor.offset else end.offset
            else -> 0
        }
        val finish = when (index) {
            lastIndex -> if (forward) end.offset else anchor.offset
            else -> length
        }
        if (start !in 0..length || finish !in start..length || start == finish) return null
        return TextRange(start, finish)
    }

    fun listVisibleRange(blocks: List<MarkdownDocumentBlock>, blockId: String, path: List<Int>,
                         lineIndex: Int, length: Int): TextRange? {
        val end = focus ?: return null
        val blockIndex = blocks.indexOfFirst { it.id == blockId }
        val anchorIndex = blocks.indexOfFirst { it.id == anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == end.blockId }
        if (blockIndex < 0 || anchorIndex < 0 || focusIndex < 0 || lineIndex != 0 || path.size != 1) return null
        fun key(position: MarkdownFormattedTextPosition, index: Int) =
            Triple(index, position.listPath?.firstOrNull() ?: -1, position.offset)
        val forward = compareValuesBy(key(anchor, anchorIndex), key(end, focusIndex),
            { it.first }, { it.second }, { it.third }) <= 0
        val first = if (forward) anchor else end
        val last = if (forward) end else anchor
        val firstIndex = minOf(anchorIndex, focusIndex)
        val lastIndex = maxOf(anchorIndex, focusIndex)
        if (blockIndex !in firstIndex..lastIndex) return null
        val itemIndex = path.first()
        val start = if (blockIndex == firstIndex && first.listPath != null) {
            if (itemIndex < first.listPath.first()) return null
            if (itemIndex == first.listPath.first()) first.offset else 0
        } else 0
        val finish = if (blockIndex == lastIndex && last.listPath != null) {
            if (itemIndex > last.listPath.first()) return null
            if (itemIndex == last.listPath.first()) last.offset else length
        } else length
        if (start !in 0..length || finish !in start..length || start == finish) return null
        return TextRange(start, finish)
    }
    /** Paint only the displayed characters of a table cell endpoint. */
    fun tableVisibleRange(blocks: List<MarkdownDocumentBlock>, blockId: String,
                          cell: MarkdownTableCellPosition, length: Int): TextRange? {
        val end = focus ?: return null
        val anchorIndex = blocks.indexOfFirst { it.id == anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == end.blockId }
        val index = blocks.indexOfFirst { it.id == blockId }
        if (anchorIndex < 0 || focusIndex < 0 || index < 0) return null
        if (anchorIndex == focusIndex) {
            if (index != anchorIndex || anchor.tableCell != cell || end.tableCell != cell) return null
            val start = minOf(anchor.offset, end.offset)
            val finish = maxOf(anchor.offset, end.offset)
            return if (start in 0..length && finish in start..length && start != finish)
                TextRange(start, finish) else null
        }
        val first = if (anchorIndex < focusIndex) anchor else end
        val last = if (anchorIndex < focusIndex) end else anchor
        val start = when {
            index == minOf(anchorIndex, focusIndex) && first.tableCell == cell -> first.offset
            index == maxOf(anchorIndex, focusIndex) && last.tableCell == cell -> 0
            else -> return null
        }
        val finish = if (index == minOf(anchorIndex, focusIndex)) length else last.offset
        return if (start in 0..length && finish in start..length && start != finish)
            TextRange(start, finish) else null
    }

}
