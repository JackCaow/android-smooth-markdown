package com.jackcaow.smoothmarkdown

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult

/** Target a real glyph instead of the center of a full-width Text or a multiline code block. */
internal fun SemanticsNodeInteraction.glyphCenterInRoot(offset: Int = 0): Offset {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
        check(action(layouts)) { "Text did not expose its layout result" }
    }
    val layout = layouts.single()
    return fetchSemanticsNode().boundsInRoot.topLeft + layout.getBoundingBox(offset).center
}

internal fun SemanticsNodeInteraction.glyphCenterLocal(offset: Int = 0): Offset {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
        check(action(layouts)) { "Text did not expose its layout result" }
    }
    return layouts.single().getBoundingBox(offset).center
}
