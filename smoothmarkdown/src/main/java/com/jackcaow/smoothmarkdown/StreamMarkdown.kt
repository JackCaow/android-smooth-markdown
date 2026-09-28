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
) {
    val errorHandler by rememberUpdatedState(onError)
    // HTML is a rendering option, not a new stream. Keep collecting the same
    // Flow when the switch changes and project the current prefix below.
    val snapshot by produceState(initialValue = StreamSnapshot(), key1 = chunks, key2 = throttleMillis) {
        value = StreamSnapshot()
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis())
        var pending: Job? = null
        try {
            chunks.collect { chunk ->
                val wait = buffer.append(chunk, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = StreamSnapshot(buffer.visibleText)
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = StreamSnapshot(buffer.visibleText)
                    }
                }
            }
            pending?.cancel()
            buffer.finish(SystemClock.uptimeMillis())
            value = StreamSnapshot(buffer.visibleText, complete = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            errorHandler(error)
        } finally {
            pending?.cancel()
        }
    }
    SmoothMarkdown(snapshot.renderText(enableHtml), modifier, onLinkClick, onImageClick, enableHtml,
        styleSheet = styleSheet, plugins = plugins, onImageClickWithMetadata = onImageClickWithMetadata,
        imageBuilder = imageBuilder)
}

internal data class StreamSnapshot(val text: String = "", val complete: Boolean = false) {
    fun renderText(enableHtml: Boolean): String =
        if (enableHtml && !complete) SafeHtml.safeRenderPrefix(text) else text
}
