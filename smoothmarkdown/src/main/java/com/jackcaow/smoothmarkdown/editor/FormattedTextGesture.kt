package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.geometry.Offset

internal enum class FormattedTextHandle { ANCHOR, FOCUS }

/** Pure range transitions shared by touch drag and the explicit endpoint controls. */
internal class FormattedTextGesture(private val positions: FormattedTextPositionRegistry) {
    fun beginCrossFieldDrag(anchor: MarkdownFormattedTextPosition, windowPoint: Offset, source: String): FormattedTextEndpoints? {
        val focus = positions.positionAt(windowPoint, source) ?: return null
        if (focus.blockId == anchor.blockId) return null
        return FormattedTextEndpoints(source, anchor, focus)
    }

    fun move(selection: FormattedTextEndpoints, handle: FormattedTextHandle,
             windowPoint: Offset, currentSource: String, tolerancePx: Float = 0f): FormattedTextEndpoints? {
        if (selection.source != currentSource) return null
        val position = positions.positionAt(windowPoint, currentSource, tolerancePx) ?: return null
        return when (handle) {
            FormattedTextHandle.ANCHOR -> selection.copy(anchor = position)
            FormattedTextHandle.FOCUS -> selection.withFocus(position)
        }
    }

    fun hitHandle(selection: FormattedTextEndpoints, windowPoint: Offset,
                  currentSource: String, radiusPx: Float): FormattedTextHandle? {
        if (selection.source != currentSource || selection.focus == null || selection.anchor == selection.focus) return null
        return listOf(FormattedTextHandle.ANCHOR to selection.anchor,
            FormattedTextHandle.FOCUS to selection.focus)
            .mapNotNull { (handle, position) ->
                positions.cursorWindowPoint(position, currentSource)?.let { handle to (it - windowPoint).getDistance() }
            }
            .filter { it.second <= radiusPx }
            .minByOrNull { it.second }
            ?.first
    }
}
