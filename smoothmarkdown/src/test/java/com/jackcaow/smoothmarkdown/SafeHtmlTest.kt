package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.commonmark.node.Paragraph

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

    @Test fun htmlSubscriptAndSuperscriptUseFlutterRelativeSizeAndAllowOverrides() {
        val paragraph = parseMarkdown("H<sub>2</sub>O and x<sup>2</sup>", enableHtml = true).firstChild!!
        val default = inlineText(paragraph, enableHtml = true)
        assertEquals("H2O and x2", default.text)
        val sub = default.spanStyles.single { default.text.substring(it.start, it.end) == "2" && it.start == 1 }.item
        val sup = default.spanStyles.single { default.text.substring(it.start, it.end) == "2" && it.start > 1 }.item
        assertEquals(0.75.em, sub.fontSize)
        assertEquals(BaselineShift.Subscript, sub.baselineShift)
        assertEquals(0.75.em, sup.fontSize)
        assertEquals(BaselineShift.Superscript, sup.baselineShift)

        val customized = inlineRender(paragraph, enableHtml = true, styleSheet = MarkdownStyleSheet(
            subscriptStyle = SpanStyle(color = Color.Red, fontSize = 9.sp),
            superscriptStyle = SpanStyle(color = Color.Blue, fontSize = 10.sp),
        )).text
        val customSub = customized.spanStyles.single { it.start == 1 }.item
        val customSup = customized.spanStyles.single { it.start > 1 }.item
        assertEquals(9.sp, customSub.fontSize)
        assertEquals(Color.Red, customSub.color)
        assertEquals(BaselineShift.Subscript, customSub.baselineShift)
        assertEquals(10.sp, customSup.fontSize)
        assertEquals(Color.Blue, customSup.color)
        assertEquals(BaselineShift.Superscript, customSup.baselineShift)
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

    @Test fun mixedInlineImagesRemainWidgetsWithStylesAndLinks() {
        val paragraph = parseMarkdown(
            "before **bold** ![Markdown](assets/icon.png) [link](https://example.com) " +
                "<img src='https://example.com/logo.png' alt='HTML' width='64'> after",
        ).firstChild as Paragraph
        val enabled = inlineRender(paragraph, enableHtml = true)
        assertEquals(2, enabled.images.size)
        assertEquals(listOf("Markdown", "HTML"), enabled.images.values.map { it.alt })
        assertEquals(64f, enabled.images.values.last().width)
        assertTrue(enabled.text.text.contains("before bold"))
        assertTrue(enabled.text.text.contains("after"))
        assertFalse(enabled.text.text.contains("<img"))
        assertTrue(enabled.text.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && enabled.text.text.substring(it.start, it.end) == "bold"
        })
        assertEquals("https://example.com", enabled.text.getStringAnnotations("url", 0, enabled.text.length).single().item)

        val disabled = inlineRender(paragraph, enableHtml = false)
        assertEquals(1, disabled.images.size)
        assertTrue(disabled.text.text.contains("<img"))
    }

    @Test fun unsafeInlineImagesKeepAltWithoutEmbedding() {
        val paragraph = parseMarkdown(
            "safe <img src='javascript:alert(1)' alt='unsafe'> end ![bad](file:///etc/passwd)",
        ).firstChild as Paragraph
        val rendered = inlineRender(paragraph, enableHtml = true)
        assertTrue(rendered.images.isEmpty())
        assertTrue(rendered.text.text.contains("unsafe"))
        assertTrue(rendered.text.text.contains("bad"))
    }
}
