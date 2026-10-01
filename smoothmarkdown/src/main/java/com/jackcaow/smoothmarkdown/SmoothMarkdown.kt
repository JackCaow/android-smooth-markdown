package com.jackcaow.smoothmarkdown

import android.content.ClipData
import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalTextToolbar
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
import androidx.compose.ui.platform.LocalConfiguration
import com.jackcaow.smoothmarkdown.ast.Strikethrough
import com.jackcaow.smoothmarkdown.ast.TableBlock
import com.jackcaow.smoothmarkdown.ast.TableCell
import com.jackcaow.smoothmarkdown.ast.TableRow
import com.jackcaow.smoothmarkdown.ast.TaskListItemMarker
import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.Code
import com.jackcaow.smoothmarkdown.ast.Emphasis
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.HardLineBreak
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.HtmlBlock
import com.jackcaow.smoothmarkdown.ast.HtmlInline
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.IndentedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Link
import com.jackcaow.smoothmarkdown.ast.ListItem
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.OrderedList
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.SoftLineBreak
import com.jackcaow.smoothmarkdown.ast.StrongEmphasis
import com.jackcaow.smoothmarkdown.ast.ThematicBreak
import com.jackcaow.smoothmarkdown.ast.Text as MarkdownTextNode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private val baseParser = NativeMarkdownParser(enableGFM = true, enableExtensions = true)

internal fun parseMarkdown(markdown: String, plugins: ParserPluginRegistry? = null, enableCache: Boolean = true, enableHtml: Boolean = false): Node {
    // The HTML pass changes the AST, so the two parser modes need separate cache keys.
    if (enableCache && plugins == null) SmoothMarkdownCache.get(markdown, enableHtml)?.let { return it }
    val parser = if (plugins == null) baseParser else NativeMarkdownParser(enableGFM = true, enableExtensions = true, plugins = plugins)
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
private val LocalMarkdownBuilders = compositionLocalOf<MarkdownBuilderRegistry?> { null }
private val LocalReaderSelectionController = compositionLocalOf<SmoothSelectionController?> { null }
private val LocalMarkdownEnableHtml = compositionLocalOf { false }
private val LocalEnhancedComponents = compositionLocalOf { false }
private val LocalOnImageClickWithMetadata = compositionLocalOf<((String, String?, String?) -> Unit)?> { null }
private val LocalImageBuilder = compositionLocalOf<(@Composable (String, String?, String?) -> Unit)?> { null }
private val LocalOnMentionClick = compositionLocalOf<((String) -> Unit)?> { null }
private val LocalOnHashtagClick = compositionLocalOf<((String) -> Unit)?> { null }
private val LocalOnWikilinkClick = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun enhancedLinkDecoration(
    text: AnnotatedString,
    layout: androidx.compose.runtime.State<TextLayoutResult?>,
): Modifier {
    val sheet = LocalMarkdownStyleSheet.current
    val color = sheet.linkStyle?.color?.takeIf { it != Color.Unspecified } ?: sheet.linkColor
    return enhancedLinkDecoration(text, layout, LocalEnhancedComponents.current, color)
}

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
    codeBlockOptions: CodeBlockOptions? = null,
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
    /** Custom renderers for parsed CommonMark nodes; these override built-in rendering. */
    builderRegistry: MarkdownBuilderRegistry? = null,
    /** Match Flutter's opt-in decorative headers, quotes, links and code controls. */
    useEnhancedComponents: Boolean = false,
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
    onCodeCopiedWithMetadata: ((String, String?) -> Unit)? = null,
) {
    val pluginVersion = plugins?.version
    val builderVersion = builderRegistry?.version
    val resolvedStyleSheet = styleSheet.resolved()
    val streamingDocument = LocalStreamingMarkdownDocument.current?.takeIf {
        it.source == markdown && it.plugins === plugins && it.enableHtml == enableHtml
    }
    val document = streamingDocument?.document ?: if (enableCache)
        remember(markdown, plugins, pluginVersion, enableHtml) { parseMarkdown(markdown, plugins, enableHtml = enableHtml) }
    else parseMarkdown(markdown, plugins, enableCache = false, enableHtml = enableHtml)
    val sourceOrderMap = remember(document) { readerSelectionSourceOrder(document) }
    val blocks = remember(document) { document.children().toList() }
    val selectionGroups = remember(blocks, selectable, selectableAsSingleRegion, codeBlockBuilder, builderRegistry, builderVersion, plugins, pluginVersion) {
        groupSelectableBlocks(
            blocks,
            bridgeVisibleNonText = selectable || selectableAsSingleRegion,
            bridgeBuiltInCode = (selectable || selectableAsSingleRegion) && codeBlockBuilder == null,
            bridgeDetails = selectable || selectableAsSingleRegion,
            selectionMode = { node -> if (selectable || selectableAsSingleRegion)
                readerBlockSelectionMode(node, builderRegistry, plugins) else null },
        )
    }
    val localController = remember { SmoothSelectionController() }
    val activeController = (selectionController ?: localController).takeIf { selectable && !selectableAsSingleRegion }
    SideEffect {
        activeController?.bindDocument(
            document, enableHtml, plugins, builderRegistry,
            customCodeBuilder = codeBlockBuilder != null,
            customImageBuilder = imageBuilder != null,
        )
    }
    DisposableEffect(activeController, document) {
        onDispose { activeController?.unbindDocument(document) }
    }
    val fullDocumentSelectionMode = activeController?.fullDocumentSelectionMode == true
    val lazyListState = rememberLazyListState()
    val fullScrollState = rememberScrollState()
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
        LocalReaderSelectionSourceOrder provides sourceOrderMap,
        LocalMarkdownResources provides resourceOptions,
        LocalMarkdownStrings provides strings,
        LocalCodeBlockOptions provides (codeBlockOptions ?: if (useEnhancedComponents) CodeBlockOptions() else CodeBlockOptions(
            showCopyButton = false, showLanguageTag = false, enableSyntaxHighlighting = false)),
        LocalCodeBlockBuilder provides codeBlockBuilder,
        LocalOnCodeCopied provides onCodeCopied,
        LocalOnCodeCopiedWithMetadata provides onCodeCopiedWithMetadata,
        LocalMarkdownStyleSheet provides resolvedStyleSheet,
        LocalParserPlugins provides plugins,
        LocalMarkdownBuilders provides builderRegistry,
        LocalReaderSelectionController provides activeController,
        LocalMarkdownEnableHtml provides enableHtml,
        LocalEnhancedComponents provides useEnhancedComponents,
        LocalOnImageClickWithMetadata provides onImageClickWithMetadata,
        LocalImageBuilder provides imageBuilder,
        LocalOnMentionClick provides onMentionClick,
        LocalOnHashtagClick provides onHashtagClick,
        LocalOnWikilinkClick provides onWikilinkClick,
        LocalMarkdownSelectionOptions provides ReaderSelectionOptions(
            selectable = selectable || selectableAsSingleRegion,
            outerRegion = selectable || selectableAsSingleRegion,
            nonTextSelectionAnchor = selectable && !selectableAsSingleRegion,
            onTextPositioned = targetCallback,
            onTextDisposed = targetDisposed,
        ),
    ) {
        val backgroundModifier = if (resolvedStyleSheet.backgroundColor != null) modifier.background(resolvedStyleSheet.backgroundColor) else modifier
        val content: @Composable () -> Unit = {
            if (scrollable && !fullDocumentSelectionMode) {
                LazyColumn(
                    modifier = backgroundModifier,
                    state = lazyListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(resolvedStyleSheet.contentPadding),
                ) {
                    // Lazy saved-state keys must be Bundle-compatible; retained stream blocks
                    // receive monotonic Long IDs, while ordinary readers preserve index keys.
                    itemsIndexed(selectionGroups, key = if (streamingDocument == null) null else { _, group ->
                        streamingDocument.blockKeys.getValue(group.first())
                    }) { _, group ->
                        MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml, selectable)
                    }
                }
            } else {
                val fullModifier = if (scrollable) backgroundModifier.verticalScroll(fullScrollState)
                    else backgroundModifier
                Column(fullModifier.padding(resolvedStyleSheet.contentPadding).onGloballyPositioned {
                    if (fullDocumentSelectionMode) activeController?.onFullDocumentLaidOut()
                }) {
                    selectionGroups.forEach { group ->
                        if (streamingDocument == null) {
                            MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml, selectable)
                        } else key(streamingDocument.blockKeys.getValue(group.first())) {
                            MarkdownSelectionGroup(group, onLinkClick, onImageClick, enableHtml, selectable)
                        }
                    }
                }
            }
        }
        if (selectable && !selectableAsSingleRegion) {
            MarkdownSelectionRegion(activeController, selectionMenuActions, showDefaultCopyAction,
                onScroll = { delta -> if (scrollable) {
                    if (fullDocumentSelectionMode) fullScrollState.scrollBy(delta) else lazyListState.scrollBy(delta)
                } }, content = content)
        } else content()
    }
}

