package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class MarkdownDesignTokensTest {
    @Test fun customSyntaxPaletteReachesActualHighlightedSpans() {
        val palette = MarkdownSyntaxColors(Color.Magenta, Color.Green, Color.Gray, Color.Yellow)
        val text = highlightedCode("val answer = 42", "kotlin", false, palette)
        assertEquals("val answer = 42", text.text)
        assertTrue(text.spanStyles.any { it.start == 0 && it.end == 3 && it.item.color == Color.Magenta })
        assertTrue(text.spanStyles.any { it.start == 13 && it.end == 15 && it.item.color == Color.Yellow })
    }
    @Test fun invalidGeometryAndOpacityNormalizeWithoutCrashing() {
        val normalized = MarkdownDesignTokens(
            heading = MarkdownHeadingTokens(barWidth = (-1).dp, decoratedThroughLevel = 20),
            code = MarkdownCodeTokens(scrollbarThickness = Float.NaN.dp),
            quote = MarkdownQuoteTokens(iconAlpha = 2f),
            math = MarkdownMathTokens(displayScale = 0f),
            link = MarkdownLinkTokens(hoverDurationMillis = -1),
        ).normalized()
        assertEquals(0.dp, normalized.heading.barWidth)
        assertEquals(6, normalized.heading.decoratedThroughLevel)
        assertEquals(0.dp, normalized.code.scrollbarThickness)
        assertEquals(1f, normalized.quote.iconAlpha)
        assertEquals(1.2f, normalized.math.displayScale)
        assertEquals(0, normalized.link.hoverDurationMillis)
    }
    @Test fun nestedCopiesPreserveUnmodifiedStylesAndDocumentDecoration() {
        val sheet = MarkdownStyleSheet.github()
        val custom = sheet.copy(designTokens = sheet.designTokens.copy(code = sheet.designTokens.code.copy(copyLabel = "复制")))
        assertEquals(sheet.headingStyles, custom.headingStyles)
        assertEquals(sheet.codeBlockDecoration, custom.codeBlockDecoration)
        assertEquals(sheet.designTokens.quote, custom.designTokens.quote)
        assertEquals("复制", custom.designTokens.code.copyLabel)
    }
    @Test fun mathFontCustomizationIsContainedInCss() {
        val html = NativeTeXMathML.html("x^2", display = true, fontFamily = "monospace", fontWeight = 700, italic = true)
        assertTrue(html.contains("font-family:monospace;"))
        assertTrue(html.contains("font-weight:700;font-style:italic"))
        val escaped = NativeTeXMathML.cssFontFamily("x\";</style><script>")
        assertFalse(escaped.contains("</style>"))
        assertFalse(escaped.contains("<script>"))
    }
}
