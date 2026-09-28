package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarkdownStyleSheetTest {
    @Test fun presetsAreIndependentAndHaveExpectedPalette() {
        val light = MarkdownStyleSheet.light()
        val dark = MarkdownStyleSheet.dark()
        val github = MarkdownStyleSheet.github()
        val githubDark = MarkdownStyleSheet.github(dark = true)
        val vscode = MarkdownStyleSheet.vscode()
        val vscodeDark = MarkdownStyleSheet.vscode(dark = true)

        assertEquals(Color(0xFF0969DA), github.linkColor)
        assertEquals(Color(0xFF58A6FF), githubDark.linkColor)
        assertEquals(Color(0xFF0066BF), vscode.linkColor)
        assertEquals(Color(0xFF4FC1FF), vscodeDark.linkColor)
        assertNotEquals(light.textColor, dark.textColor)
        assertNotEquals(light.codeBackground, github.codeBackground)
        assertEquals(6, github.headingStyles?.size)
        assertEquals(Color(0xFFEEEEEE), light.tableHeaderBackgroundColor)
        assertEquals(Color(0xFF303030), dark.tableHeaderBackgroundColor)
        assertEquals(1.dp, light.horizontalRuleThickness)
    }

    @Test fun customLinkAndInlineCodeStylesReachRenderedRanges() {
        val sheet = MarkdownStyleSheet(
            linkColor = Color.Magenta,
            inlineCodeBackground = Color.Cyan,
            inlineCodeTextColor = Color.Black,
        )
        val paragraph = parseMarkdown("[link](https://example.com) and `code`").firstChild
        val rendered = inlineRender(paragraph, enableHtml = false, styleSheet = sheet).text
        val linkIndex = rendered.text.indexOf("link")
        val codeIndex = rendered.text.indexOf("code")
        assertEquals(Color.Magenta, rendered.spanStyles.first { linkIndex in it.start until it.end && it.item.color == Color.Magenta }.item.color)
        assertEquals(Color.Cyan, rendered.spanStyles.first { codeIndex in it.start until it.end && it.item.background == Color.Cyan }.item.background)
    }

    @Test fun invalidSpacingAndHeadingCountAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MarkdownStyleSheet(listIndent = (-1).dp) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownStyleSheet(horizontalRuleThickness = (-1).dp) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownStyleSheet(headingStyles = listOf(TextStyle.Default)) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownBlockquoteDecoration(borderWidth = (-1).dp) }
    }

    @Test fun blockquoteDecorationOverridesLegacyColorsAndKeepsConfigurableInsets() {
        val sheet = MarkdownStyleSheet(
            quoteBarColor = Color.Red,
            quoteBackground = Color.Yellow,
            blockquoteDecoration = MarkdownBlockquoteDecoration(
                backgroundColor = Color.Cyan,
                borderColor = Color.Blue,
                borderWidth = 7.dp,
            ),
            blockquotePadding = androidx.compose.foundation.layout.PaddingValues(
                start = 21.dp, top = 5.dp, end = 13.dp, bottom = 9.dp,
            ),
        )
        val resolved = resolveBlockquoteDecoration(sheet, Color.Green)
        assertEquals(Color.Cyan, resolved.backgroundColor)
        assertEquals(Color.Blue, resolved.borderColor)
        assertEquals(7.dp, resolved.borderWidth)
        assertEquals(21.dp, sheet.blockquotePadding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(13.dp, sheet.blockquotePadding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(5.dp, sheet.blockquotePadding.calculateTopPadding())
        assertEquals(9.dp, sheet.blockquotePadding.calculateBottomPadding())
    }

    @Test fun blockquoteLegacyAliasesAndThemeFallbackRemainAvailable() {
        val legacy = resolveBlockquoteDecoration(
            MarkdownStyleSheet(quoteBarColor = Color.Red, quoteBackground = Color.Yellow),
            Color.Green,
        )
        assertEquals(Color.Red, legacy.borderColor)
        assertEquals(Color.Yellow, legacy.backgroundColor)
        assertEquals(4.dp, legacy.borderWidth)

        val default = resolveBlockquoteDecoration(MarkdownStyleSheet.default(), Color.Green)
        assertEquals(Color.Green, default.borderColor)
        assertEquals(null, default.backgroundColor)
        assertEquals(16.dp, MarkdownStyleSheet.default().blockquotePadding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, MarkdownStyleSheet.default().blockquotePadding.calculateTopPadding())
        assertEquals(Color(0xFFFAFAFA), MarkdownStyleSheet.light().quoteBackground)
        assertEquals(Color(0xFF212121), MarkdownStyleSheet.dark().quoteBackground)
    }
}
