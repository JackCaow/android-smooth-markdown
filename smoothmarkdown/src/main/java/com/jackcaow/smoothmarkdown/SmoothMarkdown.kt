package com.jackcaow.smoothmarkdown

import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.decode.SvgDecoder
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

private val baseParser = Parser.builder().extensions(
    listOf(
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
    ),
).customBlockParserFactory(FootnoteDefinitionParserFactory())
    .customBlockParserFactory(DetailsParserFactory())
    .customBlockParserFactory(MathBlockParserFactory())
    .customInlineContentParserFactory(MathInlineParserFactory())
    .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
    .build()

internal fun parseMarkdown(markdown: String, plugins: ParserPluginRegistry? = null): Node {
    if (plugins == null) SmoothMarkdownCache.get(markdown)?.let { return it }
    val parser = if (plugins == null || (plugins.blockPlugins.isEmpty() && plugins.inlinePlugins.isEmpty())) baseParser else {
        val builder = Parser.builder().extensions(listOf(
            StrikethroughExtension.create(), TablesExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create(),
        ))
        plugins.blockPlugins.forEach { builder.customBlockParserFactory(PluginBlockParserFactory(it)) }
        builder.customBlockParserFactory(FootnoteDefinitionParserFactory())
            .customBlockParserFactory(DetailsParserFactory())
            .customBlockParserFactory(MathBlockParserFactory())
            .customInlineContentParserFactory(MathInlineParserFactory())
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        if (plugins.inlinePlugins.isNotEmpty()) builder.customInlineContentParserFactory(PluginInlineParserFactory(plugins))
        builder.build()
    }
    val document = parser.parse(markdown)
    plugins?.transformFencedBlocks(document)
    val result = FootnoteReferencePostProcessor(markdown).process(document)
    if (plugins == null) SmoothMarkdownCache.put(markdown, result)
    return result
}

private val LocalParserPlugins = compositionLocalOf<ParserPluginRegistry?> { null }
private val LocalOnImageClickWithMetadata = compositionLocalOf<((String, String?, String?) -> Unit)?> { null }
private val LocalImageBuilder = compositionLocalOf<(@Composable (String, String?, String?) -> Unit)?> { null }
private val LocalOnMentionClick = compositionLocalOf<((String) -> Unit)?> { null }
private val LocalOnHashtagClick = compositionLocalOf<((String) -> Unit)?> { null }
private val LocalOnWikilinkClick = compositionLocalOf<((String) -> Unit)?> { null }

/** Sends the original image source and metadata to both registered callbacks. */
internal fun dispatchImageClick(
    image: SafeHtml.ImageSpec,
    onImageClick: (String) -> Unit,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)?,
) {
    onImageClick(image.source)
    onImageClickWithMetadata?.invoke(image.source, image.alt, image.title)
}

/**
 * Renders CommonMark and the currently supported GFM extensions with Compose.
 * Image taps call [onImageClick] and, when supplied, [onImageClickWithMetadata]
 * with the original source, alternative text, and title.
 * [imageBuilder] replaces only the image content; URL validation, sizing, semantics, and taps
 * remain managed by the reader. It receives the original source, alt text, and title.
 */
