package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.text.font.FontWeight

class SafeHtmlTest {
    @Test fun lexesBoundedTagsAndKeepsFirstAttribute() {
        val tag = SafeHtml.lexTag("<IMG src='https://x/a.png' ALT=first alt=second loading>")!!
        assertEquals("img", tag.name)
        assertEquals("https://x/a.png", tag.attributes["src"])
        assertEquals("first", tag.attributes["alt"])
        assertEquals("", tag.attributes["loading"])
        assertNull(SafeHtml.lexTag("<img src='unterminated>"))
        assertNull(SafeHtml.lexTag("<a " + "x".repeat(513) + ">"))
    }

    @Test fun rejectsObfuscatedSchemesAndDistinguishesImages() {
        assertTrue(SafeHtml.isSafeLink("#section"))
        assertTrue(SafeHtml.isSafeLink("//cdn.example.com/a"))
        assertFalse(SafeHtml.isSafeLink("java\tscript:alert(1)"))
        assertFalse(SafeHtml.isSafeLink("data:text/plain,x"))
        assertTrue(SafeHtml.isSafeImageSource("assets/icon.png"))
        assertFalse(SafeHtml.isSafeImageSource("//cdn.example.com/a.png"))
        assertFalse(SafeHtml.isSafeImageSource("mailto:a@b.com"))
    }

    @Test fun acceptsOnlyBoundedStyles() {
        assertEquals(0xFFFF0000.toInt(), SafeHtml.color("#f00"))
        assertEquals(0xFFFF0000.toInt(), SafeHtml.color("red"))
        assertNull(SafeHtml.color("#ff000080"))
        assertEquals(24f, SafeHtml.fontSize("18pt"))
        assertNull(SafeHtml.fontSize("200px"))
        assertEquals(16f, SafeHtml.legacyFontSize("3"))
        assertEquals("red", SafeHtml.cssDeclarations("color:red; position:fixed; color:blue")["color"])
    }

    @Test fun inlineHtmlIsOptInAndUnsafeLinksLoseTheirAction() {
        val paragraph = parseMarkdown("a <b>bold</b> <a href='javascript:alert(1)'>unsafe</a><br>end").firstChild!!
        val defaultText = inlineText(paragraph, enableHtml = false)
        assertTrue(defaultText.text.contains("<b>"))
        val rendered = inlineText(paragraph, enableHtml = true)
        assertEquals("a bold unsafe\nend", rendered.text)
        assertTrue(rendered.spanStyles.any { span ->
            span.item.fontWeight == FontWeight.Bold && rendered.text.substring(span.start, span.end) == "bold"
        })
        assertTrue(rendered.getStringAnnotations("url", 0, rendered.length).isEmpty())
    }

    @Test fun withholdsOnlyPartialTagsOutsideCode() {
        assertEquals("lead ", SafeHtml.safeRenderPrefix("lead <font colo"))
        assertEquals("Use `List<T` here. ", SafeHtml.safeRenderPrefix("Use `List<T` here. <font colo"))
        assertEquals("Use `List<T` here.", SafeHtml.safeRenderPrefix("Use `List<T` here."))
        assertEquals("```dart\nList<T items;\n```", SafeHtml.safeRenderPrefix("```dart\nList<T items;\n```"))
        assertEquals("count 5 <3", SafeHtml.safeRenderPrefix("count 5 <3"))
    }

    @Test fun parsesNestedWhitelistedBlocksAndTrailingSibling() {
        val block = SafeHtml.parseBlock("<div align='center'>outer <div>inner</div> tail</div> after")
        assertEquals(SafeHtml.Block.Container("div", "outer <div>inner</div> tail", "center", " after"), block)
        assertEquals(SafeHtml.Block.Rule, SafeHtml.parseBlock("<hr>"))
        assertNull(SafeHtml.parseBlock("<script>alert(1)</script>"))
    }

    @Test fun htmlImageValidatesSourceAndDimensions() {
        val image = SafeHtml.imageTag("<img src='https://x/a.png' alt='pic' width='64' height='32px'>")!!
        assertEquals("pic", image.alt)
        assertEquals(64f, image.width)
        assertEquals(32f, image.height)
        assertNull(SafeHtml.imageTag("<img src='javascript:alert(1)' alt='unsafe'>"))
        assertEquals("unsafe", SafeHtml.imageAlt("<img src='javascript:alert(1)' alt='unsafe'>"))
        assertNull(SafeHtml.dimension("100%"))
        assertNull(SafeHtml.dimension("10001"))
    }
}
