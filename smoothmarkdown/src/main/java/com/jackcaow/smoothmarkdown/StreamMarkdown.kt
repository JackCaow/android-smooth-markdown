package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Appends incoming chunks and renders the accumulated Markdown document.
 * [imageBuilder] is forwarded to [SmoothMarkdown] for safe block, inline, and HTML images.
 * [onComplete] receives the full source after a finite flow finishes and its final text is shown.
 */
@Composable
fun StreamMarkdown(
    chunks: Flow<String>,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    throttleMillis: Long = 50,
    enableHtml: Boolean = false,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    plugins: ParserPluginRegistry? = null,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)? = null,
    imageBuilder: (@Composable (String, String?, String?) -> Unit)? = null,
    scrollable: Boolean = true,
    codeBlockOptions: CodeBlockOptions? = null,
    codeBlockBuilder: (@Composable (String, String?) -> Unit)? = null,
    onCodeCopied: ((String) -> Unit)? = null,
    selectable: Boolean = false,
    selectionController: SmoothSelectionController? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (Throwable) -> Unit)? = null,
    selectionMenuActions: List<SmoothSelectionMenuAction> = emptyList(),
    showDefaultCopyAction: Boolean = true,
    builderRegistry: MarkdownBuilderRegistry? = null,
    useEnhancedComponents: Boolean = false,
    onComplete: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null,
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
    selectableAsSingleRegion: Boolean = false,
    onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
    onCodeCopiedWithMetadata: ((String, String?) -> Unit)? = null,
) {
    val errorHandler by rememberUpdatedState(onError)
    val streamIdentity = remember(chunks, throttleMillis) { Any() }
    // HTML is a rendering option, not a new stream. Keep collecting the same
    // Flow when the switch changes and project the current prefix below.
    val emission by produceState(initialValue = StreamingMarkdownEmission(StreamSnapshot(), streamIdentity), key1 = chunks, key2 = throttleMillis) {
        value = StreamingMarkdownEmission(StreamSnapshot(), streamIdentity)
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis())
        var pending: Job? = null
        var hasReceivedData = false
        try {
            chunks.collect { chunk ->
                hasReceivedData = true
                val wait = buffer.append(chunk, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = true), streamIdentity)
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = true), streamIdentity)
                    }
                }
            }
            pending?.cancel()
            buffer.finish(SystemClock.uptimeMillis())
            value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, complete = true, hasReceivedData = hasReceivedData), streamIdentity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            withContext(Dispatchers.Main.immediate) {
                errorHandler(error)
                value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = hasReceivedData, error = error), streamIdentity)
            }
        } finally {
            pending?.cancel()
        }
    }
    val snapshot = emission.snapshot.takeIf { emission.identity === streamIdentity } ?: StreamSnapshot()
    StreamMarkdownContent(snapshot, streamIdentity, onComplete, onError, modifier, onLinkClick, onImageClick, enableHtml, styleSheet,
        plugins, onImageClickWithMetadata, imageBuilder, scrollable, codeBlockOptions, codeBlockBuilder,
        onCodeCopied, selectable, selectionController, selectionMenuActions, showDefaultCopyAction, builderRegistry,
        useEnhancedComponents,
        loadingContent, errorContent, onMentionClick, onHashtagClick, onWikilinkClick,
        resourceOptions, strings, selectableAsSingleRegion, onTextPositioned, onCodeCopiedWithMetadata)
}

/** Renders a cumulative streaming source. A late-composed chat bubble receives the latest
 * complete prefix from [StateFlow], so lazy list recycling cannot lose earlier chunks.
 * [onComplete] runs only if the StateFlow itself completes; ordinary hot StateFlows do not.
 */