@Composable
fun SmoothMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    enableHtml: Boolean = false,
    codeBlockOptions: CodeBlockOptions = CodeBlockOptions(),
    codeBlockBuilder: (@Composable (String, String?) -> Unit)? = null,
    onCodeCopied: ((String) -> Unit)? = null,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    plugins: ParserPluginRegistry? = null,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)? = null,
    imageBuilder: (@Composable (String, String?, String?) -> Unit)? = null,
    /** Compose blocks without an inner scroll container, for use in a virtualized chat item. */
    scrollable: Boolean = true,
    /** Receives the username without @ when a parsed mention is tapped. */
    onMentionClick: ((String) -> Unit)? = null,
    /** Receives the tag without # when a parsed hashtag is tapped. */
    onHashtagClick: ((String) -> Unit)? = null,
    /** Receives the complete title inside a parsed `[[wikilink]]`. */
    onWikilinkClick: ((String) -> Unit)? = null,
) {
    val document = remember(markdown, plugins) { parseMarkdown(markdown, plugins) }
    val blocks = remember(document) { document.children().toList() }
    val selectionGroups = remember(blocks) { groupSelectableBlocks(blocks) }
    CompositionLocalProvider(
        LocalCodeBlockOptions provides codeBlockOptions,
        LocalCodeBlockBuilder provides codeBlockBuilder,
        LocalOnCodeCopied provides onCodeCopied,
        LocalMarkdownStyleSheet provides styleSheet,
        LocalParserPlugins provides plugins,
        LocalOnImageClickWithMetadata provides onImageClickWithMetadata,
        LocalImageBuilder provides imageBuilder,
        LocalOnMentionClick provides onMentionClick,
        LocalOnHashtagClick provides onHashtagClick,
        LocalOnWikilinkClick provides onWikilinkClick,
    ) {
        val backgroundModifier = if (styleSheet.backgroundColor != null) modifier.background(styleSheet.backgroundColor) else modifier
        if (scrollable) {
            LazyColumn(
                modifier = backgroundModifier,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(styleSheet.contentPadding),
            ) {
                itemsIndexed(selectionGroups) { _, group ->
                    MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml)
                }
            }
        } else {
            Column(backgroundModifier.padding(styleSheet.contentPadding)) {
                selectionGroups.forEach { group ->
                    MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml)
                }
            }
        }
    }
}

@Composable
private fun MarkdownSelectionGroup(
    group: List<Node>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    enableHtml: Boolean,
) {
    if (group.size == 1 && (group.single() is FencedCodeBlock || group.single() is IndentedCodeBlock)) {
        MarkdownBlock(group.single(), onLinkClick, onImageClick, enableHtml)
    } else {
        SelectionContainer {
            Column {
                group.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
            }
        }
    }
}

/** Adjacent prose shares one selection registrar; LazyColumn still recycles other blocks. */
internal fun groupSelectableBlocks(blocks: List<Node>): List<List<Node>> {
    val groups = mutableListOf<List<Node>>()
    val pending = mutableListOf<Node>()
    fun flush() { if (pending.isNotEmpty()) { groups += pending.toList(); pending.clear() } }
    for (block in blocks) {
        val prose = block is Heading || block is Paragraph || block is BlockQuote ||
            block is BulletList || block is OrderedList
        if (prose) pending += block else { flush(); groups += listOf(block) }
    }
    flush()
    return groups
}

