package com.jackcaow.smoothmarkdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Proves the packaged NDK library actually loads in Android, with no silent Kotlin fallback. */
@RunWith(AndroidJUnit4::class)
class RustParserRuntimeTest {
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
}
