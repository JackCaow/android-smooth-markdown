package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

/** Programmatic control of the reader's selectable text region. */
class SmoothSelectionController {
    private var region: SelectionState? = null
    private var anchors: NonTextAnchorRegistry? = null
    private val targets = mutableMapOf<Any, MarkdownSelectionTarget>()

    /** The currently selected visible text, omitting non-text selection anchors. */
    val selectedText: String
        get() = region?.let { visibleSelectedText(it.selectedTexts, anchors?.snapshot().orEmpty()).text }.orEmpty()

    /** Select all text currently registered with the Compose selection region. */
    fun selectAll() { region?.selectAll() }

    /** Select a range in the region's currently registered text. */
    fun select(range: TextRange) { region?.select(range) }

    /** Select the rendered word at a position in this Android window. */
    fun selectWordAt(windowPosition: Offset) { selectAt(windowPosition, ReaderSelectionGranularity.WORD) }

    /** Select the rendered paragraph at a position in this Android window. */
    fun selectParagraphAt(windowPosition: Offset) { selectAt(windowPosition, ReaderSelectionGranularity.PARAGRAPH) }

    /** Clear the current text selection. */
    fun clear() { region?.clear() }

    private fun selectAt(position: Offset, granularity: ReaderSelectionGranularity) {
        val state = region ?: return
        if (targets.values.none { it.boundsInWindow.contains(position) &&
                it.containsTextAtWindowPosition?.invoke(position) != false }) {
            state.clear()
            return
        }
        // Compose exposes registered text through selectedTexts after selectAll.
        // Narrow the selection synchronously, as SmoothSelectionRegion does in Flutter.
        state.selectAll()
        val range = readerSelectionRangeAt(position, targets.values, state.selectedTexts, granularity)
        if (range == null || range.collapsed) state.clear() else state.select(range)
    }

    internal fun track(target: MarkdownSelectionTarget) { targets[target.key] = target }

    internal fun removeTarget(key: Any) { targets.remove(key) }

    internal fun attach(state: SelectionState, anchorRegistry: NonTextAnchorRegistry) {
        region = state
        anchors = anchorRegistry
    }

    internal fun detach(state: SelectionState) {
        if (region === state) {
            region = null
            anchors = null
            targets.clear()
        }
    }
}

internal enum class ReaderSelectionGranularity { WORD, PARAGRAPH }

/** Match a visual target to Compose's selectable-text order, including repeated paragraphs. */
internal fun readerSelectionRangeAt(
    position: Offset,
    targets: Collection<MarkdownSelectionTarget>,
    selectableTexts: List<AnnotatedString>,
    granularity: ReaderSelectionGranularity,
): TextRange? {
    val target = targets.asSequence()
        .filter { it.text.isNotEmpty() && it.boundsInWindow.contains(position) &&
            it.containsTextAtWindowPosition?.invoke(position) != false }
        .minByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
        ?: return null
    val matches = targets.filter { it.text.text == target.text.text }
        .sortedWith(compareBy({ it.boundsInWindow.top }, { it.boundsInWindow.left }))
    val ordinal = matches.indexOfFirst { it.key === target.key }
    if (ordinal < 0) return null
    val index = selectableTexts.withIndex().filter { it.value.text == target.text.text }
        .getOrNull(ordinal)?.index ?: return null
    val text = target.text.text
    val offset = target.offsetAtWindowPosition?.invoke(position)?.coerceIn(0, text.length) ?: return null
    val local = when (granularity) {
        ReaderSelectionGranularity.WORD -> target.wordBoundaryAtWindowPosition?.invoke(position)
            ?: fallbackWordBoundary(text, offset)
        ReaderSelectionGranularity.PARAGRAPH -> {
            val start = if (offset == 0) 0 else text.lastIndexOf('\n', offset - 1) + 1
            val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            TextRange(start, end)
        }
    }
    val base = selectableTexts.take(index).sumOf { it.length }
    return TextRange(base + local.start, base + local.end)
}

private fun fallbackWordBoundary(text: String, offset: Int): TextRange {
    if (text.isEmpty()) return TextRange(0)
    val index = offset.coerceAtMost(text.lastIndex)
    if (!text[index].isLetterOrDigit() && text[index] != '_') return TextRange(index)
    var start = index
    var end = index + 1
    while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
    return TextRange(start, end)
}
