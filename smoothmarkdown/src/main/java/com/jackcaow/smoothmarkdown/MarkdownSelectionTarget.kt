package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

/** A rendered selectable text node and its current window bounds. */
data class MarkdownSelectionTarget(
    val key: Any,
    val boundsInWindow: Rect,
    val text: AnnotatedString,
    val offsetAtWindowPosition: ((Offset) -> Int)? = null,
    val wordBoundaryAtWindowPosition: ((Offset) -> TextRange)? = null,
    val containsTextAtWindowPosition: ((Offset) -> Boolean)? = null,
)

internal fun textLayoutContainsWindowPoint(layout: TextLayoutResult?, point: Offset, bounds: Rect): Boolean {
    if (layout == null) return false
    val local = point - bounds.topLeft
    if (local.y < 0f || local.y >= layout.size.height) return false
    val line = layout.getLineForVerticalPosition(local.y)
    return local.x >= layout.getLineLeft(line) && local.x <= layout.getLineRight(line)
}

internal data class MarkdownSelectionOptions(
    val selectable: Boolean = false,
    val outerRegion: Boolean = false,
    val nonTextSelectionAnchor: Boolean = false,
    val onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
    val onTextDisposed: ((Any) -> Unit)? = null,
)

internal val LocalMarkdownSelectionOptions = compositionLocalOf { MarkdownSelectionOptions() }

@Composable
internal fun SelectableMarkdownContent(content: @Composable () -> Unit) {
    val options = LocalMarkdownSelectionOptions.current
    if (!options.selectable || options.outerRegion) content() else SelectionContainer { content() }
}
