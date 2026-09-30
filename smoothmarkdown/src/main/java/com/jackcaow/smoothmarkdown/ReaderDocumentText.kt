package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.TableBlock
import com.jackcaow.smoothmarkdown.ast.TableCell
import com.jackcaow.smoothmarkdown.ast.TableRow
import com.jackcaow.smoothmarkdown.ast.TaskListItemMarker
import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.Code
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.HardLineBreak
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.HtmlBlock
import com.jackcaow.smoothmarkdown.ast.HtmlInline
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.IndentedCodeBlock
import com.jackcaow.smoothmarkdown.ast.ListItem
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.OrderedList
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.SoftLineBreak
import com.jackcaow.smoothmarkdown.ast.ThematicBreak
import com.jackcaow.smoothmarkdown.ast.Text

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
 * is for explicit document copying and the exact, unchanged native whole-document selection.
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
                is Image -> result.append(inline(current)) // Semantic alt text, not image pixels.
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
                            tag.name == "img" && !tag.isClosing ->
                                result.append(tag.attributes["alt"].orEmpty())
                            // Formatting tags have no selectable glyph.
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
                    } else append(path, inline(sole))
                    return
                }
                if (enableHtml && sole is HtmlInline && SafeHtml.imageTag(sole.literal) != null) {
                    append(path, SafeHtml.imageTag(sole.literal)?.alt.orEmpty())
                    return
                }
                append(path, inline(node))
            }
            is FencedCodeBlock -> append(path, node.literal)
            is IndentedCodeBlock -> append(path, node.literal)
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
                    SafeHtml.imageTag(node.literal) != null ->
                        append(path, SafeHtml.imageTag(node.literal)?.alt.orEmpty())
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
            is ThematicBreak -> Unit
            is BlockMathNode -> if (node.latex.isNotEmpty()) append(path, "$$${node.latex}$$")
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
