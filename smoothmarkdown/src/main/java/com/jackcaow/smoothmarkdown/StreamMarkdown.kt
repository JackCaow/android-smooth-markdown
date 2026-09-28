package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Appends incoming chunks and renders the accumulated Markdown document. */
@Composable
fun StreamMarkdown(
    chunks: Flow<String>,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    val markdown by produceState(initialValue = "", key1 = chunks) {
        val buffer = StringBuilder()
        try {
            chunks.collect { chunk ->
                buffer.append(chunk)
                value = buffer.toString()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onError(error)
        }
    }
    SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick)
}
