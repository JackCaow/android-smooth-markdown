package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Component tokens used by both SmoothMarkdown and StreamMarkdown. Null colors/styles inherit
 * MarkdownStyleSheet or MaterialTheme. Copy a group to override only the desired properties. */
data class MarkdownDesignTokens(
    val heading: MarkdownHeadingTokens = MarkdownHeadingTokens(),
    val quote: MarkdownQuoteTokens = MarkdownQuoteTokens(),
    val code: MarkdownCodeTokens = MarkdownCodeTokens(),
    val link: MarkdownLinkTokens = MarkdownLinkTokens(),
    val details: MarkdownDetailsTokens = MarkdownDetailsTokens(),
    val keyboard: MarkdownKeyboardTokens = MarkdownKeyboardTokens(),
    val math: MarkdownMathTokens = MarkdownMathTokens(),
    val footnotePadding: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
    val imagePlaceholderMinSize: Dp = 48.dp,
    val mermaid: MarkdownMermaidTokens = MarkdownMermaidTokens(),
    val plugins: MarkdownPluginTokens = MarkdownPluginTokens(),
    val typography: MarkdownTypographyTokens = MarkdownTypographyTokens(),
    val document: MarkdownDocumentTokens = MarkdownDocumentTokens(),
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(heading = MarkdownHeadingTokens())



}

/** Decorative heading bar and underline; text remains controlled by headingStyles. */
data class MarkdownHeadingTokens(
    val accentColor: Color? = null,
    val decoratedThroughLevel: Int = 2,
    val padding: PaddingValues = PaddingValues(vertical = 8.dp),
    val barWidth: Dp = 4.dp,
    val barRadius: Dp = 2.dp,
    val barSpacing: Dp = 12.dp,
    val barEndAlpha: Float = .3f,
    val ruleThickness: Dp = 2.dp,
    val ruleStartAlpha: Float = .3f,
    val ruleEndAlpha: Float = 0f,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(accentColor = null)
}

/** Quote fill/ornament. Explicit token overrides take precedence over legacy quote decoration. */
data class MarkdownQuoteTokens(
    val gradientStartColor: Color? = null,
    val gradientEndColor: Color? = null,
    val backgroundStartAlpha: Float = .45f,
    val backgroundEndAlpha: Float = .8f,
    val borderAlpha: Float = .6f,
    val showIcon: Boolean = true,
    val iconColor: Color? = null,
    val iconAlpha: Float = .4f,
    val iconStyle: TextStyle = TextStyle(fontSize = 24.sp),
    val iconSpacing: Dp = 12.dp,
    /** Canonical quote decoration overrides legacy StyleSheet fields when non-null. */
    val backgroundColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Dp? = null,
    val padding: PaddingValues? = null,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(gradientStartColor = null)
}

/** Syntax colors may be overridden per theme without replacing the code renderer. */
class MarkdownSyntaxColors(val keyword: Color, val string: Color, val comment: Color, val number: Color) {
    // Keep the four-color constructor, components and copy bridges while copying every accent.
    var type: Color? = null
    var function: Color? = null
    var property: Color? = null
    var operator: Color? = null
    var punctuation: Color? = null
    var diffAdd: Color? = null
    var diffRemove: Color? = null
    operator fun component1(): Color = keyword
    operator fun component2(): Color = string
    operator fun component3(): Color = comment
    operator fun component4(): Color = number
    fun copy(keyword: Color = this.keyword, string: Color = this.string,
             comment: Color = this.comment, number: Color = this.number): MarkdownSyntaxColors =
        MarkdownSyntaxColors(keyword, string, comment, number).also {
            it.type = type; it.function = function; it.property = property; it.operator = operator
            it.punctuation = punctuation; it.diffAdd = diffAdd; it.diffRemove = diffRemove
        }
    override fun toString(): String {
        val base = "keyword=$keyword, string=$string, comment=$comment, number=$number"
        val accents = if (listOf(type, function, property, operator, punctuation, diffAdd, diffRemove).all { it == null }) ""
            else ", type=$type, function=$function, property=$property, operator=$operator, punctuation=$punctuation, diffAdd=$diffAdd, diffRemove=$diffRemove"
        return "MarkdownSyntaxColors($base$accents)"
    }
    override fun hashCode(): Int {
        val base = listOf(string, comment, number).fold(keyword.hashCode()) { result, value -> 31 * result + value.hashCode() }
        val accents = listOf(type, function, property, operator, punctuation, diffAdd, diffRemove)
        return if (accents.all { it == null }) base else accents.fold(base) { result, value -> 31 * result + (value?.hashCode() ?: 0) }
    }
    override fun equals(other: Any?): Boolean = other is MarkdownSyntaxColors &&
        keyword == other.keyword && string == other.string && comment == other.comment && number == other.number &&
        type == other.type && function == other.function && property == other.property && operator == other.operator &&
        punctuation == other.punctuation && diffAdd == other.diffAdd && diffRemove == other.diffRemove
    companion object {
        fun light() = MarkdownSyntaxColors(Color(0xFFCF222E), Color(0xFF0A3069), Color(0xFF6E7781), Color(0xFF0550AE))
        fun dark() = MarkdownSyntaxColors(Color(0xFFFF7B72), Color(0xFFA5D6FF), Color(0xFF8B949E), Color(0xFF79C0FF))
    }
}

