package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.ast.CustomBlock
import com.jackcaow.smoothmarkdown.ast.CustomNode

internal class InlineMathNode(val latex: String) : CustomNode()
internal class BlockMathNode(var latex: String = "") : CustomBlock()

/** Measures the same owned TeX tree and font used by the system MathML renderer. */
@Composable
internal fun rememberMathRenderer(latex: String, displayMode: Boolean): NativeMathExtent {
    val sheet = LocalMarkdownStyleSheet.current
    val style = sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val font = style.fontSize.takeIf { it != androidx.compose.ui.unit.TextUnit.Unspecified }?.value ?: 16f
    val size = font * density.fontScale * density.density * if (displayMode) 1.2f else 1f
    return remember(latex, size, displayMode) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        }
        NativeTeXMetrics.preferredExtent(NativeTeXParser.parse(latex), size, displayMode) { text, textSize ->
            paint.textSize = textSize
            paint.measureText(text)
        }
    }
}

@Composable
internal fun NativeMath(latex: String, displayMode: Boolean, modifier: Modifier = Modifier) {
    SystemMath(latex, displayMode, modifier)
}

@Composable
internal fun BlockMath(node: BlockMathNode) {
    if (node.latex.isEmpty()) return
    SelectableNonTextBlock {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            NativeMath(node.latex, displayMode = true)
        }
    }
}
