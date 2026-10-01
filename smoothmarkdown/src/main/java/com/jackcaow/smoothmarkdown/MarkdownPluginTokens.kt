package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual tokens for opt-in built-in plugins. Inline styles replace the built-in mention/hashtag style. */
data class MarkdownPluginTokens(
    val mentionStyle: SpanStyle = SpanStyle(color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold),
    val hashtagStyle: SpanStyle = SpanStyle(color = Color(0xFF1976D2)),
    val admonition: MarkdownAdmonitionTokens = MarkdownAdmonitionTokens(),
    val thinking: MarkdownPluginPanelTokens = MarkdownPluginPanelTokens(),
    val artifact: MarkdownPluginPanelTokens = MarkdownPluginPanelTokens(),
    val toolCall: MarkdownPluginPanelTokens = MarkdownPluginPanelTokens(),
)

/**
 * Tokens shared by thinking, artifact, and tool call panels. Null colors and styles inherit
 * MaterialTheme (monospace content is retained for artifacts and tool input).
 * [outerPadding] is outside the panel; header, content, and metadata padding are inside it.
 * [backgroundColor] fills the panel and [headerBackgroundColor] fills its heading.
 * [borderWidth] and [dividerThickness] can be zero to hide the corresponding rules.
 * [titleStyle], [contentStyle], [metadataStyle], [statusStyle], and [iconStyle] override the
 * complete text style of their respective parts. Color overrides are applied separately.
 */
data class MarkdownPluginPanelTokens(
    val outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    val headerPadding: PaddingValues = PaddingValues(12.dp),
    val contentPadding: PaddingValues = PaddingValues(12.dp),
    val metadataPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    val cornerRadius: Dp = 6.dp,
    val borderWidth: Dp = 1.dp,
    val borderColor: Color? = null,
    val backgroundColor: Color? = null,
    val headerBackgroundColor: Color? = null,
    val titleColor: Color? = null,
    val contentColor: Color? = null,
    val metadataColor: Color? = null,
    val accentColor: Color? = null,
    val titleStyle: TextStyle? = null,
    val contentStyle: TextStyle? = null,
    val metadataStyle: TextStyle? = null,
    val statusStyle: TextStyle? = null,
    val iconStyle: TextStyle? = null,
    val dividerThickness: Dp = 1.dp,
    val iconSpacing: Dp = 8.dp,
)

/**
 * Admonition tokens. [accentColors] maps each semantic type to its stripe/title color;
 * unlisted types inherit MaterialTheme.primary. Explicit background/title/border overrides
 * take precedence over the accent. [backgroundAlpha] sets the default accent wash opacity.
 * [accentWidth], [accentHeight], and [contentSpacing] control the stripe and its gap;
 * zero widths hide the stripe or border. [titleStyle] inherits bold Material text when null.
 */
data class MarkdownAdmonitionTokens(
    val accentColors: Map<AdmonitionType, Color> = mapOf(
        AdmonitionType.NOTE to Color(0xFF1976D2),
        AdmonitionType.TIP to Color(0xFF2E7D32),
        AdmonitionType.WARNING to Color(0xFFED6C02),
        AdmonitionType.DANGER to Color(0xFFD32F2F),
        AdmonitionType.IMPORTANT to Color(0xFF7B1FA2),
    ),
    val outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    val contentPadding: PaddingValues = PaddingValues(12.dp),
    val contentSpacing: Dp = 10.dp,
    val cornerRadius: Dp = 0.dp,
    val borderWidth: Dp = 0.dp,
    val borderColor: Color? = null,
    val backgroundColor: Color? = null,
    val backgroundAlpha: Float = 0.09f,
    val accentWidth: Dp = 4.dp,
    val accentHeight: Dp = 64.dp,
    val titleColor: Color? = null,
    val titleStyle: TextStyle? = null,
)
