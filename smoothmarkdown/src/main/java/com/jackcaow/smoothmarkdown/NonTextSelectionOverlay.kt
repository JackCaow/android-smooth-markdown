package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import java.security.SecureRandom

private val anchorRandom = SecureRandom()
private const val anchorAlphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
internal const val nonTextAnchorAnnotationTag = "smooth-markdown-selection-anchor"

internal fun newNonTextSelectionAnchor(): String = buildString(500) {
    append("smd")
    repeat(497) { append(anchorAlphabet[anchorRandom.nextInt(anchorAlphabet.length)]) }
}

internal class NonTextAnchorRegistry {
    private val anchors = mutableSetOf<String>()

    @Synchronized fun register(anchor: String) { anchors += anchor }
    @Synchronized fun unregister(anchor: String) { anchors -= anchor }
    @Synchronized fun snapshot(): Set<String> = anchors.toSet()
}

internal val LocalNonTextAnchorRegistry = compositionLocalOf<NonTextAnchorRegistry?> { null }

/** Matches Flutter's invisible selectable geometry over a nontext block. */
@Composable
internal fun SelectableNonTextBlock(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (!LocalMarkdownSelectionOptions.current.nonTextSelectionAnchor) {
        content()
        return
    }
    var heightPx by remember { mutableIntStateOf(0) }
    val rowHeight = with(LocalDensity.current) { 14.sp.toPx() }
    val rows = ceil(heightPx / rowHeight).toInt().coerceIn(1, 200)
    val registry = LocalNonTextAnchorRegistry.current
    val row = remember { newNonTextSelectionAnchor() }
    DisposableEffect(registry, row) {
        registry?.register(row)
        onDispose { registry?.unregister(row) }
    }
    val anchor = remember(rows, row) {
        buildAnnotatedString {
            append(List(rows) { row }.joinToString("\n"))
            addStringAnnotation(nonTextAnchorAnnotationTag, row, 0, length)
        }
    }
    Box(Modifier.onSizeChanged { heightPx = it.height }) {
        content()
        Text(
            anchor,
            modifier = Modifier.matchParentSize().testTag("nontext-selection-anchor").semantics {
                hideFromAccessibility()
            }.then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
            color = Color.Transparent,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            softWrap = false,
            maxLines = rows,
            overflow = TextOverflow.Clip,
        )
    }
}
