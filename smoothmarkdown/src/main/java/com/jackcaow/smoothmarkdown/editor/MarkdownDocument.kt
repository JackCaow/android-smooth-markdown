package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SourceBlockParserPlugin
import com.jackcaow.smoothmarkdown.parseMarkdown
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.NativeCustomBlockMatch
import com.jackcaow.smoothmarkdown.nativeparser.SourceRange
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge
import com.jackcaow.smoothmarkdown.ast.TableBlock
import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.HtmlBlock
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.IndentedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.OrderedList
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.ThematicBreak

/** Top-level syntax recognized by the first semantic editing pass. Nested content remains source-backed. */
enum class MarkdownBlockKind { PARAGRAPH, HEADING, QUOTE, BULLET_LIST, ORDERED_LIST, CODE, TABLE, IMAGE, RULE, RAW }

/** A lossless source slice. [range] uses UTF-16 offsets in the document passed to the codec. */
data class MarkdownDocumentBlock(
    val id: String,
    val kind: MarkdownBlockKind,
    val source: String,
    val range: TextRange,
    val headingLevel: Int? = null,
    val language: String? = null,
)

/** Immutable document whose source is preserved byte-for-byte until a block is explicitly edited. */
data class MarkdownDocument(val source: String, val blocks: List<MarkdownDocumentBlock>) {
    fun toMarkdown(): String = source
    fun blockById(id: String): MarkdownDocumentBlock? = blocks.firstOrNull { it.id == id }
}