@Composable
private fun MarkdownBlock(node: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean, textAlign: TextAlign? = null) {
    val sheet = LocalMarkdownStyleSheet.current
    val plugins = LocalParserPlugins.current
    when (node) {
        is Heading -> {
            val baseStyle = sheet.headingStyles?.get(node.level - 1) ?: MaterialTheme.typography.headlineMedium.copy(
                fontSize = (32 - (node.level - 1) * 3).sp,
                fontWeight = FontWeight.Bold,
            )
            MarkdownInlineText(
                inlineRender(node, enableHtml, sheet, plugins),
                baseStyle.copy(color = baseStyle.color.takeUnless { it == Color.Unspecified }
                    ?: sheet.headingColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurface),
                onLinkClick,
                onImageClick,
                textAlign,
                modifier = Modifier.semantics { heading() },
            )
        }
        is Paragraph -> {
            val meaningful = node.children().filterNot { it is MarkdownTextNode && it.literal.isBlank() }.toList()
            val sole = meaningful.singleOrNull()
            val htmlImage = if (enableHtml && sole is HtmlInline) SafeHtml.imageTag(sole.literal) else null
            when {
                sole is Image -> MarkdownImage(
                    SafeHtml.ImageSpec(sole.destination, sole.plainText(), sole.title, null, null), onImageClick,
                )
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                else -> MarkdownInlineText(inlineRender(node, enableHtml, sheet, plugins), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, onImageClick, textAlign)
            }
        }
        is FencedCodeBlock -> EnhancedCodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> EnhancedCodeBlock(node.literal, null)
        is BlockQuote -> Row(
            (if (sheet.quoteBackground != null) Modifier.fillMaxWidth().background(sheet.quoteBackground)
            else Modifier.fillMaxWidth()).padding(bottom = sheet.blockSpacing),
        ) {
            Box(Modifier.width(3.dp).height(44.dp).background(sheet.quoteBarColor ?: MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column {
                node.children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml, textAlign) }
            }
        }
        is BulletList, is OrderedList -> MarkdownList(node, onLinkClick, onImageClick, enableHtml)
        is TableBlock -> MarkdownTable(node, onLinkClick, onImageClick, enableHtml)
        is DetailsNode -> MarkdownDetails(node, onLinkClick, onImageClick, enableHtml)
        is BlockMathNode -> BlockMath(node)
        is PluginBlockNode -> {
            val renderer = plugins?.blockRenderer(node)
            if (renderer != null) renderer.RenderBlock(node) { child -> MarkdownBlock(child, onLinkClick, onImageClick, enableHtml) }
        }
        is FootnoteDefinitionNode -> Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                "[${node.label}]: ",
                style = (sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge).copy(fontWeight = FontWeight.Bold, color = sheet.footnoteColor),
            )
            Box(Modifier.weight(1f)) {
                MarkdownInlineText(
                    inlineRender(node, enableHtml, sheet, plugins), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                    onLinkClick, onImageClick, textAlign, bottomPadding = 0.dp,
                )
            }
        }
        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = sheet.blockSpacing), color = sheet.ruleColor ?: MaterialTheme.colorScheme.outlineVariant)
        is HtmlBlock -> {
            val htmlImage = if (enableHtml) SafeHtml.imageTag(node.literal) else null
            val imageAlt = if (enableHtml) SafeHtml.imageAlt(node.literal) else null
            val html = if (enableHtml) SafeHtml.parseBlock(node.literal) else null
            when {
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                imageAlt != null -> MarkdownText(AnnotatedString(imageAlt), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
                html is SafeHtml.Block.Rule -> HorizontalDivider(Modifier.padding(vertical = sheet.blockSpacing), color = sheet.ruleColor ?: MaterialTheme.colorScheme.outlineVariant)
                html is SafeHtml.Block.Container -> {
                    val alignment = when (html.alignment) {
                        "left" -> TextAlign.Left
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        else -> textAlign
                    }
                    if (html.name == "blockquote") {
                        Row(
                            (if (sheet.quoteBackground != null) Modifier.fillMaxWidth().background(sheet.quoteBackground)
                            else Modifier.fillMaxWidth()).padding(bottom = sheet.blockSpacing),
                        ) {
                            Box(Modifier.width(3.dp).height(44.dp).background(sheet.quoteBarColor ?: MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(12.dp))
                            Column { parseMarkdown(html.content, plugins).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                        }
                    } else {
                        Column { parseMarkdown(html.content, plugins).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                    }
                    if (html.trailing.isNotBlank()) {
                        parseMarkdown(html.trailing, plugins).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, textAlign) }
                    }
                }
                else -> MarkdownText(AnnotatedString(node.literal), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
            }
        }
        else -> MarkdownText(AnnotatedString(node.plainText()), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick)
    }
}

@Composable
private fun MarkdownDetails(
    node: DetailsNode,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    enableHtml: Boolean,
) {
    val sheet = LocalMarkdownStyleSheet.current
    val expanded = rememberSaveable(node) { mutableStateOf(node.isOpen) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .border(1.dp, sheet.tableBorderColor ?: MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .semantics { stateDescription = if (expanded.value) "Expanded" else "Collapsed" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded.value) "Collapse details" else "Expand details",
                ) { expanded.value = !expanded.value }
                .padding(12.dp),
        ) {
            Text(if (expanded.value) "⌄" else "›", style = MaterialTheme.typography.titleMedium,
                color = sheet.textColor ?: Color.Unspecified)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                val summary = node.summary.singleOrNull()
                if (summary is Paragraph) {
                    MarkdownInlineText(
                        inlineRender(summary, enableHtml, sheet, LocalParserPlugins.current), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                        onLinkClick, onImageClick, bottomPadding = 0.dp,
                        onPlainTextTap = { expanded.value = !expanded.value },
                    )
                } else {
                    Column {
                        node.summary.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
                    }
                }
            }
        }
        if (expanded.value && node.body.isNotEmpty()) {
            HorizontalDivider(color = sheet.tableBorderColor ?: MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                node.body.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: AnnotatedString, style: androidx.compose.ui.text.TextStyle, onLinkClick: (String) -> Unit, textAlign: TextAlign? = null, bottomPadding: androidx.compose.ui.unit.Dp? = null, modifier: Modifier = Modifier, onPlainTextTap: (() -> Unit)? = null) {
    val sheet = LocalMarkdownStyleSheet.current
    val onMentionClick = LocalOnMentionClick.current
    val onHashtagClick = LocalOnHashtagClick.current
    val onWikilinkClick = LocalOnWikilinkClick.current
    val foreground = if (style.color != Color.Unspecified) style.color else sheet.textColor ?: MaterialTheme.colorScheme.onSurface
    val links = text.getStringAnnotations("url", 0, text.length).filter { isSafeLink(it.item) }
    val actions = links.map { link ->
        CustomAccessibilityAction("Open link ${text.text.substring(link.start, link.end)}") {
            onLinkClick(link.item)
            true
        }
    } + pluginAccessibilityActions(text, onMentionClick, onHashtagClick, onWikilinkClick)
    val layout = remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val base = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
    Text(
        text = text,
        style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
        modifier = if (actions.isEmpty() && onPlainTextTap == null) base else base
            .then(if (actions.isEmpty()) Modifier else Modifier.semantics { customActions = actions })
            .pointerInput(text, onPlainTextTap, onLinkClick, onMentionClick, onHashtagClick, onWikilinkClick) {
                detectTapGestures { position ->
                    layout.value?.getOffsetForPosition(position)?.let { offset ->
                        dispatchTextTap(text, offset, onLinkClick, onPlainTextTap, onMentionClick, onHashtagClick, onWikilinkClick)
                    }
                }
            },
        onTextLayout = { layout.value = it },
    )
}

@Composable
private fun MarkdownInlineText(
    render: InlineRender,
    style: androidx.compose.ui.text.TextStyle,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    textAlign: TextAlign? = null,
    bottomPadding: androidx.compose.ui.unit.Dp? = null,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
    onPlainTextTap: (() -> Unit)? = null,
) {
    val sheet = LocalMarkdownStyleSheet.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val onMentionClick = LocalOnMentionClick.current
    val onHashtagClick = LocalOnHashtagClick.current
    val onWikilinkClick = LocalOnWikilinkClick.current
    val foreground = if (style.color != Color.Unspecified) style.color else sheet.textColor ?: MaterialTheme.colorScheme.onSurface
    if (render.images.isEmpty() && render.math.isEmpty()) {
        if (interactive) MarkdownText(render.text, style, onLinkClick, textAlign, bottomPadding, modifier, onPlainTextTap)
        else Text(
            render.text,
            style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier),
        )
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
            InlineImage(image, width, height, if (interactive) onImageClick else null)
        }
    }.toMutableMap()
    render.math.forEach { (id, latex) ->
        val renderer = rememberMathRenderer(latex, displayMode = false)
        val widthDp = with(density) { (renderer?.widthPx ?: (latex.length * 10f)).coerceAtLeast(1f).toDp() }
        val heightDp = with(density) { (renderer?.totalHeightPx ?: 24f).coerceAtLeast(1f).toDp() }
        inline[id] = InlineTextContent(
            placeholder = Placeholder(
                width = with(density) { widthDp.toSp() },
                height = with(density) { heightDp.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            if (renderer == null) Text("$$latex$")
            else Canvas(Modifier.width(widthDp).height(heightDp)) {
                renderer.draw(drawContext.canvas.nativeCanvas)
            }
        }
    }
    if (!interactive) {
        Text(
            text = render.text,
            inlineContent = inline,
            style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier),
        )
        return
    }
    val links = render.text.getStringAnnotations("url", 0, render.text.length).filter { isSafeLink(it.item) }
    val actions = links.map { link ->
        CustomAccessibilityAction("Open link ${render.text.text.substring(link.start, link.end)}") {
            onLinkClick(link.item)
            true
        }
    } + pluginAccessibilityActions(render.text, onMentionClick, onHashtagClick, onWikilinkClick) + render.images.values.map { image ->
        CustomAccessibilityAction("Open image ${image.alt.ifBlank { image.title ?: "Image" }}") {
            dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
            true
        }
    }
        Text(
            text = render.text,
            inlineContent = inline,
            style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
                .semantics { customActions = actions }.pointerInput(render.text, onPlainTextTap, onLinkClick, onMentionClick, onHashtagClick, onWikilinkClick) {
                detectTapGestures { position ->
                    layout.value?.getOffsetForPosition(position)?.let { offset ->
                        dispatchTextTap(render.text, offset, onLinkClick, onPlainTextTap, onMentionClick, onHashtagClick, onWikilinkClick)
                    }
                }
            },
            onTextLayout = { layout.value = it },
        )
}

@Composable
private fun InlineImage(image: SafeHtml.ImageSpec, width: Float, height: Float, onImageClick: ((String) -> Unit)?) {
    val sheet = LocalMarkdownStyleSheet.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val imageBuilder = LocalImageBuilder.current
    val model = imageModel(image.source) ?: return Text(image.alt, color = sheet.textColor ?: Color.Unspecified)
    val modifier = Modifier.width(width.dp).height(height.dp)
        .semantics { contentDescription = image.alt.ifBlank { image.title ?: "Image" } }
    Box(if (onImageClick != null) modifier.clickable(
            role = Role.Button, onClickLabel = "Open image",
        ) { dispatchImageClick(image, onImageClick, onImageClickWithMetadata) } else modifier) {
        if (imageBuilder != null) imageBuilder(image.source, image.alt, image.title)
        else SubcomposeAsyncImage(
            model = imageRequest(image.source, model),
            contentDescription = null,
            modifier = Modifier.width(width.dp).height(height.dp),
            contentScale = ContentScale.Fit,
            loading = { androidx.compose.material3.CircularProgressIndicator() },
            error = { Text(image.alt.ifBlank { image.title ?: "Image" }, color = sheet.textColor ?: Color.Unspecified) },
        )
    }
}

@Composable
private fun MarkdownImage(image: SafeHtml.ImageSpec, onImageClick: (String) -> Unit) {
    val sheet = LocalMarkdownStyleSheet.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val imageBuilder = LocalImageBuilder.current
    val url = image.source
    val model = imageModel(url)
    if (model == null) {
        Text(image.alt, modifier = Modifier.padding(bottom = sheet.blockSpacing), color = sheet.textColor ?: Color.Unspecified)
        return
    }
    var imageModifier: Modifier = Modifier
    imageModifier = if (image.width != null) imageModifier.width(image.width.dp) else imageModifier.fillMaxWidth()
    if (image.height != null) imageModifier = imageModifier.height(image.height.dp)
    Box(Modifier.padding(bottom = sheet.blockSpacing).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .semantics { contentDescription = image.alt.ifBlank { image.title ?: "Image" } }
        .clickable(role = Role.Button, onClickLabel = "Open image") {
            dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
        }) {
        if (imageBuilder != null) Box(imageModifier) { imageBuilder(url, image.alt, image.title) }
        else SubcomposeAsyncImage(
            model = imageRequest(url, model),
            contentDescription = null,
            modifier = imageModifier,
            loading = { androidx.compose.material3.CircularProgressIndicator() },
            error = { Text(image.alt.ifBlank { image.title ?: "Image" }, color = sheet.textColor ?: Color.Unspecified) },
        )
    }
}

@Composable
private fun imageRequest(source: String, model: String): Any {
    if (!isSvgImageSource(source)) return model
    val context = LocalContext.current
    return remember(context, model) {
        ImageRequest.Builder(context)
            .data(model)
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
}

internal fun isSvgImageSource(source: String): Boolean =
    source.substringBefore('#').substringBefore('?').endsWith(".svg", ignoreCase = true)

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
    val sheet = LocalMarkdownStyleSheet.current
    val start = (list as? OrderedList)?.startNumber ?: 1
    val items = list.children().filterIsInstance<ListItem>().toList()
    Column(Modifier.fillMaxWidth().padding(bottom = sheet.listSpacing)
        .semantics { collectionInfo = CollectionInfo(items.size, 1) }) {
        items.forEachIndexed { index, item ->
            val task = item.children().filterIsInstance<TaskListItemMarker>().firstOrNull()
            val marker = when {
                task != null -> if (task.isChecked) "☑" else "☐"
                list is OrderedList -> "${start + index}."
                else -> "•"
            }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
            }) {
                Text(marker, modifier = Modifier.width(sheet.listIndent), style = sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, color = sheet.textColor ?: Color.Unspecified)
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
    val sheet = LocalMarkdownStyleSheet.current
    val rows = table.children().flatMap { it.children() }.filterIsInstance<TableRow>().toList()
    val columns = rows.maxOfOrNull { it.children().filterIsInstance<TableCell>().count() } ?: 0
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = sheet.blockSpacing)
        .semantics { collectionInfo = CollectionInfo(rows.size, columns) }) {
        rows.forEachIndexed { rowIndex, row ->
            Row {
                row.children().filterIsInstance<TableCell>().forEachIndexed { columnIndex, cell ->
                    val style = if (cell.isHeader) sheet.tableHeaderStyle ?: MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else sheet.tableCellStyle ?: MaterialTheme.typography.bodyMedium
                    Box(Modifier.width(150.dp).border(0.5.dp, sheet.tableBorderColor ?: MaterialTheme.colorScheme.outline)
                        .padding(sheet.tableCellPadding).semantics(mergeDescendants = true) {
                            collectionItemInfo = CollectionItemInfo(rowIndex, 1, columnIndex, 1)
                            if (cell.isHeader) heading()
                        }) {
                        MarkdownInlineText(inlineRender(cell, enableHtml, sheet, LocalParserPlugins.current), style, onLinkClick, onImageClick)
                    }
                }
            }
        }
    }
}

