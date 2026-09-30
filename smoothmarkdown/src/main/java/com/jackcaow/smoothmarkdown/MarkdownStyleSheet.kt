package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fill and left border for Markdown blockquotes. Set [borderWidth] to zero to hide the border. */
data class MarkdownBlockquoteDecoration(
    val backgroundColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Dp = 4.dp,
)

/** Fill, outline, and corner radius of a fenced or indented code block. */
data class MarkdownCodeBlockDecoration(
    val backgroundColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Dp = 0.dp,
    val cornerRadius: Dp = 6.dp,
)

/** One edge of a table border. A null edge in [MarkdownTableBorder] is not drawn. */
data class MarkdownTableBorderSide(
    val color: Color,
    val width: Dp = 1.dp,
)

/** Flutter TableBorder's four outside edges and two shared inside rules. */
data class MarkdownTableBorder(
    val top: MarkdownTableBorderSide? = null,
    val right: MarkdownTableBorderSide? = null,
    val bottom: MarkdownTableBorderSide? = null,
    val left: MarkdownTableBorderSide? = null,
    val horizontalInside: MarkdownTableBorderSide? = null,
    val verticalInside: MarkdownTableBorderSide? = null,
) {
    companion object {
        fun all(color: Color, width: Dp = 1.dp): MarkdownTableBorder {
            val side = MarkdownTableBorderSide(color, width)
            return MarkdownTableBorder(side, side, side, side, side, side)
        }
    }
}

