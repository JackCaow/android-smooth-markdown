package com.jackcaow.smoothmarkdown.demo

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown

/** The Flutter plugin fixture, with its page-level source expansion control. */
@Composable
fun PluginDemo(
    markdown: String,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry,
    modifier: Modifier = Modifier,
) {
    var showSource by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Text("插件系统演示", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Text("展示 Flutter Smooth Markdown 的插件系统功能",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Surface(Modifier.padding(12.dp).weight(1f), tonalElevation = 2.dp) {
            Column {
                SmoothMarkdown(markdown, Modifier.weight(1f), styleSheet = styleSheet, plugins = plugins)
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
