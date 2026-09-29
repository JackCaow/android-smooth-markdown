package com.jackcaow.smoothmarkdown

import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikilinkTest {
    private val plugins = ParserPluginRegistry().also { it.register(WikilinkPlugin()) }

    @Test fun parsesWholeTargetAndLeavesMalformedOrCodePlain() {
        val paragraph = parseMarkdown("Open [[Daily|today]] and [[Bad]name]] plus `[[Code]]`", plugins).firstChild as Paragraph
        val rendered = inlineText(paragraph, false, plugins)
        assertEquals("Open Daily|today and [[Bad]name]] plus [[Code]]", rendered.text)
        assertEquals(listOf("Daily|today"), rendered.getStringAnnotations("wikilink", 0, rendered.length).map { it.item })
        assertTrue(parseMarkdown("[[ ]]", plugins).firstChild != null)
    }

    @Test fun noteTapReceivesFullTargetWithoutOpeningOrdinaryLink() {
        val paragraph = parseMarkdown("[[Project Plan]] and [web](https://example.com)", plugins).firstChild as Paragraph
        val rendered = inlineText(paragraph, false, plugins)
        val taps = mutableListOf<String>()
        dispatchTextTap(rendered, rendered.text.indexOf("Project") + 2,
            onLinkClick = { taps += "url:$it" }, onPlainTextTap = { taps += "plain" },
            onWikilinkClick = { taps += "wiki:$it" })
        dispatchTextTap(rendered, rendered.text.indexOf("web") + 1,
            onLinkClick = { taps += "url:$it" }, onPlainTextTap = { taps += "plain" },
            onWikilinkClick = { taps += "wiki:$it" })
        assertEquals(listOf("wiki:Project Plan", "url:https://example.com"), taps)
    }
}
