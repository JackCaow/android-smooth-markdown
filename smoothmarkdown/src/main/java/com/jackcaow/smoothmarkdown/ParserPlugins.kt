package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.parser.SourceLine
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/** Opt-in extension point. Higher priority plugins are tried first; equal priorities keep registration order. */
interface ParserPlugin {
    val id: String
    val name: String
    val priority: Int get() = 0
}

/** A custom inline AST node. Each plugin owns its node type and its presentation. */
open class PluginInlineNode : CustomNode()

data class InlineParseResult(val node: PluginInlineNode, val consumed: Int)
data class InlinePluginPresentation(val text: String, val style: SpanStyle = SpanStyle())

interface InlineParserPlugin : ParserPlugin {
    /** One character that starts this syntax. */
    val triggerCharacter: Char
    fun canParse(text: String, index: Int): Boolean
    /** Return null to let the next plugin or CommonMark handle the input. */
    fun parse(text: String, startIndex: Int): InlineParseResult?
    fun render(node: PluginInlineNode): InlinePluginPresentation?
}

/** A custom block AST node. */
open class PluginBlockNode : CustomBlock() {
    internal var pluginId: String = ""
}

interface BlockParserPlugin : ParserPlugin {
    fun canStart(line: String): Boolean = false
    /** Return null to let the next plugin or CommonMark handle the line. */
    fun createNode(openingLine: String): PluginBlockNode? = null
    fun isClosingLine(line: String): Boolean = false
    /** Node-aware closing hook for plugins with multiple delimiter styles. */
    fun isClosingLine(node: PluginBlockNode, line: String): Boolean = isClosingLine(line)
    /** Receives content lines, excluding delimiters, after the block is complete. */
    fun complete(node: PluginBlockNode, contentLines: List<String>) = Unit
    /** Converts a CommonMark fenced code block after parsing; return null for ordinary code. */
    fun parseFencedCodeBlock(block: FencedCodeBlock): PluginBlockNode? = null
    /** Text for whole-document copy when [RenderBlock] replaces a block; null means it is unknown. */
    fun documentText(node: PluginBlockNode): String? = null
    @Composable fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit)
}

/** Optional editor source-span companion to [BlockParserPlugin].
 *
 * It sees the whole document like Flutter's block parser contract. A null result, zero
 * consumption, or consumption beyond the remaining lines leaves CommonMark in charge.
 * The editor invokes this only at a top-level CommonMark block boundary.
 */
interface SourceBlockParserPlugin : ParserPlugin {
    fun canParse(line: String, lines: List<String>, index: Int): Boolean
    fun parse(lines: List<String>, startIndex: Int): SourceBlockParseResult?
}

data class SourceBlockParseResult(val linesConsumed: Int)

/** A mutable registry configured before passing it to [SmoothMarkdown]. Plugins are opt-in. */
class ParserPluginRegistry {
    private val blocks = mutableListOf<BlockParserPlugin>()
    private val sourceBlocks = mutableListOf<SourceBlockParserPlugin>()
    private val inlines = mutableListOf<InlineParserPlugin>()
    val blockPlugins: List<BlockParserPlugin> get() = blocks.toList()
    val sourceBlockPlugins: List<SourceBlockParserPlugin> get() = sourceBlocks.toList()
    val inlinePlugins: List<InlineParserPlugin> get() = inlines.toList()
    val inlineTriggerCharacters: Set<Char> get() = inlines.mapTo(linkedSetOf()) { it.triggerCharacter }

