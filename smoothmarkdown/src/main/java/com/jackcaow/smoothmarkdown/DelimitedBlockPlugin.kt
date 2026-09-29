package com.jackcaow.smoothmarkdown

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.commonmark.node.Node

/** An opt-in, source-backed `:::type info` block. Subclass to supply a richer reader view. */
open class DelimitedBlockPlugin(val type: String, override val priority: Int = 0) : BlockParserPlugin, SourceBlockParserPlugin {
    init {
        require(type.matches(Regex("[A-Za-z][A-Za-z0-9_-]*"))) { "Invalid delimited block type: $type" }
    }

    override val id: String = "delimited:$type"
    override val name: String = "Delimited $type block"

    private val opening = Regex("^ {0,3}:::${Regex.escape(type)}(?:[ \\t]+(.*))?$")
    private val closing = Regex("^ {0,3}:::[ \\t]*$")

    override fun canStart(line: String): Boolean = opening.matches(line.removeSuffix("\r"))

    override fun canParse(line: String, lines: List<String>, index: Int): Boolean = canStart(line)

    override fun parse(lines: List<String>, startIndex: Int): SourceBlockParseResult? {
        if (startIndex !in lines.indices || !canStart(lines[startIndex])) return null
        val closingIndex = (startIndex + 1 until lines.size).firstOrNull { isClosingLine(lines[it]) }
        return SourceBlockParseResult((closingIndex ?: lines.lastIndex) - startIndex + 1)
    }

    override fun createNode(openingLine: String): PluginBlockNode? {
        val match = opening.matchEntire(openingLine.removeSuffix("\r")) ?: return null
        return DelimitedBlockNode(type, match.groupValues[1])
    }

    override fun isClosingLine(line: String): Boolean = closing.matches(line.removeSuffix("\r"))

    override fun complete(node: PluginBlockNode, contentLines: List<String>) {
        (node as DelimitedBlockNode).contentLines = contentLines.toList()
    }

    override fun documentText(node: PluginBlockNode): String? =
        (node as? DelimitedBlockNode)?.contentLines?.joinToString("\n")

    override fun selectionMode(node: PluginBlockNode) = MarkdownBlockSelectionMode.NATIVE_TEXT

    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        Text((node as DelimitedBlockNode).contentLines.joinToString("\n"))
    }
}

/** Parsed metadata is for rendering; editors retain the exact original Markdown source. */
class DelimitedBlockNode(val type: String, val info: String) : PluginBlockNode() {
    var contentLines: List<String> = emptyList()
        internal set
}
