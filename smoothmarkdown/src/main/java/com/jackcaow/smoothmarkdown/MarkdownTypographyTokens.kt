package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.TextStyle

/** Six explicit heading roles: an incomplete heading collection cannot be constructed. */
data class MarkdownHeadingStyles(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val h5: TextStyle,
    val h6: TextStyle,
) {
    fun asList(): List<TextStyle> = listOf(h1, h2, h3, h4, h5, h6)

}

/** Canonical typography overrides. Null inherits the corresponding legacy style, then host theme. */
data class MarkdownTypographyTokens(
    val paragraph: TextStyle? = null,
    val headings: MarkdownHeadingStyles? = null,
    val code: TextStyle? = null,
    val tableHeader: TextStyle? = null,
    val tableCell: TextStyle? = null,
    val listBullet: TextStyle? = null,
    val keyboard: TextStyle? = null,
)