@Composable
private fun MarkdownSelectionRegion(
    controller: SmoothSelectionController?,
    menuActions: List<SmoothSelectionMenuAction>,
    showDefaultCopyAction: Boolean,
    onScroll: suspend (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val anchorRegistry = remember { NonTextAnchorRegistry() }
    val state = remember { ReaderSelectionState() }
    val selectionForCopy: () -> VisibleSelection = {
        val selected = state.selectedTexts
        controller?.fullDocumentSemanticText(selected)?.let { VisibleSelection(it, true) }
            ?: visibleSelectedText(selected, anchorRegistry.snapshot())
    }
    val copyVisible: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        state.clear()
        controller?.exitFullDocumentSelection()
    }
    val menuObserver = LocalReaderSelectionMenuObserver.current
    val menu = remember(view, state) { ReaderCopyMenuProvider(view) }
    DisposableEffect(menu) { onDispose { menu.hide() } }
    val selectedTexts = state.selectedTexts
    // Subscribe during composition so ending a drag republishes the native toolbar.
    val dragging = state.dragPosition != null
    SideEffect {
        if (selectedTexts.isNotEmpty() && !dragging) {
            menu.show(state.selectionBounds, selectionForCopy, copyVisible,
                menuActions, showDefaultCopyAction, state::selectAll, { state.clear(); controller?.exitFullDocumentSelection() }, state.selectionRevision)
            menuObserver?.invoke(menu.snapshot())
        } else { menu.hide(); menuObserver?.invoke(null) }
    }
    DisposableEffect(controller, state, anchorRegistry, clipboard) {
        controller?.attach(state, anchorRegistry, copyVisible)
        onDispose { controller?.detach(state) }
    }
    val fullRequest = controller?.fullDocumentSelectRequest ?: 0
    val fullReady = controller?.fullDocumentLayoutReady ?: 0
    LaunchedEffect(controller, state, fullRequest, fullReady) {
        if (controller != null && fullRequest > 0 && fullReady == fullRequest &&
            controller.fullDocumentSelectionMode) {
            withFrameNanos { }
            if (controller.fullDocumentSelectRequest == fullRequest && controller.fullDocumentSelectionMode) {
                state.selectAll()
                if (state.selectedTexts.isEmpty()) controller.exitFullDocumentSelection()
                else controller.markFullDocumentSelected(fullRequest, state.selectedTexts)
            }
        }
    }
    LaunchedEffect(controller, state) {
        snapshotFlow { state.selectedTexts.isNotEmpty() }.collect { hasSelection ->
            if (!hasSelection && controller?.fullDocumentSelectionEstablished == true) {
                controller.exitFullDocumentSelection()
            }
        }
    }
    val keyboardCopy = Modifier.onPreviewKeyEvent { event ->
        if (event.key == Key.Back && state.selectedTexts.isNotEmpty()) {
            if (event.type == KeyEventType.KeyUp) { state.clear(); controller?.exitFullDocumentSelection() }
            true
        } else if (event.type == KeyEventType.KeyDown && event.key == Key.C &&
            (event.isCtrlPressed || event.isMetaPressed) && state.selectedTexts.isNotEmpty()) {
            copyVisible(selectionForCopy().text)
            true
        } else false
    }
    CompositionLocalProvider(LocalNonTextAnchorRegistry provides anchorRegistry) {
        ReaderSelectionRegion(state, keyboardCopy, onScroll, content)
    }
}

