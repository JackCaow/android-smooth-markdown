package com.jackcaow.smoothmarkdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.NativeCustomInlineMatch
import com.jackcaow.smoothmarkdown.nativeparser.SourceRange
import com.jackcaow.smoothmarkdown.editor.MarkdownInlineEditing
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.Text
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Proves the packaged NDK library actually loads in Android, with no silent Kotlin fallback. */
@RunWith(AndroidJUnit4::class)
class RustParserRuntimeTest {
    @Test fun packagedStreamSessionRetainsBlocksAndInvalidatesLateReferences() {
        StreamingMarkdownSession().use { session ->
            val source = "# 中文🙂\n\nFirst [target][x]\n\nSecond\n\nTail"
            val first = session.parse(source).document.children().toList()
            val next = session.parse(source + " grows\n\nMore")
            assertTrue("New packaged streaming JNI entry points must execute", session.retainedBlockCount > 0)
            assertSame(first.first(), next.document.firstChild)
            assertSame(next.document, next.document.firstChild?.parent)
            val resolved = session.parse(next.source + "\n\n[x]: /resolved\n")
            assertEquals(0, session.retainedBlockCount)
            assertEquals("/resolved", resolved.document.descendants().filterIsInstance<com.jackcaow.smoothmarkdown.ast.Link>().single().destination)
        }
    }
    @Test fun packagedNativeParserRunsForCoreAndReaderUnicodeExtensions() {
        val bridge = Class.forName("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
        val instance = bridge.getField("INSTANCE").get(null)
        assertTrue("Packaged Rust JNI library must load on Android", bridge.getMethod("getAvailable").invoke(instance) as Boolean)
        val counter = bridge.getDeclaredField("successfulParses").apply { isAccessible = true }.get(null) as AtomicLong
        val source = "# 中文🙂e\u0301\r\n\r\nText \$x+y\$ [^note]\n\n[^note]: Native **footnote**\n\n\$\$z^2\$\$"
        var before = counter.get()
        val root = NativeMarkdownASTParser().parse(source)
        assertTrue("Core must execute Rust, not fallback", counter.get() > before)
        fun check(node: NativeMarkdownNode) {
            assertEquals(source.substring(node.sourceRange.offset, node.sourceRange.end), node.source)
            node.children.forEach(::check)
        }
        check(root)
        before = counter.get()
        val document = NativeMarkdownParser(enableGFM = true, enableExtensions = true).parse(source)
        assertTrue("Default Reader extensions must execute Rust on Android", counter.get() > before)
        assertTrue(document.descendants().any { it is InlineMathNode && it.latex == "x+y" })
        assertTrue(document.descendants().any { it is BlockMathNode && it.latex == "z^2" })
        assertTrue(document.descendants().any { it is FootnoteDefinitionNode && it.label == "note" })
    }
    @Test fun packagedHooksEditorAndMutableHTMLShareRustOnAndroid() {
        val bridge = Class.forName("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
        val count = bridge.getDeclaredField("successfulParses").apply { isAccessible = true }.get(null) as AtomicLong
        val renders = bridge.getDeclaredField("successfulHtmlRenders").apply { isAccessible = true }.get(null) as AtomicLong
        val payload = Any()
        val before = count.get()
        val source = "> 中文🙂 @x\n> next"
        val tree = NativeMarkdownASTParser(customInline = { text, index, offset ->
            if (text.startsWith("@x", index)) NativeCustomInlineMatch(
                NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, "@x", SourceRange(offset, 2), payload = payload), 2) else null
        }).parse(source)
        assertTrue(count.get() > before)
        assertSame(payload, tree.children.single().children.single().children.last { it.payload === payload }.payload)
        val editorBefore = count.get()
        assertEquals("# 中文🙂", MarkdownInlineEditing.parse("# **中文🙂**").visible)
        assertTrue(count.get() > editorBefore)
        val document = MarkdownCoreParser().parse("# Before")
        val heading = document.firstChild as Heading
        (heading.firstChild as Text).literal = "Changed<🙂>"
        val htmlBefore = renders.get()
        assertEquals("<h1>Changed&lt;🙂&gt;</h1>\n", NativeMarkdownHTMLSerializer().render(document))
        assertTrue(renders.get() > htmlBefore)
    }

}