@Composable
fun StreamMarkdown(
    prefixes: StateFlow<String>,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    throttleMillis: Long = 50,
    enableHtml: Boolean = false,
    styleSheet: MarkdownStyleSheet = MarkdownStyleSheet.default(),
    plugins: ParserPluginRegistry? = null,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)? = null,
    imageBuilder: (@Composable (String, String?, String?) -> Unit)? = null,
    scrollable: Boolean = true,
    codeBlockOptions: CodeBlockOptions? = null,
    codeBlockBuilder: (@Composable (String, String?) -> Unit)? = null,
    onCodeCopied: ((String) -> Unit)? = null,
    selectable: Boolean = false,
    selectionController: SmoothSelectionController? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (Throwable) -> Unit)? = null,
    selectionMenuActions: List<SmoothSelectionMenuAction> = emptyList(),
    showDefaultCopyAction: Boolean = true,
    builderRegistry: MarkdownBuilderRegistry? = null,
    useEnhancedComponents: Boolean = false,
    onComplete: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null,
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
    strings: MarkdownStrings = LocalMarkdownStrings.current,
    selectableAsSingleRegion: Boolean = false,
    onTextPositioned: ((MarkdownSelectionTarget) -> Unit)? = null,
    onCodeCopiedWithMetadata: ((String, String?) -> Unit)? = null,
) {
    val errorHandler by rememberUpdatedState(onError)
    val streamIdentity = remember(prefixes, throttleMillis) { Any() }
    val emission by produceState(initialValue = StreamingMarkdownEmission(StreamSnapshot(), streamIdentity), key1 = prefixes, key2 = throttleMillis) {
        value = StreamingMarkdownEmission(StreamSnapshot(), streamIdentity)
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis())
        var pending: Job? = null
        try {
            prefixes.collect { prefix ->
                val wait = buffer.appendPrefix(prefix, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = true), streamIdentity)
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = true), streamIdentity)
                    }
                }
            }
            pending?.cancel()
            buffer.finish(SystemClock.uptimeMillis())
            value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, complete = true, hasReceivedData = true), streamIdentity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            withContext(Dispatchers.Main.immediate) {
                errorHandler(error)
                value = StreamingMarkdownEmission(StreamSnapshot(buffer.visibleText, hasReceivedData = true, error = error), streamIdentity)
            }
        } finally {
            pending?.cancel()
        }
    }
    val snapshot = emission.snapshot.takeIf { emission.identity === streamIdentity } ?: StreamSnapshot()
    StreamMarkdownContent(snapshot, streamIdentity, onComplete, onError, modifier, onLinkClick, onImageClick, enableHtml, styleSheet,
        plugins, onImageClickWithMetadata, imageBuilder, scrollable, codeBlockOptions, codeBlockBuilder,
        onCodeCopied, selectable, selectionController, selectionMenuActions, showDefaultCopyAction, builderRegistry,
        useEnhancedComponents,
        loadingContent, errorContent, onMentionClick, onHashtagClick, onWikilinkClick,
        resourceOptions, strings, selectableAsSingleRegion, onTextPositioned, onCodeCopiedWithMetadata)
}