/** Converts rendered Markdown syntax into source-backed editable blocks. */
object MarkdownDocumentCodec {
    fun parse(source: String, plugins: ParserPluginRegistry? = null): MarkdownDocument {
        val hostBlocks: ((List<String>, Int, Int) -> NativeCustomBlockMatch?)? = if (plugins?.blockPlugins?.isNotEmpty() == true) {
            { lines, index, offset ->
                plugins.findBlockPlugins(lines[index]).firstNotNullOfOrNull { plugin ->
                    val node = plugin.createNode(lines[index]) ?: return@firstNotNullOfOrNull null
                    node.pluginId = plugin.id
                    var end = index + 1
                    while (end < lines.size && !plugin.isClosingLine(node, lines[end])) end++
                    plugin.complete(node, lines.subList(index + 1, end))
                    if (end < lines.size) end++
                    val raw = lines.subList(index, end).joinToString("\n")
                    NativeCustomBlockMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, raw,
                        SourceRange(offset, raw.length), payload = node), end - index)
                }
            }
        } else null
        val root = if (hostBlocks == null) RustMarkdownBridge.parseForEditor(source)
            else RustMarkdownBridge.parse(source, true, true, customBlock = hostBlocks)
        root?.let { return nativeDocument(source, it, plugins) }
        return legacyDocument(source, plugins)
    }

    /** Shared AST ranges are authoritative; platform source plugins only overlay their own syntax. */
    private fun nativeDocument(source: String, root: NativeMarkdownNode, plugins: ParserPluginRegistry?): MarkdownDocument {
        val lines = lineRanges(source)
        val sourceLines = lines.map { (start, end) -> source.substring(start, end) }
        val nodes = root.children
        val blocks = mutableListOf<MarkdownDocumentBlock>()
        var index = 0
        var previousEnd = 0
        fun contentEnd(end: Int): Int {
            var limit = end
            // Terminal line separators belong to document trivia, not an editable block.
            while (limit > 0 && source[limit - 1] in "\r\n") limit--
            return limit
        }
        while (index < nodes.size) {
            val node = nodes[index]
            var start = node.sourceRange.offset
            var end = contentEnd(node.sourceRange.end)
            var kind = when (node.kind) {
                NativeMarkdownNode.Kind.HEADING -> MarkdownBlockKind.HEADING
                NativeMarkdownNode.Kind.BLOCK_QUOTE -> MarkdownBlockKind.QUOTE
                NativeMarkdownNode.Kind.LIST -> if (node.ordered) MarkdownBlockKind.ORDERED_LIST else MarkdownBlockKind.BULLET_LIST
                NativeMarkdownNode.Kind.FENCED_CODE, NativeMarkdownNode.Kind.INDENTED_CODE -> MarkdownBlockKind.CODE
                NativeMarkdownNode.Kind.TABLE -> MarkdownBlockKind.TABLE
                NativeMarkdownNode.Kind.THEMATIC_BREAK -> MarkdownBlockKind.RULE
                NativeMarkdownNode.Kind.PARAGRAPH -> if (node.children.singleOrNull()?.kind == NativeMarkdownNode.Kind.IMAGE) MarkdownBlockKind.IMAGE else MarkdownBlockKind.PARAGRAPH
                else -> MarkdownBlockKind.RAW
            }
            var nextIndex = index + 1
            val firstLine = lines.indexOfLast { it.first <= start }
            if (firstLine >= 0) {
                val override = plugins?.sourceBlockPlugins?.firstNotNullOfOrNull { plugin ->
                    if (!plugin.canParse(sourceLines[firstLine], sourceLines, firstLine)) return@firstNotNullOfOrNull null
                    val consumed = plugin.parse(sourceLines, firstLine)?.linesConsumed ?: return@firstNotNullOfOrNull null
                    if (consumed !in 1..sourceLines.size - firstLine) return@firstNotNullOfOrNull null
                    val pluginEnd = lines[firstLine + consumed - 1].second
                    if (pluginEnd < end) return@firstNotNullOfOrNull null
                    var candidate = nextIndex
                    while (candidate < nodes.size && nodes[candidate].sourceRange.offset < pluginEnd) {
                        if (contentEnd(nodes[candidate].sourceRange.end) > pluginEnd) return@firstNotNullOfOrNull null
                        candidate++
                    }
                    pluginEnd to candidate
                }
                if (override != null) {
                    start = lines[firstLine].first
                    end = override.first
                    nextIndex = override.second
                    kind = MarkdownBlockKind.RAW
                }
            }
            if (start >= previousEnd && end > start) {
                blocks += MarkdownDocumentBlock("block-${blocks.size}", kind, source.substring(start, end), TextRange(start, end),
                    headingLevel = node.level.takeIf { kind == MarkdownBlockKind.HEADING },
                    language = node.info.trim().takeIf { kind == MarkdownBlockKind.CODE && it.isNotEmpty() })
                previousEnd = end
            }
            index = nextIndex
        }
        return MarkdownDocument(source, blocks)
    }

    /** Only native-artifact/FFI failure reaches the original source-checkout implementation. */
    private fun legacyDocument(source: String, plugins: ParserPluginRegistry?): MarkdownDocument {
        val lines = lineRanges(source)
        val sourceLines = lines.map { (start, end) -> source.substring(start, end) }
        val blocks = mutableListOf<MarkdownDocumentBlock>()
        var node: Node? = parseMarkdown(source, plugins).firstChild
        var previousEnd = 0
        while (node != null) {
            val spans = node.sourceSpans
            if (spans.isNotEmpty()) {
                val first = spans.minOf { it.lineIndex }
                val last = spans.maxOf { it.lineIndex }
                if (first in lines.indices && last in lines.indices) {
                    val start = lines[first].first
                    val currentNode = node
                    val sourceOverride = plugins?.sourceBlockPlugins?.firstNotNullOfOrNull { plugin ->
                        validSourceEnd(plugin, sourceLines, first, last, currentNode)
                    }
                    val endLine = sourceOverride?.first ?: last
                    val end = lines[endLine].second
                    if (start >= previousEnd && end > start) {
                        val (kind, level, language) = if (sourceOverride == null) classify(node)
                            else Triple(MarkdownBlockKind.RAW, null, null)
                        blocks += MarkdownDocumentBlock(
                            id = "block-${blocks.size}", kind = kind,
                            source = source.substring(start, end), range = TextRange(start, end),
                            headingLevel = level, language = language,
                        )
                        previousEnd = end
                    }
                    if (sourceOverride != null) {
                        node = sourceOverride.second
                        continue
                    }
                }
            }
            node = node.next
        }
        return MarkdownDocument(source, blocks)
    }

    /** Accept only complete top-level blocks so a plugin cannot consume part of a CommonMark node. */
    private fun validSourceEnd(
        plugin: SourceBlockParserPlugin,
        lines: List<String>,
        first: Int,
        currentLast: Int,
        current: Node,
    ): Pair<Int, Node?>? {
        if (!plugin.canParse(lines[first], lines, first)) return null
        val consumed = plugin.parse(lines, first)?.linesConsumed ?: return null
        if (consumed !in 1..(lines.size - first)) return null
        val endLine = first + consumed - 1
        if (endLine < currentLast) return null
        var next = current.next
        while (next != null) {
            val spans = next.sourceSpans
            if (spans.isEmpty()) return null
            val nextFirst = spans.minOf { it.lineIndex }
            if (nextFirst > endLine) break
            if (spans.maxOf { it.lineIndex } > endLine) return null
            next = next.next
        }
        return endLine to next
    }

    /** Start and content-end offsets, excluding each line's CR/LF delimiter. */
    private fun lineRanges(source: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var cursor = 0
        while (cursor < source.length) {
            if (source[cursor] == '\n' || source[cursor] == '\r') {
                result += start to cursor
                if (source[cursor] == '\r' && cursor + 1 < source.length && source[cursor + 1] == '\n') cursor++
                start = cursor + 1
            }
            cursor++
        }
        result += start to source.length
        return result
    }

    private fun classify(node: Node): Triple<MarkdownBlockKind, Int?, String?> = when (node) {
        is Heading -> Triple(MarkdownBlockKind.HEADING, node.level, null)
        is BlockQuote -> Triple(MarkdownBlockKind.QUOTE, null, null)
        is BulletList -> Triple(MarkdownBlockKind.BULLET_LIST, null, null)
        is OrderedList -> Triple(MarkdownBlockKind.ORDERED_LIST, null, null)
        is FencedCodeBlock -> Triple(MarkdownBlockKind.CODE, null, node.info?.trim()?.takeIf { it.isNotEmpty() })
        is IndentedCodeBlock -> Triple(MarkdownBlockKind.CODE, null, null)
        is TableBlock -> Triple(MarkdownBlockKind.TABLE, null, null)
        is ThematicBreak -> Triple(MarkdownBlockKind.RULE, null, null)
        is Paragraph -> Triple(if (node.firstChild is Image && node.firstChild?.next == null) MarkdownBlockKind.IMAGE else MarkdownBlockKind.PARAGRAPH, null, null)
        is HtmlBlock -> Triple(MarkdownBlockKind.RAW, null, null)
        else -> Triple(MarkdownBlockKind.RAW, null, null)
    }
}

