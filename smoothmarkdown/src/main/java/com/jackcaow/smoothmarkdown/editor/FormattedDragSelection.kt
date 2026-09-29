package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Flutter's formatted drag selects complete sibling text blocks, list items, or table cells. */
internal sealed interface FormattedDragTarget {
    data class Block(val id: String) : FormattedDragTarget
    data class ListItem(val blockId: String, val path: List<Int>) : FormattedDragTarget
    data class TableCell(val blockId: String, val row: Int, val column: Int) : FormattedDragTarget
}

/** Layout bounds are kept outside Compose state; drag callbacks only update selection state. */
internal class FormattedDragSelection(private val controller: MarkdownEditorController) {
    private val bounds = mutableMapOf<FormattedDragTarget, Rect>()
    private var active: FormattedDragTarget? = null
    private var last: FormattedDragTarget? = null

    fun register(target: FormattedDragTarget, rect: Rect) { bounds[target] = rect }
    fun unregister(target: FormattedDragTarget) { bounds.remove(target) }

    fun begin(target: FormattedDragTarget): Boolean {
        val started = when (target) {
            is FormattedDragTarget.Block -> controller.beginFormattedBlockDrag(target.id)
            is FormattedDragTarget.ListItem -> controller.beginFormattedListItemDrag(target.blockId, target.path)
            is FormattedDragTarget.TableCell -> controller.beginFormattedTableCellDrag(target.blockId, target.row, target.column)
        }
        active = target.takeIf { started }
        last = active
        return started
    }

    fun update(windowPoint: Offset): Boolean {
        val anchor = active ?: return false
        val target = bounds.entries
            .asSequence()
            .filter { (candidate, rect) -> compatible(anchor, candidate) && rect.contains(windowPoint) }
            .minByOrNull { (_, rect) -> rect.width * rect.height }
            ?.key ?: return false
        if (target == last) return true
        val extended = when (target) {
            is FormattedDragTarget.Block -> controller.extendFormattedBlockDrag(target.id)
            is FormattedDragTarget.ListItem -> controller.extendFormattedListItemDrag(target.blockId, target.path)
            is FormattedDragTarget.TableCell -> controller.extendFormattedTableCellDrag(target.blockId, target.row, target.column)
        }
        if (extended) last = target
        return extended
    }

    fun end() { active = null; last = null }

    private fun compatible(anchor: FormattedDragTarget, candidate: FormattedDragTarget): Boolean = when (anchor) {
        is FormattedDragTarget.Block -> candidate is FormattedDragTarget.Block
        is FormattedDragTarget.ListItem -> candidate is FormattedDragTarget.ListItem &&
            candidate.blockId == anchor.blockId && candidate.path.dropLast(1) == anchor.path.dropLast(1)
        is FormattedDragTarget.TableCell -> candidate is FormattedDragTarget.TableCell && candidate.blockId == anchor.blockId
    }
}

/** Attach to the rendered content, so long press and drag work without selection buttons. */
@Composable
internal fun Modifier.formattedDragSelectionTarget(
    registry: FormattedDragSelection,
    target: FormattedDragTarget,
): Modifier {
    val coordinates = remember(registry, target) { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(registry, target) {
        onDispose { registry.unregister(target) }
    }
    return this.onGloballyPositioned {
        coordinates[0] = it
        registry.register(target, it.boundsInWindow())
    }.pointerInput(registry, target) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                var claimed = false
                try {
                    // Let BasicTextField own selection inside its own bounds. Observe movement
                    // before child handlers consume it, and claim only a cross-target drag.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                        if (!change.pressed) break
                        val layout = coordinates[0] ?: continue
                        val windowPoint = layout.localToWindow(change.position)
                        if (!claimed && !layout.boundsInWindow().contains(windowPoint)) {
                            claimed = registry.begin(target)
                        }
                        if (claimed) {
                            registry.update(windowPoint)
                            change.consume()
                        }
                    }
                } finally {
                    if (claimed) registry.end()
                }
            }
        }
    }
}
