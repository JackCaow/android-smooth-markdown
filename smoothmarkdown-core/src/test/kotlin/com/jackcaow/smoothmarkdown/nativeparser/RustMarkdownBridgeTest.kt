package com.jackcaow.smoothmarkdown.nativeparser

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class RustMarkdownBridgeTest {
    @Test fun nativeParserActuallyRunsAndKeepsUnicodeSourceRanges() {
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue("Rust backend is required", RustMarkdownBridge.available)
        assumeTrue("Native library is optional for source-only checkouts", RustMarkdownBridge.available)
        val source = "# 中文🙂e\u0301\r\n\r\nA **粗体** [链接](https://example.test/🙂)"
        val before = RustMarkdownBridge.successfulParseCount
        val root = NativeMarkdownASTParser().parse(source)
        assertTrue(RustMarkdownBridge.successfulParseCount > before)
        assertEquals(SourceRange(0, source.length), root.sourceRange)
        assertEquals(source, root.source)
        fun check(node: NativeMarkdownNode) {
            assertTrue(node.sourceRange.offset >= 0 && node.sourceRange.end <= source.length)
            assertEquals(source.substring(node.sourceRange.offset, node.sourceRange.end), node.source)
            node.children.forEach(::check)
        }
        check(root)
    }
    @Test fun builtInMathAndFootnotesComeThroughNativeBatch() {
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue(RustMarkdownBridge.available)
        assumeTrue(RustMarkdownBridge.available)
        val root = NativeMarkdownASTParser().parse("A \$x\$ [^n]\n\n[^n]: note\n\n\$\$\ny\n\$\$")
        fun kinds(node: NativeMarkdownNode): List<NativeMarkdownNode.Kind> = listOf(node.kind) + node.children.flatMap(::kinds)
        val parsed = kinds(root)
        assertTrue(parsed.contains(NativeMarkdownNode.Kind.INLINE_MATH))
        assertTrue(parsed.contains(NativeMarkdownNode.Kind.FOOTNOTE_REFERENCE))
        assertTrue(parsed.contains(NativeMarkdownNode.Kind.FOOTNOTE_DEFINITION))
        assertTrue(parsed.contains(NativeMarkdownNode.Kind.BLOCK_MATH))
    }
    @Test fun wireRetainsSupplementaryCharactersAndRejectsTruncation() {
        val source = "中文🙂e\u0301"
        val output = ByteArrayOutputStream()
        fun word(value: Long) { repeat(4) { output.write((value shr (it * 8)).toInt() and 255) } }
        fun string(value: String?) {
            if (value == null) { word(0xffffffffL); return }
            word(value.length.toLong()); value.forEach { output.write(it.code and 255); output.write(it.code shr 8) }
        }
        fun record(kind: Int, children: Int, literal: String? = null) {
            word(kind.toLong()); word(0); word(source.length.toLong()); word(0); word(0); word(0); word(children.toLong())
            string(source); string(""); string(""); string(null); string(literal); string(""); word(0)
        }
        output.write(byteArrayOf(83, 77, 82, 49)); word(3)
        record(0, 1); record(1, 1); record(12, 0, source)
        val bytes = output.toByteArray()
        val root = RustMarkdownWire.decode(bytes, source)
        assertEquals(source, root.children.single().children.single().literalText)
        try { RustMarkdownWire.decode(bytes.copyOf(bytes.size - 1), source); fail("Truncated wire accepted") }
        catch (_: IllegalArgumentException) { }
        val invalidRange = bytes.copyOf().also { it[12] = 127 }
        try { RustMarkdownWire.decode(invalidRange, source); fail("Out-of-bounds source span accepted") }
        catch (_: IllegalArgumentException) { }
        try { RustMarkdownWire.decode(bytes + byteArrayOf(0), source); fail("Trailing record accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun optionalEndToEndFFIAndHostDecodeBenchmark() {
        assumeTrue("Set SMOOTH_MARKDOWN_BENCH=1 for observational FFI plus host AST decoding timings", System.getenv("SMOOTH_MARKDOWN_BENCH") == "1")
        assertTrue(RustMarkdownBridge.available)
        val sample = "# Heading 中文🙂\n\nParagraph with **bold** and [link](https://example.test).\n\n- first\n- second\n\n"
        val source = sample.repeat(maxOf(1, 100_000 / sample.toByteArray(Charsets.UTF_8).size))
        val before = RustMarkdownBridge.successfulParseCount
        val started = System.nanoTime()
        repeat(5) { assertEquals(source, NativeMarkdownASTParser().parse(source).source) }
        assertEquals(5L, RustMarkdownBridge.successfulParseCount - before)
        println("Rust host end-to-end: bytes=${source.toByteArray(Charsets.UTF_8).size}, parses=5, ms=${(System.nanoTime() - started) / 1_000_000.0}")
    }
    @Test fun tightAndLooseListItemsKeepExistingPublicShape() {
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue(RustMarkdownBridge.available)
        assumeTrue(RustMarkdownBridge.available)
        val tight = NativeMarkdownASTParser().parse("- one\n- two\n").children.single()
        assertTrue(tight.isTight == true)
        assertEquals(NativeMarkdownNode.Kind.TEXT, tight.children.first().children.first().kind)
        val loose = NativeMarkdownASTParser().parse("- one\n\n- two\n").children.single()
        assertTrue(loose.isTight == false)
        assertEquals(NativeMarkdownNode.Kind.PARAGRAPH, loose.children.first().children.first().kind)
    }
    @Test fun sourceSentinelKeepsOriginalUnpairedSurrogates() {
        val source = "中文🙂" + '\uD800'
        val output = ByteArrayOutputStream()
        fun word(value: Long) { repeat(4) { output.write((value shr (it * 8)).toInt() and 255) } }
        output.write(byteArrayOf(83, 77, 82, 49)); word(1)
        word(0); word(0); word(source.length.toLong()); word(0); word(0); word(0); word(0)
        word(0xfffffffeL); word(0); word(0); word(0xffffffffL); word(0xffffffffL); word(0); word(0)
        assertEquals(source, RustMarkdownWire.decode(output.toByteArray(), source).source)
    }
    @Test fun suppliedHostHooksRemainOnFallbackPath() {
        var calls = 0
        val root = NativeMarkdownASTParser(customInline = { source, index, offset ->
            if (source[index] == '@') {
                calls++
                NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, "@", SourceRange(offset, 1)), 1)
            } else null
        }).parse("@ custom")
        assertTrue(calls > 0)
        assertTrue(root.children.single().children.any { it.kind == NativeMarkdownNode.Kind.RAW })
    }
}