/** Small semantic editing layer with stable block IDs for unchanged blocks and snapshot undo/redo. */
class MarkdownDocumentEditor(initialSource: String, private val plugins: ParserPluginRegistry? = null) {
    var document: MarkdownDocument = MarkdownDocumentCodec.parse(initialSource, plugins)
        private set
    private val undo = ArrayDeque<MarkdownDocument>()
    private val redo = ArrayDeque<MarkdownDocument>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun replaceBlockSource(id: String, replacement: String): Boolean {
        val block = document.blockById(id) ?: return false
        val candidate = MarkdownDocumentCodec.parse(replacement, plugins)
        if (candidate.blocks.size != 1 || candidate.blocks.single().range != TextRange(0, replacement.length)) return false
        val nextSource = document.source.replaceRange(block.range.min, block.range.max, replacement)
        val reparsed = MarkdownDocumentCodec.parse(nextSource, plugins)
        val index = document.blocks.indexOf(block)
        if (reparsed.blocks.size != document.blocks.size || reparsed.blocks.getOrNull(index)?.source != replacement) return false
        undo.addLast(document)
        redo.clear()
        document = reparsed.copy(blocks = reparsed.blocks.mapIndexed { i, parsed ->
            parsed.copy(id = document.blocks[i].id)
        })
        return true
    }

    /** Rewrites an ATX heading marker; setext headings are deliberately unsupported in this first pass. */
    fun setHeadingLevel(id: String, level: Int): Boolean {
        val block = document.blockById(id) ?: return false
        if (block.kind != MarkdownBlockKind.HEADING || level !in 1..6) return false
        val match = Regex("^( {0,3})#{1,6}(\\s+)").find(block.source) ?: return false
        return replaceBlockSource(id, match.groupValues[1] + "#".repeat(level) + match.groupValues[2] + block.source.substring(match.range.last + 1))
    }

    fun undo(): Boolean {
        if (undo.isEmpty()) return false
        redo.addLast(document)
        document = undo.removeLast()
        return true
    }

    fun redo(): Boolean {
        if (redo.isEmpty()) return false
        undo.addLast(document)
        document = redo.removeLast()
        return true
    }
}
