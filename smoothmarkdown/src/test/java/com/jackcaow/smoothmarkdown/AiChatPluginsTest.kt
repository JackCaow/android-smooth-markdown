package com.jackcaow.smoothmarkdown

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatPluginsTest {
    private val plugins = ParserPluginRegistry().also {
        it.registerAll(listOf(ThinkingPlugin(), ArtifactPlugin(), ToolCallPlugin()))
    }

    @Test fun thinkingFormatsCollapseAndFollowingParagraph() {
        val xml = parseMarkdown("<thinking>\nFirst\nSecond\n</thinking>\n\nAfter", plugins)
        val node = xml.firstChild as ThinkingNode
        assertEquals("First\nSecond", node.content)
        assertTrue(node.isCollapsed)
        assertTrue(node.next is Paragraph)

        val alias = parseMarkdown("<think>\nAlias\n</think>", plugins).firstChild as ThinkingNode
        assertEquals("Alias", alias.content)
        val markdown = parseMarkdown("<|thinking|>\nReason\n<|/thinking|>", plugins).firstChild as ThinkingNode
        assertEquals("Reason", markdown.content)
    }

    @Test fun thinkingEndMustMatchOpeningStyleAndUnclosedConsumesToEnd() {
        val node = parseMarkdown("<thinking>\nA\n<|/thinking|>\nB\n</thinking>", plugins).firstChild as ThinkingNode
        assertEquals("A\n<|/thinking|>\nB", node.content)
        assertEquals("unfinished", (parseMarkdown("<think>\nunfinished", plugins).firstChild as ThinkingNode).content)
        assertEquals("", (parseMarkdown("<think>\n</think>", plugins).firstChild as ThinkingNode).content)
    }

    @Test fun artifactAttributesAliasesMimeTypesAndRawContent() {
        val code = parseMarkdown("<artifact identifier=\"hello-py\" type=\"code\" language=\"python\" title=\"Hello World\">\nprint('Hello')\n</artifact>", plugins).firstChild as ArtifactNode
        assertEquals("hello-py", code.identifier)
        assertEquals(ArtifactType.CODE, code.artifactType)
        assertEquals("python", code.language)
        assertEquals("Hello World", code.title)
        assertEquals("print('Hello')", code.content)

        val document = parseMarkdown("<artifact id='readme' type='text/markdown'>\n# Heading\n</artifact>", plugins).firstChild as ArtifactNode
        assertEquals("readme", document.identifier)
        assertEquals(ArtifactType.DOCUMENT, document.artifactType)
        assertEquals("# Heading", document.content)

        val custom = parseMarkdown("<artifact id='x' type='my-kind' lang='swift'>\n<svg onload='bad'>\n</artifact>", plugins).firstChild as ArtifactNode
        assertEquals(ArtifactType.CUSTOM, custom.artifactType)
        assertEquals("my-kind", custom.customType)
        assertEquals("swift", custom.language)
        assertEquals("<svg onload='bad'>", custom.content)
    }

    @Test fun artifactDefaultsAndUnclosedContent() {
        val artifact = parseMarkdown("<artifact type='html'>\n<div>Hello</div>", plugins).firstChild as ArtifactNode
        assertEquals("unnamed", artifact.identifier)
        assertEquals(ArtifactType.HTML, artifact.artifactType)
        assertEquals("<div>Hello</div>", artifact.content)
    }

    @Test fun toolCallParsesNameIdInputAndPendingStatus() {
        val tool = parseMarkdown("<tool_use>\n<tool_name>search</tool_name>\n<tool_id>search_001</tool_id>\n<input>\nquery: hello\n</input>\n</tool_use>", plugins).firstChild as ToolCallNode
        assertEquals("search", tool.toolName)
        assertEquals("search_001", tool.toolId)
        assertEquals("query: hello", tool.parameters)
        assertEquals(ToolCallStatus.PENDING, tool.status)
        assertNull(tool.result)
        val bare = parseMarkdown("<tool_use>\n<tool_name>get_time</tool_name>\n</tool_use>", plugins).firstChild as ToolCallNode
        assertNull(bare.parameters)
        assertNull(bare.toolId)
    }

    @Test fun mixedPluginsAreOptInAndDoNotInterfereWithCodeOrOrdinaryHtml() {
        val markdown = "<thinking>\nA\n</thinking>\n\n<artifact id='a' type='code'>\nx\n</artifact>\n\n<tool_use>\n<tool_name>run</tool_name>\n</tool_use>"
        val document = parseMarkdown(markdown, plugins)
        assertTrue(document.firstChild is ThinkingNode)
        assertTrue(document.firstChild.next is ArtifactNode)
        assertTrue(document.firstChild.next.next is ToolCallNode)
        assertFalse(parseMarkdown(markdown).firstChild is PluginBlockNode)
        assertTrue(parseMarkdown("```xml\n<thinking>\nA\n</thinking>\n```", plugins).firstChild is FencedCodeBlock)
        assertTrue(parseMarkdown("<div>ordinary</div>", plugins).firstChild is HtmlBlock)
    }
}