@Composable
private fun StreamMarkdownContent(
    snapshot: StreamSnapshot,
    streamIdentity: Any,
    onComplete: ((String) -> Unit)?,
    onError: (Throwable) -> Unit,
    modifier: Modifier,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    enableHtml: Boolean,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry?,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)?,
    imageBuilder: (@Composable (String, String?, String?) -> Unit)?,
    scrollable: Boolean,
    codeBlockOptions: CodeBlockOptions?,
    codeBlockBuilder: (@Composable (String, String?) -> Unit)?,
    onCodeCopied: ((String) -> Unit)?,
    selectable: Boolean,
    selectionController: SmoothSelectionController?,
    selectionMenuActions: List<SmoothSelectionMenuAction>,
    showDefaultCopyAction: Boolean,
    builderRegistry: MarkdownBuilderRegistry?,
    useEnhancedComponents: Boolean,
    loadingContent: (@Composable () -> Unit)?,
    errorContent: (@Composable (Throwable) -> Unit)?,
    onMentionClick: ((String) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?,
    onWikilinkClick: ((String) -> Unit)?,
    resourceOptions: MarkdownResourceOptions,
    strings: MarkdownStrings,
    selectableAsSingleRegion: Boolean,
    onTextPositioned: ((MarkdownSelectionTarget) -> Unit)?,
    onCodeCopiedWithMetadata: ((String, String?) -> Unit)?,
) {
    val pluginVersion = plugins?.version
    val requests = remember(streamIdentity, plugins, pluginVersion, enableHtml) { StreamingMarkdownRequests() }
    val publisher = remember(requests) { StreamingMarkdownPublisher() }
    val fallback = remember(requests) { StreamingMarkdownSession(plugins, enableHtml) }
    DisposableEffect(fallback) { onDispose { fallback.close() } }
    val completion = remember(streamIdentity) { StreamingMarkdownCompletion() }
    val latestCompletion by rememberUpdatedState(onComplete)
    val latestError by rememberUpdatedState(onError)
    val renderText = snapshot.renderText(enableHtml)
    val eligible = snapshot.error == null && streamingNativeEligible(renderText, plugins, enableHtml)
    val worker = remember(requests, eligible) { if (eligible) StreamingMarkdownWorker() else null }
    DisposableEffect(worker) { onDispose { worker?.close() } }
    val request = requests.prepare(renderText, snapshot.complete, eligible)
    // The UI retains only publication metadata after adaptation, not a duplicate native AST.
    var ready by remember(requests) { mutableStateOf<Pair<StreamingMarkdownRequest, StreamingMarkdownDocument>?>(null) }
    var parseError by remember(requests) { mutableStateOf<Throwable?>(null) }
    val displayed = remember(requests) { arrayOfNulls<StreamingMarkdownDocument>(1) }
    SideEffect {
        if (requests.commit(request, eligible)) {
            parseError = null
            if (eligible) worker?.submit(request)
        }
    }
    LaunchedEffect(worker) {
        val engine = worker ?: return@LaunchedEffect
        for (result in engine.results) {
            // A Channel can resume an unconfined/test composition continuation on the
            // sending worker. Never rely on its inherited dispatcher for mutable Markup.
            withContext(Dispatchers.Main.immediate) {
                if (!requests.accepts(result)) return@withContext
                try {
                    result.error?.let { throw it }
                    val document = if (result.fullTree == null) fallback.parse(result.request.source)
                        else publisher.apply(result)
                    ready = result.request to document
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    publisher.invalidate()
                    parseError = error
                    latestError(error)
                }
            }
        }
    }
    val activeError = snapshot.error ?: parseError
    when {
        activeError != null && errorContent != null -> errorContent(activeError)
        !snapshot.hasReceivedData && !snapshot.complete && loadingContent != null -> loadingContent()
        else -> {
            // Keep the last successful source/document paired while background parsing is pending.
            // Sending new source with an old AST would make SmoothMarkdown synchronously reparse it.
            val currentReady = ready?.takeIf {
                it.first.context === request.context && it.first.epoch == request.epoch
            }
            val document = if (!eligible && snapshot.error == null) fallback.parse(renderText)
                else currentReady?.second ?: displayed[0] ?: emptyStreamingDocument()
            val publishedVersion = if (!eligible && snapshot.error == null) request.version else currentReady?.first?.version ?: 0
            CompositionLocalProvider(LocalStreamingMarkdownDocument provides document) {
                SmoothMarkdown(document.source, modifier, onLinkClick, onImageClick, enableHtml,
                    codeBlockOptions = codeBlockOptions, codeBlockBuilder = codeBlockBuilder,
                    onCodeCopied = onCodeCopied, styleSheet = styleSheet, plugins = plugins,
                    onImageClickWithMetadata = onImageClickWithMetadata, imageBuilder = imageBuilder,
                    scrollable = scrollable, enableCache = false, selectable = selectable,
                    selectionController = selectionController, selectionMenuActions = selectionMenuActions,
                    showDefaultCopyAction = showDefaultCopyAction, builderRegistry = builderRegistry,
                    useEnhancedComponents = useEnhancedComponents,
                    onMentionClick = onMentionClick, onHashtagClick = onHashtagClick,
                    onWikilinkClick = onWikilinkClick, resourceOptions = resourceOptions, strings = strings,
                    selectableAsSingleRegion = selectableAsSingleRegion, onTextPositioned = onTextPositioned,
                    onCodeCopiedWithMetadata = onCodeCopiedWithMetadata)
            }
            SideEffect {
                displayed[0] = document
            }
            LaunchedEffect(request, publishedVersion, document.source, activeError) {
                if (activeError != null || !request.complete || publishedVersion != request.version ||
                    document.source != request.source) return@LaunchedEffect
                // LazyColumn creates its visible children during measure, after the parent's
                // SideEffects. Await the next frame and yield through the main queue so the
                // final document has completed that layout/subcomposition before notification.
                withContext(Dispatchers.Main.immediate) {
                    withFrameNanos { }
                    yield()
                    ensureActive()
                    if (requests.latest !== request) return@withContext
                    if (completion.publish(request, publishedVersion, document.source, requests.latest)) {
                        try { latestCompletion?.invoke(document.source) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Throwable) { parseError = error; latestError(error) }
                    }
                }
            }
        }
    }
}

internal data class StreamSnapshot(
    val text: String = "",
    val complete: Boolean = false,
    val hasReceivedData: Boolean = false,
    val error: Throwable? = null,
) {
    fun renderText(enableHtml: Boolean): String =
        if (enableHtml && !complete) SafeHtml.safeRenderPrefix(text) else text
}