internal data class VisibleSelection(val text: String, val hadAnchor: Boolean)

/** Filter only this Reader's annotated nontext geometry while preserving unrelated clipboard text. */
internal fun readerCopyText(
    incoming: AnnotatedString?,
    selectedTexts: List<AnnotatedString>,
    anchors: Set<String>,
    fullDocumentSemanticText: String? = null,
): AnnotatedString? {
    if (incoming == null || incoming.text != selectedTexts.joinToString("\n") { it.text }) return incoming
    if (fullDocumentSemanticText != null) return AnnotatedString(fullDocumentSemanticText)
    val selected = visibleSelectedText(selectedTexts, anchors)
    return if (selected.hadAnchor) AnnotatedString(selected.text) else incoming
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
    val streamKeys = LocalStreamingMarkdownDocument.current?.blockKeys
    if (group.size == 1 && (group.single() is FencedCodeBlock || group.single() is IndentedCodeBlock)) {
        MarkdownBlock(group.single(), onLinkClick, onImageClick, enableHtml)
    } else if (outerRegion || !selectable) {
        Column { group.forEach { node ->
            if (streamKeys == null) MarkdownBlock(node, onLinkClick, onImageClick, enableHtml)
            else key(streamKeys[node] ?: node) { MarkdownBlock(node, onLinkClick, onImageClick, enableHtml) }
        } }
    } else {
        SelectionContainer {
            Column {
                group.forEach { node ->
                    if (streamKeys == null) MarkdownBlock(node, onLinkClick, onImageClick, enableHtml)
                    else key(streamKeys[node] ?: node) { MarkdownBlock(node, onLinkClick, onImageClick, enableHtml) }
                }
            }
        }
    }
}

/** A renderer must opt in before its Compose content can join neighboring native selection. */
internal fun readerBlockSelectionMode(
    node: Node,
    builders: MarkdownBuilderRegistry?,
    plugins: ParserPluginRegistry?,
): MarkdownBlockSelectionMode? {
    builders?.findBuilder(node)?.let { return it.selectionMode(node) }
    if (node is Paragraph) {
        val sole = node.children().filterNot { it is MarkdownTextNode && it.literal.isBlank() }.singleOrNull()
        if (sole is Image) builders?.findBuilder(sole)?.let { return it.selectionMode(sole) }
    }
    if (node is PluginBlockNode) return plugins?.blockRenderer(node)?.selectionMode(node)
        ?: MarkdownBlockSelectionMode.NONE
    return null
}

/** Keep selectable prose, visual anchors, built-in code, and opted-in blocks mounted together. */
internal fun groupSelectableBlocks(
    blocks: List<Node>,
    bridgeVisibleNonText: Boolean = false,
    bridgeBuiltInCode: Boolean = false,
    bridgeDetails: Boolean = false,
    selectionMode: (Node) -> MarkdownBlockSelectionMode? = { null },
): List<List<Node>> {
    val groups = mutableListOf<List<Node>>()
    val pending = mutableListOf<Node>()
    fun flush() { if (pending.isNotEmpty()) { groups += pending.toList(); pending.clear() } }
    var needsFollowingProse = false
    for (block in blocks) {
        val mode = selectionMode(block)
        val builtIn = block is Heading || block is Paragraph || block is BlockQuote ||
            block is BulletList || block is OrderedList ||
            (bridgeVisibleNonText && (block is TableBlock || block is ThematicBreak || block is BlockMathNode)) ||
            (bridgeBuiltInCode && (block is FencedCodeBlock || block is IndentedCodeBlock))
        val prose = when (mode) {
            MarkdownBlockSelectionMode.NONE -> false
            MarkdownBlockSelectionMode.NATIVE_TEXT, MarkdownBlockSelectionMode.NON_TEXT -> true
            null -> builtIn
        }
        if (needsFollowingProse) {
            if (prose) {
                pending += block
                flush()
                needsFollowingProse = false
                continue
            }
            flush()
            needsFollowingProse = false
        }
        if (bridgeDetails && block is DetailsNode && mode != MarkdownBlockSelectionMode.NONE) {
            val preceding = pending.removeLastOrNull()
            flush()
            if (preceding != null) pending += preceding
            pending += block
            needsFollowingProse = true
            continue
        }
        if (prose) pending += block else { flush(); groups += listOf(block) }
    }
    flush()
    return groups
}

@Composable
private fun ReaderCustomBlockSelection(mode: MarkdownBlockSelectionMode, content: @Composable () -> Unit) {
    if (mode == MarkdownBlockSelectionMode.NON_TEXT) {
        SelectableNonTextBlock { DisableSelection { content() } }
    } else if (mode == MarkdownBlockSelectionMode.NATIVE_TEXT) {
        // Arbitrary host Text composables retain their native local selection. Hosts that need
        // programmatic cross-block selection render SmoothSelectableText or context children.
        SelectionContainer { content() }
    } else content()
}

@Composable
private fun MarkdownBlock(node: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean, textAlign: TextAlign? = null) {
    val sourceIndex = LocalReaderSelectionSourceOrder.current[node]
    val parent = LocalReaderSelectionOrder.current
    // Scope paths retain AST order. Positioned children establish their order within this
    // scope, including builders that recompose independently and reinsert an earlier Text.
    val order = ReaderSelectionOrder(sourceIndex?.let { listOf(it) } ?: parent?.allocate().orEmpty())
    CompositionLocalProvider(LocalReaderSelectionOrder provides order) {
        MarkdownBlockContent(node, onLinkClick, onImageClick, enableHtml, textAlign)
    }
}

