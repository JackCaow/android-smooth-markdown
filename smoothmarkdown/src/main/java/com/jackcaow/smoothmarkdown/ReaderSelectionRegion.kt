package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Draw owned handles/highlights over the original renderer, preserving all Markdown layout. */
@Composable
internal fun ReaderSelectionRegion(state: ReaderSelectionState, modifier: Modifier, onScroll: suspend (Float) -> Unit, content: @Composable () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.range) { if (!state.range.collapsed) focusRequester.requestFocus() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scrollCallback by rememberUpdatedState(onScroll)
    var dragInProgress by remember { mutableStateOf(false) }
    val edgePx = with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(state, coordinates, state.dragPosition != null) {
        while (state.dragPosition != null) {
            withFrameNanos { }
            val point = state.dragPosition ?: break
            // Holding a selected word near the edge is not a drag.
            if (!dragInProgress) continue
            val root = coordinates ?: continue
            val local = root.windowToLocal(point)
            val height = root.size.height.toFloat()
            val delta = when {
                local.y < edgePx -> -((edgePx - local.y) / edgePx).coerceIn(0f, 2f) * 12f
                local.y > height - edgePx -> ((local.y - height + edgePx) / edgePx).coerceIn(0f, 2f) * 12f
                else -> 0f
            }
            if (delta != 0f) {
                scrollCallback(delta)
                state.offsetAt(point)?.let { offset ->
                    state.select(if (state.dragStartHandle) TextRange(offset, state.dragOppositeOffset)
                        else TextRange(state.dragOppositeOffset, offset))
                }
            }
        }
    }
    val handleColor = androidx.compose.foundation.text.selection.LocalTextSelectionColors.current.handleColor
    Box(modifier.clipToBounds().focusRequester(focusRequester).focusable().onGloballyPositioned { coordinates = it }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val initial = coordinates?.localToWindow(down.position) ?: down.position
            // Leave arbitrary host Text to its own public native SelectionContainer. Only
            // registered layout targets are intercepted by this owned selection engine.
            val hits = state.orderedTargets.filter { it.boundsInWindow.contains(initial) }
            if (hits.isEmpty()) return@awaitEachGesture
            var latest = down
            var cancelled = false
            // Start the timeout on Initial, before an enclosing native SelectionContainer's
            // Main-pass detector. During the timeout, short taps and ordinary scrolling remain
            // unconsumed; once established, the owned selection gets the subsequent drag.
            withTimeoutOrNull<Unit>(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        cancelled = true
                        break
                    }
                    latest = change
                }
            }
            if (cancelled) return@awaitEachGesture
            val longPress = latest
            val point = coordinates?.localToWindow(longPress.position) ?: longPress.position
            if (!state.selectWordAt(point)) return@awaitEachGesture
            dragInProgress = false
            state.dragPosition = point
            state.dragStartHandle = false
            state.dragOppositeOffset = state.range.start
            longPress.consume()
            try {
                while (true) {
                    // Intercept an established text drag before child scrolling/click detectors.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    val moved = coordinates?.localToWindow(change.position) ?: change.position
                    state.dragPosition = moved
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        break
                    }
                    // Compose may deliver a stationary Move after the long-press timeout.
                    // Keep the initial word until the user actually starts dragging.
                    if (!dragInProgress && (change.position - longPress.position).getDistance() > viewConfiguration.touchSlop) {
                        dragInProgress = true
                    }
                    if (dragInProgress && change.position != change.previousPosition) {
                        state.offsetAt(moved)?.let { state.select(TextRange(state.dragOppositeOffset, it)) }
                    }
                    change.consume()
                    if (!change.pressed) break
                }
            } finally {
                dragInProgress = false
                state.dragPosition = null
            }
        }
    }.pointerInput(state) {
        // Observe short taps without consuming them: links, images and controls keep their
        // ordinary click behavior while the old text selection is dismissed.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val hadSelection = !state.range.collapsed
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
            if (hadSelection && up != null && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                state.clear()
            }
        }
    }) {
        CompositionLocalProvider(LocalReaderSelectionState provides state) { content() }
        if (!state.range.collapsed) {
            for (start in listOf(true, false)) {
                val windowPosition = state.handlePosition(start) ?: continue
                val root = coordinates ?: continue
                val visible = root.boundsInWindow()
                if (windowPosition.x < visible.left || windowPosition.x > visible.right ||
                    windowPosition.y < visible.top || windowPosition.y > visible.bottom) continue
                val point = root.windowToLocal(windowPosition)
                val sizePx = with(LocalDensity.current) { 24.dp.toPx() }
                val visibleTopLeft = root.windowToLocal(visible.topLeft)
                val visibleBottomRight = root.windowToLocal(visible.bottomRight)
                // Anchor coordinates are physical window coordinates. Avoid Box's RTL-aware
                // TopStart placement, and keep the complete touch target in the visible reader.
                val x = (point.x - sizePx / 2).coerceIn(visibleTopLeft.x,
                    maxOf(visibleTopLeft.x, visibleBottomRight.x - sizePx))
                val y = point.y.coerceIn(visibleTopLeft.y,
                    maxOf(visibleTopLeft.y, visibleBottomRight.y - sizePx))
                Canvas(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset {
                    IntOffset(x.roundToInt(), y.roundToInt())
                }.size(24.dp).testTag(if (start) "reader-selection-start-handle" else "reader-selection-end-handle")
                    .semantics { contentDescription = if (start) "Selection start" else "Selection end" }
                    .pointerInput(state, start) {
                        var dragPoint = windowPosition
                        var opposite = 0
                        try { detectDragGestures(
                            onDragStart = {
                                dragPoint = state.handlePosition(start) ?: windowPosition
                                opposite = if (start) state.range.max else state.range.min
                                dragInProgress = true
                                state.dragPosition = dragPoint
                                state.dragStartHandle = start
                                state.dragOppositeOffset = opposite
                            },
                            onDragEnd = { dragInProgress = false; state.dragPosition = null },
                            onDragCancel = { dragInProgress = false; state.dragPosition = null },
                            onDrag = { change, delta ->
                                dragPoint += delta
                                state.dragPosition = dragPoint
                                state.offsetAt(dragPoint)?.let { offset ->
                                    // Keep the opposite endpoint fixed while crossing it.
                                    state.select(if (start) TextRange(offset, state.dragOppositeOffset) else TextRange(state.dragOppositeOffset, offset))
                                }
                                change.consume()
                            },
                        ) } finally {
                            // A handle can leave the clipped viewport and be disposed while
                            // dragging. Cancellation must also stop the frame-based scroll loop.
                            dragInProgress = false
                            state.dragPosition = null
                        }
                    }) {
                    drawCircle(handleColor, radius = size.minDimension / 2)
                }
            }
        }
    }
}

/** Keep selected lazy items mounted until selection clears, matching native SelectionContainer. */
@Composable
internal fun RetainReaderSelectionTarget(key: Any) {
    val state = LocalReaderSelectionState.current ?: return
    val container = androidx.compose.ui.layout.LocalPinnableContainer.current
    val selected = state.selectedSegments.any { it.target.key == key }
    androidx.compose.runtime.DisposableEffect(container, selected) {
        val handle = if (selected) container?.pin() else null
        onDispose { handle?.release() }
    }
}

internal val LocalReaderSelectionState = androidx.compose.runtime.compositionLocalOf<ReaderSelectionState?> { null }


/** Draw selection beneath glyphs but above each block's own background. */
@Composable
internal fun readerSelectionHighlight(key: Any): Modifier {
    val state = LocalReaderSelectionState.current ?: return Modifier
    val color = androidx.compose.foundation.text.selection.LocalTextSelectionColors.current.backgroundColor
    return Modifier.drawBehind {
        for (segment in state.selectedSegments) {
            if (segment.target.key != key) continue
            val layout = segment.target.layoutResult ?: continue
            clipRect { drawPath(layout.getPathForRange(segment.start, segment.end), color) }
        }
    }
}
