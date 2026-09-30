package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val paragraph = parseMarkdown("[link](https://example.com) and `code`").firstChild!!
        val rendered = inlineRender(paragraph, enableHtml = false, styleSheet = sheet).text
        val linkIndex = rendered.text.indexOf("link")
        val codeIndex = rendered.text.indexOf("code")
        assertEquals(Color.Magenta, rendered.spanStyles.first { linkIndex in it.start until it.end && it.item.color == Color.Magenta }.item.color)
        assertEquals(Color.Cyan, rendered.spanStyles.first { codeIndex in it.start until it.end && it.item.background == Color.Cyan }.item.background)
    }

    @Test fun configurableInlineStylesReachMarkdownAndHtmlRanges() {
        val sheet = MarkdownStyleSheet(
            boldStyle = SpanStyle(color = Color.Red, fontSize = 21.sp),
            italicStyle = SpanStyle(color = Color.Green),
            strikethroughStyle = SpanStyle(color = Color.Blue),
            linkStyle = SpanStyle(color = Color.Magenta),
            inlineCodeStyle = SpanStyle(color = Color.Cyan, fontSize = 19.sp),
        )
        val markdown = parseMarkdown("**bold** *italic* ~~strike~~ [link](https://example.com) `code`").firstChild!!
        val rendered = inlineRender(markdown, enableHtml = false, styleSheet = sheet).text
        fun matching(word: String, color: Color) = rendered.spanStyles.first {
            val index = rendered.text.indexOf(word)
            index in it.start until it.end && it.item.color == color
        }.item
        assertEquals(FontWeight.Bold, matching("bold", Color.Red).fontWeight)
        assertEquals(21.sp, matching("bold", Color.Red).fontSize)
        assertEquals(FontStyle.Italic, matching("italic", Color.Green).fontStyle)
        assertEquals(TextDecoration.LineThrough, matching("strike", Color.Blue).textDecoration)
        assertEquals(TextDecoration.Underline, matching("link", Color.Magenta).textDecoration)
        assertEquals(19.sp, matching("code", Color.Cyan).fontSize)

        val html = parseMarkdown("<b>heavy</b> <code>literal</code> <a href=\"https://example.com\">safe</a>").firstChild!!
        val htmlRendered = inlineRender(html, enableHtml = true, styleSheet = sheet).text
        assertEquals(Color.Red, htmlRendered.spanStyles.first {
            htmlRendered.text.indexOf("heavy") in it.start until it.end && it.item.fontWeight == FontWeight.Bold
        }.item.color)
        assertEquals(Color.Cyan, htmlRendered.spanStyles.first {
            htmlRendered.text.indexOf("literal") in it.start until it.end && it.item.fontSize == 19.sp
        }.item.color)
        assertEquals(Color.Magenta, htmlRendered.spanStyles.first {
            htmlRendered.text.indexOf("safe") in it.start until it.end && it.item.textDecoration == TextDecoration.Underline
        }.item.color)
    }

    @Test fun invalidSpacingAndHeadingCountNormalizeAtConsumption() {
        assertEquals(0.dp, MarkdownStyleSheet(listIndent = (-1).dp).resolved().listIndent)
        assertEquals(0.dp, MarkdownStyleSheet(horizontalRuleThickness = (-1).dp).resolved().horizontalRuleThickness)
        assertEquals(6, MarkdownStyleSheet(headingStyles = listOf(TextStyle.Default)).resolved().headingStyles?.size)
        assertEquals(0.dp, MarkdownStyleSheet(blockquoteDecoration = MarkdownBlockquoteDecoration(borderWidth = (-1).dp)).resolved().blockquoteDecoration?.borderWidth)
        assertEquals(0.dp, MarkdownStyleSheet(codeBlockDecoration = MarkdownCodeBlockDecoration(borderWidth = (-1).dp)).resolved().codeBlockDecoration?.borderWidth)
        assertEquals(0.dp, MarkdownStyleSheet(codeBlockDecoration = MarkdownCodeBlockDecoration(cornerRadius = (-1).dp)).resolved().codeBlockDecoration?.cornerRadius)
    }

    @Test fun codeBlockDecorationOverridesLegacyFillAndUsesPerEdgePadding() {
        val sheet = MarkdownStyleSheet(
            codeBackground = Color.Red,
            codePadding = 8.dp,
            codeBlockDecoration = MarkdownCodeBlockDecoration(
                backgroundColor = Color.Cyan,
                borderColor = Color.Blue,
                borderWidth = 2.dp,
                cornerRadius = 9.dp,
            ),
            codeBlockPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 21.dp, top = 5.dp, end = 13.dp, bottom = 9.dp,
            ),
        )
        val resolved = resolveCodeBlockDecoration(sheet, Color.Green)
        assertEquals(Color.Cyan, resolved.backgroundColor)
        assertEquals(Color.Blue, resolved.borderColor)
        assertEquals(2.dp, resolved.borderWidth)
        assertEquals(9.dp, resolved.cornerRadius)
        assertEquals(21.dp, resolved.padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(13.dp, resolved.padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(5.dp, resolved.padding.calculateTopPadding())
        assertEquals(9.dp, resolved.padding.calculateBottomPadding())
    }

    @Test fun codeBlockLegacyAndPresetDecorationResolution() {
        val legacy = resolveCodeBlockDecoration(MarkdownStyleSheet(codeBackground = Color.Red, codePadding = 7.dp), Color.Green)
        assertEquals(Color.Red, legacy.backgroundColor)
        assertEquals(null, legacy.borderColor)
        assertEquals(7.dp, legacy.padding.calculateTopPadding())

        val light = resolveCodeBlockDecoration(MarkdownStyleSheet.light(), Color.Green)
        assertEquals(Color(0xFFF5F5F5), light.backgroundColor)
        assertEquals(Color(0xFFE0E0E0), light.borderColor)
        assertEquals(4.dp, light.cornerRadius)
        val githubOverride = resolveCodeBlockDecoration(MarkdownStyleSheet.github().copy(codeBackground = Color.Magenta), Color.Green)
        assertEquals(Color.Magenta, githubOverride.backgroundColor)
        assertEquals(0.dp, githubOverride.borderWidth)
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