/** Code controls and overflow indicator; code body uses codeStyle/codeBlockPadding. */
data class MarkdownCodeTokens(
    val headerPadding: PaddingValues = PaddingValues(start = 12.dp, end = 8.dp),
    val languageStyle: TextStyle? = null,
    val copyStyle: TextStyle? = null,
    val copyColor: Color? = null,
    val copiedColor: Color = Color(0xFF2DA44E),
    val copyLabel: String? = null,
    val copiedLabel: String? = null,
    val copyFeedbackMillis: Long = 2000,
    val syntaxColors: MarkdownSyntaxColors? = null,
    val showScrollbar: Boolean = true,
    val scrollbarPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    val scrollbarThickness: Dp = 3.dp,
    val scrollbarMinThumbWidth: Dp = 24.dp,
    val scrollbarTrackColor: Color? = null,
    val scrollbarThumbColor: Color? = null,
    val scrollbarTrackAlpha: Float = .12f,
    val scrollbarThumbAlpha: Float = .45f,
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Dp? = null,
    val cornerRadius: Dp? = null,
    val padding: PaddingValues? = null,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(languageStyle = null)
}

/** Pointer-hover underline and external-link glyph. */
data class MarkdownLinkTokens(
    val hoverDurationMillis: Int = 200,
    val underlineColor: Color? = null,
    val underlineAlpha: Float = .3f,
    val hoverUnderlineAlpha: Float = 1f,
    val underlineThickness: Dp = 1.dp,
    val hoverUnderlineThickness: Dp = 2.dp,
    val underlineOffset: Dp = 1.dp,
    val showExternalIcon: Boolean = true,
    val iconColor: Color? = null,
    val iconSize: Dp = 12.dp,
    val iconGap: Dp = 2.dp,
    val iconTopOffset: Dp = 1.dp,
    val iconStrokeWidth: Dp = 1.2.dp,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(hoverDurationMillis = 200)
}

data class MarkdownDetailsTokens(
    val cornerRadius: Dp = 6.dp,
    val outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    val borderWidth: Dp = 1.dp,
    val borderColor: Color? = null,
    val backgroundColor: Color? = null,
    val summaryPadding: PaddingValues = PaddingValues(12.dp),
    val bodyPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
    val iconSpacing: Dp = 8.dp,
    val iconStyle: TextStyle? = null,
    val iconColor: Color? = null,
    val dividerThickness: Dp = 1.dp,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(borderColor = null)
}

data class MarkdownKeyboardTokens(
    val backgroundColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Dp = 1.dp,
    val cornerRadius: Dp = 4.dp,
    val padding: PaddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
    val extraWidth: Dp = 2.dp,
    val extraHeight: Dp = 2.dp,
    val textStyle: TextStyle = TextStyle(fontSize = 13.sp),
    val backgroundAlpha: Float = .12f,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(backgroundColor = null)
}

data class MarkdownMathTokens(
    val textStyle: TextStyle? = null,
    val displayScale: Float = 1.2f,
    val blockPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    val fontFamily: String = "serif",
    val color: Color? = null,
) {
    /** Keep the public JVM no-argument constructor across Kotlin compiler versions. */
    constructor() : this(textStyle = null)
}
