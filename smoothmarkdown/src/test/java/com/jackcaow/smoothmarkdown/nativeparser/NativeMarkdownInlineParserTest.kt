package com.jackcaow.smoothmarkdown.nativeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMarkdownInlineParserTest {
    private fun parse(source: String, offset: Int = 0, references: Map<String, NativeMarkdownReference> = emptyMap(), gfm: Boolean = true) =
        NativeMarkdownInlineParser.parse(source, offset, references, gfm)

    @Test fun emphasisUsesFlankingAndRuleOfThree() {
        val nodes = parse("***deep*** a_b_c **a *b* c**", gfm = false)
        val first = nodes.first()
        assertEquals(NativeMarkdownNode.Kind.EMPHASIS, first.kind)
        assertEquals(NativeMarkdownNode.Kind.STRONG, first.children.single().kind)
        assertTrue(nodes.any { it.kind == NativeMarkdownNode.Kind.TEXT && "a_b_c" in it.source })
        assertEquals(NativeMarkdownNode.Kind.STRONG, nodes.last().kind)
        assertEquals(NativeMarkdownNode.Kind.EMPHASIS, nodes.last().children[1].kind)
        assertFalse(parse("a***b**c*", gfm = false).any { it.kind == NativeMarkdownNode.Kind.STRONG })
    }

    @Test fun emojiAndNestedLinksKeepUtf16Ranges() {
        val source = "🐈 **[x😀](https://example.org/a_(b) \"t\")**"
        val nodes = parse(source, 11)
        val strong = nodes.last()
        val link = strong.children.single()
        assertEquals(11 + source.indexOf("**"), strong.sourceRange.offset)
        assertEquals("https://example.org/a_(b)", link.destination)
        assertEquals("t", link.title)
        assertEquals("x😀", link.children.single().semanticText)
        assertEquals(3, link.children.single().sourceRange.length)
        fun check(node: NativeMarkdownNode) {
            assertEquals(node.source, source.substring(node.sourceRange.offset - 11, node.sourceRange.end - 11))
            node.children.forEach(::check)
        }
        nodes.forEach(::check)
    }

    @Test fun namedAndNumericEntitiesAreDecodedWithoutRuntimeLibrary() {
        assertEquals("∳ ≂̸ 😀 � �", NativeMarkdownTextDecoder.decode("&CounterClockwiseContourIntegral; &NotEqualTilde; &#x1f600; &#0; &#xD800;"))
        assertEquals("&unknown; &#x1234567;", NativeMarkdownTextDecoder.decode("&unknown; &#x1234567;"))
        assertEquals("&amp; *", NativeMarkdownTextDecoder.decode("\\&amp; \\*"))
        assertEquals("a  b", NativeMarkdownTextDecoder.codeSpan("` a\r\n b `"))
        assertEquals(2125, NativeHTMLEntityTable.values.size)
    }

    @Test fun referencesCaseFoldAndCodeProtectsDelimiters() {
        val references = mapOf("strasse" to NativeMarkdownReference("/x", "title"))
        val nodes = parse("[Straße] `` *x* `y` ``", references = references)
        assertEquals(NativeMarkdownNode.Kind.LINK, nodes.first().kind)
        assertEquals("/x", nodes.first().destination)
        assertEquals("*x* `y`", nodes.last().semanticText)
    }

    @Test fun gfmAutolinksTrimOnlyUnbalancedClosingPunctuation() {
        val nodes = parse("https://example.org/a_(b)). www.example.com & <span data-x='>'>x</span>")
        val links = nodes.filter { it.kind == NativeMarkdownNode.Kind.LINK }
        assertEquals(listOf("https://example.org/a_(b)", "http://www.example.com"), links.map { it.destination })
        assertEquals("<span data-x='>'>", nodes.first { it.kind == NativeMarkdownNode.Kind.INLINE_HTML }.source)
        assertEquals(NativeMarkdownNode.Kind.STRIKETHROUGH, parse("~~ok~~").single().kind)
        assertEquals(NativeMarkdownNode.Kind.TEXT, parse("~~~literal~~~").single().kind)
    }

    @Test fun customPluginNeverSeesEscapedOrCodeSpanContent() {
        val source = "`@code` \\@escaped @live"
        val nodes = NativeMarkdownInlineParser.parse(source, 20, emptyMap(), true, customInline = { text, index, absolute ->
            if (text.startsWith("@live", index)) NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, "@live", SourceRange(absolute, 5), payload = "plugin"), 5) else null
        })
        assertEquals(NativeMarkdownNode.Kind.INLINE_CODE, nodes.first().kind)
        assertEquals("plugin", nodes.last().payload)
        assertEquals(20 + source.indexOf("@live"), nodes.last().sourceRange.offset)
    }
}
