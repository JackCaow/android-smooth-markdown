package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.SpanStyle
import com.jackcaow.smoothmarkdown.ast.CustomBlock
import com.jackcaow.smoothmarkdown.ast.CustomNode
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Node

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
    /** Return null to let the next plugin or the native parser handle the input. */
    fun parse(text: String, startIndex: Int): InlineParseResult?
    fun render(node: PluginInlineNode): InlinePluginPresentation?
}

/** A custom block AST node. */
open class PluginBlockNode : CustomBlock() {
    internal var pluginId: String = ""
}

interface BlockParserPlugin : ParserPlugin {
    fun canStart(line: String): Boolean = false
    /** Return null to let the next plugin or the native parser handle the line. */
    fun createNode(openingLine: String): PluginBlockNode? = null
    fun isClosingLine(line: String): Boolean = false
    /** Node-aware closing hook for plugins with multiple delimiter styles. */
    fun isClosingLine(node: PluginBlockNode, line: String): Boolean = isClosingLine(line)
    /** Receives content lines, excluding delimiters, after the block is complete. */
    fun complete(node: PluginBlockNode, contentLines: List<String>) = Unit
    /** Converts a fenced code block after parsing; return null for ordinary code. */
    fun parseFencedCodeBlock(block: FencedCodeBlock): PluginBlockNode? = null
    /** Text for whole-document copy when [RenderBlock] replaces a block; null means it is unknown. */
    fun documentText(node: PluginBlockNode): String? = null
    /** Explicit native selection contract for this block's [RenderBlock] output. */
    fun selectionMode(node: PluginBlockNode): MarkdownBlockSelectionMode = MarkdownBlockSelectionMode.NONE
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

/** Observable mutable registry. Register/unregister triggers reader and editor updates without replacing the registry.
 * Mutate on the UI thread, or inside a Compose mutable snapshot. Plugins are opt-in. */
class ParserPluginRegistry {
    private val revision = mutableStateOf(0L)
    /** Observable configuration version, including render-only plugin changes. */
    val version: Long get() = revision.value
    private fun changed() { revision.value = revision.value + 1 }
    private val blocks = mutableListOf<BlockParserPlugin>()
    private val sourceBlocks = mutableListOf<SourceBlockParserPlugin>()
    private val inlines = mutableListOf<InlineParserPlugin>()
    val blockPlugins: List<BlockParserPlugin> get() { version; return blocks.toList() }
    val sourceBlockPlugins: List<SourceBlockParserPlugin> get() { version; return sourceBlocks.toList() }
    val inlinePlugins: List<InlineParserPlugin> get() { version; return inlines.toList() }
    val inlineTriggerCharacters: Set<Char> get() { version; return inlines.mapTo(linkedSetOf()) { it.triggerCharacter } }

    fun register(plugin: ParserPlugin) {
        require(plugin is BlockParserPlugin || plugin is SourceBlockParserPlugin || plugin is InlineParserPlugin) {
            "Unknown plugin type: ${plugin::class.java.name}"
        }
        // Validate all roles before mutation so a duplicate never leaves a partially registered plugin.
        require(plugin !is BlockParserPlugin || blocks.none { it.id == plugin.id }) { "Duplicate block plugin id: ${plugin.id}" }
        require(plugin !is SourceBlockParserPlugin || sourceBlocks.none { it.id == plugin.id }) { "Duplicate source block plugin id: ${plugin.id}" }
        require(plugin !is InlineParserPlugin || inlines.none { it.id == plugin.id }) { "Duplicate inline plugin id: ${plugin.id}" }
        if (plugin is BlockParserPlugin) registerBlock(plugin)
        if (plugin is SourceBlockParserPlugin) registerSourceBlock(plugin)
        if (plugin is InlineParserPlugin) registerInline(plugin)
    }
    /** Installs a batch atomically; duplicates and unsupported types leave content/version unchanged. */
    fun registerAll(plugins: Iterable<ParserPlugin>) {
        val batch = plugins.toList()
        if (batch.isEmpty()) return
        val candidate = copy()
        batch.forEach(candidate::register)
        blocks.clear(); blocks.addAll(candidate.blocks)
        sourceBlocks.clear(); sourceBlocks.addAll(candidate.sourceBlocks)
        inlines.clear(); inlines.addAll(candidate.inlines)
        changed()
    }
    fun registerBlock(plugin: BlockParserPlugin) {
        require(blocks.none { it.id == plugin.id }) { "Duplicate block plugin id: ${plugin.id}" }
        blocks += plugin
        blocks.sortWith(compareByDescending<BlockParserPlugin> { it.priority })
        changed()
    }
    fun registerSourceBlock(plugin: SourceBlockParserPlugin) {
        require(sourceBlocks.none { it.id == plugin.id }) { "Duplicate source block plugin id: ${plugin.id}" }
        sourceBlocks += plugin
        sourceBlocks.sortWith(compareByDescending<SourceBlockParserPlugin> { it.priority })
        changed()
    }
    fun registerInline(plugin: InlineParserPlugin) {
        require(inlines.none { it.id == plugin.id }) { "Duplicate inline plugin id: ${plugin.id}" }
        inlines += plugin
        inlines.sortWith(compareByDescending<InlineParserPlugin> { it.priority })
        changed()
    }
    fun unregisterBlock(id: String): Boolean = blocks.removeAll { it.id == id }.also { if (it) changed() }
    fun unregisterSourceBlock(id: String): Boolean = sourceBlocks.removeAll { it.id == id }.also { if (it) changed() }
    fun unregisterInline(id: String): Boolean = inlines.removeAll { it.id == id }.also { if (it) changed() }
    fun getBlockPlugin(id: String): BlockParserPlugin? = blocks.also { version }.firstOrNull { it.id == id }
    fun getInlinePlugin(id: String): InlineParserPlugin? = inlines.also { version }.firstOrNull { it.id == id }
    fun getInlinePluginByTrigger(char: Char): InlineParserPlugin? = inlines.also { version }.firstOrNull { it.triggerCharacter == char }
    fun isInlineTrigger(char: Char): Boolean = inlines.also { version }.any { it.triggerCharacter == char }
    fun findInlinePlugins(text: String, index: Int): List<InlineParserPlugin> =
        if (index !in text.indices) emptyList() else inlines.also { version }.filter { it.triggerCharacter == text[index] && it.canParse(text, index) }
    fun findBlockPlugins(line: String): List<BlockParserPlugin> = blocks.also { version }.filter { it.canStart(line) }
    fun clear() {
        if (blocks.isEmpty() && sourceBlocks.isEmpty() && inlines.isEmpty()) return
        blocks.clear(); sourceBlocks.clear(); inlines.clear(); changed()
    }
    fun copy(): ParserPluginRegistry = ParserPluginRegistry().also { version }.also { it.blocks.addAll(blocks); it.sourceBlocks.addAll(sourceBlocks); it.inlines.addAll(inlines) }

    internal fun renderInline(node: PluginInlineNode): InlinePluginPresentation? =
        inlines.also { version }.firstNotNullOfOrNull { it.render(node) }
    internal fun blockRenderer(node: PluginBlockNode): BlockParserPlugin? =
        blocks.also { version }.firstOrNull { it.canRender(node) }
    internal fun transformFencedBlocks(root: Node) {
        fun visit(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                val fenced = child as? FencedCodeBlock
                if (fenced != null) {
                    val replacement = blocks.firstNotNullOfOrNull { plugin ->
                        plugin.parseFencedCodeBlock(fenced)?.also { it.pluginId = plugin.id }
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
