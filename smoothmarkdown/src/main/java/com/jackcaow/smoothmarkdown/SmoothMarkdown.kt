package com.jackcaow.smoothmarkdown

import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import coil.compose.SubcomposeAsyncImage
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
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans

private val parser = Parser.builder().extensions(
    listOf(
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
    ),
).customBlockParserFactory(FootnoteDefinitionParserFactory())
    .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
    .build()

internal fun parseMarkdown(markdown: String): Node = FootnoteReferencePostProcessor(markdown).process(parser.parse(markdown))

/** Renders CommonMark and the currently supported GFM extensions with Compose. */
@Composable
fun SmoothMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    enableHtml: Boolean = false,
) {
    val document = remember(markdown) { parseMarkdown(markdown) }
    val blocks = remember(document) { document.children().toList() }
    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        itemsIndexed(blocks) { _, block ->
            MarkdownBlock(block, onLinkClick, onImageClick, enableHtml)
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean, textAlign: TextAlign? = null) {
    when (node) {
        is Heading -> MarkdownInlineText(
            inlineRender(node, enableHtml),
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = (32 - (node.level - 1) * 3).sp,
                fontWeight = FontWeight.Bold,
            ),
            onLinkClick,
            onImageClick,
            textAlign,
        )
        is Paragraph -> {
            val meaningful = node.children().filterNot { it is MarkdownTextNode && it.literal.isBlank() }.toList()
            val sole = meaningful.singleOrNull()
            val htmlImage = if (enableHtml && sole is HtmlInline) SafeHtml.imageTag(sole.literal) else null
            when {
                sole is Image -> MarkdownImage(
                    SafeHtml.ImageSpec(sole.destination, sole.plainText(), sole.title, null, null), onImageClick,
                )
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                else -> MarkdownInlineText(inlineRender(node, enableHtml), MaterialTheme.typography.bodyLarge, onLinkClick, onImageClick, textAlign)
            }
        }
        is FencedCodeBlock -> CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, null)
        is BlockQuote -> Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Box(Modifier.width(3.dp).height(44.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column {
                node.children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml, textAlign) }
            }
        }
        is BulletList, is OrderedList -> MarkdownList(node, onLinkClick, onImageClick, enableHtml)
        is TableBlock -> MarkdownTable(node, onLinkClick, onImageClick, enableHtml)
        is FootnoteDefinitionNode -> Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                "[${node.label}]: ",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1976D2)),
            )
            Box(Modifier.weight(1f)) {
                MarkdownInlineText(
                    inlineRender(node, enableHtml), MaterialTheme.typography.bodyLarge,
                    onLinkClick, onImageClick, textAlign, bottomPadding = 0.dp,
                )
            }
        }
        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 14.dp))
        is HtmlBlock -> {
            val htmlImage = if (enableHtml) SafeHtml.imageTag(node.literal) else null
            val imageAlt = if (enableHtml) SafeHtml.imageAlt(node.literal) else null
            val html = if (enableHtml) SafeHtml.parseBlock(node.literal) else null
            when {
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                imageAlt != null -> MarkdownText(AnnotatedString(imageAlt), MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
                html is SafeHtml.Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 14.dp))
                html is SafeHtml.Block.Container -> {
                    val alignment = when (html.alignment) {
                        "left" -> TextAlign.Left
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        else -> textAlign
                    }
                    if (html.name == "blockquote") {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Box(Modifier.width(3.dp).height(44.dp).background(MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(12.dp))
                            Column { parseMarkdown(html.content).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                        }
                    } else {
                        Column { parseMarkdown(html.content).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                    }
                    if (html.trailing.isNotBlank()) {
                        parseMarkdown(html.trailing).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, textAlign) }
                    }
                }
                else -> MarkdownText(AnnotatedString(node.literal), MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
            }
        }
        else -> MarkdownText(AnnotatedString(node.plainText()), MaterialTheme.typography.bodyLarge, onLinkClick)
    }
}