@Composable
private fun MarkdownBlockContent(node: Node, onLinkClick: (String) -> Unit, onImageClick: (String) -> Unit, enableHtml: Boolean, textAlign: TextAlign? = null) {
    val sheet = LocalMarkdownStyleSheet.current
    val plugins = LocalParserPlugins.current
    val builder = LocalMarkdownBuilders.current?.findBuilder(node)
    if (builder != null) {
        ReaderCustomBlockSelection(builder.selectionMode(node)) {
            builder.Render(node, MarkdownBuilderContext(
                sheet, enableHtml, onLinkClick, onImageClick,
                childRenderer = { child -> MarkdownBlock(child, onLinkClick, onImageClick, enableHtml, textAlign) },
                inlineChildRenderer = { parent, style ->
                    MarkdownInlineText(
                        inlineRender(parent, enableHtml, sheet, plugins, LocalMarkdownBuilders.current),
                        style ?: sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                        onLinkClick, onImageClick, textAlign,
                    )
                },
            ))
        }
        return
    }
    when (node) {
        is Heading -> {
            val baseStyle = sheet.headingStyles?.get(node.level - 1) ?: MaterialTheme.typography.headlineMedium.copy(
                fontSize = (32 - (node.level - 1) * 3).sp,
                fontWeight = FontWeight.Bold,
            )
            val resolvedStyle = baseStyle.copy(color = baseStyle.color.takeUnless { it == Color.Unspecified }
                ?: sheet.headingColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurface)
            if (!LocalEnhancedComponents.current) {
                MarkdownInlineText(
                    inlineRender(node, enableHtml, sheet, plugins, LocalMarkdownBuilders.current),
                    resolvedStyle, onLinkClick, onImageClick, textAlign,
                    modifier = Modifier.semantics { heading() },
                )
            } else {
                val tokens = sheet.designTokens.heading
                val primary = tokens.accentColor ?: MaterialTheme.colorScheme.primary
                val decorated = node.level <= tokens.decoratedThroughLevel
                val barHeight = with(LocalDensity.current) {
                    if (resolvedStyle.fontSize.isSpecified) resolvedStyle.fontSize.toDp() else 24.dp
                }
                Column(Modifier.fillMaxWidth().padding(bottom = sheet.blockSpacing)) {
                Row(
                    Modifier.fillMaxWidth().padding(tokens.padding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (decorated) {
                        Box(
                            Modifier.padding(end = tokens.barSpacing)
                                .width(tokens.barWidth)
                                .height(barHeight)
                                .clip(RoundedCornerShape(tokens.barRadius))
                                .background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = tokens.barEndAlpha)))),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        MarkdownInlineText(
                            inlineRender(node, enableHtml, sheet, plugins, LocalMarkdownBuilders.current),
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
                        Modifier.fillMaxWidth().height(tokens.ruleThickness).background(
                            Brush.horizontalGradient(listOf(primary.copy(alpha = tokens.ruleStartAlpha), primary.copy(alpha = tokens.ruleEndAlpha))),
                        ),
                    )
                }
                }
            }
        }
        is Paragraph -> {
            val meaningful = node.children().filterNot { it is MarkdownTextNode && it.literal.isBlank() }.toList()
            val sole = meaningful.singleOrNull()
            val soleBuilder = sole?.let { LocalMarkdownBuilders.current?.findBuilder(it) }
            val htmlImage = if (enableHtml && sole is HtmlInline) SafeHtml.imageTag(sole.literal) else null
            when {
                sole is Image && soleBuilder != null -> {
                    ReaderCustomBlockSelection(soleBuilder.selectionMode(sole)) {
                        soleBuilder.Render(sole, MarkdownBuilderContext(
                            sheet, enableHtml, onLinkClick, onImageClick,
                            childRenderer = { child -> MarkdownBlock(child, onLinkClick, onImageClick, enableHtml, textAlign) },
                            inlineChildRenderer = { parent, style ->
                                MarkdownInlineText(
                                    inlineRender(parent, enableHtml, sheet, plugins, LocalMarkdownBuilders.current),
                                    style ?: sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                                    onLinkClick, onImageClick, textAlign,
                                )
                            },
                        ))
                    }
                }
                sole is Image -> MarkdownImage(
                    SafeHtml.ImageSpec(sole.destination, sole.plainText(), sole.title, null, null), onImageClick,
                )
                htmlImage != null -> MarkdownImage(htmlImage, onImageClick)
                else -> MarkdownInlineText(inlineRender(node, enableHtml, sheet, plugins, LocalMarkdownBuilders.current), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge, onLinkClick, onImageClick, textAlign)
            }
        }
        is FencedCodeBlock -> EnhancedCodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> EnhancedCodeBlock(node.literal, null)
        is BlockQuote -> MarkdownBlockquote(sheet, LocalEnhancedComponents.current) {
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
            if (renderer != null) {
                ReaderCustomBlockSelection(renderer.selectionMode(node)) {
                    renderer.RenderBlock(node) { child -> MarkdownBlock(child, onLinkClick, onImageClick, enableHtml) }
                }
            }
        }
        is FootnoteDefinitionNode -> Row(
            Modifier.fillMaxWidth().padding(sheet.designTokens.footnotePadding),
        ) {
            Text(
                "[${node.label}]: ",
                style = (sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge).copy(fontWeight = FontWeight.Bold, color = sheet.footnoteColor),
            )
            Box(Modifier.weight(1f)) {
                MarkdownInlineText(
                    inlineRender(node, enableHtml, sheet, plugins, LocalMarkdownBuilders.current), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
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
private fun MarkdownBlockquote(sheet: MarkdownStyleSheet, enhanced: Boolean = false, content: @Composable () -> Unit) {
    val decoration = resolveBlockquoteDecoration(sheet, MaterialTheme.colorScheme.primary)
    val tokens = sheet.designTokens.quote
    val primary = MaterialTheme.colorScheme.primary
    val explicitFill = sheet.blockquoteDecoration?.backgroundColor ?: sheet.quoteBackground
    val background = if (enhanced) {
        if (explicitFill != null) Modifier.background(explicitFill)
        else Modifier.background(Brush.linearGradient(listOf(
            tokens.gradientStartColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = tokens.backgroundStartAlpha),
            tokens.gradientEndColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = tokens.backgroundEndAlpha))))
    } else decoration.backgroundColor?.let { Modifier.background(it) } ?: Modifier
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = sheet.blockSpacing)
            .testTag("markdown-blockquote")
            .then(background)
            .drawBehind {
                if (decoration.borderWidth.value > 0) {
                    drawRect(
                        color = if (enhanced && sheet.blockquoteDecoration?.borderColor == null && sheet.quoteBarColor == null) primary.copy(alpha = tokens.borderAlpha) else decoration.borderColor,
                        size = Size(decoration.borderWidth.toPx().coerceAtMost(size.width), size.height),
                    )
                }
            }
            .padding(sheet.blockquotePadding),
    ) {
        if (enhanced) {
            Row(verticalAlignment = Alignment.Top) {
                if (tokens.showIcon) {
                    DisableSelection {
                        Text("❝", color = tokens.iconColor ?: primary.copy(alpha = tokens.iconAlpha), style = tokens.iconStyle)
                    }
                    Spacer(Modifier.width(tokens.iconSpacing))
                }
                Column(Modifier.weight(1f)) { content() }
            }
        } else content()
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
    val strings = LocalMarkdownStrings.current
    val tokens = sheet.designTokens.details
    val borderColor = tokens.borderColor ?: sheet.tableBorderColor ?: MaterialTheme.colorScheme.outlineVariant
    val selectionController = LocalReaderSelectionController.current
    val expanded = rememberSaveable(node) {
        mutableStateOf(selectionController?.detailsExpanded(node) ?: node.isOpen)
    }
    SideEffect { selectionController?.setDetailsExpanded(node, expanded.value) }
    val shape = RoundedCornerShape(tokens.cornerRadius)
    Column(
        Modifier.fillMaxWidth().padding(tokens.outerPadding)
            .background(tokens.backgroundColor ?: Color.Transparent, shape)
            .let { if (tokens.borderWidth > 0.dp) it.border(tokens.borderWidth, borderColor, shape) else it }
            .clip(shape),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .semantics { stateDescription = if (expanded.value) strings.expanded else strings.collapsed }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded.value) strings.collapseDetails else strings.expandDetails,
                ) {
                    expanded.value = !expanded.value
                    selectionController?.setDetailsExpanded(node, expanded.value)
                }
                .padding(tokens.summaryPadding),
        ) {
            DisableSelection {
                Text(if (expanded.value) "⌄" else "›", style = tokens.iconStyle ?: MaterialTheme.typography.titleMedium,
                    color = tokens.iconColor ?: tokens.iconStyle?.color?.takeUnless { it == Color.Unspecified } ?: sheet.textColor ?: Color.Unspecified)
            }
            Spacer(Modifier.width(tokens.iconSpacing))
            Box(Modifier.weight(1f)) {
                val summary = node.summary.singleOrNull()
                if (summary is Paragraph) {
                    MarkdownInlineText(
                        inlineRender(summary, enableHtml, sheet, LocalParserPlugins.current, LocalMarkdownBuilders.current), sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                        onLinkClick, onImageClick, bottomPadding = 0.dp,
                        onPlainTextTap = {
                            expanded.value = !expanded.value
                            selectionController?.setDetailsExpanded(node, expanded.value)
                        },
                    )
                } else {
                    Column {
                        node.summary.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
                    }
                }
            }
        }
        if (expanded.value && node.body.isNotEmpty()) {
            if (tokens.dividerThickness > 0.dp) HorizontalDivider(color = borderColor, thickness = tokens.dividerThickness)
            Column(Modifier.fillMaxWidth().padding(tokens.bodyPadding)) {
                node.body.forEach { MarkdownBlock(it, onLinkClick, onImageClick, enableHtml) }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: AnnotatedString, style: androidx.compose.ui.text.TextStyle, onLinkClick: (String) -> Unit, textAlign: TextAlign? = null, bottomPadding: androidx.compose.ui.unit.Dp? = null, modifier: Modifier = Modifier, onPlainTextTap: (() -> Unit)? = null) {
    if (LocalReaderSelectionState.current != null) DisableSelection { MarkdownTextContent(text, style, onLinkClick, textAlign, bottomPadding, modifier, onPlainTextTap) }
    else MarkdownTextContent(text, style, onLinkClick, textAlign, bottomPadding, modifier, onPlainTextTap)
}

@Composable
private fun MarkdownTextContent(text: AnnotatedString, style: androidx.compose.ui.text.TextStyle, onLinkClick: (String) -> Unit, textAlign: TextAlign? = null, bottomPadding: androidx.compose.ui.unit.Dp? = null, modifier: Modifier = Modifier, onPlainTextTap: (() -> Unit)? = null) {
    val sheet = LocalMarkdownStyleSheet.current
    val strings = LocalMarkdownStrings.current
    val onMentionClick = LocalOnMentionClick.current
    val onHashtagClick = LocalOnHashtagClick.current
    val onWikilinkClick = LocalOnWikilinkClick.current
    val foreground = if (style.color != Color.Unspecified) style.color else sheet.textColor ?: MaterialTheme.colorScheme.onSurface
    val links = text.getStringAnnotations("url", 0, text.length).filter { isSafeLink(it.item) }
    val actions = links.map { link ->
        CustomAccessibilityAction("${strings["Open link"]} ${text.text.substring(link.start, link.end)}") {
            onLinkClick(link.item)
            true
        }
    } + pluginAccessibilityActions(text, onMentionClick, onHashtagClick, onWikilinkClick, strings)
    val layout = remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val selectionOptions = LocalMarkdownSelectionOptions.current
    val selectionKey = remember { Any() }
    val sourceOrder = rememberReaderSelectionOrder()
    RetainReaderSelectionTarget(selectionKey)
    DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
        onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
    }
    val tracking = selectionOptions.onTextPositioned?.let { callback ->
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = androidx.compose.ui.geometry.Rect(
                coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero),
                androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
            )
            callback(MarkdownSelectionTarget(
                selectionKey, bounds, text,
                offsetAtWindowPosition = { windowPoint ->
                    layout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                },
            ).apply {
                this.sourceOrder = sourceOrder
                layoutResult = layout.value
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
    val selectionHighlight = readerSelectionHighlight(selectionKey)
    val base = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
        .then(enhancedLinkDecoration(text, layout)).then(tracking).then(selectionHighlight)
    Text(
        text = text,
        style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
        modifier = if (actions.isEmpty() && onPlainTextTap == null) base else base
            .then(if (actions.isEmpty()) Modifier else Modifier.semantics { customActions = actions })
            .pointerInput(text, onPlainTextTap, onLinkClick, onMentionClick, onHashtagClick, onWikilinkClick) {
                if (onPlainTextTap != null) {
                    // A native selectable Text can consume a short press while clearing its prior
                    // selection. Observe that tap before the selection pass, without stealing a
                    // long press or drag used to select the summary itself.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                            up.consume()
                            layout.value?.getOffsetForPosition(up.position)?.let { offset ->
                                dispatchTextTap(text, offset, onLinkClick, onPlainTextTap, onMentionClick, onHashtagClick, onWikilinkClick)
                            }
                        }
                    }
                } else {
                    detectTapGestures { position ->
                        layout.value?.getOffsetForPosition(position)?.let { offset ->
                            dispatchTextTap(text, offset, onLinkClick, null, onMentionClick, onHashtagClick, onWikilinkClick)
                        }
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
    if (LocalReaderSelectionState.current != null) DisableSelection { MarkdownInlineTextContent(render, style, onLinkClick, onImageClick, textAlign, bottomPadding, interactive, modifier, onPlainTextTap) }
    else MarkdownInlineTextContent(render, style, onLinkClick, onImageClick, textAlign, bottomPadding, interactive, modifier, onPlainTextTap)
}

@Composable
private fun MarkdownInlineTextContent(
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
    val strings = LocalMarkdownStrings.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val onMentionClick = LocalOnMentionClick.current
    val onHashtagClick = LocalOnHashtagClick.current
    val onWikilinkClick = LocalOnWikilinkClick.current
    val foreground = if (style.color != Color.Unspecified) style.color else sheet.textColor ?: MaterialTheme.colorScheme.onSurface
    val selectionOptions = LocalMarkdownSelectionOptions.current
    val selectionKey = remember { Any() }
    val sourceOrder = rememberReaderSelectionOrder()
    RetainReaderSelectionTarget(selectionKey)
    val layout = remember(render.text) { mutableStateOf<TextLayoutResult?>(null) }
    DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
        onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
    }
    val tracking = selectionOptions.onTextPositioned?.let { callback ->
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = androidx.compose.ui.geometry.Rect(
                coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero),
                androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
            )
            callback(MarkdownSelectionTarget(
                selectionKey, bounds, render.text,
                offsetAtWindowPosition = { windowPoint ->
                    layout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                },
            ).apply {
                this.sourceOrder = sourceOrder
                layoutResult = layout.value
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
    val selectionHighlight = readerSelectionHighlight(selectionKey)
    if (render.images.isEmpty() && render.math.isEmpty() && render.kbds.isEmpty() && render.customWidgets.isEmpty()) {
        if (interactive) MarkdownText(render.text, style, onLinkClick, textAlign, bottomPadding, modifier, onPlainTextTap)
        else Text(
            render.text,
            style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
                .then(enhancedLinkDecoration(render.text, layout)).then(tracking).then(selectionHighlight),
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
            val model = imageModel(image.source)
            val imageState = if (customImageBuilder == null && model != null) rememberNativeImage(model) else null
            val natural = imageState?.data?.let { ImageSize(it.width, it.height) }
            val size = imageSize(image.width, image.height, natural, maxImageWidth)
            InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { size.width.dp.toSp() },
                    height = with(density) { size.height.dp.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                InlineImage(image, size.width, size.height, if (interactive) onImageClick else null, imageState)
            }
        }.toMutableMap()
        render.math.forEach { (id, latex) ->
            val renderer = rememberMathRenderer(latex, displayMode = false)
            val widthDp = with(density) { renderer.width.coerceAtLeast(1f).toDp() }
            val heightDp = with(density) { renderer.height.coerceAtLeast(1f).toDp() }
            inline[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { widthDp.toSp() },
                    height = with(density) { heightDp.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                NativeMath(latex, displayMode = false, Modifier.width(widthDp).height(heightDp))
            }
        }
        render.kbds.forEach { (id, label) ->
            val tokens = sheet.designTokens.keyboard
            val border = tokens.borderColor ?: sheet.ruleColor ?: Color(0xFFBDBDBD)
            val base = sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge
            val keyStyle = base.copy(fontFamily = FontFamily.Monospace).merge(tokens.textStyle)
                .merge(sheet.kbdStyle).let { it.copy(color = it.color.takeUnless { color -> color == Color.Unspecified } ?: foreground) }
            val measured = textMeasurer.measure(label, style = keyStyle, maxLines = 1, softWrap = false)
            val width = with(density) { measured.size.width.toDp() } + tokens.padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + tokens.padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + tokens.extraWidth
            val height = with(density) { measured.size.height.toDp() } + tokens.padding.calculateTopPadding() + tokens.padding.calculateBottomPadding() + tokens.extraHeight
            val shape = RoundedCornerShape(tokens.cornerRadius)
            inline[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { width.toSp() },
                    height = with(density) { height.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                // The parent annotated text already contains the key label for selection.
                // Its visual inline child must not register that label a second time.
                DisableSelection {
                    Box(Modifier.background(tokens.backgroundColor ?: border.copy(alpha = tokens.backgroundAlpha), shape).let { if (tokens.borderWidth > 0.dp) it.border(tokens.borderWidth, border, shape) else it }
                        .padding(tokens.padding)) {
                        Text(label, style = keyStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
        render.customWidgets.forEach { (id, custom) ->
            val presentation = custom.presentation
            inline[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { presentation.width.toSp() },
                    height = with(density) { presentation.height.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                val enableHtml = LocalMarkdownEnableHtml.current
                val context = MarkdownBuilderContext(
                    sheet, enableHtml, onLinkClick, onImageClick,
                    childRenderer = { child -> MarkdownBlock(child, onLinkClick, onImageClick, enableHtml) },
                    inlineChildRenderer = { parent, style ->
                        MarkdownInlineText(
                            inlineRender(parent, enableHtml, sheet, LocalParserPlugins.current, LocalMarkdownBuilders.current),
                            style ?: sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge,
                            onLinkClick, onImageClick,
                        )
                    },
                )
                custom.builder.Render(custom.node, context)
            }
        }
        if (!interactive) {
            Text(
                text = render.text,
                inlineContent = inline,
                style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
                    .then(enhancedLinkDecoration(render.text, layout)).then(tracking).then(selectionHighlight),
            )
        } else {
            val links = render.text.getStringAnnotations("url", 0, render.text.length).filter { isSafeLink(it.item) }
            val actions = links.map { link ->
                CustomAccessibilityAction("${strings["Open link"]} ${render.text.text.substring(link.start, link.end)}") {
                    onLinkClick(link.item)
                    true
                }
            } + pluginAccessibilityActions(render.text, onMentionClick, onHashtagClick, onWikilinkClick, strings) + render.images.values.map { image ->
                CustomAccessibilityAction("${strings["Open image"]} ${image.alt.ifBlank { image.title ?: strings["Image"] }}") {
                    dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
                    true
                }
            }
            Text(
                text = render.text,
                inlineContent = inline,
                style = style.copy(color = foreground, textAlign = textAlign ?: TextAlign.Unspecified),
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding ?: sheet.blockSpacing).then(modifier)
                    .then(enhancedLinkDecoration(render.text, layout)).then(tracking).then(selectionHighlight)
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
private fun InlineImage(image: SafeHtml.ImageSpec, width: Float, height: Float, onImageClick: ((String) -> Unit)?, imageState: NativeImageState? = null) {
    val sheet = LocalMarkdownStyleSheet.current
    val strings = LocalMarkdownStrings.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val imageBuilder = LocalImageBuilder.current
    val model = imageModel(image.source) ?: return Text(image.alt, color = sheet.textColor ?: Color.Unspecified)
    val modifier = Modifier.width(width.dp).height(height.dp)
        .semantics { contentDescription = image.alt.ifBlank { image.title ?: strings["Image"] } }
    Box(if (onImageClick != null) modifier.clickable(
            role = Role.Button, onClickLabel = strings["Open image"],
        ) { dispatchImageClick(image, onImageClick, onImageClickWithMetadata) } else modifier) {
        if (imageBuilder != null) imageBuilder(image.source, image.alt, image.title)
        else NativeMarkdownImage(
            source = model,
            imageState = imageState,
            contentDescription = null,
            modifier = Modifier.width(width.dp).height(height.dp),
            contentScale = ContentScale.Fit,
            loading = { androidx.compose.material3.CircularProgressIndicator() },
            error = { Text(image.alt.ifBlank { image.title ?: strings["Image"] }, color = sheet.textColor ?: Color.Unspecified) },
        )
    }
}

@Composable
private fun MarkdownImage(image: SafeHtml.ImageSpec, onImageClick: (String) -> Unit) {
    val sheet = LocalMarkdownStyleSheet.current
    val strings = LocalMarkdownStrings.current
    val onImageClickWithMetadata = LocalOnImageClickWithMetadata.current
    val imageBuilder = LocalImageBuilder.current
    val url = image.source
    val model = imageModel(url)
    if (model == null) {
        Text(image.alt, modifier = Modifier.padding(bottom = sheet.blockSpacing), color = sheet.textColor ?: Color.Unspecified)
        return
    }
    val imageState = if (imageBuilder == null) rememberNativeImage(model) else null
    val natural = imageState?.data?.let { ImageSize(it.width, it.height) }
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
            Box(Modifier.padding(bottom = sheet.blockSpacing).sizeIn(minWidth = sheet.designTokens.imagePlaceholderMinSize, minHeight = sheet.designTokens.imagePlaceholderMinSize)
                .semantics { contentDescription = image.alt.ifBlank { image.title ?: strings["Image"] } }
                .clickable(role = Role.Button, onClickLabel = strings["Open image"]) {
                    dispatchImageClick(image, onImageClick, onImageClickWithMetadata)
                }) {
                if (imageBuilder != null) Box(customModifier) { imageBuilder(url, image.alt, image.title) }
                else NativeMarkdownImage(
                    source = model,
                    imageState = imageState,
                    contentDescription = null,
                    modifier = imageModifier,
                    loading = { androidx.compose.material3.CircularProgressIndicator() },
                    error = { Text(image.alt.ifBlank { image.title ?: strings["Image"] }, color = sheet.textColor ?: Color.Unspecified) },
                )
            }
        }
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = (sheet.tableCellPadding * 2 + 1.dp) * columns
        val viewportWidth = maxWidth.takeIf { it.value.isFinite() && it.value > 0f }
            ?: LocalConfiguration.current.screenWidthDp.dp
        val tableWidth = maxOf(viewportWidth, minimumWidth)
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = sheet.blockSpacing)
            .semantics { collectionInfo = CollectionInfo(rows.size, columns) }) {
            rows.forEachIndexed { rowIndex, row ->
                val cells = row.children().filterIsInstance<TableCell>().toList()
                val rowIsHeader = cells.firstOrNull()?.isHeader == true
                Row(Modifier.width(tableWidth).height(IntrinsicSize.Min)) {
                    repeat(columns) { columnIndex ->
                        val cell = cells.getOrNull(columnIndex)
                        val cellAlignment = when (cell?.alignment) {
                            TableCell.Alignment.CENTER -> Alignment.Center
                            TableCell.Alignment.RIGHT -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        }
                        val cellTextAlign = when (cell?.alignment) {
                            TableCell.Alignment.CENTER -> TextAlign.Center
                            TableCell.Alignment.RIGHT -> TextAlign.Right
                            else -> TextAlign.Left
                        }
                        val style = if (cell?.isHeader == true) sheet.tableHeaderStyle ?: MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        else sheet.tableCellStyle ?: MaterialTheme.typography.bodyMedium
                        val edges = tableCellBorderEdges(border, rowIndex, rows.size, columnIndex, columns)
                        Box(Modifier.weight(1f).fillMaxHeight()
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
                            }, contentAlignment = cellAlignment) {
                            if (cell != null) {
                                val sourceIndex = LocalReaderSelectionSourceOrder.current[cell]
                                val cellOrder = ReaderSelectionOrder(listOf(sourceIndex ?: Int.MAX_VALUE))
                                CompositionLocalProvider(LocalReaderSelectionOrder provides cellOrder) {
                                    MarkdownInlineText(
                                        inlineRender(cell, enableHtml, sheet, LocalParserPlugins.current, LocalMarkdownBuilders.current),
                                        style, onLinkClick, onImageClick,
                                        textAlign = cellTextAlign,
                                        bottomPadding = 0.dp,
                                    )
                                }
                            }
                        }
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
    val customWidgets: Map<String, CustomInlineWidget> = emptyMap(),
)

internal data class CustomInlineWidget(
    val node: Node,
    val builder: MarkdownNodeBuilder,
    val presentation: MarkdownInlinePresentation.Widget,
)

internal fun inlineText(node: Node, enableHtml: Boolean, plugins: ParserPluginRegistry? = null): AnnotatedString = inlineRender(node, enableHtml, plugins = plugins).text

internal fun inlineRender(node: Node, enableHtml: Boolean, styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(), plugins: ParserPluginRegistry? = null, builders: MarkdownBuilderRegistry? = null): InlineRender {
    val images = linkedMapOf<String, SafeHtml.ImageSpec>()
    val math = linkedMapOf<String, String>()
    val kbds = linkedMapOf<String, String>()
    val customWidgets = linkedMapOf<String, CustomInlineWidget>()
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
        val customBuilder = builders?.findBuilder(current)
        val customPresentation = customBuilder?.renderInline(current)
        if (customPresentation != null) {
            when (customPresentation) {
                is MarkdownInlinePresentation.Text -> {
                    append(customPresentation.text)
                    addStyle(customPresentation.style, start, length)
                }
                is MarkdownInlinePresentation.Widget -> {
                    require(customPresentation.width.value.isFinite() && customPresentation.width.value > 0f &&
                        customPresentation.height.value.isFinite() && customPresentation.height.value > 0f) {
                        "Inline widget width and height must be finite and positive"
                    }
                    val id = "custom-${customWidgets.size}"
                    customWidgets[id] = CustomInlineWidget(current, customBuilder, customPresentation)
                    appendInlineContent(id, customPresentation.fallbackText)
                }
            }
            if (enableHtml) htmlStack.forEach { applyHtmlStyle(it, start, length) }
            return
        }
        var leaf = true
        when (current) {
            is com.jackcaow.smoothmarkdown.ast.Text -> append(current.literal)
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
                    addStyle(when (current) {
                        is MentionNode -> styleSheet.designTokens.plugins.mentionStyle
                        is HashtagNode -> styleSheet.designTokens.plugins.hashtagStyle
                        else -> presentation.style
                    }, start, length)
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
                addStringAnnotation("enhanced-link", current.destination, start, end)
            }
        }
    }
    node.children().forEach(::appendNode)
    }
    return InlineRender(text, images, math, kbds, customWidgets)
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
    strings: MarkdownStrings = MarkdownStrings(),
): List<CustomAccessibilityAction> = buildList {
    if (onMentionClick != null) {
        text.getStringAnnotations("mention", 0, text.length).forEach { mention ->
            if (safeLinkAt(text, mention.start) == null) add(CustomAccessibilityAction("${strings["Open mention"]} @${mention.item}") {
                onMentionClick(mention.item)
                true
            })
        }
    }
    if (onHashtagClick != null) {
        text.getStringAnnotations("hashtag", 0, text.length).forEach { hashtag ->
            if (safeLinkAt(text, hashtag.start) == null) add(CustomAccessibilityAction("${strings["Open hashtag"]} #${hashtag.item}") {
                onHashtagClick(hashtag.item)
                true
            })
        }
    }
    if (onWikilinkClick != null) {
        text.getStringAnnotations("wikilink", 0, text.length).forEach { wikilink ->
            if (safeLinkAt(text, wikilink.start) == null) add(CustomAccessibilityAction("${strings["Open note"]} ${wikilink.item}") {
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

internal fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}

private fun Node.plainText(): String = buildString {
    fun visit(node: Node) {
        when (node) {
            is com.jackcaow.smoothmarkdown.ast.Text -> append(node.literal)
            is Code -> append(node.literal)
            is FencedCodeBlock -> append(node.literal)
            is IndentedCodeBlock -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            else -> node.children().forEach(::visit)
        }
    }
    visit(this@plainText)
}
