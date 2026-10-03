package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import org.junit.Assert.*
import org.junit.Test

class HtmlDetailsIndependentProbeTest {
    @Test fun malformedEarlierDisclosureDoesNotHideLaterValidDisclosure() {
        val document = parseMarkdown("before <details invalid>bad</details> <details><summary>Good</summary>ok</details> after", enableHtml = true, enableCache = false)
        assertTrue("A valid later disclosure must still render", document.children().any { it is DetailsNode })
    }
    @Test fun repeatedDisclosureAndHtmlCodeClosersRetainTail() {
        val document = parseMarkdown("before <details><summary>One</summary><code></details></code>body</details> mid <details><summary>Two</summary>second</details> after", enableHtml = true, enableCache = false)
        val nodes = document.children().toList()
        assertEquals(2, nodes.count { it is DetailsNode })
        assertEquals(" after", inlineText(nodes.last(), true).text)
        val first = nodes.filterIsInstance<DetailsNode>().first()
        assertEquals("<code></details></code>body", first.bodySource)
    }
    @Test fun pairedRunDoesNotConsumeLaterIndependentCloser() {
        val document = parseMarkdown("before <details><summary>One</summary>literal `</details>` body</details> after", enableHtml = true, enableCache = false)
        val first = document.children().filterIsInstance<DetailsNode>().first()
        assertEquals("literal `</details>` body", first.bodySource)
    }
    @Test fun htmlSummaryCodeIsLiteral() {
        val document = parseMarkdown("<details><summary><code>**literal**</code></summary>body</details>", enableHtml = true, enableCache = false)
        val details = document.firstChild as DetailsNode
        assertEquals("**literal**", inlineText(details.summary.single(), true).text)
    }
    @Test fun inlineSummaryPreservesFootnoteProjectionLikeBlockSummary() {
        val inline = parseMarkdown("<details><summary>Info [^n]</summary>body</details>", enableHtml = true, enableCache = false).firstChild as DetailsNode
        val block = parseMarkdown("<details>\n<summary>Info [^n]</summary>\nbody\n</details>", enableHtml = true, enableCache = false).firstChild as DetailsNode
        assertEquals(inlineText(block.summary.single(), true).text, inlineText(inline.summary.single(), true).text)
    }

}
