package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.style.BaselineShift
import org.junit.Assert.*
import org.junit.Test

class InlineFormattingParityTest {
    private fun registry() = ParserPluginRegistry().apply {
        register(HighlightPlugin()); register(SuperscriptPlugin()); register(SubscriptPlugin())
    }
    @Test fun formattingIsOptInAndUsesLocalStyles() {
        val source = "==hello world== x^2^ H~2~O ~~gone~~"
        val plugins = registry()
        val node = parseMarkdown(source, plugins, enableCache = false).firstChild!!
        val rendered = inlineRender(node, false, plugins = plugins).text
        assertEquals("hello world x2 H2O gone", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.baselineShift == BaselineShift.Superscript })
        assertTrue(rendered.spanStyles.any { it.item.baselineShift == BaselineShift.Subscript })
        assertTrue(inlineText(parseMarkdown(source).firstChild!!, false).text.contains("==hello world=="))
    }
    @Test fun codeEscapesMalformedRunsAndWhitespaceKeepTheirMeaning() {
        val plugins = registry()
        val source = "`==code==` \\==escaped== ===bad=== ^two words^ H~x~O ~~strike~~"
        val text = inlineText(parseMarkdown(source, plugins, enableCache = false).firstChild!!, false, plugins).text
        assertEquals("==code== ==escaped== ===bad=== ^two words^ HxO strike", text)
        assertNull(HighlightPlugin().parse("==bad`code`==", 0))
        assertNull(SubscriptPlugin().parse("~2~", 0))
        assertNull(SuperscriptPlugin().parse("^bad\nline^", 0))
    }
    @Test fun smallHtmlRetainsSurroundingBoldAndLocalStyle() {
        val node = parseMarkdown("**bold <small>small</small>**", enableHtml = true).firstChild!!
        val rendered = inlineRender(node, true, MarkdownStyleSheet.default().copy(
            smallStyle = androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Red))).text
        assertEquals("bold small", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color.Red })
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }
}
