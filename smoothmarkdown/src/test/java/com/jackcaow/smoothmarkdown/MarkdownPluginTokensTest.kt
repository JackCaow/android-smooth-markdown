package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarkdownPluginTokensTest {
    @Test fun defaultInlineStylesRemainCompatible() {
        val tokens = MarkdownPluginTokens()
        assertEquals(Color(0xFF1976D2), tokens.mentionStyle.color)
        assertEquals(FontWeight.SemiBold, tokens.mentionStyle.fontWeight)
        assertEquals(tokens.mentionStyle, MentionPlugin().render(MentionNode("alice"))!!.style)
        assertEquals(tokens.hashtagStyle, HashtagPlugin().render(HashtagNode("android"))!!.style)
    }

    @Test fun customInlineTokensReachRenderedTextAndPreserveTapAnnotations() {
        val plugins = ParserPluginRegistry().also { it.registerAll(listOf(MentionPlugin(), HashtagPlugin())) }
        val paragraph = parseMarkdown("Hello @alice #android", plugins).firstChild!!
        val style = MarkdownStyleSheet(designTokens = MarkdownDesignTokens(plugins = MarkdownPluginTokens(
            mentionStyle = SpanStyle(color = Color.Magenta, fontSize = 23.sp, fontWeight = FontWeight.Normal),
            hashtagStyle = SpanStyle(color = Color.Green, background = Color.Yellow),
        )))
        val text = inlineRender(paragraph, false, style, plugins).text
        val mention = text.text.indexOf("@alice")
        val hashtag = text.text.indexOf("#android")
        val mentionSpan = text.spanStyles.first { mention in it.start until it.end && it.item.color == Color.Magenta }.item
        assertEquals(23.sp, mentionSpan.fontSize)
        assertEquals(FontWeight.Normal, mentionSpan.fontWeight)
        assertEquals(Color.Yellow, text.spanStyles.first { hashtag in it.start until it.end && it.item.color == Color.Green }.item.background)
        assertEquals("alice", text.getStringAnnotations("mention", mention, mention + 1).single().item)
        assertEquals("android", text.getStringAnnotations("hashtag", hashtag, hashtag + 1).single().item)
    }

    @Test fun panelAndAdmonitionNormalizeInvalidGeometryAndOpacity() {
        val panel = MarkdownPluginPanelTokens(cornerRadius = (-1).dp, iconSpacing = Float.NaN.dp, borderWidth = Float.POSITIVE_INFINITY.dp).normalized()
        assertEquals(0.dp, panel.cornerRadius)
        assertEquals(0.dp, panel.iconSpacing)
        assertEquals(0.dp, panel.borderWidth)
        val admonition = MarkdownAdmonitionTokens(accentWidth = (-1).dp, backgroundAlpha = 1.1f).normalized()
        assertEquals(0.dp, admonition.accentWidth)
        assertEquals(1f, admonition.backgroundAlpha)
        assertEquals(0f, MarkdownAdmonitionTokens(backgroundAlpha = Float.NaN).normalized().backgroundAlpha)
    }

    @Test fun zeroDimensionsPermitHidingDecoration() {
        val panel = MarkdownPluginPanelTokens(borderWidth = 0.dp, dividerThickness = 0.dp, cornerRadius = 0.dp)
        val admonition = MarkdownAdmonitionTokens(accentWidth = 0.dp, borderWidth = 0.dp, backgroundAlpha = 0f)
        assertEquals(0.dp, panel.borderWidth)
        assertEquals(0.dp, admonition.accentWidth)
    }
}
