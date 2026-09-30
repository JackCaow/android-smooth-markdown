package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentTextTest {
    @Test fun builtInRenderersProjectExactSemanticCopyInDisplayOrder() {
        val source = """
            Intro ![inline](https://example.com/in.png) and MONEYx+1MONEY.

            ![standalone](https://example.com/out.png)

            ~~~kotlin
            val x = 1
            ~~~

            | Name | Value |
            | --- | --- |
            | Cell | ![table](https://example.com/cell.png) |

            MONEYMONEYE=mc^2MONEYMONEY

            <details open>
            <summary>Summary</summary>
            Detail body.
            </details>
        """.trimIndent().replace("MONEY", "$")
        val projection = readerDocumentText(parseMarkdown(source), false, null, null, emptyMap())
        assertTrue(projection.complete)
        assertTrue(projection.text.contains("Intro inline and \$x+1\$."))
        assertTrue(projection.text.contains("\nstandalone\nval x = 1\n\n"))
        assertTrue(projection.text.contains("Name\tValue\nCell\ttable"))
        assertTrue(projection.text.contains("\$\$E=mc^2\$\$"))
        assertTrue(projection.text.indexOf("\$\$E=mc^2\$\$") < projection.text.indexOf("Summary"))
        assertTrue(projection.text.contains("Summary\nDetail body."))
        assertFalse(projection.text.contains("https://example.com"))
        assertFalse(projection.text.contains("KOTLIN"))

        val html = readerDocumentText(parseMarkdown(
            "Before <img src='https://example.com/a.png' alt='HTML inline'> after\n\n" +
                "<img src='https://example.com/b.png' alt='HTML block'>",
            enableHtml = true,
        ), true, null, null, emptyMap())
        assertEquals("Before HTML inline after\nHTML block", html.text)
    }

    @Test fun semanticCopyRequiresUnchangedNativeWholeSelectionSnapshot() {
        val full = listOf(AnnotatedString("first"), AnnotatedString("last"))
        val projection = ReaderDocumentText("first\nimage alt\nlast", emptyList(), complete = true)
        assertEquals(projection.text, exactWholeDocumentCopyText(full, full.toList(), projection))
        assertNull(exactWholeDocumentCopyText(full, listOf(AnnotatedString("first")), projection))
        assertNull(exactWholeDocumentCopyText(null, full, projection))
        assertNull(exactWholeDocumentCopyText(full, full, projection.copy(complete = false)))
    }
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
        assertTrue(first.text.endsWith("val x = 1\n"))
        val duplicate = first.blocks[2]
        assertEquals("😀 one", first.text.substring(duplicate.start, duplicate.end))
        assertEquals(duplicate, first.blockAt(duplicate.start + 2)) // emoji occupies two UTF-16 units.
        assertNull(first.blockAt(duplicate.end))
    }

    @Test fun projectionFollowsCollapsedDetailsAndCopiesImageAltWithoutAnchorGeometry() {
        val document = parseMarkdown(
            "Before ![photo](https://example.com/photo.png) after\n\n" +
                "<details>\n<summary>Open me</summary>\nHidden **body**\n</details>\n\n---\n\nAfter",
        )
        val closed = readerDocumentText(document, false, null, null, emptyMap())
        val details = document.children().filterIsInstance<DetailsNode>().single()
        val opened = readerDocumentText(document, false, null, null, mapOf(details to true))
        assertTrue(closed.complete)
        assertEquals("Before photo after\nOpen me\nAfter", closed.text)
        assertEquals("Before photo after\nOpen me\nHidden body\nAfter", opened.text)

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

    @Test fun standaloneImageBuilderMustOptInToTextCopy() {
        val builders = MarkdownBuilderRegistry().register(Image::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Image
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
        })
        val document = parseMarkdown("![alt](https://example.com/image.png)")
        assertFalse(readerDocumentText(document, false, null, builders, emptyMap()).complete)
    }

    @Test fun nativeFullSelectionEligibilityIsBoundedAndRejectsUnknownVisualBuilders() {
        val controller = SmoothSelectionController()
        val small = parseMarkdown("First\n\nLast")
        controller.bindDocument(small, false, null, null)
        assertEquals("First\nLast", controller.fullDocumentSelectionProjection()?.text)

        controller.bindDocument(small, false, null, null, customCodeBuilder = true)
        assertEquals("First\nLast", controller.fullDocumentSelectionProjection()?.text)
        controller.bindDocument(small, false, null, null, customImageBuilder = true)
        assertEquals("First\nLast", controller.fullDocumentSelectionProjection()?.text)

        val code = parseMarkdown("~~~kotlin\nval x = 1\n~~~")
        controller.bindDocument(code, false, null, null, customCodeBuilder = true)
        assertNull(controller.fullDocumentSelectionProjection())
        val image = parseMarkdown("Before ![icon](https://example.com/icon.png) after")
        controller.bindDocument(image, false, null, null, customImageBuilder = true)
        assertNull(controller.fullDocumentSelectionProjection())
        val htmlImage = parseMarkdown("Before <img src='https://example.com/icon.png' alt='icon'> after", enableHtml = true)
        controller.bindDocument(htmlImage, true, null, null, customImageBuilder = true)
        assertNull(controller.fullDocumentSelectionProjection())

        val builders = MarkdownBuilderRegistry().register(Paragraph::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
            override fun documentText(node: Node) = "visible text"
        })
        controller.bindDocument(small, false, null, builders)
        assertEquals("visible text\nvisible text", controller.documentText)
        assertNull(controller.fullDocumentSelectionProjection())

        val plugins = ParserPluginRegistry().apply { registerBlock(DelimitedBlockPlugin("note")) }
        controller.bindDocument(parseMarkdown(":::note\ninside\n:::", plugins), false, plugins, null)
        assertEquals("inside", controller.documentText)
        assertNull(controller.fullDocumentSelectionProjection())

        val many = parseMarkdown((0..MAX_FULL_SELECTION_BLOCKS).joinToString("\n\n") { "Block $it" })
        controller.bindDocument(many, false, null, null)
        assertNull(controller.fullDocumentSelectionProjection())

        controller.bindDocument(parseMarkdown("x".repeat(MAX_FULL_SELECTION_UTF16 + 1)), false, null, null)
        assertNull(controller.fullDocumentSelectionProjection())

        val manyImages = parseMarkdown("Start\n\n" +
            (0..700).joinToString("\n\n") { "![alt](https://example.com/$it.png)" })
        controller.bindDocument(manyImages, false, null, null)
        assertTrue(readerDocumentNodeCount(manyImages) > MAX_FULL_SELECTION_RENDER_NODES)
        assertNull(controller.fullDocumentSelectionProjection())
    }
}
