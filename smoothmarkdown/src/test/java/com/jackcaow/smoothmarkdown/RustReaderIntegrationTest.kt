package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Heading
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/** No backend switch leaks into the public Reader API. Inspect its internal diagnostic counter. */
class RustReaderIntegrationTest {
    private fun requireBackend(): AtomicLong {
        val bridge = Class.forName("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
        val instance = bridge.getField("INSTANCE").get(null)
        val available = bridge.getMethod("getAvailable").invoke(instance) as Boolean
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue("Rust backend is required", available)
        assumeTrue("JNI is optional in source-only checkouts", available)
        return bridge.getDeclaredField("successfulParses").apply { isAccessible = true }.get(null) as AtomicLong
    }
    @Test fun defaultReaderActuallyUsesRust() {
        val counter = requireBackend()
        val before = counter.get()
        val document = parseMarkdown("# 中文🙂\n\nA **bold**", enableCache = false)
        assertTrue(counter.get() > before)
        assertTrue(document.firstChild is Heading)
    }
    @Test fun readerNativeExtensionsReallyUseRust() {
        val counter = requireBackend()
        val before = counter.get()
        val document = NativeMarkdownParser(enableGFM = true, enableExtensions = true).parse("A \$x\$ [^n]\n\n[^n]: note")
        assertTrue(counter.get() > before)
        assertTrue(document.descendants().any { it is InlineMathNode })
        assertTrue(document.descendants().any { it is FootnoteDefinitionNode })
    }
    @Test fun missingNativeArtifactRetainsReaderMathAndFootnoteFallback() {
        val urls = linkedSetOf<java.net.URL>()
        System.getProperty("smoothmarkdown.test.classpath")?.split(java.io.File.pathSeparator)
            ?.filter { it.isNotBlank() }?.forEach { urls.add(java.io.File(it).toURI().toURL()) }
        var parent: ClassLoader? = javaClass.classLoader
        while (parent != null) {
            if (parent is java.net.URLClassLoader) urls.addAll(parent.getURLs())
            parent = parent.parent
        }
        assertTrue("Test classpath must support isolated optional-library validation", urls.isNotEmpty())
        val previousLibrary = System.getProperty("smoothmarkdown.rust.library")
        val previousRequired = System.getProperty("smoothmarkdown.rust.required")
        try {
            System.setProperty("smoothmarkdown.rust.library", "/__smoothmarkdown_missing_native_library__.so")
            System.setProperty("smoothmarkdown.rust.required", "false")
            java.net.URLClassLoader(urls.toTypedArray(), null).use { loader ->
                val parserType = loader.loadClass("com.jackcaow.smoothmarkdown.NativeMarkdownParser")
                val registryType = loader.loadClass("com.jackcaow.smoothmarkdown.ParserPluginRegistry")
                val parser = parserType.getConstructor(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, registryType).newInstance(true, true, null)
                val parse = parserType.getMethod("parse", String::class.java)
                fun first(node: Any): Any = node.javaClass.getMethod("getFirstChild").invoke(node)
                val math = first(parse.invoke(parser, "Before\n\$\$x^2\$\$\nAfter"))
                assertEquals("Paragraph", math.javaClass.simpleName)
                val formula = math.javaClass.getMethod("getNext").invoke(math)
                assertEquals("BlockMathNode", formula.javaClass.simpleName)
                assertEquals("x^2", formula.javaClass.getMethod("getLatex").invoke(formula))
                val definition = first(parse.invoke(parser, "[^note]: First **bold**\n    Second\n\n    Third\nAfter"))
                assertEquals("FootnoteDefinitionNode", definition.javaClass.simpleName)
                assertTrue((definition.javaClass.getDeclaredField("rawInlineSource").apply { isAccessible = true }.get(definition) as String).contains("Third"))
                assertEquals("Paragraph", definition.javaClass.getMethod("getNext").invoke(definition).javaClass.simpleName)
                val bridge = loader.loadClass("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
                assertFalse(bridge.getMethod("getAvailable").invoke(bridge.getField("INSTANCE").get(null)) as Boolean)
            }
        } finally {
            if (previousLibrary == null) System.clearProperty("smoothmarkdown.rust.library") else System.setProperty("smoothmarkdown.rust.library", previousLibrary)
            if (previousRequired == null) System.clearProperty("smoothmarkdown.rust.required") else System.setProperty("smoothmarkdown.rust.required", previousRequired)
        }
    }
    @Test fun hostPluginParsingRemainsCompatible() {
        val counter = requireBackend()
        val before = counter.get()
        val registry = ParserPluginRegistry().apply { register(MentionPlugin()) }
        val document = NativeMarkdownParser(enableGFM = true, enableExtensions = true, plugins = registry).parse("Hello @jack")
        assertEquals(before, counter.get())
        assertTrue(document.descendants().any { it is MentionNode })
    }
}
