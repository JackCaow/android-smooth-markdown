package com.jackcaow.smoothmarkdown.editor

/** Editor state captured after a Compose frame for host telemetry. */
data class MarkdownEditorPerformanceSnapshot(
    /** UTF-16 code units in the current Markdown source. */
    val sourceLength: Int,
    val blockCount: Int,
    /** No formatted segment cache exists in the native Android editor yet. */
    val formattedSegmentCount: Int?,
    val formattedSegmentCacheHit: Boolean?,
    val retainedFormattedSegmentKeyCount: Int?,
    val mode: MarkdownEditorMode,
    val isComposing: Boolean,
    val searchMatchCount: Int,
    val slashSuggestionsVisible: Boolean,
    /** The formatted wikilink popup does not expose visibility to the host yet. */
    val wikilinkSuggestionsVisible: Boolean?,
    val timestampMillis: Long,
)

/** Reuses a semantic block count while only mode, search, or selection changes. */
internal class MarkdownEditorPerformanceReporter {
    private var cachedSource: String? = null
    private var cachedBlockCount = 0

    fun capture(
        controller: MarkdownEditorController,
        searchQuery: String,
        searchOpen: Boolean,
        slashSuggestionsVisible: Boolean,
        timestampMillis: Long = System.currentTimeMillis(),
    ): MarkdownEditorPerformanceSnapshot {
        val source = controller.text
        if (cachedSource != source) {
            cachedSource = source
            cachedBlockCount = controller.semanticDocument().blocks.size
        }
        val value = controller.value
        val composition = value.composition
        val isComposing = composition != null && !composition.collapsed &&
            composition.min >= 0 && composition.max <= source.length
        return MarkdownEditorPerformanceSnapshot(
            sourceLength = source.length,
            blockCount = cachedBlockCount,
            formattedSegmentCount = null,
            formattedSegmentCacheHit = null,
            retainedFormattedSegmentKeyCount = null,
            mode = controller.mode,
            isComposing = isComposing,
            searchMatchCount = if (searchOpen) controller.findMatches(searchQuery).size else 0,
            slashSuggestionsVisible = slashSuggestionsVisible,
            wikilinkSuggestionsVisible = null,
            timestampMillis = timestampMillis,
        )
    }
}

/** Source-field focus transitions, distinct from formatted and search field focus. */
internal class MarkdownEditorSourceFocusTracker {
    private var focused = false

    fun setFocused(next: Boolean, callback: ((Boolean) -> Unit)?) {
        if (focused == next) return
        focused = next
        callback?.invoke(next)
    }
}
