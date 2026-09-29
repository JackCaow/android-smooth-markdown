package com.jackcaow.smoothmarkdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text

/** UTF-16 offsets into [ReaderDocumentText.text], stable while the parsed document is unchanged. */
internal data class ReaderTextBlock(val id: String, val start: Int, val end: Int)

internal data class ReaderDocumentText(
    val text: String,
    val blocks: List<ReaderTextBlock>,
    /** False when a renderer can display content that cannot be projected as text. */
    val complete: Boolean,
) {
    fun blockAt(offset: Int): ReaderTextBlock? =
        blocks.firstOrNull { offset >= it.start && offset < it.end }
}

/**
 * Projects the whole parsed document without composing offscreen LazyColumn items. The projection
 * is for explicit document copying, not a Compose selection or a claim about selection handles.
 * Top-level and nested block paths are deterministic within one parsed document revision.
 */
internal fun readerDocumentText(
    document: Node,
    enableHtml: Boolean,
    plugins: ParserPluginRegistry?,
    builders: MarkdownBuilderRegistry?,
    detailsExpanded: Map<DetailsNode, Boolean>,
): ReaderDocumentText {
    val text = StringBuilder()
    val blocks = mutableListOf<ReaderTextBlock>()
    var complete = true

    fun inline(node: Node): String {
        val result = StringBuilder()
        fun visit(current: Node) {
            builders?.findBuilder(current)?.renderInline(current)?.let { custom ->
                result.append(when (custom) {
                    is MarkdownInlinePresentation.Text -> custom.text
                    is MarkdownInlinePresentation.Widget -> custom.fallbackText
                })
                return
            }
            when (current) {
                is Text -> result.append(current.literal)
                is Code -> result.append(current.literal)
                is SoftLineBreak, is HardLineBreak -> result.append('\n')
                is Image -> Unit // The displayed image is not the alt text.
                is InlineMathNode -> result.append('$').append(current.latex).append('$')
                is HtmlKbdNode -> result.append(current.label)
                is FootnoteReferenceNode -> result.append('[').append(current.label).append(']')
                is PluginInlineNode -> {
                    val presentation = plugins?.renderInline(current)
                    if (presentation == null) complete = false
                    else result.append(presentation.text)
                }
                is HtmlInline -> {
                    if (!enableHtml) result.append(current.literal)
                    else {
                        val tag = SafeHtml.lexTag(current.literal)
                        when {
                            tag == null || tag.end != current.literal.length -> result.append(current.literal)
                            tag.name == "br" && !tag.isClosing -> result.append('\n')
                            // HTML image and formatting tags have no selectable glyph.
                        }
                    }
                }
                else -> current.children().forEach(::visit)
            }
        }
        node.children().forEach(::visit)
        return result.toString()
    }

    fun append(id: String, value: String) {
        if (value.isEmpty()) return
        if (text.isNotEmpty()) text.append('\n')
        val start = text.length
        text.append(value)
        blocks += ReaderTextBlock(id, start, text.length)
    }

    fun visitBlock(node: Node, path: String) {
        val builder = builders?.findBuilder(node)
        if (builder != null) {
            val replacement = builder.documentText(node)
            if (replacement == null) complete = false
            else append(path, replacement)
            return
        }
        when (node) {
            is Heading, is Paragraph -> {
                val sole = node.children().filterNot { it is Text && it.literal.isBlank() }.singleOrNull()
                if (node is Paragraph && sole is Image) {
                    val custom = builders?.findBuilder(sole)
                    if (custom != null) {
                        val replacement = custom.documentText(sole)
                        if (replacement == null) complete = false else append(path, replacement)
                    }
                    return
                }
                if (enableHtml && sole is HtmlInline && SafeHtml.imageTag(sole.literal) != null) return
                append(path, inline(node))
            }
            is FencedCodeBlock -> append(path, node.literal.trimEnd('\n'))
            is IndentedCodeBlock -> append(path, node.literal.trimEnd('\n'))
            is BlockQuote -> node.children().forEachIndexed { index, child -> visitBlock(child, "${path}/${index}") }
            is BulletList, is OrderedList -> {
                val start = (node as? OrderedList)?.startNumber ?: 1
                node.children().filterIsInstance<ListItem>().forEachIndexed { itemIndex, item ->
                    val task = item.children().filterIsInstance<TaskListItemMarker>().firstOrNull()
                    val marker = when {
                        task != null -> if (task.isChecked) "☑" else "☐"
                        node is OrderedList -> "${start + itemIndex}."
                        else -> "•"
                    }
                    val children = item.children().filterNot { it is TaskListItemMarker }.toList()
                    children.forEachIndexed { childIndex, child ->
                        val childPath = "${path}/${itemIndex}/${childIndex}"
                        if (childIndex == 0 && (child is Paragraph || child is Heading)) {
                            append(childPath, "${marker} ${inline(child)}")
                        } else visitBlock(child, childPath)
                    }
                    if (children.isEmpty()) append("${path}/${itemIndex}", marker)
                }
            }
            is TableBlock -> {
                node.children().flatMap { it.children() }.filterIsInstance<TableRow>()
                    .forEachIndexed { rowIndex, row ->
                        val cells = row.children().filterIsInstance<TableCell>().map(::inline)
                        append("${path}/${rowIndex}", cells.joinToString("\t"))
                    }
            }
            is DetailsNode -> {
                node.summary.forEachIndexed { index, child -> visitBlock(child, "${path}/s${index}") }
                if (detailsExpanded[node] ?: node.isOpen) {
                    node.body.forEachIndexed { index, child -> visitBlock(child, "${path}/b${index}") }
                }
            }
            is FootnoteDefinitionNode -> append(path, "[${node.label}]: ${inline(node)}")
            is HtmlBlock -> {
                val html = if (enableHtml) SafeHtml.parseBlock(node.literal) else null
                when {
                    !enableHtml -> append(path, node.literal)
                    SafeHtml.imageTag(node.literal) != null -> Unit
                    SafeHtml.imageAlt(node.literal) != null -> append(path, SafeHtml.imageAlt(node.literal).orEmpty())
                    html is SafeHtml.Block.Rule -> Unit
                    html is SafeHtml.Block.Container -> {
                        val nested = parseMarkdown(html.content, plugins, enableHtml = true)
                        nested.children().forEachIndexed { index, child -> visitBlock(child, "${path}/c${index}") }
                        if (html.trailing.isNotBlank()) {
                            parseMarkdown(html.trailing, plugins, enableHtml = true).children()
                                .forEachIndexed { index, child -> visitBlock(child, "${path}/t${index}") }
                        }
                    }
                    else -> append(path, node.literal)
                }
            }
            is ThematicBreak, is BlockMathNode -> Unit // Canvas and rules are not text glyphs.
            is PluginBlockNode -> {
                val replacement = plugins?.blockRenderer(node)?.documentText(node)
                if (replacement == null) complete = false
                else append(path, replacement)
            }
            else -> complete = false
        }
    }

    document.children().forEachIndexed { index, node -> visitBlock(node, index.toString()) }
    return ReaderDocumentText(text.toString(), blocks, complete)
}
