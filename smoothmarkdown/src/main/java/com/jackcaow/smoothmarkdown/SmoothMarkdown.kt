package com.jackcaow.smoothmarkdown

import android.content.ClipData
import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size as CoilSize
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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

internal fun parseMarkdown(markdown: String, plugins: ParserPluginRegistry? = null, enableCache: Boolean = true, enableHtml: Boolean = false): Node {
    // The HTML pass changes the AST, so the two parser modes need separate cache keys.
    if (enableCache && plugins == null) SmoothMarkdownCache.get(markdown, enableHtml)?.let { return it }
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
    if (enableHtml) {
        HtmlCodePostProcessor(markdown).process(document)
        HtmlKbdPostProcessor().process(document)
    }
    val result = FootnoteReferencePostProcessor(markdown).process(document)
    (plugins?.getInlinePlugin("wikilink") as? WikilinkPlugin)?.let {
        WikilinkPostProcessor.process(result, it)
    }
    if (enableCache && plugins == null) SmoothMarkdownCache.put(markdown, result, enableHtml)
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
    /** Set when an outer SelectionContainer owns one selection across all Markdown blocks. */
    selectableAsSingleRegion: Boolean = false,
    /** Reports rendered text bounds for programmatic selection by touch position. */
    onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
    /** Reuse the shared parsed document for unchanged content. Disable for rapidly changing text. */
    enableCache: Boolean = true,
    /** Allow selection and copying across the rendered Markdown blocks. */
    selectable: Boolean = false,
    /** Controls the selectable region when [selectable] is true. */
    selectionController: SmoothSelectionController? = null,
    /** Native selection-menu actions; each receives filtered visible text. */
    selectionMenuActions: List<SmoothSelectionMenuAction> = emptyList(),
    /** Hide the native Copy item when the host supplies its own copy action. */
    showDefaultCopyAction: Boolean = true,
) {
    val document = if (enableCache) remember(markdown, plugins, enableHtml) { parseMarkdown(markdown, plugins, enableHtml = enableHtml) }
        else parseMarkdown(markdown, plugins, enableCache = false, enableHtml = enableHtml)
    val blocks = remember(document) { document.children().toList() }
    val selectionGroups = remember(blocks, selectable, selectableAsSingleRegion) {
        groupSelectableBlocks(blocks, bridgeVisibleNonText = selectable || selectableAsSingleRegion)
    }
    val activeController = selectionController.takeIf { selectable && !selectableAsSingleRegion }
    val targetCallback = remember(activeController, onTextPositioned) {
        if (activeController == null) onTextPositioned
        else { target: MarkdownSelectionTarget ->
            activeController.track(target)
            onTextPositioned?.invoke(target)
            Unit
        }
    }
    val targetDisposed = remember(activeController) {
        activeController?.let { controller -> { key: Any -> controller.removeTarget(key) } }
    }
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
        LocalMarkdownSelectionOptions provides MarkdownSelectionOptions(
            selectable = selectable || selectableAsSingleRegion,
            outerRegion = selectable || selectableAsSingleRegion,
            nonTextSelectionAnchor = selectable && !selectableAsSingleRegion,
            onTextPositioned = targetCallback,
            onTextDisposed = targetDisposed,
        ),
    ) {
        val backgroundModifier = if (styleSheet.backgroundColor != null) modifier.background(styleSheet.backgroundColor) else modifier
        val content: @Composable () -> Unit = {
            if (scrollable) {
                LazyColumn(
                    modifier = backgroundModifier,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(styleSheet.contentPadding),
                ) {
                    itemsIndexed(selectionGroups) { _, group ->
                        MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml, selectable)
                    }
                }
            } else {
                Column(backgroundModifier.padding(styleSheet.contentPadding)) {
                    selectionGroups.forEach { group ->
                        MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml, selectable)
                    }
                }
            }
        }
        if (selectable && !selectableAsSingleRegion) {
            MarkdownSelectionRegion(activeController, selectionMenuActions, showDefaultCopyAction, content)
        } else content()
    }
}

