package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.AdmonitionPlugin
import com.jackcaow.smoothmarkdown.ArtifactPlugin
import com.jackcaow.smoothmarkdown.EmojiPlugin
import com.jackcaow.smoothmarkdown.HashtagPlugin
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.MentionPlugin
import com.jackcaow.smoothmarkdown.MermaidPlugin
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.StreamMarkdown
import com.jackcaow.smoothmarkdown.ThinkingPlugin
import com.jackcaow.smoothmarkdown.ToolCallPlugin
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val themeOptions = listOf(
    "Default light" to MarkdownStyleSheet.light(),
    "Default dark" to MarkdownStyleSheet.dark(),
    "GitHub" to MarkdownStyleSheet.github(),
    "GitHub dark" to MarkdownStyleSheet.github(dark = true),
    "VS Code" to MarkdownStyleSheet.vscode(),
    "VS Code dark" to MarkdownStyleSheet.vscode(dark = true),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val examples = runCatching { loadExamples(assets) }
        setContent {
            MaterialTheme {
                if (examples.isFailure) {
                    Text("Unable to load Markdown examples: ${examples.exceptionOrNull()?.message}",
                        modifier = Modifier.safeDrawingPadding().testTag("example-load-error"))
                } else DemoHome(
                    examples = examples.getOrThrow(),
                    openMermaid = { startActivity(Intent(this, MermaidDemoActivity::class.java)) },
                    openPerformance = { startActivity(Intent(this, PerformanceActivity::class.java)) },
                    openLink = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                )
            }
        }
    }
}

@Composable
private fun DemoHome(
    examples: List<DemoExample>,
    openMermaid: () -> Unit,
    openPerformance: () -> Unit,
    openLink: (String) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var exampleId by remember { mutableStateOf(examples.first().id) }
    var pageId by remember { mutableStateOf(examples.first().id) }
    var themeIndex by remember { mutableStateOf(0) }
    var themeMenu by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var exportedLength by remember { mutableStateOf<Int?>(null) }
    val example = examples.first { it.id == exampleId }
    val specialPage = dedicatedPages.firstOrNull { it.id == pageId }
    val isEditor = pageId == "editor"
    val currentMarkdown = if (pageId == exampleId || isEditor) example.markdown else dedicatedMarkdown(pageId)
    val currentTitle = if (isEditor) "Markdown Editor" else specialPage?.title ?: example.title
    val controller = remember(exampleId, isEditor) { MarkdownEditorController(example.markdown) }
    val plugins = remember { ParserPluginRegistry().also {
        it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin(), AdmonitionPlugin(),
            MermaidPlugin(), ThinkingPlugin(), ArtifactPlugin(), ToolCallPlugin()))
    } }
    BackHandler(enabled = pageId != exampleId && drawerState.isClosed) { pageId = exampleId }
    fun select(id: String) {
        if (examples.any { it.id == id }) exampleId = id
        pageId = id
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("Smooth Markdown Demo", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(12.dp))
                    NavigationDrawerItem(label = { Text("Markdown Editor") }, selected = isEditor,
                        onClick = { select("editor") }, modifier = Modifier.testTag("nav-editor"))
                    HorizontalDivider()
                    Text("Examples", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
                    examples.forEach { item ->
                        NavigationDrawerItem(label = { Text(item.title) }, selected = pageId == item.id,
                            onClick = { select(item.id) }, modifier = Modifier.testTag("nav-${item.id}"))
                    }
                    HorizontalDivider()
                    Text("Demos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
                    dedicatedPages.forEach { item ->
                        NavigationDrawerItem(label = { Text(item.title) }, selected = pageId == item.id,
                            onClick = {
                                if (item.id == "mermaid") scope.launch { drawerState.close(); openMermaid() }
                                else if (item.id == "performance") scope.launch { drawerState.close(); openPerformance() }
                                else select(item.id)
                            }, modifier = Modifier.testTag("nav-${item.id}"))
                    }
                    HorizontalDivider()
                    Text("Languages (localization pending)", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(12.dp))
                    Text("中文 · English · 日本語 · Español · Français · 한국어",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.testTag("open-navigation")) { Text("☰ Examples") }
                TextButton(onClick = { select("editor") }, modifier = Modifier.testTag("open-editor")) { Text("Edit") }
                Column {
                    TextButton(onClick = { themeMenu = true }, modifier = Modifier.testTag("open-theme")) { Text("Theme") }
                    DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                        themeOptions.forEachIndexed { index, item ->
                            DropdownMenuItem(text = { Text(item.first) }, onClick = {
                                themeIndex = index
                                themeMenu = false
                            }, modifier = Modifier.testTag("theme-$index"))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(currentTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
                    .testTag("current-title"))
                Text(themeOptions[themeIndex].first, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("current-theme"))
            }
            if (isEditor) {
                exportedLength?.let { Text("Exported $it characters", modifier = Modifier.testTag("export-status")) }
                SmoothMarkdownEditor(
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    onPickImage = { MarkdownEditorImageSelection("smooth-markdown-mark.svg", "Demo mark", "Bundled SVG") },
                    onImportMarkdown = { "# Imported sample\n\nA host supplied this Markdown." },
                    onExportMarkdown = { exportedLength = it.length },
                )
            } else if (pageId == "stream") {
                val chunks = remember(pageId) { flow {
                    currentMarkdown.chunked(16).forEach { chunk -> emit(chunk); delay(55) }
                } }
                StreamMarkdown(chunks, Modifier.weight(1f), onLinkClick = openLink,
                    styleSheet = themeOptions[themeIndex].second, plugins = plugins)
            } else {
                SmoothMarkdown(currentMarkdown, Modifier.weight(1f), onLinkClick = openLink,
                    enableHtml = pageId == "html" || pageId == "details-summary",
                    styleSheet = themeOptions[themeIndex].second, plugins = plugins)
            }
        }
        if (!isEditor) FloatingActionButton(onClick = { showSource = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("open-source")) {
            Text("Source", modifier = Modifier.padding(horizontal = 12.dp))
        }
        }
        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Markdown Source") },
                text = {
                    SelectionContainer {
                        Text(if (isEditor) controller.text else currentMarkdown,
                            modifier = Modifier.verticalScroll(rememberScrollState()).testTag("markdown-source"),
                            fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = { TextButton(onClick = { showSource = false }) { Text("Close") } },
            )
        }
    }
}
