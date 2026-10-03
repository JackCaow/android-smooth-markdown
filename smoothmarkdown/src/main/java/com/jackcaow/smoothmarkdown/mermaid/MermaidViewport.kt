package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Center fitting diagrams; preserve their natural dimensions and scrolling when they overflow. */
@Composable
internal fun MermaidViewport(layout: MermaidLayoutResult, modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val viewport = if (maxWidth.value.isFinite()) maxWidth else layout.width.dp
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Box(Modifier.width(maxOf(viewport, layout.width.dp)), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(layout.width.dp, layout.height.dp)) { content() }
            }
        }
    }
}
