package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode.Kind

/** Projects the immutable source scanner tree into the public, mutable library-owned AST. */
class NativeMarkdownMarkupConverter(
    private val source: String,
    private val special: ((NativeMarkdownNode) -> Markup?)? = null,
) {
    private val lineStarts = buildList { add(0); source.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }

    fun convert(node: NativeMarkdownNode): Markup {
        val custom = node.payload as? Markup ?: special?.invoke(node)
        if (custom != null) return custom.also { it.sourceSpans = spans(node) }
        val result: Markup = when (node.kind) {
            Kind.DOCUMENT -> Document()
            Kind.PARAGRAPH -> Paragraph()
            Kind.HEADING -> Heading(node.level)
            Kind.TEXT -> Text(node.semanticText.orEmpty())
            Kind.STRONG -> StrongEmphasis()
            Kind.EMPHASIS -> Emphasis()
            Kind.STRIKETHROUGH -> Strikethrough()
            Kind.INLINE_CODE -> Code(node.semanticText.orEmpty())
            Kind.SOFT_BREAK -> SoftLineBreak()
            Kind.HARD_BREAK -> HardLineBreak()
            Kind.LINK -> Link(node.destination, node.title)
            Kind.IMAGE -> Image(node.destination, node.title)
            Kind.FENCED_CODE -> FencedCodeBlock(node.info, node.semanticText.orEmpty()).also {
                val opening = node.source.substringBefore('\n').trimStart()
                it.fenceChar = opening.firstOrNull() ?: '`'
                it.fenceLength = opening.takeWhile { c -> c == it.fenceChar }.length
                it.fenceIndent = node.source.takeWhile { c -> c == ' ' }.length
            }
            Kind.INDENTED_CODE -> IndentedCodeBlock(node.semanticText.orEmpty())
            Kind.INLINE_HTML -> HtmlInline(node.literalText ?: node.source)
            Kind.HTML_BLOCK -> HtmlBlock(node.literalText ?: node.source)
            Kind.BLOCK_QUOTE -> BlockQuote()
            Kind.THEMATIC_BREAK -> ThematicBreak()
            Kind.LIST -> if (node.ordered) OrderedList(node.listStart ?: 1,
                Regex("^\\s*\\d+([.)])").find(node.source)?.groupValues?.get(1)?.first() ?: '.', node.isTight ?: true)
                else BulletList(node.source.trimStart().firstOrNull() ?: '-', node.isTight ?: true)
            Kind.LIST_ITEM -> ListItem()
            Kind.TABLE -> TableBlock()
            Kind.TABLE_ROW -> TableRow()
            Kind.TABLE_CELL -> TableCell()
            Kind.REFERENCE_DEFINITION -> LinkReferenceDefinition(node.label, node.destination, node.title)
            else -> Text(node.literalText ?: node.source)
        }
        result.sourceSpans = if (node.kind == Kind.PARAGRAPH && node.children.isNotEmpty())
            coalesceSpans(node.children.flatMap(::spans)) else spans(node)
        if (node.kind == Kind.TABLE) {
            val head = TableHead()
            val body = TableBody()
            node.children.forEachIndexed { index, row ->
                val rendered = convert(row)
                rendered.children().forEachIndexed { column, cell ->
                    if (cell is TableCell) {
                        cell.isHeader = index == 0
                        cell.alignment = when (node.tableAlignments.getOrNull(column)) {
                            "left" -> TableCell.Alignment.LEFT
                            "center" -> TableCell.Alignment.CENTER
                            "right" -> TableCell.Alignment.RIGHT
                            else -> null
                        }
                    }
                }
                if (index == 0) head.appendChild(rendered) else body.appendChild(rendered)
            }
            result.appendChild(head)
            if (body.firstChild != null) result.appendChild(body)
        } else {
            var inlineParagraph: Paragraph? = null
            node.children.forEach { child ->
                val converted = convert(child)
                if (node.kind == Kind.LIST_ITEM && converted !is Block) {
                    val paragraph = inlineParagraph ?: Paragraph().also {
                        inlineParagraph = it
                        result.appendChild(it)
                    }
                    paragraph.appendChild(converted)
                    paragraph.sourceSpans = coalesceSpans(paragraph.sourceSpans + converted.sourceSpans)
                } else {
                    inlineParagraph = null
                    result.appendChild(converted)
                }
            }
            if (node.kind == Kind.LIST_ITEM && node.checked != null) {
                result.prependChild(TaskListItemMarker(node.checked))
            }
        }
        return result
    }

    /** One span per physical content line, excluding quote/list projection prefixes. */
    private fun coalesceSpans(spans: List<SourceSpan>): List<SourceSpan> = spans.groupBy { it.lineIndex }
        .toSortedMap().map { (line, fragments) ->
            val start = fragments.minOf { it.inputIndex }
            var end = fragments.maxOf { it.inputIndex + it.length }
            var physicalEnd = (lineStarts.getOrNull(line + 1)?.minus(1) ?: source.length)
            if (physicalEnd > end && source[physicalEnd - 1] == '\r') physicalEnd--
            // Source-backed editor carets include trailing spaces even when CommonMark hides them.
            if (physicalEnd > end && source.substring(end, physicalEnd).all { it == ' ' || it == '\t' }) {
                end = physicalEnd
            }
            SourceSpan(line, start - lineStarts[line], start, end - start)
        }

    private fun spans(node: NativeMarkdownNode): List<SourceSpan> {
        var cursor = node.sourceRange.offset.coerceIn(0, source.length)
        val end = node.sourceRange.end.coerceIn(cursor, source.length)
        val output = mutableListOf<SourceSpan>()
        while (cursor < end) {
            val found = lineStarts.binarySearch(cursor)
            val line = if (found >= 0) found else (-found - 2).coerceAtLeast(0)
            val nextLine = lineStarts.getOrNull(line + 1) ?: source.length + 1
            var limit = minOf(end, nextLine - 1)
            if (limit > cursor && source[limit - 1] == '\r') limit--
            if (limit > cursor) output += SourceSpan(line, cursor - lineStarts[line], cursor, limit - cursor)
            cursor = minOf(end, nextLine)
        }
        return output
    }
}
