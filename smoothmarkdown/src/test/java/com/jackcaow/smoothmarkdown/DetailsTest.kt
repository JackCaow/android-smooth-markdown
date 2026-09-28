package com.jackcaow.smoothmarkdown

import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Paragraph
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
        assertTrue(details.body.any { it is org.commonmark.node.BlockQuote })
    }

    @Test fun detailsSyntaxInsideFenceStaysCode() {
        assertTrue(parseMarkdown("```html\n<details>\n<summary>x</summary>\n</details>\n```").firstChild is FencedCodeBlock)
    }

    @Test fun summaryUsesInlineParsingEvenForBlockMarkers() {
        val details = parseMarkdown("<details>\n<summary># **Title**</summary>\nBody\n</details>").firstChild as DetailsNode
        val summary = details.summary.single() as Paragraph
        assertEquals("# Title", inlineText(summary, false).text)
    }
}
