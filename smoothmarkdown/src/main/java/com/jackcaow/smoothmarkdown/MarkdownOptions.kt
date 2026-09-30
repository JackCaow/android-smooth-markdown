package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Rendering behavior. Appearance belongs exclusively to [MarkdownStyleSheet]. */
data class MarkdownRenderOptions(
    val enableHtml: Boolean = false,
    val scrollable: Boolean = true,
    val enableCache: Boolean = true,
    val useEnhancedComponents: Boolean = false,
    /** Null inherits enhanced/plain defaults; explicit controls are independent of decorative mode. */
    val codeBlocks: CodeBlockOptions? = null,
)

/** One owner for selection: disabled, owned by this reader, or owned by an outer container. */
enum class MarkdownSelectionMode { DISABLED, DOCUMENT, @Deprecated("Use DOCUMENT") READER, OUTER_CONTAINER }

data class MarkdownSelectionOptions(
    val mode: MarkdownSelectionMode = MarkdownSelectionMode.DISABLED,
    val controller: SmoothSelectionController? = null,
    val menuActions: List<SmoothSelectionMenuAction> = emptyList(),
    val showDefaultCopyAction: Boolean = true,
) {
    /** A disabled or parent-owned selection does not retain this reader's controller/menu hooks. */
    fun resolved(): MarkdownSelectionOptions = if (mode in setOf(MarkdownSelectionMode.DOCUMENT, MarkdownSelectionMode.READER)) this
        else copy(controller = null, menuActions = emptyList(), showDefaultCopyAction = true)

}

/** Rich image callback replaces the legacy pair; every interaction is delivered once. */
data class MarkdownEvents(
    val onLinkClick: (String) -> Unit = {},
    val onImageClick: (MarkdownImageEvent) -> Unit = {},
    val onCodeCopied: ((String, String?) -> Unit)? = null,
    val onMentionClick: ((String) -> Unit)? = null,
    val onHashtagClick: ((String) -> Unit)? = null,
    val onWikilinkClick: ((String) -> Unit)? = null,
    val onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
    val onError: (Throwable) -> Unit = {},
    val onComplete: ((String) -> Unit)? = null,
)

data class MarkdownImageEvent(val source: String, val alt: String? = null, val title: String? = null)

data class MarkdownBuilders(
    val nodes: MarkdownBuilderRegistry? = null,
    val code: (@Composable (String, String?) -> Unit)? = null,
    val image: (@Composable (String, String?, String?) -> Unit)? = null,
)

/** Grouped public entry point. [renderOptions] is required to keep existing calls unambiguous.
 * Both entry points use the same rendering implementation and style precedence. */
@Composable
fun SmoothMarkdown(
    markdown: String,
    renderOptions: MarkdownRenderOptions,
    modifier: Modifier = Modifier,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    selectionOptions: MarkdownSelectionOptions = MarkdownSelectionOptions(),
    events: MarkdownEvents = MarkdownEvents(),
    plugins: ParserPluginRegistry? = null,
    builders: MarkdownBuilders = MarkdownBuilders(),
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
) {
    val selection = selectionOptions.resolved()
    SmoothMarkdown(
        markdown = markdown, modifier = modifier,
        onLinkClick = events.onLinkClick,
        onImageClickWithMetadata = { source, alt, title -> events.onImageClick(MarkdownImageEvent(source, alt, title)) },
        enableHtml = renderOptions.enableHtml, scrollable = renderOptions.scrollable,
        enableCache = renderOptions.enableCache, useEnhancedComponents = renderOptions.useEnhancedComponents,
        codeBlockOptions = renderOptions.codeBlocks, styleSheet = styleSheet, plugins = plugins,
        selectable = selection.mode in setOf(MarkdownSelectionMode.DOCUMENT, MarkdownSelectionMode.READER),
        selectableAsSingleRegion = selection.mode == MarkdownSelectionMode.OUTER_CONTAINER,
        selectionController = selection.controller, selectionMenuActions = selection.menuActions,
        showDefaultCopyAction = selection.showDefaultCopyAction,
        onCodeCopiedWithMetadata = events.onCodeCopied, onMentionClick = events.onMentionClick,
        onHashtagClick = events.onHashtagClick, onWikilinkClick = events.onWikilinkClick,
        onTextPositioned = events.onTextPositioned,
        builderRegistry = builders.nodes, codeBlockBuilder = builders.code, imageBuilder = builders.image,
        resourceOptions = resourceOptions, strings = strings,
    )
}

