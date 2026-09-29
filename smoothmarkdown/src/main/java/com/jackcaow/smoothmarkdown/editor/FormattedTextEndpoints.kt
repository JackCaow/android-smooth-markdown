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
}