    fun register(plugin: ParserPlugin) {
        require(plugin is BlockParserPlugin || plugin is SourceBlockParserPlugin || plugin is InlineParserPlugin) {
            "Unknown plugin type: ${plugin::class.java.name}"
        }
        if (plugin is BlockParserPlugin) registerBlock(plugin)
        if (plugin is SourceBlockParserPlugin) registerSourceBlock(plugin)
        if (plugin is InlineParserPlugin) registerInline(plugin)
    }
    fun registerAll(plugins: Iterable<ParserPlugin>) = plugins.forEach(::register)
    fun registerBlock(plugin: BlockParserPlugin) {
        require(blocks.none { it.id == plugin.id }) { "Duplicate block plugin id: ${plugin.id}" }
        blocks += plugin
        blocks.sortWith(compareByDescending<BlockParserPlugin> { it.priority })
    }
    fun registerSourceBlock(plugin: SourceBlockParserPlugin) {
        require(sourceBlocks.none { it.id == plugin.id }) { "Duplicate source block plugin id: ${plugin.id}" }
        sourceBlocks += plugin
        sourceBlocks.sortWith(compareByDescending<SourceBlockParserPlugin> { it.priority })
    }
    fun registerInline(plugin: InlineParserPlugin) {
        require(inlines.none { it.id == plugin.id }) { "Duplicate inline plugin id: ${plugin.id}" }
        inlines += plugin
        inlines.sortWith(compareByDescending<InlineParserPlugin> { it.priority })
    }
    fun unregisterBlock(id: String): Boolean = blocks.removeAll { it.id == id }
    fun unregisterSourceBlock(id: String): Boolean = sourceBlocks.removeAll { it.id == id }
    fun unregisterInline(id: String): Boolean = inlines.removeAll { it.id == id }
    fun getBlockPlugin(id: String): BlockParserPlugin? = blocks.firstOrNull { it.id == id }
    fun getInlinePlugin(id: String): InlineParserPlugin? = inlines.firstOrNull { it.id == id }
    fun getInlinePluginByTrigger(char: Char): InlineParserPlugin? = inlines.firstOrNull { it.triggerCharacter == char }
    fun isInlineTrigger(char: Char): Boolean = inlines.any { it.triggerCharacter == char }
    fun findInlinePlugins(text: String, index: Int): List<InlineParserPlugin> =
        if (index !in text.indices) emptyList() else inlines.filter { it.triggerCharacter == text[index] && it.canParse(text, index) }
    fun findBlockPlugins(line: String): List<BlockParserPlugin> = blocks.filter { it.canStart(line) }
    fun clear() { blocks.clear(); sourceBlocks.clear(); inlines.clear() }
    fun copy(): ParserPluginRegistry = ParserPluginRegistry().also { it.blocks.addAll(blocks); it.sourceBlocks.addAll(sourceBlocks); it.inlines.addAll(inlines) }

    internal fun renderInline(node: PluginInlineNode): InlinePluginPresentation? =
        inlines.firstNotNullOfOrNull { it.render(node) }
    internal fun blockRenderer(node: PluginBlockNode): BlockParserPlugin? =
        blocks.firstOrNull { it.canRender(node) }
    internal fun transformFencedBlocks(root: Node) {
        fun visit(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                if (child is FencedCodeBlock) {
                    val replacement = blocks.firstNotNullOfOrNull { plugin ->
                        plugin.parseFencedCodeBlock(child)?.also { it.pluginId = plugin.id }
                    }
                    if (replacement != null) {
                        child.insertBefore(replacement)
                        child.unlink()
                    }
                } else visit(child)
                child = next
            }
        }
        visit(root)
    }
}

/** Rendering dispatch is by the plugin that created the node. */
fun BlockParserPlugin.canRender(node: PluginBlockNode): Boolean = node.pluginId == id

internal class PluginBlockParserFactory(private val plugin: BlockParserPlugin) : BlockParserFactory {
    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        val line = state.line.content.toString()
        if (!plugin.canStart(line)) return null
        val node = plugin.createNode(line) ?: return null
        node.pluginId = plugin.id
        return BlockStart.of(PluginBlockParser(plugin, node)).atIndex(line.length)
    }
}

private class PluginBlockParser(private val plugin: BlockParserPlugin, private val node: PluginBlockNode) : AbstractBlockParser() {
    private val content = mutableListOf<String>()
    private var first = true
    private var closed = false
    override fun getBlock(): PluginBlockNode = node
    override fun tryContinue(state: ParserState): BlockContinue? {
        if (closed) return null
        val line = state.line.content.toString()
        if (plugin.isClosingLine(node, line)) closed = true
        return BlockContinue.atIndex(if (closed) line.length else 0)
    }
    override fun addLine(line: SourceLine) {
        if (first) { first = false; return }
        if (!closed) content += line.content.toString()
    }
    override fun closeBlock() = plugin.complete(node, content)
}

internal class PluginInlineParserFactory(private val registry: ParserPluginRegistry) : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = registry.inlineTriggerCharacters
    override fun create(): InlineContentParser = InlineContentParser { state: InlineParserState ->
        val scanner = state.scanner()
        val start = scanner.position()
        val remaining = buildString {
            while (scanner.hasNext()) { append(scanner.peek()); scanner.next() }
        }
        scanner.setPosition(start)
        for (plugin in registry.findInlinePlugins(remaining, 0)) {
            val result = plugin.parse(remaining, 0) ?: continue
            if (result.consumed !in 1..remaining.length) continue
            repeat(result.consumed) { scanner.next() }
            return@InlineContentParser ParsedInline.of(result.node, scanner.position())
        }
        ParsedInline.none()
    }
}