internal data class InlineRender(
    val text: AnnotatedString,
    val images: Map<String, SafeHtml.ImageSpec>,
    val math: Map<String, String>,
)

internal fun inlineText(node: Node, enableHtml: Boolean, plugins: ParserPluginRegistry? = null): AnnotatedString = inlineRender(node, enableHtml, plugins = plugins).text

internal fun inlineRender(node: Node, enableHtml: Boolean, styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(), plugins: ParserPluginRegistry? = null): InlineRender {
    val images = linkedMapOf<String, SafeHtml.ImageSpec>()
    val math = linkedMapOf<String, String>()
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
            "mark" -> addStyle(SpanStyle(background = styleSheet.highlightColor), start, end)
            "sub" -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript), start, end)
            "sup" -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), start, end)
            "code", "kbd" -> addStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = styleSheet.inlineCodeBackground ?: Color.Unspecified,
                color = styleSheet.inlineCodeTextColor ?: Color.Unspecified,
            ), start, end)
            "a" -> tag.attributes["href"]?.takeIf(SafeHtml::isSafeLink)?.let { url ->
                addStyle(SpanStyle(color = styleSheet.linkColor, textDecoration = TextDecoration.Underline), start, end)
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
                        color = styleSheet.footnoteColor,
                    ), start, length,
                )
            }
            is InlineMathNode -> {
                val id = "math-${math.size}"
                math[id] = current.latex
                appendInlineContent(id, "$$${current.latex}$")
            }
            is PluginInlineNode -> {
                val presentation = plugins?.renderInline(current)
                if (presentation != null) {
                    append(presentation.text)
                    addStyle(presentation.style, start, length)
                    when (current) {
                        is MentionNode -> addStringAnnotation("mention", current.username, start, length)
                        is HashtagNode -> addStringAnnotation("hashtag", current.tag, start, length)
                        is WikilinkNode -> addStringAnnotation("wikilink", current.target, start, length)
                    }
                }
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
            is Code -> addStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = styleSheet.inlineCodeBackground ?: Color.Unspecified,
                color = styleSheet.inlineCodeTextColor ?: Color.Unspecified,
            ), start, end)
            is Strikethrough -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is Link -> if (isSafeLink(current.destination)) {
                addStyle(SpanStyle(color = styleSheet.linkColor, textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("url", current.destination, start, end)
            }
        }
    }
    node.children().forEach(::appendNode)
    }
    return InlineRender(text, images, math)
}

