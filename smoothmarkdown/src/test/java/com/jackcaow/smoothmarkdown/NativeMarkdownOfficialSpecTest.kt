package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Markup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class NativeMarkdownOfficialSpecTest {
    private data class Example(val number: String, val markdown: String, val html: String)

    @Test fun commonMarkExactHtml() = verify("commonmark-0.31.2.tsv", 652, false)
    @Test fun gfmExtensionsExactHtml() = verify("gfm-extensions.tsv", 24, true)

    private fun verify(file: String, count: Int, gfm: Boolean) {
        val stream = requireNotNull(javaClass.getResourceAsStream("/native-markdown/$file"))
        val examples = stream.bufferedReader().useLines { lines -> lines.map { line ->
            val fields = line.split('\t')
            Example(fields[0], decode(fields[1]), decode(fields[2]))
        }.toList() }
        assertEquals(count, examples.size)
        val parser = NativeMarkdownParser(enableGFM = gfm)
        val serializer = NativeMarkdownHTMLSerializer()
        val failures = mutableListOf<String>()
        for (example in examples) {
            val tree = parser.parse(example.markdown)
            val actual = serializer.render(tree)
            if (actual != example.html) failures += "Example ${example.number}: ${example.markdown}\nExpected: ${example.html}\nActual: $actual"
            checkRanges(tree, example.markdown.length)
        }
        assertTrue("${failures.size}/${examples.size} failed:\n${failures.take(15).joinToString("\n")}", failures.isEmpty())
    }

    private fun checkRanges(node: Markup, length: Int) {
        for (span in node.sourceSpans) {
            assertTrue(span.inputIndex >= 0)
            assertTrue(span.inputIndex + span.length <= length)
        }
        node.children().forEach { checkRanges(it, length) }
    }

    private fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
}
