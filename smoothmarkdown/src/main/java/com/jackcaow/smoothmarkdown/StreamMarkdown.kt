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

/** Appends incoming chunks and renders the accumulated Markdown document. */
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
) {
    val errorHandler by rememberUpdatedState(onError)
    val markdown by produceState(initialValue = "", key1 = chunks, key2 = throttleMillis, key3 = enableHtml) {
        value = ""
        val buffer = StreamMarkdownBuffer(throttleMillis.coerceAtLeast(0), SystemClock.uptimeMillis(), enableHtml)
        var pending: Job? = null
        try {
            chunks.collect { chunk ->
                val wait = buffer.append(chunk, SystemClock.uptimeMillis())
                pending?.cancel()
                if (wait == null) {
                    value = buffer.visibleText
                } else {
                    pending = launch {
                        delay(wait)
                        buffer.flush(SystemClock.uptimeMillis())
                        value = buffer.visibleText
                    }
                }
            }
            pending?.cancel()
            buffer.finish(SystemClock.uptimeMillis())
            value = buffer.visibleText
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            errorHandler(error)
        } finally {
            pending?.cancel()
        }
    }
    SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick, enableHtml, styleSheet)
}
