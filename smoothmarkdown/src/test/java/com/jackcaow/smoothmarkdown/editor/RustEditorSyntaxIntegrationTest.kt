package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser
import com.jackcaow.smoothmarkdown.AdmonitionPlugin
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class RustEditorSyntaxIntegrationTest {
    private fun counter(): AtomicLong {
        val type = Class.forName("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
        assertTrue("Editor contract tests require native Rust", type.getMethod("getAvailable").invoke(type.getField("INSTANCE").get(null)) as Boolean)
        return type.getDeclaredField("successfulParses").apply { isAccessible = true }.get(null) as AtomicLong
    }
    @Test fun inlineFragmentsActuallyUseRustWithoutBlockRecognition() {
        val count = counter()
        val before = count.get()
        val source = "# text - [site](https://example.test/a_(b) \"Title\") **中文🙂**"
        val model = MarkdownInlineEditing.parse(source)
        assertTrue(count.get() > before)
        assertEquals("# text - site 中文🙂", model.visible)
        assertTrue(model.marks.any { it.kind == InlineMarkKind.LINK && it.destination == "https://example.test/a_(b)" })
        assertTrue(model.marks.any { it.kind == InlineMarkKind.BOLD })
        assertEquals(source, model.replaceVisible(model.visible))
    }
    @Test fun semanticEntityAndEscapeOffsetsPatchOnlyOriginalLexeme() {
        val source = "A &amp; **e\u0301🙂** and \\*literal\\*"
        val model = MarkdownInlineEditing.parse(source)
        assertEquals("A & e\u0301🙂 and *literal*", model.visible)
        assertEquals(TextRange(2, 7), model.sourceRangeForVisible(TextRange(2, 3)))
        assertEquals("A + **e\u0301🙂** and \\*literal\\*", model.replaceVisible(model.visible.replace("&", "+")))
        val emoji = model.visible.indexOf("🙂")
        assertNull(model.sourceRangeForVisible(TextRange(emoji, emoji + 1)))
    }
    @Test fun multiBacktickCodeUsesSharedSemanticsAndSafeToggle() {
        val source = "Before `` **bold** ` tick `` after"
        val model = MarkdownInlineEditing.parse(source)
        assertEquals("Before **bold** ` tick after", model.visible)
        val code = model.marks.single { it.kind == InlineMarkKind.CODE }
        assertEquals(TextRange(7, 22), code.range)
        val toggled = model.wrap(code.range, InlineMarkKind.CODE)!!
        assertEquals(model.visible, MarkdownInlineEditing.parse(toggled).visible)
        assertTrue(MarkdownInlineEditing.parse(toggled).marks.isEmpty())
    }
    @Test fun singleTildeGfmMarkTogglesWithoutLosingText() {
        val model = MarkdownInlineEditing.parse("before ~word~ after")
        assertEquals("before word after", model.visible)
        val mark = model.marks.single { it.kind == InlineMarkKind.STRIKETHROUGH }
        assertEquals("before word after", model.wrap(mark.range, InlineMarkKind.STRIKETHROUGH))
    }
    @Test fun hostWikiTitlesAreOpaqueToRustAndExcludedInsideCode() {
        val source = "**[[a * title]]** and `[[code]]` and \\[[escaped]]"
        val model = MarkdownInlineEditing.parse(source, true)
        assertEquals("a * title and [[code]] and [[escaped]]", model.visible)
        assertEquals(1, model.marks.count { it.kind == InlineMarkKind.WIKILINK })
        assertTrue(model.marks.any { it.kind == InlineMarkKind.BOLD && it.range == TextRange(0, 9) })
    }
    @Test fun hostBlockPluginsKeepRawBoundaryWhileStandardGrammarStillUsesRust() {
        val count = counter()
        val before = count.get()
        val source = "Before **bold**\r\n\r\n:::note Note\r\nBody\r\n\r\n- list\r\n:::\r\n\r\nAfter"
        val plugins = ParserPluginRegistry().apply { register(AdmonitionPlugin()) }
        val document = MarkdownDocumentCodec.parse(source, plugins)
        assertTrue(count.get() > before)
        assertEquals(listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.RAW, MarkdownBlockKind.PARAGRAPH), document.blocks.map { it.kind })
        assertEquals(":::note Note\r\nBody\r\n\r\n- list\r\n:::", document.blocks[1].source)
        assertEquals(source, document.toMarkdown())
    }
    @Test fun documentRangesComeFromRustAndKeepSourceTrivia() {
        val count = counter()
        val before = count.get()
        val source = "\r\n# 中文🙂\r\n\r\n| A | B |\r\n| --- | --- |\r\n| x | y |\r\n\r\n- one\r\n- two\r\n\r\nTail  \r\n"
        val document = MarkdownDocumentCodec.parse(source)
        assertTrue(count.get() > before)
        assertEquals(source, document.toMarkdown())
        assertEquals(listOf(MarkdownBlockKind.HEADING, MarkdownBlockKind.TABLE, MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.PARAGRAPH), document.blocks.map { it.kind })
        document.blocks.forEach { assertEquals(source.substring(it.range.min, it.range.max), it.source) }
        assertEquals("Tail  ", document.blocks.last().source)
        val core = NativeMarkdownASTParser().parse(source)
        assertEquals(core.children.map { it.sourceRange.offset }, document.blocks.map { it.range.min })
    }
}
