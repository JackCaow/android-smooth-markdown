package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult

/** One visible formatted text field, tied to the exact source revision that produced it. */
internal class FormattedTextPositionTarget(
    val blockId: String,
    val source: String,
    val visibleText: String,
    val boundsInWindow: () -> Rect?,
    val offsetAtWindowPoint: (Offset) -> Int?,
)

/** Coordinates stay live through scrolling; no gesture or selection state is owned here. */
internal class FormattedTextPositionRegistry {
    private val targets = mutableMapOf<String, FormattedTextPositionTarget>()

    fun register(target: FormattedTextPositionTarget) { targets[target.blockId] = target }

    fun unregister(target: FormattedTextPositionTarget) {
        if (targets[target.blockId] === target) targets.remove(target.blockId)
    }

    fun positionAt(windowPoint: Offset, currentSource: String): MarkdownFormattedTextPosition? {
        val target = targets.values.asSequence()
            .filter { it.source == currentSource && it.boundsInWindow()?.contains(windowPoint) == true }
            .minByOrNull { it.boundsInWindow()?.let { rect -> rect.width * rect.height } ?: Float.POSITIVE_INFINITY }
            ?: return null
        val rawOffset = target.offsetAtWindowPoint(windowPoint) ?: return null
        return MarkdownFormattedTextPosition(target.blockId, safeUtf16Boundary(target.visibleText, rawOffset))
    }
}

/** A caret may never split a surrogate pair, even if a layout engine reports that offset. */
internal fun safeUtf16Boundary(text: String, rawOffset: Int): Int {
    val offset = rawOffset.coerceIn(0, text.length)
    return if (offset in 1 until text.length && Character.isHighSurrogate(text[offset - 1]) &&
        Character.isLowSurrogate(text[offset])) offset - 1 else offset
}

/** Adapts a Compose text field's measured layout to the document-level window registry. */
internal class FormattedTextFieldTracker(
    blockId: String,
    source: String,
    visibleText: String,
) {
    private var coordinates: LayoutCoordinates? = null
    private var layout: TextLayoutResult? = null

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates = it }

    fun onTextLayout(result: TextLayoutResult) { layout = result }

    val target = FormattedTextPositionTarget(blockId, source, visibleText,
        boundsInWindow = { coordinates?.takeIf { it.isAttached }?.boundsInWindow() },
        offsetAtWindowPoint = { windowPoint ->
            val placed = coordinates?.takeIf { it.isAttached }
            val measured = layout?.takeIf { it.layoutInput.text.text == visibleText }
            if (placed == null || measured == null) null
            else measured.getOffsetForPosition(placed.windowToLocal(windowPoint))
        },
    )
}

@Composable
internal fun rememberFormattedTextFieldTracker(
    registry: FormattedTextPositionRegistry,
    blockId: String,
    source: String,
    visibleText: String,
): FormattedTextFieldTracker {
    val tracker = remember(registry, blockId, source, visibleText) {
        FormattedTextFieldTracker(blockId, source, visibleText)
    }
    DisposableEffect(registry, tracker) {
        registry.register(tracker.target)
        onDispose { registry.unregister(tracker.target) }
    }
    return tracker
}