/** Visual overrides for [SmoothMarkdown]. Null colors and text styles inherit MaterialTheme. */
data class MarkdownStyleSheet(
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val headingColor: Color? = null,
    val linkColor: Color = Color(0xFF0969DA),
    val codeBackground: Color? = null,
    val codeTextColor: Color? = null,
    val inlineCodeBackground: Color? = null,
    val inlineCodeTextColor: Color? = null,
    val highlightColor: Color = Color(0xFFFFF176),
    val footnoteColor: Color = Color(0xFF1976D2),
    val quoteBarColor: Color? = null,
    val quoteBackground: Color? = null,
    val tableBorderColor: Color? = null,
    val ruleColor: Color? = null,
    val headingStyles: List<TextStyle>? = null,
    val paragraphStyle: TextStyle? = null,
    val codeStyle: TextStyle? = null,
    val tableHeaderStyle: TextStyle? = null,
    val tableCellStyle: TextStyle? = null,
    val blockSpacing: Dp = 12.dp,
    val listSpacing: Dp = 8.dp,
    val contentPadding: Dp = 16.dp,
    val listIndent: Dp = 34.dp,
    val codePadding: Dp = 12.dp,
    val tableCellPadding: Dp = 8.dp,
    /** Text style for ordered and unordered list markers. Task markers remain checkbox glyphs. */
    val listBulletStyle: TextStyle? = null,
    /** Row fill behind table heading cells. */
    val tableHeaderBackgroundColor: Color? = null,
    /** Thickness for Markdown thematic breaks and HTML horizontal rules. */
    val horizontalRuleThickness: Dp = 1.dp,
    /** Overrides [quoteBackground]; its border color falls back to [quoteBarColor] when unspecified. */
    val blockquoteDecoration: MarkdownBlockquoteDecoration? = null,
    /** Insets between the blockquote border and its rendered content. */
    val blockquotePadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    /** Overrides [codeBackground] when [MarkdownCodeBlockDecoration.backgroundColor] is set. */
    val codeBlockDecoration: MarkdownCodeBlockDecoration? = null,
    /** Per-edge code insets; when absent [codePadding] remains the uniform inset. */
    val codeBlockPadding: PaddingValues? = null,
    /** Inline Markdown strong emphasis, merged over the default bold weight. */
    val boldStyle: SpanStyle? = null,
    /** Inline Markdown emphasis, merged over the default italic slant. */
    val italicStyle: SpanStyle? = null,
    /** Inline Markdown strikethrough, merged over the default strike decoration. */
    val strikethroughStyle: SpanStyle? = null,
    /** HTML `<u>` and `<ins>`, merged over the default underline decoration. */
    val underlineStyle: SpanStyle? = null,
    /** HTML `<mark>`, merged over [highlightColor]. */
    val highlightStyle: SpanStyle? = null,
    /** Inline Markdown and safe HTML links, merged over [linkColor] and underline. */
    val linkStyle: SpanStyle? = null,
    /** Inline code and HTML code/kbd, merged over legacy inline-code colors. */
    val inlineCodeStyle: SpanStyle? = null,
    /** HTML `<sub>` text style, merged over the default smaller, shifted text. */
    val subscriptStyle: SpanStyle? = null,
    /** HTML `<sup>` text style, merged over the default smaller, shifted text. */
    val superscriptStyle: SpanStyle? = null,
    /** HTML `<kbd>` key cap text style, over the default 13sp monospace text. */
    val kbdStyle: TextStyle? = null,
    /** Per-edge table border; when absent, [tableBorderColor] colors a 1dp full grid. */
    val tableBorder: MarkdownTableBorder? = null,
    /** Additional component decoration tokens; existing typography and decoration fields remain supported. */
    val designTokens: MarkdownDesignTokens = MarkdownDesignTokens(),
) {


    /** Canonical precedence: explicit design tokens > legacy decoration/style > legacy scalar > host theme.
     * Resolution is shared by every reader and streaming mode. Render options never override appearance. */
    fun resolved(): MarkdownStyleSheet {
        val tokens = designTokens.normalized()
        val document = tokens.document
        val typography = tokens.typography
        val quote = tokens.quote
        val code = tokens.code
        val oldQuote = blockquoteDecoration?.copy(borderWidth = blockquoteDecoration.borderWidth.safe())
        val oldCode = codeBlockDecoration?.copy(borderWidth = codeBlockDecoration.borderWidth.safe(), cornerRadius = codeBlockDecoration.cornerRadius.safe())
        return copy(
            designTokens = tokens,
            backgroundColor = document.backgroundColor ?: backgroundColor,
            textColor = document.textColor ?: textColor,
            headingColor = document.headingColor ?: headingColor,
            linkColor = document.linkColor ?: linkColor,
            codeBackground = document.codeBackground ?: codeBackground,
            inlineCodeBackground = document.inlineCodeBackground ?: inlineCodeBackground,
            inlineCodeTextColor = document.inlineCodeTextColor ?: inlineCodeTextColor,
            highlightColor = document.highlightColor ?: highlightColor,
            footnoteColor = document.footnoteColor ?: footnoteColor,
            quoteBarColor = document.quoteBarColor ?: quoteBarColor,
            quoteBackground = document.quoteBackground ?: quoteBackground,
            tableBorderColor = document.tableBorderColor ?: tableBorderColor,
            ruleColor = document.ruleColor ?: ruleColor,
            tableHeaderBackgroundColor = document.tableHeaderBackgroundColor ?: tableHeaderBackgroundColor,

            blockSpacing = blockSpacing.safe(), listSpacing = listSpacing.safe(), contentPadding = contentPadding.safe(),
            listIndent = listIndent.safe(), codePadding = codePadding.safe(), tableCellPadding = tableCellPadding.safe(),
            horizontalRuleThickness = horizontalRuleThickness.safe(), tableBorder = tableBorder?.normalized(document.tableBorderColor),
            paragraphStyle = (typography.paragraph ?: paragraphStyle?.safe())?.let { it.copy(color = document.textColor ?: it.color) },
            headingStyles = (typography.headings?.asList() ?: headingStyles?.let { styles -> List(6) { styles.getOrNull(it)?.safe() ?: TextStyle.Default } })?.map { it.copy(color = document.headingColor ?: it.color) },
            codeStyle = typography.code ?: codeStyle?.safe(),
            boldStyle = boldStyle?.safe(),
            italicStyle = italicStyle?.safe(),
            strikethroughStyle = strikethroughStyle?.safe(),
            underlineStyle = underlineStyle?.safe(),
            highlightStyle = highlightStyle?.safe()?.let { it.copy(background = document.highlightColor ?: it.background) },
            linkStyle = linkStyle?.safe()?.let { it.copy(color = document.linkColor ?: it.color) },
            inlineCodeStyle = inlineCodeStyle?.safe()?.let { it.copy(color = document.inlineCodeTextColor ?: it.color, background = document.inlineCodeBackground ?: it.background) },
            subscriptStyle = subscriptStyle?.safe(),
            superscriptStyle = superscriptStyle?.safe(),
            tableHeaderStyle = typography.tableHeader ?: tableHeaderStyle?.safe(),
            tableCellStyle = typography.tableCell ?: tableCellStyle?.safe(),
            listBulletStyle = typography.listBullet ?: listBulletStyle?.safe(),
            kbdStyle = typography.keyboard ?: kbdStyle?.safe(),
            blockquotePadding = quote.padding ?: blockquotePadding.safe(),
            blockquoteDecoration = if (quote.backgroundColor != null || document.quoteBackground != null || quote.borderColor != null || document.quoteBarColor != null || quote.borderWidth != null)
                MarkdownBlockquoteDecoration(
                    backgroundColor = quote.backgroundColor ?: document.quoteBackground ?: oldQuote?.backgroundColor,
                    borderColor = quote.borderColor ?: document.quoteBarColor ?: oldQuote?.borderColor,
                    borderWidth = quote.borderWidth ?: oldQuote?.borderWidth ?: 4.dp,
                ) else oldQuote,
            codeTextColor = code.textColor ?: document.codeTextColor ?: codeTextColor,
            codeBlockPadding = code.padding ?: codeBlockPadding?.safe(),
            codeBlockDecoration = if (code.backgroundColor != null || document.codeBackground != null || code.borderColor != null || code.borderWidth != null || code.cornerRadius != null)
                MarkdownCodeBlockDecoration(
                    backgroundColor = code.backgroundColor ?: document.codeBackground ?: oldCode?.backgroundColor,
                    borderColor = code.borderColor ?: oldCode?.borderColor,
                    borderWidth = code.borderWidth ?: oldCode?.borderWidth ?: 0.dp,
                    cornerRadius = code.cornerRadius ?: oldCode?.cornerRadius ?: 6.dp,
                ) else oldCode,
        )
    }

    companion object {
        /** Existing MaterialTheme-backed appearance. */
        fun default(): MarkdownStyleSheet = MarkdownStyleSheet()

        fun light(): MarkdownStyleSheet = MarkdownStyleSheet(
            backgroundColor = Color.White,
            textColor = Color(0xFF212121), headingColor = Color.Black,
            linkColor = Color(0xFF1976D2), codeBackground = Color(0xFFF5F5F5), codeTextColor = Color(0xFF212121),
            inlineCodeBackground = Color(0xFFEEEEEE), inlineCodeTextColor = Color(0xFFD32F2F),
            highlightStyle = SpanStyle(color = Color(0xDD000000)),
            quoteBarColor = Color(0xFFBDBDBD), quoteBackground = Color(0xFFFAFAFA),
            tableBorderColor = Color(0xFFD0D7DE),
            tableHeaderBackgroundColor = Color(0xFFEEEEEE),
            codeBlockDecoration = MarkdownCodeBlockDecoration(borderColor = Color(0xFFE0E0E0), borderWidth = 1.dp, cornerRadius = 4.dp),
            ruleColor = Color(0xFFD8DEE4), headingStyles = headingSizes(),
            paragraphStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            blockSpacing = 16.dp, listIndent = 24.dp,
        )

        fun dark(): MarkdownStyleSheet = MarkdownStyleSheet(
            backgroundColor = Color(0xFF121212),
            textColor = Color(0xFFB3B3B3), headingColor = Color.White,
            linkColor = Color(0xFF64B5F6), codeBackground = Color(0xFF212121),
            codeTextColor = Color(0xFFB3B3B3), inlineCodeBackground = Color(0xFF424242),
            inlineCodeTextColor = Color(0xFFEF9A9A), highlightColor = Color(0xFF4D4400),
            quoteBarColor = Color(0xFF757575),
            quoteBackground = Color(0xFF212121),
            tableBorderColor = Color(0xFF30363D), ruleColor = Color(0xFF30363D),
            tableHeaderBackgroundColor = Color(0xFF303030),
            codeBlockDecoration = MarkdownCodeBlockDecoration(borderColor = Color(0xFF616161), borderWidth = 1.dp, cornerRadius = 4.dp),
            headingStyles = headingSizes(), paragraphStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            blockSpacing = 16.dp, listIndent = 24.dp,
        )

        fun github(dark: Boolean = false): MarkdownStyleSheet = if (dark) dark().copy(
            backgroundColor = Color(0xFF0D1117),
            textColor = Color(0xFFE6EDF3), headingColor = Color(0xFFE6EDF3),
            linkColor = Color(0xFF58A6FF), codeBackground = Color(0xFF161B22),
            codeTextColor = Color(0xFFE6EDF3),
            codeBlockDecoration = MarkdownCodeBlockDecoration(cornerRadius = 6.dp),
        ) else light().copy(
            backgroundColor = Color.White,
            textColor = Color(0xFF24292F), headingColor = Color(0xFF24292F),
            linkColor = Color(0xFF0969DA), codeBackground = Color(0xFFF6F8FA),
            codeTextColor = Color(0xFF24292F),
            codeBlockDecoration = MarkdownCodeBlockDecoration(cornerRadius = 6.dp),
        )

        fun vscode(dark: Boolean = false): MarkdownStyleSheet = if (dark) dark().copy(
            backgroundColor = Color(0xFF1E1E1E),
            textColor = Color(0xFFCCCCCC), headingColor = Color(0xFFCCCCCC),
            linkColor = Color(0xFF4FC1FF), codeBackground = Color(0xFF1E1E1E),
            codeTextColor = Color(0xFFD4D4D4), quoteBarColor = Color(0xFF007ACC), tableBorderColor = Color(0xFF404040),
            codeBlockDecoration = MarkdownCodeBlockDecoration(borderColor = Color(0xFF404040), borderWidth = 1.dp, cornerRadius = 4.dp),
        ) else light().copy(
            backgroundColor = Color.White,
            textColor = Color(0xFF1E1E1E), headingColor = Color(0xFF1E1E1E),
            linkColor = Color(0xFF0066BF), codeBackground = Color(0xFFF5F5F5),
            codeTextColor = Color(0xFF1E1E1E), quoteBarColor = Color(0xFF007ACC), tableBorderColor = Color(0xFFE0E0E0),
            codeBlockDecoration = MarkdownCodeBlockDecoration(borderColor = Color(0xFFE0E0E0), borderWidth = 1.dp, cornerRadius = 4.dp),
        )

        private fun headingSizes(): List<TextStyle> = listOf(32, 28, 24, 20, 18, 16).map {
            TextStyle(fontSize = it.sp, lineHeight = (it * 1.3).sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal val LocalMarkdownStyleSheet = staticCompositionLocalOf { MarkdownStyleSheet.default() }
