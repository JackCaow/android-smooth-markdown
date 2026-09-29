package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.commonmark.node.Code
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCodeVerbatimTest {
    @Test fun htmlCodeKeepsMarkdownAndLiteralAngleBracketsVerbatim() {
        val paragraph = parseMarkdown("before <code>a<b **c**</code> after", enableHtml = true).firstChild as Paragraph
        val rendered = inlineText(paragraph, enableHtml = true)

        assertEquals("before a<b **c** after", rendered.text)
        val code = paragraph.firstChild.next as Code
        assertEquals("a<b **c**", code.literal)
        assertTrue(rendered.spanStyles.any {
            it.start == 7 && it.end == 16 && it.item.fontFamily == FontFamily.Monospace
        })
        assertFalse(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test fun htmlModeDoesNotContaminateTheDefaultCachedAst() {
        val source = "<code>**strong**</code>"
        val plainBefore = parseMarkdown(source).firstChild as Paragraph
        val html = parseMarkdown(source, enableHtml = true).firstChild as Paragraph
        val plainAfter = parseMarkdown(source).firstChild as Paragraph

        assertEquals("<code>strong</code>", inlineText(plainBefore, enableHtml = false).text)
        assertEquals("**strong**", inlineText(html, enableHtml = true).text)
        assertSame(html, parseMarkdown(source, enableHtml = true).firstChild)
        assertEquals("<code>strong</code>", inlineText(plainAfter, enableHtml = false).text)
    }

    @Test fun multipleAndUnclosedCodeTagsRetainTheirRawContent() {
        val pairs = parseMarkdown("<code>**a**</code> and <code>[b](url)</code>", enableHtml = true).firstChild as Paragraph
        assertEquals("**a** and [b](url)", inlineText(pairs, enableHtml = true).text)

        val unclosed = parseMarkdown("before <code>**still raw**", enableHtml = true).firstChild as Paragraph
        assertEquals("before **still raw**", inlineText(unclosed, enableHtml = true).text)
    }

    @Test fun ordinaryMarkdownCodeIsUnchanged() {
        val paragraph = parseMarkdown("`**literal**` and **bold**", enableHtml = true).firstChild as Paragraph
        assertEquals("**literal** and bold", inlineText(paragraph, enableHtml = true).text)
    }

    @Test fun detailsSummaryAndBodyAlsoKeepHtmlCodeVerbatim() {
        val details = parseMarkdown(
            "<details>\n<summary><code>**title**</code></summary>\n<code>**body**</code>\n</details>",
            enableHtml = true,
        ).firstChild as DetailsNode
        assertEquals("**title**", inlineText(details.summary.single() as Paragraph, enableHtml = true).text)
        assertEquals("**body**", inlineText(details.body.single() as Paragraph, enableHtml = true).text)
        val plain = parseMarkdown("<code>**body**</code>").firstChild as Paragraph
        assertEquals("<code>body</code>", inlineText(plain, enableHtml = false).text)
    }
}
