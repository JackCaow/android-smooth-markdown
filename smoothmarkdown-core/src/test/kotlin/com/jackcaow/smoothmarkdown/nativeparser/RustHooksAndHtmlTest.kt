package com.jackcaow.smoothmarkdown.nativeparser

import com.jackcaow.smoothmarkdown.NativeMarkdownHTMLSerializer
import com.jackcaow.smoothmarkdown.MarkdownCoreParser
import com.jackcaow.smoothmarkdown.ast.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Contract tests use the real JNI scanner and renderer, including mutable host values. */
class RustHooksAndHtmlTest {
    private fun requireBackend() {
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue(RustMarkdownBridge.available)
        assumeTrue(RustMarkdownBridge.available)
    }
    @Test fun hooksSeeProjectedContainerContextsAndOriginalUTF16Offsets() {
        requireBackend()
        val source = "> - 中文🙂 @jack\n>   next"
        val seen = mutableListOf<Pair<String, Int>>()
        val payload = Any()
        val before = RustMarkdownBridge.successfulParseCount
        val root = NativeMarkdownASTParser(customInline = { context, index, offset ->
            if (context.startsWith("@jack", index)) {
                seen += context to offset
                NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW,
                    "@jack", SourceRange(offset, 5), payload = payload), 5)
            } else null
        }).parse(source)
        fun nodes(node: NativeMarkdownNode): List<NativeMarkdownNode> = listOf(node) + node.children.flatMap(::nodes)
        val custom = nodes(root).single { it.payload === payload }
        assertTrue(RustMarkdownBridge.successfulParseCount > before)
        assertEquals(source.indexOf("@jack"), custom.sourceRange.offset)
        assertEquals("@jack", custom.source)
        assertTrue(seen.all { !it.first.contains("> ") && !it.first.startsWith("- ") })
        assertEquals(source.indexOf("@jack"), seen.first().second)
    }
    @Test fun customBlockUsesRustOriginalSpanAndKeepsHostPayload() {
        requireBackend()
        val source = "> :::custom\r\n> body\r\n> :::end\r\n\n# after"
        val payload = Any()
        val root = NativeMarkdownASTParser(customBlock = { lines, index, offset ->
            if (lines[index] == ":::custom") {
                assertEquals("body", lines[index + 1])
                assertEquals(source.indexOf(":::custom"), offset)
                NativeCustomBlockMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW,
                    lines.subList(index, index + 3).joinToString("\n"), SourceRange(offset, 0), payload = payload), 3)
            } else null
        }).parse(source)
        val custom = root.children.first().children.single()
        assertSame(payload, custom.payload)
        assertEquals(source.substring(custom.sourceRange.offset, custom.sourceRange.end), custom.source)
        assertTrue(custom.source.contains("> body"))
        assertEquals(NativeMarkdownNode.Kind.HEADING, root.children.last().kind)
    }
    @Test fun missedAndInvalidHooksLeaveBuiltinsAndCodeUntouched() {
        requireBackend()
        var callsInCode = 0
        val root = NativeMarkdownASTParser(customInline = { source, index, offset ->
            if (source[index] == '@') {
                if (source.substring(index).startsWith("@code")) callsInCode++
                NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, "", SourceRange(offset, 0)), 0)
            } else null
        }, customBlock = { _, _, offset -> NativeCustomBlockMatch(
            NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, "", SourceRange(offset, 0)), Int.MAX_VALUE)
        }).parse("# heading\n\n`@code` @plain **strong**")
        assertEquals(0, callsInCode)
        assertEquals(NativeMarkdownNode.Kind.HEADING, root.children.first().kind)
        assertTrue(root.children.last().children.any { it.kind == NativeMarkdownNode.Kind.STRONG })
    }
    @Test fun callbackExceptionIsNotSwallowedAsOptionalFallback() {
        requireBackend()
        val previous = System.getProperty("smoothmarkdown.rust.required")
        val original = IllegalArgumentException("plugin callback failure")
        try {
            System.setProperty("smoothmarkdown.rust.required", "false")
            try {
                NativeMarkdownASTParser(customInline = { _, _, _ -> throw original }).parse("callback")
                fail("Callback failure was swallowed")
            } catch (caught: IllegalArgumentException) { assertSame(original, caught) }
        } finally {
            if (previous == null) System.clearProperty("smoothmarkdown.rust.required") else System.setProperty("smoothmarkdown.rust.required", previous)
        }
    }
    @Test fun inlineFragmentUsesReferenceTriplesAndTranslatesHostChildrenOnlyOnce() {
        requireBackend()
        val source = "# [a][ref] @x"
        val offset = 17
        val root = RustMarkdownBridge.parseInline(source, offset,
            mapOf(" REF " to NativeMarkdownReference("/中文", "title")), customInline = { text, index, absolute ->
                if (text.startsWith("@x", index)) NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW,
                    "@x", SourceRange(absolute, 2), children = listOf(NativeMarkdownNode(NativeMarkdownNode.Kind.TEXT,
                        "x", SourceRange(absolute + 1, 1), literalText = "x"))), 2) else null
            })!!
        assertFalse(root.children.any { it.kind == NativeMarkdownNode.Kind.HEADING })
        assertEquals("/中文", root.children.single { it.kind == NativeMarkdownNode.Kind.LINK }.destination)
        val custom = root.children.single { it.kind == NativeMarkdownNode.Kind.RAW }
        assertEquals(offset + source.indexOf("@x"), custom.sourceRange.offset)
        assertEquals(custom.sourceRange.offset + 1, custom.children.single().sourceRange.offset)
    }
    @Test fun htmlRendererReadsMutatedASTRatherThanReparsingSource() {
        requireBackend()
        val document = MarkdownCoreParser(true).parse("# Before\n\n[a](/before)\n")
        val heading = document.firstChild as Heading
        heading.level = 3
        (heading.firstChild as Text).literal = "Changed<&🙂"
        val link = document.descendants().filterIsInstance<Link>().single()
        link.destination = "/changed 中文"; link.title = "new\"title"
        (link.firstChild as Text).literal = "updated"
        val before = RustMarkdownBridge.successfulHtmlRenderCount
        val html = NativeMarkdownHTMLSerializer().render(document)
        assertTrue(RustMarkdownBridge.successfulHtmlRenderCount > before)
        assertEquals("<h3>Changed&lt;&amp;🙂</h3>\n<p><a href=\"/changed%20%E4%B8%AD%E6%96%87\" title=\"new&quot;title\">updated</a></p>\n", html)
    }
    @Test fun htmlSubtreePreservesTableHeaderAlignmentAndTightParentContext() {
        requireBackend()
        val cell = TableCell(TableCell.Alignment.RIGHT, true).apply { appendChild(Text("cell")) }
        assertEquals("<th align=\"right\">cell</th>\n", NativeMarkdownHTMLSerializer().render(cell))
        val paragraph = Paragraph().apply { appendChild(Text("tight")) }
        BulletList(isTight = true).apply { appendChild(ListItem().apply { appendChild(paragraph) }) }
        assertEquals("tight", NativeMarkdownHTMLSerializer().render(paragraph))
        assertEquals("<input checked=\"\" disabled=\"\" type=\"checkbox\"> ", NativeMarkdownHTMLSerializer().render(TaskListItemMarker(true)))
        val document = Document().apply { appendChild(HtmlBlock("<b>raw</b>")) }
        assertEquals("&lt;b&gt;raw&lt;/b&gt;\n", NativeMarkdownHTMLSerializer(true).render(document))
    }
    @Test fun sourceExportAlsoUsesSharedRenderer() {
        requireBackend()
        val before = RustMarkdownBridge.successfulHtmlRenderCount
        assertEquals("<p><strong>bold</strong></p>\n", MarkdownCoreParser().renderHtml("**bold**"))
        assertTrue(RustMarkdownBridge.successfulHtmlRenderCount > before)
    }
    @Test fun htmlEmptyMutableContainersAndMarkerOnlyItemsKeepPublicBehavior() {
        requireBackend()
        val exporter = NativeMarkdownHTMLSerializer()
        assertEquals("", exporter.render(TableBody()))
        assertEquals("<table>\n</table>\n", exporter.render(TableBlock()))
        val item = ListItem().apply { appendChild(TaskListItemMarker(true)) }
        assertEquals("<li><input checked=\"\" disabled=\"\" type=\"checkbox\"> </li>\n", exporter.render(item))
    }

    @Test fun htmlMutableSignedFieldsPreserveExistingIntValues() {
        requireBackend()
        val exporter = NativeMarkdownHTMLSerializer()
        val heading = Heading(-1).apply { appendChild(Text("negative")) }
        assertEquals("<h-1>negative</h-1>\n", exporter.render(heading))
        heading.level = Int.MIN_VALUE
        assertEquals("<h-2147483648>negative</h-2147483648>\n", exporter.render(heading))
        val list = OrderedList(Int.MIN_VALUE).apply {
            appendChild(ListItem().apply { appendChild(Paragraph().apply { appendChild(Text("item")) }) })
        }
        assertEquals("<ol start=\"-2147483648\">\n<li>item</li>\n</ol>\n", exporter.render(list))
        list.startNumber = Int.MAX_VALUE
        assertEquals("<ol start=\"2147483647\">\n<li>item</li>\n</ol>\n", exporter.render(list))
    }

}
