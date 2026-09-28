package com.jackcaow.smoothmarkdown.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Mirrors Flutter's HTML demo controls and its 40 ms word-level stream. */
@Composable
fun HtmlDemo(
    markdown: String,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var enableHtml by remember { mutableStateOf(true) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamChunks by remember { mutableStateOf<Flow<String>?>(null) }
    var hasChunk by remember { mutableStateOf(false) }
    val chunks = remember(markdown) { Regex("\\S+\\s*").findAll(markdown).map { it.value }.toList() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HTML")
            Switch(checked = enableHtml, onCheckedChange = { enableHtml = it },
                modifier = Modifier.testTag("html-enabled"))
            IconButton(
                onClick = {
                    if (isStreaming) {
                        isStreaming = false
                        streamChunks = null
                    } else {
                        hasChunk = false
                        streamChunks = flow {
                            for (chunk in chunks) {
                                emit(chunk)
                                hasChunk = true
                                delay(40)
                            }
                            isStreaming = false
                        }
                        isStreaming = true
                    }
                },
                modifier = Modifier.testTag("html-stream-toggle"),
            ) { Text(if (isStreaming) "■" else "▶") }
        }
        if (isStreaming) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("html-progress"))
        val activeChunks = streamChunks
        if (activeChunks == null) {
            SmoothMarkdown(
                markdown,
                modifier = Modifier.weight(1f).testTag("html-markdown"),
                onLinkClick = onLinkClick,
                enableHtml = enableHtml,
                styleSheet = styleSheet,
                plugins = plugins,
            )
        } else {
            Box(Modifier.weight(1f)) {
                StreamMarkdown(
                    chunks = activeChunks,
                    modifier = Modifier.fillMaxSize().testTag("html-markdown"),
                    onLinkClick = onLinkClick,
                    enableHtml = enableHtml,
                    styleSheet = styleSheet,
                    plugins = plugins,
                )
                if (isStreaming && !hasChunk) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
