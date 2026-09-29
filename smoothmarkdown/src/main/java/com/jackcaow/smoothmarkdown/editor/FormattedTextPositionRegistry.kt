package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val cursorAtVisibleOffset: ((Int) -> Offset?)? = null,
)

/** Coordinates stay live through scrolling; no gesture or selection state is owned here. */
internal class FormattedTextPositionRegistry {
    private val targets = mutableMapOf<String, FormattedTextPositionTarget>()
    var geometryRevision by mutableIntStateOf(0)
        private set

    fun register(target: FormattedTextPositionTarget) { targets[target.blockId] = target; geometryRevision++ }

    fun unregister(target: FormattedTextPositionTarget) {
        if (targets[target.blockId] === target) { targets.remove(target.blockId); geometryRevision++ }
    }

    fun geometryChanged() { geometryRevision++ }

    fun cursorWindowPoint(position: MarkdownFormattedTextPosition, currentSource: String): Offset? {
        val target = targets[position.blockId]?.takeIf { it.source == currentSource } ?: return null
        if (position.offset !in 0..target.visibleText.length ||
            safeUtf16Boundary(target.visibleText, position.offset) != position.offset) return null
        return target.cursorAtVisibleOffset?.invoke(position.offset)
    }

    fun positionAt(windowPoint: Offset, currentSource: String, maxDistancePx: Float = 0f): MarkdownFormattedTextPosition? {
        val target = targets.values.asSequence()
            .mapNotNull { target ->
                val bounds = target.boundsInWindow()?.takeIf { target.source == currentSource } ?: return@mapNotNull null
                val dx = maxOf(bounds.left - windowPoint.x, 0f, windowPoint.x - bounds.right)
                val dy = maxOf(bounds.top - windowPoint.y, 0f, windowPoint.y - bounds.bottom)
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared > maxDistancePx * maxDistancePx) null else target to distanceSquared
            }
            .minWithOrNull(compareBy<Pair<FormattedTextPositionTarget, Float>> { it.second }
                .thenBy { it.first.boundsInWindow()?.let { rect -> rect.width * rect.height } ?: Float.POSITIVE_INFINITY })
            ?.first
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
    private val registry: FormattedTextPositionRegistry,
    blockId: String,
    source: String,
    visibleText: String,
) {
    private var coordinates: LayoutCoordinates? = null
    private var layout: TextLayoutResult? = null

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates = it; registry.geometryChanged() }

    fun onTextLayout(result: TextLayoutResult) { layout = result; registry.geometryChanged() }

    val target = FormattedTextPositionTarget(blockId, source, visibleText,
        boundsInWindow = { coordinates?.takeIf { it.isAttached }?.boundsInWindow() },
        offsetAtWindowPoint = { windowPoint ->
            val placed = coordinates?.takeIf { it.isAttached }
            val measured = layout?.takeIf { it.layoutInput.text.text == visibleText }
            if (placed == null || measured == null) null
            else measured.getOffsetForPosition(placed.windowToLocal(windowPoint))
        },
        cursorAtVisibleOffset = { offset ->
            val placed = coordinates?.takeIf { it.isAttached }
            val measured = layout?.takeIf { it.layoutInput.text.text == visibleText }
            if (placed == null || measured == null) null
            else measured.getCursorRect(offset).let { placed.localToWindow(Offset(it.left, it.bottom)) }
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
        FormattedTextFieldTracker(registry, blockId, source, visibleText)
    }
    DisposableEffect(registry, tracker) {
        registry.register(tracker.target)
        onDispose { registry.unregister(tracker.target) }
    }
    return tracker
}