@Composable
private fun MarkdownSelectionRegion(
    controller: SmoothSelectionController?,
    menuActions: List<SmoothSelectionMenuAction>,
    showDefaultCopyAction: Boolean,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val anchorRegistry = remember { NonTextAnchorRegistry() }
    // SelectionState captures LocalClipboard when it is created. Keep a stable
    // reference so the clipboard can inspect annotated ranges at Copy time.
    val stateHolder = remember { ReaderSelectionStateHolder() }
    val readerClipboard = remember(clipboard, stateHolder, anchorRegistry) {
        object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry()
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                clipboard.setClipEntry(readerCopyClipEntry(
                    clipEntry, stateHolder.state?.selectedTexts.orEmpty(), anchorRegistry.snapshot()))
            }
        }
    }
    val scope = rememberCoroutineScope()
    val copyVisible: (String) -> Unit = { text ->
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("selection", text)))
        }
        stateHolder.state?.clear()
    }
    val toolbarProvider = LocalTextContextMenuToolbarProvider.current
    val dropdownProvider = LocalTextContextMenuDropdownProvider.current
    val legacyToolbar = LocalTextToolbar.current
    val wrappedToolbar = remember(toolbarProvider, stateHolder, anchorRegistry, clipboard) {
        toolbarProvider?.let { provider ->
            ReaderCopyMenuProvider(provider,
                { visibleSelectedText(stateHolder.state?.selectedTexts.orEmpty(), anchorRegistry.snapshot()) }, copyVisible)
        }
    }
    val wrappedDropdown = remember(dropdownProvider, stateHolder, anchorRegistry, clipboard) {
        dropdownProvider?.let { provider ->
            ReaderCopyMenuProvider(provider,
                { visibleSelectedText(stateHolder.state?.selectedTexts.orEmpty(), anchorRegistry.snapshot()) }, copyVisible)
        }
    }
    val wrappedLegacyToolbar = remember(legacyToolbar, stateHolder, anchorRegistry, clipboard, showDefaultCopyAction) {
        ReaderCopyTextToolbar(legacyToolbar,
            { visibleSelectedText(stateHolder.state?.selectedTexts.orEmpty(), anchorRegistry.snapshot()) },
            copyVisible, showDefaultCopyAction)
    }
    CompositionLocalProvider(
        LocalNonTextAnchorRegistry provides anchorRegistry,
        LocalTextContextMenuToolbarProvider provides wrappedToolbar,
        LocalTextContextMenuDropdownProvider provides wrappedDropdown,
        LocalTextToolbar provides wrappedLegacyToolbar,
        LocalClipboard provides readerClipboard,
    ) {
        val state = rememberSelectionState()
        stateHolder.state = state
        DisposableEffect(controller, state) {
            controller?.attach(state)
            onDispose {
                controller?.detach(state)
                if (stateHolder.state === state) stateHolder.state = null
            }
        }
        val filterModifier = if (showDefaultCopyAction) Modifier else Modifier.filterTextContextMenuComponents {
            it.key != TextContextMenuKeys.CopyKey
        }
        val menuModifier = if (menuActions.isEmpty()) filterModifier else filterModifier.appendTextContextMenuComponents {
            if (state.selectedTexts.any { it.isNotEmpty() }) {
                separator()
                for (action in menuActions) {
                    item(key = action.key, label = action.label) {
                        action.onClick(visibleSelectedText(state.selectedTexts, anchorRegistry.snapshot()).text)
                        close()
                    }
                }
            }
        }
        val keyboardCopy = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.C &&
                (event.isCtrlPressed || event.isMetaPressed)) {
                val selected = visibleSelectedText(state.selectedTexts, anchorRegistry.snapshot())
                if (selected.hadAnchor) {
                    copyVisible(selected.text)
                    true
                } else false
            } else false
        }
        SelectionContainer(state = state, modifier = menuModifier.then(keyboardCopy)) { content() }
    }
}

