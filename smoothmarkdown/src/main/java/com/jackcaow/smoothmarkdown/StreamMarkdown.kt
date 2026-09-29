package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Appends incoming chunks and renders the accumulated Markdown document.
 * [imageBuilder] is forwarded to [SmoothMarkdown] for safe block, inline, and HTML images.
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
    codeBlockOptions: CodeBlockOptions = CodeBlockOptions(),
    codeBlockBuilder: (@Composable (String, String?) -> Unit)? = null,
    onCodeCopied: ((String) -> Unit)? = null,
    selectable: Boolean = false,
    selectionController: SmoothSelectionController? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (Throwable) -> Unit)? = null,
) {
    val errorHandler by rememberUpdatedState(onError)
    // HTML is a rendering option, not a new stream. Keep collecting the same
    // Flow when the switch changes and project the current prefix below.
    val snapshot by produceState(initialValue = StreamSnapshot(), key1 = chunks, key2 = throttleMillis) {
        value = StreamSnapshot()
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis())
        var pending: Job? = null
        var hasReceivedData = false
        try {
            chunks.collect { chunk ->
                hasReceivedData = true
                val wait = buffer.append(chunk, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = StreamSnapshot(buffer.visibleText, hasReceivedData = true)
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = StreamSnapshot(buffer.visibleText, hasReceivedData = true)
                    }
                }
            }
            pending?.cancel()
            buffer.finish(SystemClock.uptimeMillis())
            value = StreamSnapshot(buffer.visibleText, complete = true, hasReceivedData = hasReceivedData)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            errorHandler(error)
            value = StreamSnapshot(buffer.visibleText, hasReceivedData = hasReceivedData, error = error)
        } finally {
            pending?.cancel()
        }
    }
    StreamMarkdownContent(snapshot, modifier, onLinkClick, onImageClick, enableHtml, styleSheet,
        plugins, onImageClickWithMetadata, imageBuilder, scrollable, codeBlockOptions, codeBlockBuilder,
        onCodeCopied, selectable, selectionController, loadingContent, errorContent)
}

/** Renders a cumulative streaming source. A late-composed chat bubble receives the latest
 * complete prefix from [StateFlow], so lazy list recycling cannot lose earlier chunks.
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
    codeBlockOptions: CodeBlockOptions = CodeBlockOptions(),
    codeBlockBuilder: (@Composable (String, String?) -> Unit)? = null,
    onCodeCopied: ((String) -> Unit)? = null,
    selectable: Boolean = false,
    selectionController: SmoothSelectionController? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (Throwable) -> Unit)? = null,
) {
    val errorHandler by rememberUpdatedState(onError)
    val snapshot by produceState(initialValue = StreamSnapshot(), key1 = prefixes, key2 = throttleMillis) {
        value = StreamSnapshot()
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis())
        var pending: Job? = null
        try {
            prefixes.collect { prefix ->
                val wait = buffer.appendPrefix(prefix, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = StreamSnapshot(buffer.visibleText, hasReceivedData = true)
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = StreamSnapshot(buffer.visibleText, hasReceivedData = true)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            errorHandler(error)
            value = StreamSnapshot(buffer.visibleText, hasReceivedData = true, error = error)
        } finally {
            pending?.cancel()
        }
    }
    StreamMarkdownContent(snapshot, modifier, onLinkClick, onImageClick, enableHtml, styleSheet,
        plugins, onImageClickWithMetadata, imageBuilder, scrollable, codeBlockOptions, codeBlockBuilder,
        onCodeCopied, selectable, selectionController, loadingContent, errorContent)
}

@Composable
private fun StreamMarkdownContent(
    snapshot: StreamSnapshot,
    modifier: Modifier,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    enableHtml: Boolean,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry?,
    onImageClickWithMetadata: ((String, String?, String?) -> Unit)?,
    imageBuilder: (@Composable (String, String?, String?) -> Unit)?,
    scrollable: Boolean,
    codeBlockOptions: CodeBlockOptions,
    codeBlockBuilder: (@Composable (String, String?) -> Unit)?,
    onCodeCopied: ((String) -> Unit)?,
    selectable: Boolean,
    selectionController: SmoothSelectionController?,
    loadingContent: (@Composable () -> Unit)?,
    errorContent: (@Composable (Throwable) -> Unit)?,
) {
    when {
        snapshot.error != null && errorContent != null -> errorContent(snapshot.error)
        !snapshot.hasReceivedData && !snapshot.complete && loadingContent != null -> loadingContent()
        else -> SmoothMarkdown(snapshot.renderText(enableHtml), modifier, onLinkClick, onImageClick, enableHtml,
            codeBlockOptions = codeBlockOptions, codeBlockBuilder = codeBlockBuilder,
            onCodeCopied = onCodeCopied, styleSheet = styleSheet, plugins = plugins,
            onImageClickWithMetadata = onImageClickWithMetadata, imageBuilder = imageBuilder,
            scrollable = scrollable, enableCache = false, selectable = selectable,
            selectionController = selectionController)
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
