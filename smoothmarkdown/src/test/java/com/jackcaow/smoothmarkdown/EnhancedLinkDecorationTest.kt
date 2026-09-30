package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.ast.Link
import com.jackcaow.smoothmarkdown.ast.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedLinkDecorationTest {
    @Test fun externalAndLocalLinksKeepExactSelectableTextAndTapAnnotations() {
        val paragraph = parseMarkdown("See [site](https://example.com) and [note](/note).").firstChild!!
        val text = inlineRender(paragraph, false).text

        assertEquals("See site and note.", text.text)
        assertEquals("site", text.text.substring(4, 8))
        assertEquals("https://example.com", safeLinkAt(text, 5))
        assertEquals("/note", safeLinkAt(text, 14))
        assertEquals(listOf(
            EnhancedLinkRange(4, 8, "https://example.com"),
            EnhancedLinkRange(13, 17, "/note"),
        ), enhancedLinkRanges(text))
        assertTrue(enhancedLinkRanges(text).first().external)
        assertFalse(enhancedLinkRanges(text).last().external)
    }

    @Test fun customLinkBuilderTakesPrecedenceOverBuiltInDecoration() {
        val registry = MarkdownBuilderRegistry().register(Link::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Link
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
            override fun renderInline(node: Node) = MarkdownInlinePresentation.Text("custom")
        })
        val paragraph = parseMarkdown("[site](https://example.com)").firstChild!!
        val custom = inlineRender(paragraph, false, builders = registry).text
        assertEquals("custom", custom.text)
        assertTrue(enhancedLinkRanges(custom).isEmpty())
        assertTrue(custom.getStringAnnotations("url", 0, custom.length).isEmpty())
    }

    @Test fun htmlAnchorDoesNotBecomeMarkdownEnhancedLink() {
        val paragraph = parseMarkdown("<a href=\"https://example.com\">site</a>", enableHtml = true).firstChild!!
        val text = inlineRender(paragraph, true).text
        assertEquals("site", text.text)
        assertTrue(enhancedLinkRanges(text).isEmpty())
        assertEquals("https://example.com", safeLinkAt(text, 1))
    }
}
