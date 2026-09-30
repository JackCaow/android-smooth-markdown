package com.jackcaow.smoothmarkdown.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import kotlinx.coroutines.delay

private data class PluginFeedback(val id: Int, val message: String)

/** The Flutter plugin fixture, with its page-level source expansion control. */
@Composable
fun PluginDemo(
    markdown: String,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry,
    modifier: Modifier = Modifier,
) {
    var showSource by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PluginFeedback?>(null) }
    var nextFeedbackId by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            delay(2_000)
            feedback = null
        }
    }
    Column(modifier.fillMaxSize()) {
        Text("插件系统演示", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Text("展示 Flutter Smooth Markdown 的插件系统功能",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Surface(Modifier.padding(12.dp).weight(1f), tonalElevation = 2.dp) {
            Column {
                Box(Modifier.weight(1f)) {
                    SmoothMarkdown(
                        markdown,
                        Modifier.fillMaxSize().testTag("plugin-reader-scroll"),
                        styleSheet = styleSheet,
                        plugins = plugins,
                        onMentionClick = { username ->
                            feedback = PluginFeedback(++nextFeedbackId, "点击了用户: @$username")
                        },
                        onHashtagClick = { tag ->
                            feedback = PluginFeedback(++nextFeedbackId, "点击了标签: #$tag")
                        },
                    )
                    feedback?.let { current ->
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                .testTag("plugin-tap-feedback"),
                        ) { Text(current.message) }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { showSource = !showSource },
                    modifier = Modifier.fillMaxWidth().testTag("plugin-source-toggle")) {
                    Text("查看 Markdown 源码")
                }
                if (showSource) {
                    SelectionContainer {
                        Text(markdown, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()).padding(16.dp)
                                .testTag("plugin-source"))
                    }
                }
            }
        }
    }
}
