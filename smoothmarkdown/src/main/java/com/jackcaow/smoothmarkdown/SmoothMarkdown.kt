package com.jackcaow.smoothmarkdown

import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val parser = Parser.builder().extensions(
    listOf(
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
    ),
).build()

internal fun parseMarkdown(markdown: String): Node = parser.parse(markdown)

/** Renders CommonMark and the currently supported GFM extensions with Compose. */
@Composable
fun SmoothMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
) {
    val document = remember(markdown) { parseMarkdown(markdown) }
    val blocks = remember(document) { document.children().toList() }
    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        itemsIndexed(blocks) { _, block ->
            MarkdownBlock(block, onLinkClick, onImageClick)
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit) {
    when (node) {
        is Heading -> MarkdownText(
            inlineText(node),
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = (32 - (node.level - 1) * 3).sp,
                fontWeight = FontWeight.Bold,
            ),
            onLinkClick,
        )
        is Paragraph -> {
            val image = node.firstChild as? Image
            if (image != null && image.next == null) {
                MarkdownImage(image, onImageClick)
            } else {
                MarkdownText(inlineText(node), MaterialTheme.typography.bodyLarge, onLinkClick)
            }
        }
        is FencedCodeBlock -> CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, null)
        is BlockQuote -> Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Box(Modifier.width(3.dp).height(44.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column {
                node.children().forEach { MarkdownBlock(it, onLinkClick, onImageClick) }
            }
        }
        is BulletList, is OrderedList -> MarkdownList(node, onLinkClick, onImageClick)
        is TableBlock -> MarkdownTable(node, onLinkClick)
        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 14.dp))
        is HtmlBlock -> MarkdownText(AnnotatedString(node.literal), MaterialTheme.typography.bodyLarge, onLinkClick)
        else -> MarkdownText(AnnotatedString(node.plainText()), MaterialTheme.typography.bodyLarge, onLinkClick)
    }
}

@Composable
private fun MarkdownText(text: AnnotatedString, style: androidx.compose.ui.text.TextStyle, onLinkClick: (String) -> Unit) {
    SelectionContainer {
        ClickableText(
            text = text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            onClick = { position ->
                text.getStringAnnotations("url", position, position).firstOrNull()?.item
                    ?.takeIf(::isSafeLink)?.let(onLinkClick)
            },
        )
    }
}

@Composable
private fun CodeBlock(code: String, info: String?) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        info?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        SelectionContainer {
            Text(code, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun MarkdownImage(image: Image, onImageClick: (String) -> Unit) {
    val url = image.destination
    if (!isSafeImage(url)) {
        Text(image.plainText(), modifier = Modifier.padding(bottom = 12.dp))
        return
    }
    AsyncImage(
        model = url,
        contentDescription = image.plainText(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onImageClick(url) },
    )
}

@Composable
private fun MarkdownList(list: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit) {
    val start = (list as? OrderedList)?.startNumber ?: 1
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val task = item.children().filterIsInstance<TaskListItemMarker>().firstOrNull()
            val marker = when {
                task != null -> if (task.isChecked) "☑" else "☐"
                list is OrderedList -> "${start + index}."
                else -> "•"
            }
            Row(Modifier.fillMaxWidth()) {
                Text(marker, modifier = Modifier.width(34.dp), style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.weight(1f)) {
                    item.children().filterNot { it is TaskListItemMarker }.forEach {
                        MarkdownBlock(it, onLinkClick, onImageClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, onLinkClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        table.children().flatMap { it.children() }.filterIsInstance<TableRow>().forEach { row ->
            Row {
                row.children().filterIsInstance<TableCell>().forEach { cell ->
                    val style = if (cell.isHeader) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium
                    Box(Modifier.width(150.dp).border(0.5.dp, MaterialTheme.colorScheme.outline).padding(8.dp)) {
                        MarkdownText(inlineText(cell), style, onLinkClick)
                    }
                }
            }
        }
    }
}

private fun inlineText(node: Node): AnnotatedString = buildAnnotatedString {
    fun appendNode(current: Node) {
        val start = length
        when (current) {
            is org.commonmark.node.Text -> append(current.literal)
            is Code -> append(current.literal)
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Image -> append(current.plainText())
            is HtmlInline -> append(current.literal)
            else -> current.children().forEach(::appendNode)
        }
        val end = length
        when (current) {
            is StrongEmphasis -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            is Emphasis -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            is Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
            is Strikethrough -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is Link -> if (isSafeLink(current.destination)) {
                addStyle(SpanStyle(color = Color(0xFF0969DA), textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("url", current.destination, start, end)
            }
        }
    }
    node.children().forEach(::appendNode)
}

internal fun isSafeLink(value: String): Boolean {
    val scheme = runCatching { URI(value).scheme?.lowercase() }.getOrElse { return false }
    return scheme == null || scheme in setOf("http", "https", "mailto", "tel")
}

internal fun isSafeImage(value: String): Boolean =
    runCatching { URI(value).scheme?.lowercase() }.getOrNull() in setOf("http", "https")

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}

private fun Node.plainText(): String = buildString {
    fun visit(node: Node) {
        when (node) {
            is org.commonmark.node.Text -> append(node.literal)
            is Code -> append(node.literal)
            is FencedCodeBlock -> append(node.literal)
            is IndentedCodeBlock -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            else -> node.children().forEach(::visit)
        }
    }
    visit(this@plainText)
}
