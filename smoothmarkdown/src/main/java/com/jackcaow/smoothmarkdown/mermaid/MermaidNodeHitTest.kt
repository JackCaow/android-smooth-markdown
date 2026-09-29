package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Coordinates are in the diagram's own dp space, matching [MermaidLayoutResult]. */
internal fun MermaidDiagram.nodeIdAt(layout: MermaidLayoutResult, x: Float, y: Float): String? {
    val boxes = if (kind == MermaidKind.ERDiagram) layout.er?.entities.orEmpty() else layout.nodes
    val ids = if (kind == MermaidKind.ERDiagram) er?.entities.orEmpty().map { it.id } else nodes.map { it.id }
    return ids.firstOrNull { id ->
        val rect = boxes[id] ?: return@firstOrNull false
        x >= rect.x && x < rect.x + rect.width && y >= rect.y && y < rect.y + rect.height
    }
}

internal fun Modifier.mermaidNodeTaps(
    diagram: MermaidDiagram,
    layout: MermaidLayoutResult,
    onNodeTap: ((String) -> Unit)?,
): Modifier = if (onNodeTap == null) this else pointerInput(diagram, layout, onNodeTap) {
    detectTapGestures { position ->
        diagram.nodeIdAt(layout, position.x / density, position.y / density)?.let(onNodeTap)
    }
}
