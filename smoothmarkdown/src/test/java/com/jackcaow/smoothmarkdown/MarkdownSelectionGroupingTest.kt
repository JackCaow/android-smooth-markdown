package com.jackcaow.smoothmarkdown

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Paragraph
import org.commonmark.node.Node
import org.commonmark.node.ThematicBreak
import org.commonmark.ext.gfm.tables.TableBlock
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

    @Test fun selectableTextAcrossRuleAndTableStaysInOneMountedLazyItem() {
        val blocks = parseMarkdown("""
            Before.

            ---

            | Left | Right |
            | --- | --- |
            | One | Two |

            After.

            ```kotlin
            println(1)
            ```
        """.trimIndent()).directChildren()
        assertEquals(listOf(Paragraph::class, ThematicBreak::class, TableBlock::class,
            Paragraph::class, FencedCodeBlock::class), blocks.map { it::class })
        assertEquals(listOf(4, 1), groupSelectableBlocks(blocks, bridgeVisibleNonText = true).map { it.size })
        // Ordinary lazy rendering keeps its smaller items for virtualization.
        assertEquals(listOf(1, 1, 1, 1, 1), groupSelectableBlocks(blocks).map { it.size })
    }

    private fun Node.directChildren(): List<Node> {
        val result = mutableListOf<Node>()
        var child = firstChild
        while (child != null) { result += child; child = child.next }
        return result
    }
}
