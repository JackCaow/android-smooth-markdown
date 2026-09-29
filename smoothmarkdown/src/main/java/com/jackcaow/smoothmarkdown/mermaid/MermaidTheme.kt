package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Colors used by Flutter's four MermaidStyle presets for fenced diagrams. */
internal data class MermaidThemeColors(
    val background: Color,
    val nodeFill: Color,
    val nodeStroke: Color,
    val text: Color,
    val edge: Color,
)

internal fun mermaidThemeColors(name: String): MermaidThemeColors = when (name.lowercase()) {
    "dark" -> MermaidThemeColors(
        Color(0xFF1E1E1E), Color(0xFF2D2D2D), Color(0xFF64B5F6),
        Color(0xFFE0E0E0), Color(0xFF9E9E9E),
    )
    "forest" -> MermaidThemeColors(
        Color(0xFFF1F8E9), Color(0xFFC8E6C9), Color(0xFF388E3C),
        Color(0xFF1B5E20), Color(0xFF4CAF50),
    )
    "neutral" -> MermaidThemeColors(
        Color(0xFFFAFAFA), Color(0xFFEEEEEE), Color(0xFF757575),
        Color(0xFF424242), Color(0xFF9E9E9E),
    )
    else -> MermaidThemeColors(
        Color.White, Color(0xFFE3F2FD), Color(0xFF1976D2),
        Color(0xFF212121), Color(0xFF616161),
    )
}

internal fun ColorScheme.withMermaidTheme(colors: MermaidThemeColors): ColorScheme = copy(
    background = colors.background,
    surface = colors.background,
    surfaceVariant = colors.nodeFill,
    onSurface = colors.text,
    onSurfaceVariant = colors.text,
    primary = colors.nodeStroke,
    outline = colors.edge,
)
