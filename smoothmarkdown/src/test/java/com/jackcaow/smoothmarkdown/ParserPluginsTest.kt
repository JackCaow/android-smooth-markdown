package com.jackcaow.smoothmarkdown

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Paragraph
import org.commonmark.node.Node
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserPluginsTest {
    @Test fun registryOrdersDuplicatesCopiesAndUnregisters() {
        val registry = ParserPluginRegistry()
        registry.registerAll(listOf(EmojiPlugin(), MentionPlugin(), HashtagPlugin(), AdmonitionPlugin()))
        assertEquals(listOf("mention", "hashtag", "emoji"), registry.inlinePlugins.map { it.id })
        assertTrue(registry.isInlineTrigger('@'))
        assertEquals("mention", registry.getInlinePluginByTrigger('@')?.id)
        assertEquals("admonition", registry.findBlockPlugins("::: note").single().id)
        assertThrows(IllegalArgumentException::class.java) { registry.register(MentionPlugin()) }
        val copy = registry.copy()
        assertTrue(copy.unregisterInline("mention"))
        assertFalse(copy.unregisterInline("missing"))
        assertEquals(3, registry.inlinePlugins.size)
        copy.clear()
        assertTrue(copy.inlinePlugins.isEmpty())
        assertTrue(copy.blockPlugins.isEmpty())
    }

    @Test fun builtInInlinePluginsAreOptInAndPreserveCode() {
        val markdown = "Hello @john_doe-test, #flutter_dev :SMILE: :missing:"
        val plain = parseMarkdown(markdown).firstChild as Paragraph
        assertTrue(plain.children().none { it is PluginInlineNode })
        val registry = ParserPluginRegistry().also { it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin())) }
        val paragraph = parseMarkdown(markdown, registry).firstChild as Paragraph
        assertEquals("john_doe-test", paragraph.children().filterIsInstance<MentionNode>().single().username)
        assertEquals("flutter_dev", paragraph.children().filterIsInstance<HashtagNode>().single().tag)
        assertEquals("😄", paragraph.children().filterIsInstance<EmojiNode>().single().emoji)
        assertEquals("Hello @john_doe-test, #flutter_dev 😄 :missing:", inlineText(paragraph, false, registry).text)
        val code = parseMarkdown("```\n@john :smile:\n```", registry).firstChild
        assertTrue(code is FencedCodeBlock)
    }

    @Test fun builtInsRejectInvalidInputAndAcceptCustomEmoji() {
        val registry = ParserPluginRegistry().also {
            it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin(mapOf("custom" to "🎉"))))
        }
        val paragraph = parseMarkdown("@123 @ #1 #_private :custom:", registry).firstChild as Paragraph
        assertTrue(paragraph.children().none { it is MentionNode })
        assertEquals("_private", paragraph.children().filterIsInstance<HashtagNode>().single().tag)
        assertEquals("🎉", paragraph.children().filterIsInstance<EmojiNode>().single().emoji)
    }

    @Test fun parsedMentionAndHashtagTapsKeepTheirOwnIdsAndDoNotStealLinksOrCode() {
        val registry = ParserPluginRegistry().also { it.registerAll(listOf(MentionPlugin(), HashtagPlugin())) }
        val paragraph = parseMarkdown("Hello @john and #flutter, [@jane](https://example.com) with `@code`", registry)
            .firstChild as Paragraph
        val text = inlineText(paragraph, false, registry)
        assertEquals(listOf("john", "jane"), text.getStringAnnotations("mention", 0, text.length).map { it.item })
        assertEquals(listOf("flutter"), text.getStringAnnotations("hashtag", 0, text.length).map { it.item })
        val events = mutableListOf<String>()
        fun tap(value: String) = dispatchTextTap(
            text, text.text.indexOf(value) + 1,
            onLinkClick = { events += "link:$it" },
            onPlainTextTap = { events += "plain" },
            onMentionClick = { events += "mention:$it" },
            onHashtagClick = { events += "hashtag:$it" },
        )
        tap("@john")
        tap("#flutter")
        tap("@jane")
        tap("@code")
        assertEquals(listOf("mention:john", "hashtag:flutter", "link:https://example.com", "plain"), events)
    }

    @Test fun higherPriorityWinsAndNullParseFallsThroughSameTrigger() {
        val registry = ParserPluginRegistry()
        registry.registerInline(object : InlineParserPlugin {
            override val id = "null_first"
            override val name = "Null"
            override val priority = 100
            override val triggerCharacter = '@'
            override fun canParse(text: String, index: Int) = true
            override fun parse(text: String, startIndex: Int): InlineParseResult? = null
            override fun render(node: PluginInlineNode): InlinePluginPresentation? = null
        })
        registry.registerInline(MentionPlugin())
        assertEquals("john", (parseMarkdown("@john", registry).firstChild as Paragraph).children().filterIsInstance<MentionNode>().single().username)
    }

    @Test fun admonitionParsesKnownAliasCustomTitleMarkdownAndUnclosed() {
        val registry = ParserPluginRegistry().also { it.register(AdmonitionPlugin()) }
        val document = parseMarkdown("::: caution Important Notice\n**Read** this.\n:::\n\n::: custom Title\n- One", registry)
        val first = document.firstChild as AdmonitionNode
        val second = first.next as AdmonitionNode
        assertEquals(AdmonitionType.WARNING, first.admonitionType)
        assertEquals("Important Notice", first.title)
        assertEquals("Read this.", inlineText(first.content.single() as Paragraph, false).text)
        assertEquals(AdmonitionType.CUSTOM, second.admonitionType)
        assertEquals("custom", second.customType)
        assertEquals("Title", second.title)
        assertTrue(second.content.isNotEmpty())
    }

    @Test fun blockPriorityAndNullCreationFallThrough() {
        val registry = ParserPluginRegistry()
        fun plugin(id: String, priority: Int, createsNode: Boolean) = object : BlockParserPlugin {
            override val id = id
            override val name = id
            override val priority = priority
            override fun canStart(line: String) = line == "::: choice"
            override fun createNode(openingLine: String): PluginBlockNode? = if (createsNode) PluginBlockNode() else null
            override fun isClosingLine(line: String) = line == ":::"
            override fun complete(node: PluginBlockNode, contentLines: List<String>) = Unit
            @Composable override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) = Unit
        }
        registry.registerBlock(plugin("low", 1, true))
        registry.registerBlock(plugin("null_high", 20, false))
        registry.registerBlock(plugin("winner", 10, true))
        assertEquals(listOf("null_high", "winner", "low"), registry.blockPlugins.map { it.id })
        assertEquals("winner", (parseMarkdown("::: choice\nbody\n:::", registry).firstChild as PluginBlockNode).pluginId)
    }

    private fun org.commonmark.node.Node.children(): List<org.commonmark.node.Node> = buildList {
        var child = firstChild
        while (child != null) { add(child); child = child.next }
    }
}
