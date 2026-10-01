package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class StreamingMarkdownSessionTest {
    private fun requireNative() {
        if (System.getProperty("smoothmarkdown.rust.required") == "true") assertTrue(RustMarkdownBridge.available)
        assumeTrue(RustMarkdownBridge.available)
    }

    /** Compare actual host AST values and UTF16 coordinates, not just exported plain text. */
    private fun signature(node: Node): String {
        val fields = mutableListOf<String>()
        var type: Class<*>? = node.javaClass
        while (type != null && type != Markup::class.java) {
            type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.sortedBy { it.name }.forEach {
                it.isAccessible = true
                val value = it.get(node)
                if (value == null || value is String || value is Number || value is Boolean || value is Char || value is Enum<*>)
                    fields += "${it.name}=$value"
            }
            type = type.superclass
        }
        val nested = if (node is DetailsNode) node.summary.joinToString { signature(it) } + node.body.joinToString { signature(it) } else ""
        return "${node.javaClass.name}[${fields.joinToString()}]${node.sourceSpans}($nested${node.children().joinToString { signature(it) }})"
    }

    @Test fun appendKeepsClosedBlockInstancesAndBundleCompatibleKeys() {
        requireNative()
        StreamingMarkdownSession().use { session ->
            val source = "# Fixed\n\nFirst\n\nSecond\n\nLast"
            val first = session.parse(source)
            val old = first.document.children().toList()
            val updated = session.parse(source + " **growing**\n\nNew")
            assertTrue(session.retainedBlockCount > 0)
            val blocks = updated.document.children().toList()
            assertSame(old.first(), blocks.first())
            assertEquals(first.blockKeys[old.first()], updated.blockKeys[blocks.first()])
            assertSame(updated.document, blocks.first().parent)
            assertEquals(signature(parseMarkdown(updated.source, enableCache = false)), signature(updated.document))
            assertSame(updated, session.parse(updated.source))
        }
    }

    @Test fun streamedPrefixesMatchFreshASTIncludingReferencesAndContainers() {
        requireNative()
        val fixtures = listOf(
            "# 中文🙂\r\n\r\nA **bold** and [link](https://example.test).\r\n\r\nTail",
            "First\n\n- one\n- two\n\n> quote\n> continued\n\n```kotlin\nval x = 1\n```\n",
            "A\n\nB\n\n| head | other |\n| :--- | ---: |\n| a | b |\n",
            "Title\n====\n\nA [future][x]\n\nOther\n\n[x]: /resolved \"Title\"\n",
            "Start\n\n\$x^2\$\n\n\$\$\ny = 2\n\$\$\n\nRef [^a]\n\n[^a]: **note**\n    continued\n",
            "> \$\$\n> x\n> \$\$\n\nTail",
            "- \$\$\n  x\n  \$\$\n\nTail",
            "A \$a \$ and \$ a\$\n\n\$\$x\$\$tail\n",
            "First\n\n<details open>\n<summary>**Summary**</summary>\n\nBody\n</details>\n"
        )
        for (source in fixtures) StreamingMarkdownSession().use { session ->
            var prefix = ""
            source.chunked(7).forEach { chunk ->
                prefix += chunk
                assertEquals("Source: $prefix", signature(parseMarkdown(prefix, enableCache = false)), signature(session.parse(prefix).document))
            }
        }
    }

    @Test fun lateReferenceDefinitionReplacesEarlierUnresolvedContent() {
        requireNative()
        StreamingMarkdownSession().use { session ->
            val prefix = "[future][x]\n\nSecond\n\nThird\n\nTail"
            val first = session.parse(prefix).document.firstChild
            val final = session.parse(prefix + "\n\n[x]: /url\n")
            assertEquals(0, session.retainedBlockCount)
            assertNotSame(first, final.document.firstChild)
            assertEquals("/url", final.document.descendants().filterIsInstance<Link>().first().destination)
        }
    }

    @Test fun editedOrShorterPrefixResetsRetainedBlocks() {
        requireNative()
        StreamingMarkdownSession().use { session ->
            session.parse("First\n\nSecond\n\nThird\n\nLast")
            for (source in listOf("Changed\n\nSecond", "Short", "")) {
                val result = session.parse(source)
                assertEquals(0, session.retainedBlockCount)
                assertEquals(signature(parseMarkdown(source, enableCache = false)), signature(result.document))
            }
        }
    }

    @Test fun mutablePluginRegistryStillReparsesTheSameSource() {
        val plugins = ParserPluginRegistry()
        StreamingMarkdownSession(plugins).use { session ->
            val before = session.parse("Hi @jack").document
            assertFalse(before.descendants().any { it is MentionNode })
            plugins.register(MentionPlugin())
            val after = session.parse("Hi @jack").document
            assertEquals(0, session.retainedBlockCount)
            assertTrue(after.descendants().any { it is MentionNode })
            assertNotSame(before, after)
        }
    }

    @Test fun htmlPostprocessorsKeepWholeDocumentBehavior() {
        StreamingMarkdownSession(enableHtml = true).use { session ->
            for (source in listOf("A\n\n<kbd>Ctrl</kbd>", "A\n\n<kbd>Ctrl</kbd> + <code>x</code>")) {
                assertEquals(signature(parseMarkdown(source, enableCache = false, enableHtml = true)), signature(session.parse(source).document))
                assertEquals(0, session.retainedBlockCount)
            }
        }
    }

    @Test fun pluginCallbacksCanDependOnHostStateWithoutRegistryMutation() {
        var username = "before"
        var invocations = 0
        val registry = ParserPluginRegistry().apply { register(object : InlineParserPlugin {
            override val id = "stateful"
            override val name = "Stateful"
            override val triggerCharacter = '@'
            override fun canParse(text: String, index: Int) = text[index] == '@'
            override fun parse(text: String, startIndex: Int): InlineParseResult {
                invocations++
                return InlineParseResult(MentionNode(username), 1)
            }
            override fun render(node: PluginInlineNode) = InlinePluginPresentation((node as MentionNode).username)
        }) }
        StreamingMarkdownSession(registry).use { session ->
            assertEquals("before", session.parse("@").document.descendants().filterIsInstance<MentionNode>().single().username)
            val version = registry.version
            val before = invocations
            username = "after"
            assertEquals("after", session.parse("@").document.descendants().filterIsInstance<MentionNode>().single().username)
            assertEquals(version, registry.version)
            assertTrue(invocations > before)
        }
    }

    @Test fun missingNativeLibraryKeepsThePureJvmStreamingFallback() {
        val urls = requireNotNull(System.getProperty("smoothmarkdown.test.classpath")).split(java.io.File.pathSeparator)
            .map { java.io.File(it).toURI().toURL() }.toTypedArray()
        val library = System.getProperty("smoothmarkdown.rust.library")
        val required = System.getProperty("smoothmarkdown.rust.required")
        try {
            System.setProperty("smoothmarkdown.rust.library", "/__smoothmarkdown_missing_native_library__.so")
            System.setProperty("smoothmarkdown.rust.required", "false")
            java.net.URLClassLoader(urls, null).use { loader ->
                val type = loader.loadClass("com.jackcaow.smoothmarkdown.StreamingMarkdownSession")
                val registry = loader.loadClass("com.jackcaow.smoothmarkdown.ParserPluginRegistry")
                val session = type.getConstructor(registry, Boolean::class.javaPrimitiveType).newInstance(null, false)
                try {
                    val parse = type.getMethod("parse", String::class.java)
                    for (source in listOf("# Title", "# Title\n\nA **bold** paragraph")) {
                        val snapshot = parse.invoke(session, source)
                        val document = snapshot.javaClass.getMethod("getDocument").invoke(snapshot)
                        val heading = document.javaClass.getMethod("getFirstChild").invoke(document)
                        assertEquals("Heading", heading.javaClass.simpleName)
                        assertEquals(0, type.getMethod("getRetainedBlockCount").invoke(session))
                    }
                    val bridge = loader.loadClass("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
                    assertFalse(bridge.getMethod("getAvailable").invoke(bridge.getField("INSTANCE").get(null)) as Boolean)
                } finally { type.getMethod("close").invoke(session) }
            }
        } finally {
            if (library == null) System.clearProperty("smoothmarkdown.rust.library") else System.setProperty("smoothmarkdown.rust.library", library)
            if (required == null) System.clearProperty("smoothmarkdown.rust.required") else System.setProperty("smoothmarkdown.rust.required", required)
        }
    }
}
