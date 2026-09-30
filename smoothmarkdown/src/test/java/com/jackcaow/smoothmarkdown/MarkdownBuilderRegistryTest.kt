package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBuilderRegistryTest {
    private class Builder(
        private val matches: (Node) -> Boolean = { true },
        private val inline: (Node) -> MarkdownInlinePresentation? = { null },
    ) : MarkdownNodeBuilder {
        override fun canBuild(node: Node) = matches(node)
        @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
        override fun renderInline(node: Node) = inline(node)
    }

    @Test fun exactTypeOverridePrecedesEarlierBroadFallback() {
        val broad = Builder(matches = { true })
        val heading = Builder(matches = { it is Heading })
        val registry = MarkdownBuilderRegistry()
            .register(Node::class, broad)
            .register(Heading::class, heading)
        val parsed = parseMarkdown("# Title\n\nBody")
        assertSame(heading, registry.findBuilder(parsed.firstChild!!))
        assertSame(broad, registry.findBuilder(parsed.firstChild!!.next!!))
    }

    @Test fun rejectedExactBuilderFallsBackInRegistrationOrderAndReplacementKeepsPosition() {
        val fallback = Builder(matches = { it is Heading })
        val first = Builder(matches = { false })
        val replacement = Builder(matches = { true })
        val registry = MarkdownBuilderRegistry()
            .register(Paragraph::class, fallback)
            .register(Heading::class, first)
        val node = requireNotNull(parseMarkdown("# Title").firstChild)
        assertSame(fallback, registry.findBuilder(node))
        registry.register(Heading::class, replacement)
        assertSame(replacement, registry.findBuilder(node))
        assertSame(replacement, registry.getBuilder(Heading::class))
        assertTrue(registry.hasBuilder(Heading::class))
        assertSame(replacement, registry.unregister(Heading::class))
        assertFalse(registry.hasBuilder(Heading::class))
        assertSame(fallback, registry.findBuilder(node))
        registry.clear()
        assertNull(registry.findBuilder(node))
    }

    @Test fun inlineOverrideReplacesBuiltInTextInsideNestedHeading() {
        val builder = Builder(
            matches = { it is Text },
            inline = { MarkdownInlinePresentation.Text("custom", SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        val registry = MarkdownBuilderRegistry().register(Text::class, builder)
        val quote = parseMarkdown("> # original").firstChild as BlockQuote
        val heading = quote.firstChild as Heading
        val rendered = inlineRender(heading, false, builders = registry)
        assertEquals("custom", rendered.text.text)
        assertEquals(FontWeight.Bold, rendered.text.spanStyles.single().item.fontWeight)
        assertEquals("original", inlineRender(heading, false).text.text)
        assertTrue(rendered.customWidgets.isEmpty())
    }

    @Test fun inlineWidgetOverrideUsesExplicitPlaceholderAndCopyFallback() {
        val builder = Builder(
            matches = { it is Text },
            inline = { MarkdownInlinePresentation.Widget(24.dp, 16.dp, "ALT") },
        )
        val registry = MarkdownBuilderRegistry().register(Text::class, builder)
        val paragraph = parseMarkdown("before").firstChild as Paragraph
        val rendered = inlineRender(paragraph, false, builders = registry)
        assertEquals("ALT", rendered.text.text)
        assertEquals(paragraph.firstChild, rendered.customWidgets.values.single().node)
        assertSame(builder, rendered.customWidgets.values.single().builder)
    }

    @Test fun inlineWidgetRejectsZeroSize() {
        val registry = MarkdownBuilderRegistry().register(Text::class, Builder(
            matches = { it is Text },
            inline = { MarkdownInlinePresentation.Widget(0.dp, 16.dp, "ALT") },
        ))
        assertThrows(IllegalArgumentException::class.java) {
            inlineRender(requireNotNull(parseMarkdown("text").firstChild), false, builders = registry)
        }
    }
}