private class ReaderSelectionStateHolder {
    var state: SelectionState? = null
}

internal data class VisibleSelection(val text: String, val hadAnchor: Boolean)

/** Compose's system floating Copy writes directly through LocalClipboard on Android. */
internal fun readerCopyClipEntry(
    entry: ClipEntry?,
    selectedTexts: List<AnnotatedString>,
    anchors: Set<String>,
): ClipEntry? {
    if (entry == null || entry.clipData.itemCount != 1) return entry
    val incoming = entry.clipData.getItemAt(0).text?.toString() ?: return entry
    val selected = visibleSelectedText(selectedTexts, anchors)
    if (!selected.hadAnchor || incoming != selectedTexts.joinToString("\n") { it.text }) return entry
    return ClipEntry(ClipData.newPlainText(entry.clipData.description.label ?: "selection", selected.text))
}

/** Strip only selected ranges annotated by this mounted Reader's nontext blocks. */
internal fun visibleSelectedText(selectedTexts: List<AnnotatedString>, anchors: Set<String>): VisibleSelection {
    var hadAnchor = false
    val visible = mutableListOf<String>()
    for (selected in selectedTexts) {
        val ranges = selected.getStringAnnotations(nonTextAnchorAnnotationTag, 0, selected.length)
            .filter { it.item in anchors && it.start < it.end }
            .sortedBy { it.start }
        if (ranges.isEmpty()) {
            visible += selected.text
            continue
        }
        hadAnchor = true
        val text = buildString {
            var position = 0
            for (range in ranges) {
                if (range.start > position) append(selected.text, position, range.start)
                position = maxOf(position, range.end)
            }
            if (position < selected.length) append(selected.text, position, selected.length)
        }
        if (text.isNotEmpty()) visible += text
    }
    return VisibleSelection(visible.joinToString("\n"), hadAnchor)
}

@Composable
private fun MarkdownSelectionGroup(
    group: List<Node>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    enableHtml: Boolean,
    selectable: Boolean,
) {
    val outerRegion = LocalMarkdownSelectionOptions.current.outerRegion
    if (group.size == 1 && (group.single() is FencedCodeBlock || group.single() is IndentedCodeBlock)) {
        MarkdownBlock(group.single(), onLinkClick, onImageClick, enableHtml)
    } else if (outerRegion || !selectable) {
        Column { group.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) } }
    } else {
        SelectionContainer {
            Column {
                group.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
            }
        }
    }
}

