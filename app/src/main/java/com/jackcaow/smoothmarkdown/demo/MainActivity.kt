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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.jackcaow.smoothmarkdown.ThinkingPlugin
import com.jackcaow.smoothmarkdown.ToolCallPlugin
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorMode
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor
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
        val staticPages = runCatching { loadDemoPageMarkdown(assets) }
        val streamingFixture = runCatching { loadStreamingDemoFixture(assets) }
        val localizations = runCatching { DemoLocalizations.load(assets) }
        val preferences = getSharedPreferences("smooth-markdown-demo", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                if (examples.isFailure || staticPages.isFailure || streamingFixture.isFailure) {
                    Text("Unable to load Markdown examples: ${examples.exceptionOrNull()?.message ?: staticPages.exceptionOrNull()?.message ?: streamingFixture.exceptionOrNull()?.message}",
                        modifier = Modifier.safeDrawingPadding().testTag("example-load-error"))
                } else if (localizations.isFailure) {
                    Text("Unable to load demo languages: ${localizations.exceptionOrNull()?.message}",
                        modifier = Modifier.safeDrawingPadding().testTag("language-load-error"))
                } else DemoHome(
                    examples = examples.getOrThrow(),
                    staticPages = staticPages.getOrThrow(),
                    streamingFixture = streamingFixture.getOrThrow(),
                    localizations = localizations.getOrThrow(),
                    initialLanguage = DemoLanguage.fromCode(preferences.getString("language", null)),
                    onLanguageChange = { preferences.edit().putString("language", it.code).apply() },
                    openMermaid = { startActivity(Intent(this, MermaidDemoActivity::class.java)) },
                    openPerformance = { startActivity(Intent(this, PerformanceActivity::class.java)) },
                    openChatList = { startActivity(Intent(this, ChatListActivity::class.java)) },
                    openConversationList = { startActivity(Intent(this, ConversationListActivity::class.java)) },
                    openLink = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                )
            }
        }
    }
}