/** Stream scheduling and presentation. Incoming source collection is independent of rendering options. */
data class MarkdownStreamOptions(
    val throttleMillis: Long = 50,
    val loadingContent: (@Composable () -> Unit)? = null,
    val errorContent: (@Composable (Throwable) -> Unit)? = null,
)

@Composable
fun StreamMarkdown(
    chunks: kotlinx.coroutines.flow.Flow<String>,
    renderOptions: MarkdownRenderOptions,
    modifier: Modifier = Modifier,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    selectionOptions: MarkdownSelectionOptions = MarkdownSelectionOptions(),
    events: MarkdownEvents = MarkdownEvents(),
    plugins: ParserPluginRegistry? = null,
    builders: MarkdownBuilders = MarkdownBuilders(),
    streamOptions: MarkdownStreamOptions = MarkdownStreamOptions(),
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
) {
    val selection = selectionOptions.resolved()
    StreamMarkdown(
        chunks = chunks, modifier = modifier, onLinkClick = events.onLinkClick,
        onImageClickWithMetadata = { source, alt, title -> events.onImageClick(MarkdownImageEvent(source, alt, title)) },
        onError = events.onError, onComplete = events.onComplete,
        throttleMillis = streamOptions.throttleMillis.coerceAtLeast(0), loadingContent = streamOptions.loadingContent,
        errorContent = streamOptions.errorContent, enableHtml = renderOptions.enableHtml,
        scrollable = renderOptions.scrollable, useEnhancedComponents = renderOptions.useEnhancedComponents,
        codeBlockOptions = renderOptions.codeBlocks, styleSheet = styleSheet, plugins = plugins,
        selectable = selection.mode in setOf(MarkdownSelectionMode.DOCUMENT, MarkdownSelectionMode.READER),
        selectableAsSingleRegion = selection.mode == MarkdownSelectionMode.OUTER_CONTAINER,
        selectionController = selection.controller, selectionMenuActions = selection.menuActions,
        showDefaultCopyAction = selection.showDefaultCopyAction,
        onCodeCopiedWithMetadata = events.onCodeCopied, onMentionClick = events.onMentionClick,
        onHashtagClick = events.onHashtagClick, onWikilinkClick = events.onWikilinkClick,
        onTextPositioned = events.onTextPositioned,
        builderRegistry = builders.nodes, codeBlockBuilder = builders.code, imageBuilder = builders.image,
        resourceOptions = resourceOptions, strings = strings,
    )
}

@Composable
fun StreamMarkdown(
    prefixes: kotlinx.coroutines.flow.StateFlow<String>,
    renderOptions: MarkdownRenderOptions,
    modifier: Modifier = Modifier,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    selectionOptions: MarkdownSelectionOptions = MarkdownSelectionOptions(),
    events: MarkdownEvents = MarkdownEvents(),
    plugins: ParserPluginRegistry? = null,
    builders: MarkdownBuilders = MarkdownBuilders(),
    streamOptions: MarkdownStreamOptions = MarkdownStreamOptions(),
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
) {
    val selection = selectionOptions.resolved()
    StreamMarkdown(
        prefixes = prefixes, modifier = modifier, onLinkClick = events.onLinkClick,
        onImageClickWithMetadata = { source, alt, title -> events.onImageClick(MarkdownImageEvent(source, alt, title)) },
        onError = events.onError, onComplete = events.onComplete,
        throttleMillis = streamOptions.throttleMillis.coerceAtLeast(0), loadingContent = streamOptions.loadingContent,
        errorContent = streamOptions.errorContent, enableHtml = renderOptions.enableHtml,
        scrollable = renderOptions.scrollable, useEnhancedComponents = renderOptions.useEnhancedComponents,
        codeBlockOptions = renderOptions.codeBlocks, styleSheet = styleSheet, plugins = plugins,
        selectable = selection.mode in setOf(MarkdownSelectionMode.DOCUMENT, MarkdownSelectionMode.READER),
        selectableAsSingleRegion = selection.mode == MarkdownSelectionMode.OUTER_CONTAINER,
        selectionController = selection.controller, selectionMenuActions = selection.menuActions,
        showDefaultCopyAction = selection.showDefaultCopyAction,
        onCodeCopiedWithMetadata = events.onCodeCopied, onMentionClick = events.onMentionClick,
        onHashtagClick = events.onHashtagClick, onWikilinkClick = events.onWikilinkClick,
        onTextPositioned = events.onTextPositioned,
        builderRegistry = builders.nodes, codeBlockBuilder = builders.code, imageBuilder = builders.image,
        resourceOptions = resourceOptions, strings = strings,
    )
}
