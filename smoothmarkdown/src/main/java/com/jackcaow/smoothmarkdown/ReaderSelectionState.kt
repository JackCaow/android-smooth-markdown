package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

/** Owned selection model; no access to Compose's private selection registrar is required. */
internal class ReaderSelectionState {
    private val targets = mutableStateMapOf<Any, MarkdownSelectionTarget>()
    var selectionRevision by mutableStateOf(0)
        private set
    var dragPosition by mutableStateOf<Offset?>(null)
    var dragStartHandle = false
    var dragOppositeOffset = 0
    var range by mutableStateOf(TextRange.Zero)
        private set
    val orderedTargets: List<MarkdownSelectionTarget>
        get() = orderedReaderSelectionTargets(targets.values)
    val selectedTexts: List<AnnotatedString>
        get() = selectedSegments.map { (target, start, end) -> target.text.subSequence(start, end) }
    val selectedSegments: List<ReaderSelectionSegment>
        get() {
            if (range.collapsed) return emptyList()
            val result = mutableListOf<ReaderSelectionSegment>()
            var base = 0
            for (target in orderedTargets) {
                val start = (range.min - base).coerceIn(0, target.text.length)
                val end = (range.max - base).coerceIn(0, target.text.length)
                if (start < end) result += ReaderSelectionSegment(target, start, end)
                base += target.text.length
            }
            return result
        }
    val selectionBounds: Rect
        get() = selectedSegments.map { it.target.boundsInWindow }.reduceOrNull { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        } ?: Rect.Zero
    private data class Endpoint(val key: Any, val local: Int)
    private fun endpoint(offset: Int, end: Boolean, ordered: List<MarkdownSelectionTarget>): Endpoint? {
        var base = 0
        for (target in ordered) {
            val limit = base + target.text.length
            if (offset < limit || (end && offset == limit)) return Endpoint(target.key, (offset - base).coerceIn(0, target.text.length))
            base = limit
        }
        return ordered.lastOrNull()?.let { Endpoint(it.key, it.text.length) }
    }
    private fun offset(endpoint: Endpoint?, ordered: List<MarkdownSelectionTarget>): Int? {
        endpoint ?: return null
        var base = 0
        for (target in ordered) {
            if (target.key == endpoint.key) return base + endpoint.local.coerceIn(0, target.text.length)
            base += target.text.length
        }
        return null
    }
    private inline fun updateTargets(update: () -> Unit) {
        val oldRange = range
        val oldTargets = orderedTargets
        val start = if (!oldRange.collapsed) endpoint(oldRange.start, oldRange.start > oldRange.end, oldTargets) else null
        val end = if (!oldRange.collapsed) endpoint(oldRange.end, oldRange.end > oldRange.start, oldTargets) else null
        val dragAnchor = if (dragPosition != null) endpoint(dragOppositeOffset, dragStartHandle, oldTargets) else null
        update()
        if (dragAnchor != null) offset(dragAnchor, orderedTargets)?.let { dragOppositeOffset = it }
        if (!oldRange.collapsed) {
            val newTargets = orderedTargets
            val newStart = offset(start, newTargets)
            val newEnd = offset(end, newTargets)
            range = if (newStart != null && newEnd != null) TextRange(newStart, newEnd) else TextRange.Zero
        }
    }
    fun track(target: MarkdownSelectionTarget) {
        val old = targets[target.key]
        // onGloballyPositioned may run every frame; do not create an invalidation loop.
        if (old == null || old.boundsInWindow != target.boundsInWindow || old.text != target.text ||
            old.sourceOrder != target.sourceOrder || !equivalentSelectionLayout(old.layoutResult, target.layoutResult)) updateTargets { targets[target.key] = target }
    }
    fun remove(key: Any) { updateTargets { targets.remove(key) } }
    fun selectAll() { select(TextRange(0, orderedTargets.sumOf { it.text.length })) }
    fun select(value: TextRange) {
        val length = orderedTargets.sumOf { it.text.length }
        range = TextRange(value.start.coerceIn(0, length), value.end.coerceIn(0, length))
        selectionRevision++
    }
    fun clear() {
        range = TextRange.Zero; dragPosition = null; selectionRevision++
    }
    fun offsetAt(point: Offset): Int? {
        val ordered = orderedTargets
        val target = ordered.firstOrNull { it.boundsInWindow.contains(point) }
            ?: ordered.minByOrNull {
                val b = it.boundsInWindow
                val x = point.x.coerceIn(b.left, b.right)
                val y = point.y.coerceIn(b.top, b.bottom)
                (Offset(x, y) - point).getDistanceSquared()
            } ?: return null
        val localOffset = target.offsetAtWindowPosition?.invoke(point)
            ?: target.layoutResult?.getOffsetForPosition(point - target.boundsInWindow.topLeft) ?: 0
        return ordered.takeWhile { it.key != target.key }.sumOf { it.text.length } + localOffset.coerceIn(0, target.text.length)
    }
    fun selectWordAt(point: Offset): Boolean {
        val target = orderedTargets.filter { it.boundsInWindow.contains(point) }
            .minByOrNull { it.boundsInWindow.width * it.boundsInWindow.height } ?: return false
        val local = target.wordBoundaryAtWindowPosition?.invoke(point)
            ?: target.layoutResult?.let { it.getWordBoundary(it.getOffsetForPosition(point - target.boundsInWindow.topLeft)) }
            ?: return false
        val base = orderedTargets.takeWhile { it.key != target.key }.sumOf { it.text.length }
        select(TextRange(base + local.start, base + local.end))
        return !range.collapsed
    }
    val selectsAllRegisteredText: Boolean get() = !range.collapsed && range.min == 0 &&
        range.max == orderedTargets.sumOf { it.text.length }
    fun handlePosition(start: Boolean): Offset? {
        val segment = if (start) selectedSegments.firstOrNull() else selectedSegments.lastOrNull()
        segment ?: return null
        val layout = segment.target.layoutResult ?: return null
        val offset = if (start) segment.start else segment.end
        val cursor = layout.getCursorRect(offset)
        return segment.target.boundsInWindow.topLeft + Offset(cursor.left, cursor.bottom)
    }
}

internal data class ReaderSelectionSegment(val target: MarkdownSelectionTarget, val start: Int, val end: Int)


/** Layout identity changes during harmless recomposition; only geometry/input changes invalidate. */
internal fun equivalentSelectionLayout(a: androidx.compose.ui.text.TextLayoutResult?, b: androidx.compose.ui.text.TextLayoutResult?): Boolean =
    a === b || (a != null && b != null && a.layoutInput == b.layoutInput && a.size == b.size &&
        a.lineCount == b.lineCount && a.firstBaseline == b.firstBaseline && a.lastBaseline == b.lastBaseline)