@Composable
private fun DemoHome(
    examples: List<DemoExample>,
    staticPages: Map<String, String>,
    streamingFixture: StreamingDemoFixture,
    localizations: DemoLocalizations,
    initialLanguage: DemoLanguage,
    onLanguageChange: (DemoLanguage) -> Unit,
    openMermaid: () -> Unit,
    openPerformance: () -> Unit,
    openChatList: () -> Unit,
    openConversationList: () -> Unit,
    openLink: (String) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var languageCode by rememberSaveable { mutableStateOf(initialLanguage.code) }
    val language = DemoLanguage.fromCode(languageCode)
    var exampleId by remember { mutableStateOf(examples.first().id) }
    var pageId by remember { mutableStateOf(examples.first().id) }
    var themeIndex by remember { mutableStateOf(0) }
    var themeMenu by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var exportedLength by remember { mutableStateOf<Int?>(null) }
    val example = examples.first { it.id == exampleId }
    val specialPage = dedicatedPages.firstOrNull { it.id == pageId }
    val isEditor = pageId == "editor"
    val currentMarkdown = if (pageId == exampleId) example.markdown
        else staticPages[pageId] ?: dedicatedMarkdown(pageId)
    val currentTitle = if (isEditor) "Markdown Editor" else if (pageId == "stream") {
        localizations.text(language, "streaming_demo_title")
    } else specialPage?.let {
        localizations.page(language, it)
    } ?: localizations.example(language, example)
    val controller = remember(exampleId, isEditor) {
        MarkdownEditorController(if (isEditor) staticPages.getValue("editor") else example.markdown).also {
            if (isEditor) it.mode = MarkdownEditorMode.FORMATTED
        }
    }
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
                    Text(localizations.text(language, "drawer_header_title"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(12.dp))
                    NavigationDrawerItem(label = { Text("Markdown Editor") }, selected = isEditor,
                        onClick = { select("editor") }, modifier = Modifier.testTag("nav-editor"))
                    HorizontalDivider()
                    Text(localizations.chrome(language, "examples"),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
                    examples.forEach { item ->
                        NavigationDrawerItem(label = { Text(localizations.example(language, item)) },
                            selected = pageId == item.id,
                            onClick = { select(item.id) }, modifier = Modifier.testTag("nav-${item.id}"))
                    }
                    HorizontalDivider()
                    Text(localizations.text(language, "drawer_demos"),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
                    dedicatedPages.forEach { item ->
                        NavigationDrawerItem(label = { Text(localizations.page(language, item)) },
                            selected = pageId == item.id,
                            onClick = {
                                if (item.id == "mermaid") scope.launch { drawerState.close(); openMermaid() }
                                else if (item.id == "performance") scope.launch { drawerState.close(); openPerformance() }
                                else if (item.id == "chat-list") scope.launch { drawerState.close(); openChatList() }
                                else if (item.id == "conversation-list") scope.launch { drawerState.close(); openConversationList() }
                                else select(item.id)
                            }, modifier = Modifier.testTag("nav-${item.id}"))
                    }
                    HorizontalDivider()
                    Text(localizations.text(language, "language"),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(12.dp))
                    DemoLanguage.entries.forEach { option ->
                        NavigationDrawerItem(label = { Text(option.nativeName) },
                            selected = language == option,
                            onClick = {
                                languageCode = option.code
                                onLanguageChange(option)
                                scope.launch { drawerState.close() }
                            }, modifier = Modifier.testTag("language-${option.code}"))
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.testTag("open-navigation")) {
                    Text("☰ ${localizations.chrome(language, "examples")}")
                }
                TextButton(onClick = { select("editor") }, modifier = Modifier.testTag("open-editor")) {
                    Text(localizations.chrome(language, "edit"))
                }
                Column {
                    TextButton(onClick = { themeMenu = true }, modifier = Modifier.testTag("open-theme")) {
                        Text(localizations.text(language, "drawer_theme"))
                    }
                    DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                        themeOptions.forEachIndexed { index, item ->
                            DropdownMenuItem(text = { Text(localizations.theme(language, index)) }, onClick = {
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
                Text(localizations.theme(language, themeIndex), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("current-theme"))
            }
            if (isEditor) {
                exportedLength?.let {
                    Text(localizations.exportStatus(language, it),
                        modifier = Modifier.testTag("export-status"))
                }
                SmoothMarkdownEditor(
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    onPickImage = { MarkdownEditorImageSelection("smooth-markdown-mark.svg", "Demo mark", "Bundled SVG") },
                    onImportMarkdown = { "# Imported sample\n\nA host supplied this Markdown." },
                    onExportMarkdown = { exportedLength = it.length },
                )
            } else if (pageId == "stream") {
                StreamingDemo(
                    fixture = streamingFixture,
                    styleSheet = themeOptions[themeIndex].second,
                    plugins = plugins,
                    onLinkClick = openLink,
                    modifier = Modifier.weight(1f),
                )
            } else if (pageId == "html") {
                HtmlDemo(
                    markdown = currentMarkdown,
                    styleSheet = themeOptions[themeIndex].second,
                    plugins = plugins,
                    onLinkClick = openLink,
                    modifier = Modifier.weight(1f),
                )
            } else {
                SmoothMarkdown(currentMarkdown, Modifier.weight(1f), onLinkClick = openLink,
                    enableHtml = pageId == "html" || pageId == "details-summary",
                    styleSheet = themeOptions[themeIndex].second, plugins = plugins)
            }
        }
        if (!isEditor && pageId != "stream") FloatingActionButton(onClick = { showSource = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("open-source")) {
            Text(localizations.chrome(language, "source"), modifier = Modifier.padding(horizontal = 12.dp))
        }
        }
        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text(localizations.chrome(language, "source_title")) },
                text = {
                    SelectionContainer {
                        Text(if (isEditor) controller.text else currentMarkdown,
                            modifier = Modifier.verticalScroll(rememberScrollState()).testTag("markdown-source"),
                            fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = { TextButton(onClick = { showSource = false }) {
                    Text(localizations.chrome(language, "close"))
                } },
            )
        }
    }
}
