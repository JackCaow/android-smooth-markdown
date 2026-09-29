package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentTextTest {
    @Test fun offscreenBlocksHaveStablePathsAndUtf16Offsets() {
        val source = "# Intro\n\n😀 one\n\n😀 one\n\n| Name | Value |\n| --- | --- |\n| A | B |\n\n" +
            "~~~kotlin\nval x = 1\n~~~"
        val document = parseMarkdown(source)
        val first = readerDocumentText(document, false, null, null, emptyMap())
        val second = readerDocumentText(document, false, null, null, emptyMap())

        assertTrue(first.complete)
        assertEquals(first, second)
        assertEquals(first.blocks.map { it.id }.size, first.blocks.map { it.id }.toSet().size)
        assertEquals(listOf("0", "1", "2", "3/0", "3/1", "4"), first.blocks.map { it.id })
        assertTrue(first.text.contains("Intro\n😀 one\n😀 one"))
        assertTrue(first.text.contains("Name\tValue\nA\tB"))
        assertTrue(first.text.endsWith("val x = 1"))
        val duplicate = first.blocks[2]
        assertEquals("😀 one", first.text.substring(duplicate.start, duplicate.end))
        assertEquals(duplicate, first.blockAt(duplicate.start + 2)) // emoji occupies two UTF-16 units.
        assertNull(first.blockAt(duplicate.end))
    }

    @Test fun projectionFollowsCollapsedDetailsAndOmitsNontextGeometry() {
        val document = parseMarkdown(
            "Before ![photo](https://example.com/photo.png) after\n\n" +
                "<details>\n<summary>Open me</summary>\nHidden **body**\n</details>\n\n---\n\nAfter",
        )
        val closed = readerDocumentText(document, false, null, null, emptyMap())
        val details = document.children().filterIsInstance<DetailsNode>().single()
        val opened = readerDocumentText(document, false, null, null, mapOf(details to true))
        assertTrue(closed.complete)
        assertEquals("Before  after\nOpen me\nAfter", closed.text)
        assertEquals("Before  after\nOpen me\nHidden body\nAfter", opened.text)
        assertFalse(closed.text.contains("photo"))

        val controller = SmoothSelectionController()
        controller.bindDocument(document, false, null, null)
        assertEquals(closed.text, controller.documentText)
        controller.setDetailsExpanded(details, true)
        assertEquals(opened.text, controller.documentText)
        controller.unbindDocument(document)
        assertNull(controller.documentText)
    }

    @Test fun customBlockWithoutTextProjectionIsNotReportedComplete() {
        val document = parseMarkdown("Before\n\nAfter")
        val builders = MarkdownBuilderRegistry().register(Paragraph::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
        })
        val projection = readerDocumentText(document, false, null, builders, emptyMap())
        assertFalse(projection.complete)
        assertEquals("", projection.text)

        val controller = SmoothSelectionController()
        controller.bindDocument(document, false, null, builders)
        assertNull(controller.documentText)
        assertFalse(controller.copyAllDocumentText())
    }

    @Test fun customRenderersCanProvideWholeDocumentCopyText() {
        val builders = MarkdownBuilderRegistry().register(Paragraph::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
            override fun documentText(node: Node) = "rendered paragraph"
        })
        val built = readerDocumentText(parseMarkdown("source paragraph"), false, null, builders, emptyMap())
        assertTrue(built.complete)
        assertEquals("rendered paragraph", built.text)

        val plugins = ParserPluginRegistry().apply { registerBlock(DelimitedBlockPlugin("note")) }
        val pluginDocument = parseMarkdown(":::note\ninside\n:::", plugins)
        val projected = readerDocumentText(pluginDocument, false, plugins, null, emptyMap())
        assertTrue(projected.complete)
        assertEquals("inside", projected.text)
    }
}
