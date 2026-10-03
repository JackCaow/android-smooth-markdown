package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsTest {
    @Test fun closedDetailsParseSummaryAndMarkdownBody() {
        val details = parseMarkdown(
            "<details>\n<summary>Click **here**</summary>\n" +
                "\n- Item 1\n- Item 2\n\n**bold** body\n</details>",
        ).firstChild as DetailsNode
        assertFalse(details.isOpen)
        assertEquals("Click here", inlineText(details.summary.single() as Paragraph, false).text)
        assertTrue(details.body.any { it is BulletList })
        assertTrue(details.body.any { it is Paragraph && inlineText(it, false).text == "bold body" })
    }

    @Test fun openAndEmptyDetailsKeepFollowingBlocks() {
        val document = parseMarkdown(
            "<details open>\n<summary>Open</summary>\n</details>\n\n" +
                "<details>\n<summary>Empty</summary>\n</details>\n\nAfter",
        )
        val first = document.firstChild as DetailsNode
        val second = first.next as DetailsNode
        assertTrue(first.isOpen)
        assertTrue(first.body.isEmpty())
        assertFalse(second.isOpen)
        assertTrue(second.body.isEmpty())
        assertTrue(second.next is Paragraph)
    }

    @Test fun bodyCanContainCodeAndQuotes() {
        val details = parseMarkdown(
            "<details>\n<summary>Code</summary>\n\n" +
                "```kotlin\nprintln(1)\n```\n\n> *quoted*\n</details>",
        ).firstChild as DetailsNode
        assertTrue(details.body.any { it is FencedCodeBlock })
        assertTrue(details.body.any { it is com.jackcaow.smoothmarkdown.ast.BlockQuote })
    }

    @Test fun detailsSyntaxInsideFenceStaysCode() {
        assertTrue(parseMarkdown("```html\n<details>\n<summary>x</summary>\n</details>\n```").firstChild is FencedCodeBlock)
    }

    @Test fun summaryUsesInlineParsingEvenForBlockMarkers() {
        val details = parseMarkdown("<details>\n<summary># **Title**</summary>\nBody\n</details>").firstChild as DetailsNode
        val summary = details.summary.single() as Paragraph
        assertEquals("# Title", inlineText(summary, false).text)
    }

    @Test fun summaryKeepsSafeLinksAndInlineImageActions() {
        val details = parseMarkdown(
            "<details>\n<summary>Read [guide](https://example.com/guide) " +
                "![icon](https://example.com/icon.png \"Icon title\") " +
                "[unsafe](javascript:alert(1))</summary>\nBody\n</details>",
        ).firstChild as DetailsNode
        val render = inlineRender(details.summary.single() as Paragraph, enableHtml = false)
        assertEquals("https://example.com/guide", safeLinkAt(render.text, render.text.text.indexOf("guide")))
        assertEquals(null, safeLinkAt(render.text, render.text.text.indexOf("unsafe")))
        assertEquals("icon", render.images.values.single().alt)
        assertEquals("Icon title", render.images.values.single().title)

        val links = mutableListOf<String>()
        var expansions = 0
        dispatchTextTap(render.text, render.text.text.indexOf("guide"), { links += it }, { expansions++ })
        assertEquals(listOf("https://example.com/guide"), links)
        assertEquals(0, expansions)
        dispatchTextTap(render.text, render.text.text.indexOf("Read"), { links += it }, { expansions++ })
        dispatchTextTap(render.text, render.text.text.indexOf("unsafe"), { links += it }, { expansions++ })
        assertEquals(2, expansions)
        assertEquals(1, links.size)
    }

    @Test fun summaryHtmlImageRequiresOptInAndSafeSource() {
        val details = parseMarkdown(
            "<details>\n<summary><img src='https://example.com/icon.png' alt='Icon'> " +
                "<img src='javascript:alert(1)' alt='Unsafe'></summary>\nBody\n</details>",
        ).firstChild as DetailsNode
        val paragraph = details.summary.single() as Paragraph
        assertTrue(inlineRender(paragraph, enableHtml = false).images.isEmpty())
        val images = inlineRender(paragraph, enableHtml = true).images.values
        assertEquals(listOf("Icon"), images.map { it.alt })
    }
    @Test fun sameLineDisclosurePreservesNestedBodyAndFollowingText() {
        val document = parseMarkdown("<details open><summary>**Title**</summary>before <details><summary>Inner</summary>inside</details> after</details>tail", enableHtml = true)
        val details = document.firstChild as DetailsNode
        assertTrue(details.isOpen)
        assertEquals("Title", inlineText(details.summary.single(), true).text)
        assertTrue(details.body.any { it is DetailsNode })
        assertEquals("tail", inlineText(details.next!!, true).text)
    }

    @Test fun inlineListDisclosureKeepsListStructureAndCodeClosingTag() {
        val document = parseMarkdown("- before <details><summary>Info</summary>`</details>` body</details> after", enableHtml = true)
        val item = document.firstChild!!.firstChild!!
        assertTrue(document.firstChild is BulletList)
        val details = item.children().filterIsInstance<DetailsNode>().single()
        assertEquals("`</details>` body", details.bodySource)
        assertEquals("before ", inlineText(item.firstChild!!, true).text)
        assertEquals(" after", inlineText(details.next!!, true).text)
    }

    @Test fun sameLineHtmlRequiresOptInAndUnmatchedBacktickRemainsLiteral() {
        val source = "<details><summary>Info</summary>unmatched ` body</details>"
        assertFalse(parseMarkdown(source).firstChild is DetailsNode)
        val details = parseMarkdown(source, enableHtml = true).firstChild as DetailsNode
        assertEquals("unmatched ` body", details.bodySource)
    }
}
