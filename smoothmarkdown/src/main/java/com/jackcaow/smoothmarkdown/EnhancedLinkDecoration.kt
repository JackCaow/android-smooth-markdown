package com.jackcaow.smoothmarkdown

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/** The decoration is metadata, never an inserted glyph in selectable or copied text. */
internal data class EnhancedLinkRange(val start: Int, val end: Int, val url: String) {
    val external: Boolean get() = url.startsWith("http://") || url.startsWith("https://")
}

internal fun enhancedLinkRanges(text: AnnotatedString): List<EnhancedLinkRange> =
    text.getStringAnnotations("enhanced-link", 0, text.length)
        .filter { it.start < it.end && isSafeLink(it.item) }
        .map { EnhancedLinkRange(it.start, it.end, it.item) }

/** Adds Flutter's external-link indicator and 200 ms pointer-hover underline to native text. */
@Composable
internal fun enhancedLinkDecoration(
    text: AnnotatedString,
    layout: State<TextLayoutResult?>,
    enabled: Boolean,
    color: Color,
): Modifier {
    if (!enabled) return Modifier
    val links = remember(text) { enhancedLinkRanges(text) }
    if (links.isEmpty()) return Modifier
    var hovered by remember(text) { mutableIntStateOf(-1) }
    val hoverFraction by animateFloatAsState(
        targetValue = if (hovered >= 0) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "enhanced-link-underline",
    )
    return Modifier.pointerInput(text, links) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Exit) {
                    hovered = -1
                    continue
                }
                if (event.type != PointerEventType.Enter && event.type != PointerEventType.Move) continue
                val pointer = event.changes.firstOrNull { it.type == PointerType.Mouse || it.type == PointerType.Stylus }
                    ?: continue
                val offset = layout.value?.getOffsetForPosition(pointer.position)
                hovered = if (offset == null) -1 else links.indexOfFirst { offset >= it.start && offset < it.end }
            }
        }
    }.drawWithContent {
        drawContent()
        val result = layout.value ?: return@drawWithContent
        links.forEachIndexed { index, link ->
            val firstLine = result.getLineForOffset(link.start)
            val lastLine = result.getLineForOffset(link.end - 1)
            val fraction = if (index == hovered) hoverFraction else 0f
            for (line in firstLine..lastLine) {
                val start = maxOf(link.start, result.getLineStart(line))
                val end = minOf(link.end, result.getLineEnd(line, visibleEnd = true))
                if (start >= end) continue
                val first = result.getBoundingBox(start)
                val last = result.getBoundingBox(end - 1)
                val y = maxOf(first.bottom, last.bottom) - 1.dp.toPx()
                drawLine(color.copy(alpha = 0.3f + 0.7f * fraction),
                    Offset(minOf(first.left, last.left), y), Offset(maxOf(first.right, last.right), y),
                    strokeWidth = (1f + fraction) * 1.dp.toPx())
            }
            if (!link.external) return@forEachIndexed
            val last = result.getBoundingBox(link.end - 1)
            val x = last.right + 2.dp.toPx()
            val next = if (link.end < text.length && result.getLineForOffset(link.end) == lastLine)
                result.getBoundingBox(link.end).left else size.width
            val iconSize = 12.dp.toPx()
            // A trailing icon needs real visual space. Never add a placeholder to selectable text.
            if (next - x < iconSize || x + iconSize > size.width) return@forEachIndexed
            val y = last.top + 1.dp.toPx()
            val unit = 1.2.dp.toPx()
            val stroke = 1.2.dp.toPx()
            drawLine(color, Offset(x, y + 4 * unit), Offset(x, y + 10 * unit), stroke)
            drawLine(color, Offset(x, y + 10 * unit), Offset(x + 7 * unit, y + 10 * unit), stroke)
            drawLine(color, Offset(x + 7 * unit, y + 10 * unit), Offset(x + 7 * unit, y + 5 * unit), stroke)
            drawLine(color, Offset(x + 3 * unit, y + 7 * unit), Offset(x + 9 * unit, y + unit), stroke)
            drawLine(color, Offset(x + 5 * unit, y + unit), Offset(x + 9 * unit, y + unit), stroke)
            drawLine(color, Offset(x + 9 * unit, y + unit), Offset(x + 9 * unit, y + 5 * unit), stroke)
        }
    }
}