internal fun isSafeLink(value: String): Boolean {
    val scheme = runCatching { URI(value).scheme?.lowercase() }.getOrElse { return false }
    return scheme == null || scheme in setOf("http", "https", "mailto", "tel")
}

internal fun safeLinkAt(text: AnnotatedString, offset: Int): String? =
    text.getStringAnnotations("url", offset, offset).firstOrNull()?.item?.takeIf(::isSafeLink)

private fun pluginAccessibilityActions(
    text: AnnotatedString,
    onMentionClick: ((String) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?,
    onWikilinkClick: ((String) -> Unit)? = null,
): List<CustomAccessibilityAction> = buildList {
    if (onMentionClick != null) {
        text.getStringAnnotations("mention", 0, text.length).forEach { mention ->
            if (safeLinkAt(text, mention.start) == null) add(CustomAccessibilityAction("Open mention @${mention.item}") {
                onMentionClick(mention.item)
                true
            })
        }
    }
    if (onHashtagClick != null) {
        text.getStringAnnotations("hashtag", 0, text.length).forEach { hashtag ->
            if (safeLinkAt(text, hashtag.start) == null) add(CustomAccessibilityAction("Open hashtag #${hashtag.item}") {
                onHashtagClick(hashtag.item)
                true
            })
        }
    }
    if (onWikilinkClick != null) {
        text.getStringAnnotations("wikilink", 0, text.length).forEach { wikilink ->
            if (safeLinkAt(text, wikilink.start) == null) add(CustomAccessibilityAction("Open note ${wikilink.item}") {
                onWikilinkClick(wikilink.item)
                true
            })
        }
    }
}

internal fun dispatchTextTap(
    text: AnnotatedString,
    offset: Int,
    onLinkClick: (String) -> Unit,
    onPlainTextTap: (() -> Unit)?,
    onMentionClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null,
) {
    val link = safeLinkAt(text, offset)
    val mention = text.getStringAnnotations("mention", offset, offset).firstOrNull()?.item
    val hashtag = text.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.item
    val wikilink = text.getStringAnnotations("wikilink", offset, offset).firstOrNull()?.item
    when {
        link != null -> onLinkClick(link)
        mention != null && onMentionClick != null -> onMentionClick(mention)
        hashtag != null && onHashtagClick != null -> onHashtagClick(hashtag)
        wikilink != null && onWikilinkClick != null -> onWikilinkClick(wikilink)
        else -> onPlainTextTap?.invoke()
    }
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