/** Keep text around visible nontext blocks mounted in one lazy item for a shared selection range. */
internal fun groupSelectableBlocks(blocks: List<Node>, bridgeVisibleNonText: Boolean = false): List<List<Node>> {
    val groups = mutableListOf<List<Node>>()
    val pending = mutableListOf<Node>()
    fun flush() { if (pending.isNotEmpty()) { groups += pending.toList(); pending.clear() } }
    for (block in blocks) {
        val prose = block is Heading || block is Paragraph || block is BlockQuote ||
            block is BulletList || block is OrderedList ||
            (bridgeVisibleNonText && (block is TableBlock || block is ThematicBreak))
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
            val resolvedStyle = baseStyle.copy(color = baseStyle.color.takeUnless { it == Color.Unspecified }
                ?: sheet.headingColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurface)
            val primary = MaterialTheme.colorScheme.primary
            val decorated = node.level <= 2
            val barHeight = with(LocalDensity.current) {
                if (resolvedStyle.fontSize.isSpecified) resolvedStyle.fontSize.toDp() else 24.dp
            }
            Column(Modifier.fillMaxWidth().padding(bottom = sheet.blockSpacing)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (decorated) {
                        Box(
                            Modifier.padding(end = 12.dp)
                                .width(4.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.3f)))),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        MarkdownInlineText(
                            inlineRender(node, enableHtml, sheet, plugins),
                            resolvedStyle,
                            onLinkClick,
                            onImageClick,
                            textAlign,
                            bottomPadding = 0.dp,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
                if (decorated) {
                    Box(
                        Modifier.fillMaxWidth().height(2.dp).background(
                            Brush.horizontalGradient(listOf(primary.copy(alpha = 0.3f), primary.copy(alpha = 0f))),
                        ),
                    )
                }
            }
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
        is BlockQuote -> MarkdownBlockquote(sheet) {
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
        is ThematicBreak -> SelectableNonTextBlock {
            HorizontalDivider(
                Modifier.padding(vertical = sheet.blockSpacing),
                thickness = sheet.horizontalRuleThickness,
                color = sheet.ruleColor ?: MaterialTheme.colorScheme.outlineVariant,
            )
        }
        is HtmlBlock -> {
            val htmlImage = if (enableHtml) SafeHtml.imageTag(node.literal) else null
            val imageAlt = if (enableHtml) SafeHtml.imageAlt(node.literal) else null
            val html = if (enableHtml) SafeHtml.parseBlock(node.literal) else null
            when {
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                imageAlt != null -> MarkdownText(AnnotatedString(imageAlt), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
                html is SafeHtml.Block.Rule -> SelectableNonTextBlock {
                    HorizontalDivider(
                        Modifier.padding(vertical = sheet.blockSpacing),
                        thickness = sheet.horizontalRuleThickness,
                        color = sheet.ruleColor ?: MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                html is SafeHtml.Block.Container -> {
                    val alignment = when (html.alignment) {
                        "left" -> TextAlign.Left
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        else -> textAlign
                    }
                    if (html.name == "blockquote") {
                        MarkdownBlockquote(sheet) {
                            Column { parseMarkdown(html.content, plugins, enableHtml = true).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                        }
                    } else {
                        Column { parseMarkdown(html.content, plugins, enableHtml = true).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, alignment) } }
                    }
                    if (html.trailing.isNotBlank()) {
                        parseMarkdown(html.trailing, plugins, enableHtml = true).children().forEach { MarkdownBlock(it, onLinkClick, onImageClick, true, textAlign) }
                    }
                }
                else -> MarkdownText(AnnotatedString(node.literal), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, textAlign)
            }
        }
        else -> MarkdownText(AnnotatedString(node.plainText()), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick)
    }
}

internal data class ResolvedBlockquoteDecoration(
    val backgroundColor: Color?,
    val borderColor: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
)

internal fun resolveBlockquoteDecoration(sheet: MarkdownStyleSheet, defaultBorderColor: Color): ResolvedBlockquoteDecoration {
    val override = sheet.blockquoteDecoration
    return ResolvedBlockquoteDecoration(
        backgroundColor = override?.backgroundColor ?: if (override == null) sheet.quoteBackground else null,
        borderColor = override?.borderColor ?: sheet.quoteBarColor ?: defaultBorderColor,
        borderWidth = override?.borderWidth ?: 4.dp,
    )
}

@Composable
private fun MarkdownBlockquote(sheet: MarkdownStyleSheet, content: @Composable () -> Unit) {
    val decoration = resolveBlockquoteDecoration(sheet, MaterialTheme.colorScheme.primary)
    val background = decoration.backgroundColor?.let { Modifier.background(it) } ?: Modifier
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = sheet.blockSpacing)
            .testTag("markdown-blockquote")
            .then(background)
            .drawBehind {
                if (decoration.borderWidth.value > 0) {
                    drawRect(
                        color = decoration.borderColor,
                        size = Size(decoration.borderWidth.toPx().coerceAtMost(size.width), size.height),
                    )
                }
            }
            .padding(sheet.blockquotePadding),
    ) {
        content()
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
    val selectionOptions = LocalMarkdownSelectionOptions.current
    val selectionKey = remember { Any() }
    DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
        onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
    }
    val tracking = selectionOptions.onTextPositioned?.let { callback ->
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            callback(MarkdownSelectionTarget(
                selectionKey, bounds, text,
                offsetAtWindowPosition = { windowPoint ->
                    layout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                },
            ).apply {
                wordBoundaryAtWindowPosition = { windowPoint ->
                    layout.value?.let { result ->
                        result.getWordBoundary(result.getOffsetForPosition(windowPoint - bounds.topLeft))
                    } ?: TextRange(0)
                }
                containsTextAtWindowPosition = { windowPoint ->
                    textLayoutContainsWindowPoint(layout.value, windowPoint, bounds)
                }
            })
        }
    } ?: Modifier
    val base = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier).then(tracking)
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
    val selectionOptions = LocalMarkdownSelectionOptions.current
    val selectionKey = remember { Any() }
    val layout = remember(render.text) { mutableStateOf<TextLayoutResult?>(null) }
    DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
        onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
    }
    val tracking = selectionOptions.onTextPositioned?.let { callback ->
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            callback(MarkdownSelectionTarget(
                selectionKey, bounds, render.text,
                offsetAtWindowPosition = { windowPoint ->
                    layout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                },
            ).apply {
                wordBoundaryAtWindowPosition = { windowPoint ->
                    layout.value?.let { result ->
                        result.getWordBoundary(result.getOffsetForPosition(windowPoint - bounds.topLeft))
                    } ?: TextRange(0)
                }
                containsTextAtWindowPosition = { windowPoint ->
                    textLayoutContainsWindowPoint(layout.value, windowPoint, bounds)
                }
            })
        }
    } ?: Modifier
    if (render.images.isEmpty() && render.math.isEmpty() && render.kbds.isEmpty()) {
        if (interactive) MarkdownText(render.text, style, onLinkClick, textAlign, bottomPadding, modifier, onPlainTextTap)
        else Text(
            render.text,
            style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier).then(tracking),
        )
        return
    }
    BoxWithConstraints {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxImageWidth = maxWidth.value.takeIf { it.isFinite() && it > 0f }
            ?: LocalConfiguration.current.screenWidthDp.toFloat()
        val customImageBuilder = LocalImageBuilder.current
        val inline = render.images.mapValues { (_, image) ->
            val natural = if (customImageBuilder == null && (image.width == null || image.height == null))
                rememberImageIntrinsicSize(image.source) else null
            val size = imageSize(image.width, image.height, natural, maxImageWidth)
            InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { size.width.dp.toSp() },
                    height = with(density) { size.height.dp.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                InlineImage(image, size.width, size.height, if (interactive) onImageClick else null)
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
        render.kbds.forEach { (id, label) ->
            val border = sheet.ruleColor ?: Color(0xFFBDBDBD)
            val base = sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge
            val keyStyle = base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                .merge(sheet.kbdStyle).let { it.copy(color = it.color.takeUnless { color -> color == Color.Unspecified } ?: foreground) }
            val measured = textMeasurer.measure(label, style = keyStyle, maxLines = 1, softWrap = false)
            val width = with(density) { measured.size.width.toDp() } + 12.dp
            val height = with(density) { measured.size.height.toDp() } + 4.dp
            val shape = RoundedCornerShape(4.dp)
            inline[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { width.toSp() },
                    height = with(density) { height.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Box(Modifier.background(border.copy(alpha = 0.12f), shape).border(1.dp, border, shape)
                    .padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(label, style = keyStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
            }
        }
        if (!interactive) {
            Text(
                text = render.text,
                inlineContent = inline,
                style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier).then(tracking),
            )
        } else {
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
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier).then(tracking)
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
    }
}

@Composable
private fun rememberImageIntrinsicSize(source: String): ImageSize? {
    val model = imageModel(source) ?: return null
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val request = remember(context, model) {
        ImageRequest.Builder(context).data(model).size(CoilSize.ORIGINAL).apply {
            if (isSvgImageSource(source)) decoderFactory(SvgDecoder.Factory())
        }.build()
    }
    return produceState<ImageSize?>(null, imageLoader, request) {
        val result = imageLoader.execute(request) as? SuccessResult ?: return@produceState
        val drawable = result.drawable
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            value = ImageSize(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        }
    }.value
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
    val natural = if (imageBuilder == null && (image.width == null || image.height == null))
        rememberImageIntrinsicSize(url) else null
    SelectableNonTextBlock(onClick = {
        dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
    }) {
        BoxWithConstraints {
            val maxImageWidth = maxWidth.value.takeIf { it.isFinite() && it > 0f }
                ?: LocalConfiguration.current.screenWidthDp.toFloat()
            val size = imageSize(image.width, image.height, natural, maxImageWidth)
            val imageModifier = Modifier.width(size.width.dp).height(size.height.dp)
            val customModifier = Modifier
                .then(if (image.width != null) Modifier.width(image.width.dp) else Modifier)
                .then(if (image.height != null) Modifier.height(image.height.dp) else Modifier)
            Box(Modifier.padding(bottom = sheet.blockSpacing).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = image.alt.ifBlank { image.title ?: "Image" } }
                .clickable(role = Role.Button, onClickLabel = "Open image") {
                    dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
                }) {
                if (imageBuilder != null) Box(customModifier) { imageBuilder(url, image.alt, image.title) }
                else SubcomposeAsyncImage(
                    model = imageRequest(url, model),
                    contentDescription = null,
                    modifier = imageModifier,
                    loading = { androidx.compose.material3.CircularProgressIndicator() },
                    error = { Text(image.alt.ifBlank { image.title ?: "Image" }, color = sheet.textColor ?: Color.Unspecified) },
                )
            }
        }
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
                val markerStyle = if (task == null) sheet.listBulletStyle ?: sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge
                    else sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge
                val markerColor = if (task == null) markerStyle.color.takeUnless { it == Color.Unspecified } ?: sheet.textColor ?: Color.Unspecified
                    else sheet.textColor ?: Color.Unspecified
                Text(
                    marker,
                    modifier = Modifier.width(sheet.listIndent),
                    style = markerStyle,
                    color = markerColor,
                )
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
    val columns = (rows.maxOfOrNull { it.children().filterIsInstance<TableCell>().count() } ?: 0).coerceAtLeast(1)
    val border = resolveTableBorder(sheet, MaterialTheme.colorScheme.outline)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = sheet.blockSpacing)
        .semantics { collectionInfo = CollectionInfo(rows.size, columns) }) {
        rows.forEachIndexed { rowIndex, row ->
            val cells = row.children().filterIsInstance<TableCell>().toList()
            val rowIsHeader = cells.firstOrNull()?.isHeader == true
            Row(Modifier.height(IntrinsicSize.Min)) {
                repeat(columns) { columnIndex ->
                    val cell = cells.getOrNull(columnIndex)
                    val style = if (cell?.isHeader == true) sheet.tableHeaderStyle ?: MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else sheet.tableCellStyle ?: MaterialTheme.typography.bodyMedium
                    val edges = tableCellBorderEdges(border, rowIndex, rows.size, columnIndex, columns)
                    Box(Modifier.width(150.dp).fillMaxHeight()
                        .then(if (rowIsHeader) sheet.tableHeaderBackgroundColor?.let { Modifier.background(it) } ?: Modifier else Modifier)
                        .drawBehind {
                            fun drawEdge(side: MarkdownTableBorderSide?, x: Float, y: Float, width: Float, height: Float) {
                                if (side == null || side.width.value == 0f) return
                                drawRect(side.color, topLeft = Offset(x, y), size = Size(width, height))
                            }
                            edges.top?.let { drawEdge(it, 0f, 0f, size.width, it.width.toPx().coerceAtMost(size.height)) }
                            edges.right?.let {
                                val stroke = it.width.toPx().coerceAtMost(size.width)
                                drawEdge(it, size.width - stroke, 0f, stroke, size.height)
                            }
                            edges.bottom?.let {
                                val stroke = it.width.toPx().coerceAtMost(size.height)
                                drawEdge(it, 0f, size.height - stroke, size.width, stroke)
                            }
                            edges.left?.let { drawEdge(it, 0f, 0f, it.width.toPx().coerceAtMost(size.width), size.height) }
                        }
                        .padding(sheet.tableCellPadding).semantics(mergeDescendants = true) {
                            collectionItemInfo = CollectionItemInfo(rowIndex, 1, columnIndex, 1)
                            if (cell?.isHeader == true) heading()
                        }) {
                        if (cell != null) MarkdownInlineText(inlineRender(cell, enableHtml, sheet, LocalParserPlugins.current), style, onLinkClick, onImageClick)
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
    val kbds: Map<String, String>,
)

internal fun inlineText(node: Node, enableHtml: Boolean, plugins: ParserPluginRegistry? = null): AnnotatedString = inlineRender(node, enableHtml, plugins = plugins).text

internal fun inlineRender(node: Node, enableHtml: Boolean, styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(), plugins: ParserPluginRegistry? = null): InlineRender {
    val images = linkedMapOf<String, SafeHtml.ImageSpec>()
    val math = linkedMapOf<String, String>()
    val kbds = linkedMapOf<String, String>()
    val boldSpan = SpanStyle(fontWeight = FontWeight.Bold).merge(styleSheet.boldStyle)
    val italicSpan = SpanStyle(fontStyle = FontStyle.Italic).merge(styleSheet.italicStyle)
    val strikeSpan = SpanStyle(textDecoration = TextDecoration.LineThrough).merge(styleSheet.strikethroughStyle)
    val underlineSpan = SpanStyle(textDecoration = TextDecoration.Underline).merge(styleSheet.underlineStyle)
    val highlightSpan = SpanStyle(background = styleSheet.highlightColor).merge(styleSheet.highlightStyle)
    val linkSpan = SpanStyle(color = styleSheet.linkColor, textDecoration = TextDecoration.Underline).merge(styleSheet.linkStyle)
    val codeSpan = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = styleSheet.inlineCodeBackground ?: Color.Unspecified,
        color = styleSheet.inlineCodeTextColor ?: Color.Unspecified,
    ).merge(styleSheet.inlineCodeStyle)
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
            "b", "strong" -> addStyle(boldSpan, start, end)
            "i", "em" -> addStyle(italicSpan, start, end)
            "s", "del", "strike" -> addStyle(strikeSpan, start, end)
            "u", "ins" -> addStyle(underlineSpan, start, end)
            "mark" -> addStyle(highlightSpan, start, end)
            "sub" -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
                .merge(styleSheet.subscriptStyle), start, end)
            "sup" -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
                .merge(styleSheet.superscriptStyle), start, end)
            "code" -> addStyle(codeSpan, start, end)
            "a" -> tag.attributes["href"]?.takeIf(SafeHtml::isSafeLink)?.let { url ->
                addStyle(linkSpan, start, end)
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
            is HtmlKbdNode -> {
                if (current.label.isNotEmpty()) {
                    val id = "kbd-${kbds.size}"
                    kbds[id] = current.label
                    appendInlineContent(id, current.label)
                }
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
            is StrongEmphasis -> addStyle(boldSpan, start, end)
            is Emphasis -> addStyle(italicSpan, start, end)
            is Code -> addStyle(codeSpan, start, end)
            is Strikethrough -> addStyle(strikeSpan, start, end)
            is Link -> if (isSafeLink(current.destination)) {
                addStyle(linkSpan, start, end)
                addStringAnnotation("url", current.destination, start, end)
            }
        }
    }
    node.children().forEach(::appendNode)
    }
    return InlineRender(text, images, math, kbds)
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