@Composable
private fun MarkdownText(text: AnnotatedString, style: androidx.compose.ui.text.TextStyle, onLinkClick: (String) -> Unit, textAlign: TextAlign? = null, bottomPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    SelectionContainer {
        ClickableText(
            text = text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
            onClick = { position ->
                text.getStringAnnotations("url", position, position).firstOrNull()?.item
                    ?.takeIf(::isSafeLink)?.let(onLinkClick)
            },
        )
    }
}

@Composable
private fun MarkdownInlineText(
    render: InlineRender,
    style: androidx.compose.ui.text.TextStyle,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    textAlign: TextAlign? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    if (render.images.isEmpty()) {
        MarkdownText(render.text, style, onLinkClick, textAlign, bottomPadding)
        return
    }
    val density = LocalDensity.current
    val layout = remember(render.text) { mutableStateOf<TextLayoutResult?>(null) }
    val inline = render.images.mapValues { (_, image) ->
        val width = image.width ?: 32f
        val height = image.height ?: 32f
        InlineTextContent(
            placeholder = Placeholder(
                width = with(density) { width.dp.toSp() },
                height = with(density) { height.dp.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            InlineImage(image, width, height, onImageClick)
        }
    }
    SelectionContainer {
        Text(
            text = render.text,
            inlineContent = inline,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding).pointerInput(render.text) {
                detectTapGestures { position ->
                    layout.value?.getOffsetForPosition(position)?.let { offset ->
                        render.text.getStringAnnotations("url", offset, offset).firstOrNull()?.item
                            ?.takeIf(::isSafeLink)?.let(onLinkClick)
                    }
                }
            },
            onTextLayout = { layout.value = it },
        )
    }
}

@Composable
private fun InlineImage(image: SafeHtml.ImageSpec, width: Float, height: Float, onImageClick: (String) -> Unit) {
    val model = imageModel(image.source) ?: return Text(image.alt)
    SubcomposeAsyncImage(
        model = model,
        contentDescription = image.alt.ifBlank { image.title ?: "Image" },
        modifier = Modifier.width(width.dp).height(height.dp).clickable { onImageClick(image.source) },
        contentScale = ContentScale.Fit,
        loading = { androidx.compose.material3.CircularProgressIndicator() },
        error = { Text(image.alt.ifBlank { image.title ?: "Image" }) },
    )
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
private fun MarkdownImage(image: SafeHtml.ImageSpec, onImageClick: (String) -> Unit) {
    val url = image.source
    val model = imageModel(url)
    if (model == null) {
        Text(image.alt, modifier = Modifier.padding(bottom = 12.dp))
        return
    }
    var imageModifier: Modifier = Modifier.padding(bottom = 12.dp)
    imageModifier = if (image.width != null) imageModifier.width(image.width.dp) else imageModifier.fillMaxWidth()
    if (image.height != null) imageModifier = imageModifier.height(image.height.dp)
    SubcomposeAsyncImage(
        model = model,
        contentDescription = image.alt.ifBlank { image.title ?: "Image" },
        modifier = imageModifier.clickable { onImageClick(url) },
        loading = { androidx.compose.material3.CircularProgressIndicator() },
        error = { Text(image.alt.ifBlank { image.title ?: "Image" }) },
    )
}

private fun imageModel(url: String): String? {
    val local = url.isNotBlank() && !url.startsWith("//") && !url.contains("..") && !url.contains(':') && !url.contains('\\')
    return when {
        local -> "file:///android_asset/${url.trimStart('/')}"
        isSafeImage(url) -> url
        else -> null
    }
}

@Composable
private fun MarkdownList(list: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean) {
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
                        MarkdownBlock(it, onLinkClick, onImageClick, enableHtml)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        table.children().flatMap { it.children() }.filterIsInstance<TableRow>().forEach { row ->
            Row {
                row.children().filterIsInstance<TableCell>().forEach { cell ->
                    val style = if (cell.isHeader) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium
                    Box(Modifier.width(150.dp).border(0.5.dp, MaterialTheme.colorScheme.outline).padding(8.dp)) {
                        MarkdownInlineText(inlineRender(cell, enableHtml), style, onLinkClick, onImageClick)
                    }
                }
            }
        }
    }
}

internal data class InlineRender(val text: AnnotatedString, val images: Map<String, SafeHtml.ImageSpec>)

internal fun inlineText(node: Node, enableHtml: Boolean): AnnotatedString = inlineRender(node, enableHtml).text

internal fun inlineRender(node: Node, enableHtml: Boolean): InlineRender {
    val images = linkedMapOf<String, SafeHtml.ImageSpec>()
    val text = buildAnnotatedString {
    val htmlStack = mutableListOf<SafeHtml.Tag>()

    fun appendImage(image: SafeHtml.ImageSpec) {
        if (imageModel(image.source) == null) {
            append(image.alt)
            return
        }
        val id = "image-${images.size}"
        images[id] = image
        appendInlineContent(id, image.alt)
    }

    fun applyHtmlStyle(tag: SafeHtml.Tag, start: Int, end: Int) {
        if (start == end) return
        when (tag.name) {
            "b", "strong" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            "i", "em" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            "s", "del", "strike" -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            "u", "ins" -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            "mark" -> addStyle(SpanStyle(background = Color.Yellow.copy(alpha = 0.4f)), start, end)
            "sub" -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript), start, end)
            "sup" -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), start, end)
            "code", "kbd" -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
            "a" -> tag.attributes["href"]?.takeIf(SafeHtml::isSafeLink)?.let { url ->
                addStyle(SpanStyle(color = Color(0xFF0969DA), textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("url", url, start, end)
            }
            "font", "span" -> {
                val css = if (tag.name == "span") SafeHtml.cssDeclarations(tag.attributes["style"] ?: "") else emptyMap()
                val color = (if (tag.name == "font") tag.attributes["color"] else css["color"])?.let(SafeHtml::color)
                val background = css["background-color"]?.let(SafeHtml::color)
                val size = if (tag.name == "font") tag.attributes["size"]?.let(SafeHtml::legacyFontSize)
                    else css["font-size"]?.let(SafeHtml::fontSize)
                addStyle(SpanStyle(
                    color = color?.let(::Color) ?: Color.Unspecified,
                    background = background?.let(::Color) ?: Color.Unspecified,
                    fontSize = size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                ), start, end)
            }
        }
    }

    fun appendNode(current: Node) {
        val start = length
        var leaf = true
        when (current) {
            is org.commonmark.node.Text -> append(current.literal)
            is Code -> append(current.literal)
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is FootnoteReferenceNode -> {
                append("[${current.label}]")
                addStyle(
                    SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 0.75.em,
                        color = Color(0xFF1976D2),
                    ), start, length,
                )
            }
            is Image -> appendImage(SafeHtml.ImageSpec(current.destination, current.plainText(), current.title, null, null))
            is HtmlInline -> {
                if (!enableHtml) append(current.literal)
                else {
                    val tag = SafeHtml.lexTag(current.literal)
                    if (tag == null || tag.end != current.literal.length) append(current.literal)
                    else if (tag.isClosing) {
                        val match = htmlStack.indexOfLast { it.name == tag.name }
                        if (match >= 0) htmlStack.subList(match, htmlStack.size).clear()
                    } else if (tag.name in SafeHtml.voidTags) {
                        when (tag.name) {
                            "br" -> append("\n")
                            "img" -> {
                                val image = SafeHtml.imageTag(current.literal)
                                if (image != null) appendImage(image)
                                else append(tag.attributes["alt"] ?: "")
                            }
                        }
                    } else if (!tag.isSelfClosing) htmlStack += tag
                }
            }
            else -> { leaf = false; current.children().forEach(::appendNode) }
        }
        val end = length
        if (enableHtml && leaf) htmlStack.forEach { applyHtmlStyle(it, start, end) }
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
    return InlineRender(text, images)
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
