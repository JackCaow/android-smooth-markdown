package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
    }
}
