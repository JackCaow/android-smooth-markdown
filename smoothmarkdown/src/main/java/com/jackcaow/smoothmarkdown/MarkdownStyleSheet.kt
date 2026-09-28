package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
) {
    init {
        require(borderWidth.value >= 0) { "Blockquote borderWidth cannot be negative" }
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
    val highlightColor: Color = Color.Yellow.copy(alpha = 0.4f),
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
) {
    init {
        require(headingStyles == null || headingStyles.size == 6) { "headingStyles must contain H1 through H6" }
        require(blockSpacing.value >= 0 && listSpacing.value >= 0 && contentPadding.value >= 0 && listIndent.value >= 0 &&
            codePadding.value >= 0 && tableCellPadding.value >= 0) { "Markdown spacing cannot be negative" }
        require(horizontalRuleThickness.value >= 0) { "horizontalRuleThickness cannot be negative" }
    }

    companion object {
        /** Existing MaterialTheme-backed appearance. */
        fun default(): MarkdownStyleSheet = MarkdownStyleSheet()

        fun light(): MarkdownStyleSheet = MarkdownStyleSheet(
            backgroundColor = Color.White,
            textColor = Color(0xFF212121), headingColor = Color.Black,
            linkColor = Color(0xFF1976D2), codeBackground = Color(0xFFF5F5F5), codeTextColor = Color(0xFF212121),
            inlineCodeBackground = Color(0xFFEEEEEE), inlineCodeTextColor = Color(0xFFD32F2F),
            quoteBarColor = Color(0xFFBDBDBD), quoteBackground = Color(0xFFFAFAFA),
            tableBorderColor = Color(0xFFD0D7DE),
            tableHeaderBackgroundColor = Color(0xFFEEEEEE),
            ruleColor = Color(0xFFD8DEE4), headingStyles = headingSizes(),
            paragraphStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            blockSpacing = 16.dp, listIndent = 24.dp,
        )

        fun dark(): MarkdownStyleSheet = MarkdownStyleSheet(
            backgroundColor = Color(0xFF121212),
            textColor = Color(0xFFB3B3B3), headingColor = Color.White,
            linkColor = Color(0xFF64B5F6), codeBackground = Color(0xFF212121),
            codeTextColor = Color(0xFFB3B3B3), inlineCodeBackground = Color(0xFF424242),
            inlineCodeTextColor = Color(0xFFEF9A9A), quoteBarColor = Color(0xFF757575),
            quoteBackground = Color(0xFF212121),
            tableBorderColor = Color(0xFF30363D), ruleColor = Color(0xFF30363D),
            tableHeaderBackgroundColor = Color(0xFF303030),
            headingStyles = headingSizes(), paragraphStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            blockSpacing = 16.dp, listIndent = 24.dp,
        )

        fun github(dark: Boolean = false): MarkdownStyleSheet = if (dark) dark().copy(
            backgroundColor = Color(0xFF0D1117),
            textColor = Color(0xFFE6EDF3), headingColor = Color(0xFFE6EDF3),
            linkColor = Color(0xFF58A6FF), codeBackground = Color(0xFF161B22),
            codeTextColor = Color(0xFFE6EDF3),
        ) else light().copy(
            backgroundColor = Color.White,
            textColor = Color(0xFF24292F), headingColor = Color(0xFF24292F),
            linkColor = Color(0xFF0969DA), codeBackground = Color(0xFFF6F8FA),
            codeTextColor = Color(0xFF24292F),
        )

        fun vscode(dark: Boolean = false): MarkdownStyleSheet = if (dark) dark().copy(
            backgroundColor = Color(0xFF1E1E1E),
            textColor = Color(0xFFCCCCCC), headingColor = Color(0xFFCCCCCC),
            linkColor = Color(0xFF4FC1FF), codeBackground = Color(0xFF1E1E1E),
            codeTextColor = Color(0xFFD4D4D4), quoteBarColor = Color(0xFF007ACC), tableBorderColor = Color(0xFF404040),
        ) else light().copy(
            backgroundColor = Color.White,
            textColor = Color(0xFF1E1E1E), headingColor = Color(0xFF1E1E1E),
            linkColor = Color(0xFF0066BF), codeBackground = Color(0xFFF5F5F5),
            codeTextColor = Color(0xFF1E1E1E), quoteBarColor = Color(0xFF007ACC), tableBorderColor = Color(0xFFE0E0E0),
        )

        private fun headingSizes(): List<TextStyle> = listOf(32, 28, 24, 20, 18, 16).map {
            TextStyle(fontSize = it.sp, lineHeight = (it * 1.3).sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal val LocalMarkdownStyleSheet = staticCompositionLocalOf { MarkdownStyleSheet.default() }
