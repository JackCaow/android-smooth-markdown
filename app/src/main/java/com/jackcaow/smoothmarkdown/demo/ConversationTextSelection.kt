package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.MarkdownSelectionTarget

/** Maps a long-press in rendered Markdown to that text composable's global selection range. */
internal fun paragraphSelectionRange(
    pressInWindow: Offset,
    targets: Collection<MarkdownSelectionTarget>,
    selectableTexts: List<AnnotatedString>,
): TextRange? {
    val target = targets.asSequence()
        .filter { it.text.isNotEmpty() && it.boundsInWindow.contains(pressInWindow) }
        .minByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
        ?: return null
    val visualMatches = targets.filter { it.text.text == target.text.text }
        .sortedWith(compareBy({ it.boundsInWindow.top }, { it.boundsInWindow.left }))
    val ordinal = visualMatches.indexOfFirst { it.key === target.key }
    if (ordinal < 0) return null
    val selectableIndex = selectableTexts.withIndex()
        .filter { it.value.text == target.text.text }
        .getOrNull(ordinal)?.index ?: return null
    val text = target.text.text
    val pressedOffset = target.offsetAtWindowPosition?.invoke(pressInWindow)?.coerceIn(0, text.length)
    val localStart = if (pressedOffset == null || pressedOffset == 0) 0 else
        text.lastIndexOf('\n', pressedOffset - 1) + 1
    val localEnd = if (pressedOffset == null) text.length else
        text.indexOf('\n', pressedOffset).let { if (it < 0) text.length else it }
    val globalStart = selectableTexts.take(selectableIndex).sumOf { it.length }
    return TextRange(globalStart + localStart, globalStart + localEnd)
}
