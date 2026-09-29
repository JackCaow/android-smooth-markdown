package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString

/** A rendered selectable text node and its current window bounds. */
data class MarkdownSelectionTarget(
    val key: Any,
    val boundsInWindow: Rect,
    val text: AnnotatedString,
    val offsetAtWindowPosition: ((Offset) -> Int)? = null,
)

internal data class MarkdownSelectionOptions(
    val selectable: Boolean = false,
    val outerRegion: Boolean = false,
    val nonTextSelectionAnchor: Boolean = false,
    val onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
)

internal val LocalMarkdownSelectionOptions = compositionLocalOf { MarkdownSelectionOptions() }

@Composable
internal fun SelectableMarkdownContent(content: @Composable () -> Unit) {
    val options = LocalMarkdownSelectionOptions.current
    if (!options.selectable || options.outerRegion) content() else SelectionContainer { content() }
}
