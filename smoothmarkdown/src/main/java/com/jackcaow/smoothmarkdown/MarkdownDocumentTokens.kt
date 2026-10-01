package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color

/** Canonical document colors. Null restores the original StyleSheet fallback; resolution never mutates it. */
data class MarkdownDocumentTokens(
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val headingColor: Color? = null,
    val linkColor: Color? = null,
    val codeBackground: Color? = null,
    val codeTextColor: Color? = null,
    val inlineCodeBackground: Color? = null,
    val inlineCodeTextColor: Color? = null,
    val highlightColor: Color? = null,
    val footnoteColor: Color? = null,
    val quoteBarColor: Color? = null,
    val quoteBackground: Color? = null,
    val tableBorderColor: Color? = null,
    val ruleColor: Color? = null,
    val tableHeaderBackgroundColor: Color? = null,
)
