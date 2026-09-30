package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.IndentedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.ThematicBreak
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.TableBlock
import androidx.compose.runtime.Composable
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
        // A host builder owns its own layout, so it keeps this safe fallback.
        assertEquals(groups, groupSelectableBlocks(blocks, bridgeVisibleNonText = true,
            bridgeBuiltInCode = false))
        // The built-in code text shares one mounted item with its prose neighbors.
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks, bridgeBuiltInCode = true))
    }

    @Test fun indentedCodeCanBridgeAdjacentProseOnlyWhenBuiltInSelectionIsEnabled() {
        val blocks = parseMarkdown("Before.\n\n    first\n    second\n\nAfter.").directChildren()
        assertEquals(listOf(Paragraph::class, IndentedCodeBlock::class, Paragraph::class), blocks.map { it::class })
        assertEquals(listOf(1, 1, 1), groupSelectableBlocks(blocks).map { it.size })
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks, bridgeBuiltInCode = true))
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

    @Test fun blockMathBridgesProseOnlyWithVisibleNonTextSelection() {
        val blocks = parseMarkdown("Before.\n\n\u0024\u0024\nx + y\n\u0024\u0024\n\nAfter.").directChildren()
        assertEquals(listOf(Paragraph::class, BlockMathNode::class, Paragraph::class), blocks.map { it::class })
        assertEquals(listOf(1, 1, 1), groupSelectableBlocks(blocks).map { it.size })
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks, bridgeVisibleNonText = true))
    }

    @Test fun builtInPluginsDeclareTheirContinuousSelectionBehavior() {
        val plugins = ParserPluginRegistry().apply {
            registerBlock(DelimitedBlockPlugin("note"))
            registerBlock(MermaidPlugin())
        }
        val blocks = parseMarkdown("""
            Before.

            :::note
            Text content.
            :::

            ```mermaid
            graph TD
              A --> B
            ```

            After.
        """.trimIndent(), plugins).directChildren()
        assertEquals(listOf(Paragraph::class, DelimitedBlockNode::class, MermaidDiagramNode::class,
            Paragraph::class), blocks.map { it::class })
        assertEquals(MarkdownBlockSelectionMode.NATIVE_TEXT, readerBlockSelectionMode(blocks[1], null, plugins))
        assertEquals(MarkdownBlockSelectionMode.NON_TEXT, readerBlockSelectionMode(blocks[2], null, plugins))
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks, bridgeVisibleNonText = true,
            selectionMode = { readerBlockSelectionMode(it, null, plugins) }))
    }

    @Test fun unknownCustomRenderersRemainIsolatedUnlessTheyOptIn() {
        val blocks = parseMarkdown("Before.\n\n![image](https://example.com/i.png)\n\nAfter.").directChildren()
        val imageBuilder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Image
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
        }
        val builders = MarkdownBuilderRegistry().register(Image::class, imageBuilder)
        assertEquals(listOf(1, 1, 1), groupSelectableBlocks(blocks, bridgeVisibleNonText = true,
            selectionMode = { readerBlockSelectionMode(it, builders, null) }).map { it.size })
        val visual = object : MarkdownNodeBuilder by imageBuilder {
            override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NON_TEXT
        }
        builders.register(Image::class, visual)
        assertEquals(listOf(blocks), groupSelectableBlocks(blocks, bridgeVisibleNonText = true,
            selectionMode = { readerBlockSelectionMode(it, builders, null) }))

        val unknownPlugin = object : DelimitedBlockPlugin("opaque") {
            override fun selectionMode(node: PluginBlockNode) = MarkdownBlockSelectionMode.NONE
        }
        val plugins = ParserPluginRegistry().apply { registerBlock(unknownPlugin) }
        val pluginBlocks = parseMarkdown("Before.\n\n:::opaque\ninside\n:::\n\nAfter.", plugins).directChildren()
        assertEquals(listOf(1, 1, 1), groupSelectableBlocks(pluginBlocks,
            selectionMode = { readerBlockSelectionMode(it, null, plugins) }).map { it.size })
    }

    @Test fun detailsBridgeOnlyItsNearestProseNeighbors() {
        val source = """
            Earlier paragraph.

            Before details.

            <details open>
            <summary>Summary</summary>
            Body.
            </details>

            After details.

            Later paragraph.
        """.trimIndent()
        for (enableHtml in listOf(false, true)) {
            val blocks = parseMarkdown(source, enableHtml = enableHtml).directChildren()
            assertEquals(listOf(Paragraph::class, Paragraph::class, DetailsNode::class,
                Paragraph::class, Paragraph::class), blocks.map { it::class })
            assertEquals(listOf(2, 1, 2), groupSelectableBlocks(blocks).map { it.size })
            assertEquals(listOf(1, 3, 1), groupSelectableBlocks(blocks, bridgeDetails = true).map { it.size })
            assertEquals(blocks, groupSelectableBlocks(blocks, bridgeDetails = true).flatten())
        }
    }

    @Test fun detailsAndBuiltInCodeShareTheSameMountedSelectionGroup() {
        val blocks = parseMarkdown("""
            Before.

            ```kotlin
            println("before")
            ```

            <details open>
            <summary>Summary</summary>
            Body.
            </details>

            ```kotlin
            println("after")
            ```

            After.
        """.trimIndent()).directChildren()
        assertEquals(listOf(Paragraph::class, FencedCodeBlock::class, DetailsNode::class,
            FencedCodeBlock::class, Paragraph::class), blocks.map { it::class })
        assertEquals(listOf(1, 3, 1), groupSelectableBlocks(blocks,
            bridgeBuiltInCode = true, bridgeDetails = true).map { it.size })
        assertEquals(blocks, groupSelectableBlocks(blocks,
            bridgeBuiltInCode = true, bridgeDetails = true).flatten())
    }

    private fun Node.directChildren(): List<Node> {
        val result = mutableListOf<Node>()
        var child = firstChild
        while (child != null) { result += child; child = child.next }
        return result
    }
}
