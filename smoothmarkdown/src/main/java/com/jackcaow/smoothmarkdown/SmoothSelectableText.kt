package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * Text for custom builders participating in the Reader's programmatic cross-block selection.
 * Arbitrary Compose Text remains locally selectable through NATIVE_TEXT; this helper additionally
 * supplies its source text and layout to SmoothSelectionController without private Compose APIs.
 */
@Composable
fun SmoothSelectableText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) =
    SmoothSelectableText(AnnotatedString(text), modifier, style)

@Composable
fun SmoothSelectableText(text: AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val options = LocalMarkdownSelectionOptions.current
    val key = remember { Any() }
    val sourceOrder = rememberReaderSelectionOrder()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    RetainReaderSelectionTarget(key)
    DisposableEffect(key, options.onTextDisposed) { onDispose { options.onTextDisposed?.invoke(key) } }
    val tracked = modifier.onGloballyPositioned { coordinates ->
        val bounds = Rect(coordinates.localToWindow(Offset.Zero), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        options.onTextPositioned?.invoke(MarkdownSelectionTarget(key, bounds, text,
            offsetAtWindowPosition = { layout?.getOffsetForPosition(it - bounds.topLeft) ?: 0 },
        ).apply {
            this.sourceOrder = sourceOrder
            layoutResult = layout
            wordBoundaryAtWindowPosition = { point -> layout?.let { it.getWordBoundary(it.getOffsetForPosition(point - bounds.topLeft)) }
                ?: androidx.compose.ui.text.TextRange.Zero }
            containsTextAtWindowPosition = { point -> textLayoutContainsWindowPoint(layout, point, bounds) }
        })
    }.then(readerSelectionHighlight(key))
    val content: @Composable () -> Unit = { Text(text, modifier = tracked, style = style, onTextLayout = { layout = it }) }
    if (LocalReaderSelectionState.current != null) DisableSelection { content() } else content()
}
