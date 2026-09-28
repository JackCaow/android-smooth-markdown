package com.jackcaow.smoothmarkdown

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Paragraph
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSelectionGroupingTest {
    @Test fun proseAcrossHeadingParagraphListAndQuoteSharesOneRegion() {
        val blocks = parseMarkdown("""
            # Heading

            First paragraph.

            - A list item
            - Another item

            > Quoted text

            After quote.
        """.trimIndent()).directChildren()
        assertEquals(listOf(Heading::class, Paragraph::class, BulletList::class, BlockQuote::class, Paragraph::class),
            blocks.map { it::class })
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks))
    }

    @Test fun codeKeepsItsOwnSelectionRegionAndProseRegroupsAfterIt() {
        val blocks = parseMarkdown("""
            Before code.

            ```kotlin
            println("code")
            ```

            # After code

            More prose.
        """.trimIndent()).directChildren()
        val groups = groupSelectableBlocks(blocks)
        assertEquals(listOf(1, 1, 2), groups.map { it.size })
        assertTrue(groups[1].single() is FencedCodeBlock)
    }

    private fun Node.directChildren(): List<Node> {
        val result = mutableListOf<Node>()
        var child = firstChild
        while (child != null) { result += child; child = child.next }
        return result
    }
}
