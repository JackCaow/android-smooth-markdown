package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.mermaid.MermaidThemeColors

/**
 * Host application styling for Mermaid fences and standalone MermaidDiagramView.
 * [colors] takes precedence over a fence's theme preset; null preserves the preset or MaterialTheme.
 * [typography] replaces diagram text roles without exposing a Material3 type.
 * [outerPadding] and [maxHeight] control fences; standalone views retain their caller's modifier.
 * Diagram source style/classDef directives still override individual node colors.
 * Chart series/status palettes and graph layout geometry are currently diagram-specific.
 */
data class MarkdownMermaidTokens(
    val colors: MermaidThemeColors? = null,
    val typography: MarkdownMermaidTypography? = null,
    val outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    val maxHeight: Dp = 420.dp,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(colors = null)
}

/** Platform text roles; unspecified roles inherit the host theme. */
data class MarkdownMermaidTypography(
    val bodySmall: TextStyle? = null,
    val bodyMedium: TextStyle? = null,
    val bodyLarge: TextStyle? = null,
    val labelSmall: TextStyle? = null,
    val labelMedium: TextStyle? = null,
    val labelLarge: TextStyle? = null,
    val titleSmall: TextStyle? = null,
    val titleMedium: TextStyle? = null,
    val titleLarge: TextStyle? = null,
    val headlineSmall: TextStyle? = null,
    val headlineMedium: TextStyle? = null,
    val headlineLarge: TextStyle? = null,
    val displaySmall: TextStyle? = null,
    val displayMedium: TextStyle? = null,
    val displayLarge: TextStyle? = null,
)
