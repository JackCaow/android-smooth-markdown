package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Title
import coil.compose.AsyncImage
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
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorHostAction
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImagePickEvent
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImagePickStatus
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorMode
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor
import java.io.File
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

private fun exampleIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Filled.FormatBold
    1 -> Icons.Filled.Title
    2 -> Icons.Filled.FormatListBulleted
    3 -> Icons.Filled.Code
    4 -> Icons.Filled.FormatQuote
    5 -> Icons.Filled.Link
    6 -> Icons.Filled.AutoAwesome
    7 -> Icons.Filled.Palette
    8 -> Icons.Filled.Dashboard
    else -> Icons.Filled.Article
}

private fun demoIcon(id: String): ImageVector = when (id) {
    "math" -> Icons.Filled.Calculate
    "stream" -> Icons.Filled.Stream
    "footnote" -> Icons.Filled.NoteAdd
    "html" -> Icons.Filled.Code
    "chat-list" -> Icons.Filled.Chat
    "ai" -> Icons.Filled.AutoAwesome
    "conversation-list" -> Icons.Filled.Forum
    "plugin" -> Icons.Filled.Extension
    "mermaid" -> Icons.Filled.Schema
    else -> Icons.Filled.Article
}

/** Flutter's Drawer uses flat ListTiles (56/72 dp), rather than M3 navigation pills. */
@Composable
private fun DemoDrawerItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val iconColor = if (selected) {
        if (isDark) Color(0xFF90CAF9) else Color(0xFF2196F3)
    } else if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.54f)
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = if (subtitle == null) 56.dp else 72.dp)
            .background(if (selected && isDark) Color(0xFF161B22) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconColor)
        Spacer(Modifier.width(32.dp))
        Column {
            Text(title, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (isDark) Color.White else if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp,
                color = if (isDark) Color.White.copy(alpha = 0.38f) else Color.Gray)
        }
    }
}

// Flutter's six presets share two chrome palettes: each preset selects its own
// MarkdownStyleSheet, while its brightness controls the surrounding demo page.
internal fun demoThemeIsDark(themeIndex: Int): Boolean = themeIndex in setOf(1, 3, 5)

internal fun demoColorScheme(themeIndex: Int): ColorScheme = if (demoThemeIsDark(themeIndex)) {
    darkColorScheme(
        background = Color(0xFF0D1117),
        surface = Color(0xFF161B22),
        surfaceContainerLow = Color(0xFF161B22),
    )
} else {
    lightColorScheme(background = Color.White, surface = Color.White)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editorHost = DemoEditorActivityHost(this)
        val examples = runCatching { loadExamples(assets) }
        val staticPages = runCatching { loadDemoPageMarkdown(assets) }
        val streamingFixture = runCatching { loadStreamingDemoFixture(assets) }
        val localizations = runCatching { DemoLocalizations.load(assets) }
        val preferences = getSharedPreferences("smooth-markdown-demo", MODE_PRIVATE)
        setContent {
            var themeIndex by rememberSaveable { mutableStateOf(0) }
            MaterialTheme(colorScheme = demoColorScheme(themeIndex)) {
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
                    themeIndex = themeIndex,
                    onThemeChange = { themeIndex = it },
                    openMermaid = { startActivity(Intent(this, MermaidDemoActivity::class.java)) },
                    openPerformance = { startActivity(Intent(this, PerformanceActivity::class.java)) },
                    openChatList = {
                        startActivity(Intent(this, ChatListActivity::class.java).apply {
                            putExtra(ChatListActivity.EXTRA_PARENT_DARK, demoThemeIsDark(themeIndex))
                        })
                    },
                    openAIChat = { startActivity(Intent(this, AIChatActivity::class.java)) },
                    openConversationList = { startActivity(Intent(this, ConversationListActivity::class.java)) },
                    openLink = { url -> Toast.makeText(this, "Link tapped: $url", Toast.LENGTH_SHORT).show() },
                    pickEditorImage = editorHost::pickImage,
                    importEditorMarkdown = editorHost::importMarkdown,
                    exportEditorMarkdown = editorHost::exportMarkdown,
                    resolveEditorImage = editorHost::resolveImage,
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
    themeIndex: Int,
    onThemeChange: (Int) -> Unit,
    openMermaid: () -> Unit,
    openPerformance: () -> Unit,
    openChatList: () -> Unit,
    openAIChat: () -> Unit,
    openConversationList: () -> Unit,
    openLink: (String) -> Unit,
    pickEditorImage: suspend () -> MarkdownEditorImageSelection?,
    importEditorMarkdown: suspend () -> String?,
    exportEditorMarkdown: suspend (String) -> Unit,
    resolveEditorImage: (String) -> File?,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isDark = demoThemeIsDark(themeIndex)
    var languageCode by rememberSaveable { mutableStateOf(initialLanguage.code) }
    val language = DemoLanguage.fromCode(languageCode)
    var exampleId by remember { mutableStateOf(examples.first().id) }
    var pageId by remember { mutableStateOf(examples.first().id) }
    var themeMenu by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var exportedLength by remember { mutableStateOf<Int?>(null) }
    var imagePickStatus by remember { mutableStateOf<MarkdownEditorImagePickStatus?>(null) }
    var hostActionError by remember { mutableStateOf<String?>(null) }
    var pdfExportLength by remember { mutableStateOf<Int?>(null) }
    var tappedWikilink by remember { mutableStateOf<String?>(null) }
    val example = examples.first { it.id == exampleId }
    val specialPage = dedicatedPages.firstOrNull { it.id == pageId }
    val isEditor = pageId == "editor"
    val isHome = pageId == exampleId
    val currentMarkdown = if (pageId == exampleId) example.markdown
        else staticPages[pageId] ?: dedicatedMarkdown(pageId)
    val currentTitle = when (pageId) {
        "editor" -> "Markdown Editor"
        "math" -> "Math Formula Demo"
        "stream" -> "Streaming Markdown Demo"
        "footnote" -> "Footnotes Demo"
        "html" -> "HTML Tags Demo"
        "plugin" -> "Plugin System Demo"
        else -> specialPage?.let { localizations.page(language, it) }
            ?: example.title
    }
    val controller = remember(exampleId, isEditor) {
        MarkdownEditorController(if (isEditor) staticPages.getValue("editor") else example.markdown).also {
            if (isEditor) it.mode = MarkdownEditorMode.FORMATTED
        }
    }
    val plugins = remember { ParserPluginRegistry().also {
        it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin(), AdmonitionPlugin(),
            MermaidPlugin(), ThinkingPlugin(), ArtifactPlugin(), ToolCallPlugin()))
    } }
    BackHandler(enabled = !isHome && drawerState.isClosed) { pageId = exampleId }
    fun select(id: String) {
        if (examples.any { it.id == id }) exampleId = id
        pageId = id
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isHome,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerContainerColor = if (isDark) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                            .background(Brush.linearGradient(if (isDark)
                                listOf(Color(0xFF161B22), Color(0xFF21262D))
                            else listOf(Color.Blue, Color(0xFF9C27B0))))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Icon(Icons.Filled.Article, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(localizations.text(language, "drawer_header_title"),
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    DemoDrawerItem(
                        title = "Markdown Editor", subtitle = "Scratch-style editing preview",
                        icon = Icons.Filled.EditNote, selected = false, isDark = isDark,
                        onClick = { select("editor") }, modifier = Modifier.testTag("nav-editor"),
                    )
                    HorizontalDivider()
                    examples.forEachIndexed { index, item ->
                        DemoDrawerItem(
                            // Flutter's example titles are fixed strings across all demo languages.
                            title = item.title,
                            icon = exampleIcon(index), selected = pageId == item.id,
                            isDark = isDark, onClick = { select(item.id) },
                            modifier = Modifier.testTag("nav-${item.id}"),
                        )
                    }
                    HorizontalDivider()
                    Text(localizations.text(language, "drawer_demos"),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                    dedicatedPages.forEach { item ->
                        DemoDrawerItem(
                            title = localizations.page(language, item),
                            subtitle = item.subtitle.takeIf { item.id in setOf("html", "chat-list", "ai", "conversation-list", "plugin", "mermaid") },
                            icon = demoIcon(item.id), selected = pageId == item.id,
                            isDark = isDark, onClick = {
                                if (item.id == "mermaid") scope.launch { drawerState.close(); openMermaid() }
                                else if (item.id == "performance") scope.launch { drawerState.close(); openPerformance() }
                                else if (item.id == "chat-list") scope.launch { drawerState.close(); openChatList() }
                                else if (item.id == "ai") scope.launch { drawerState.close(); openAIChat() }
                                else if (item.id == "conversation-list") scope.launch { drawerState.close(); openConversationList() }
                                else select(item.id)
                            }, modifier = Modifier.testTag("nav-${item.id}"),
                        )
                    }
                    HorizontalDivider()
                    Text(localizations.text(language, "language"),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                    DemoLanguage.entries.forEach { option ->
                        DemoDrawerItem(
                            title = option.nativeName, icon = Icons.Filled.Language,
                            selected = false, isDark = isDark, onClick = {
                                languageCode = option.code
                                onLanguageChange(option)
                                scope.launch { drawerState.close() }
                            }, modifier = Modifier.testTag("language-${option.code}"),
                        )
                    }
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            if (isHome) {
                Row(Modifier.fillMaxWidth().height(56.dp)
                    .background(if (isDark) Color(0xFF161B22) else MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("open-navigation")) {
                        Icon(Icons.Filled.Menu, contentDescription = localizations.chrome(language, "examples"))
                    }
                    Text("Smooth Markdown Demo", style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("demo-app-title"))
                    IconButton(onClick = { select("editor") }, modifier = Modifier.testTag("open-editor")) {
                        Icon(Icons.Filled.EditNote, contentDescription = localizations.chrome(language, "edit"))
                    }
                    Box {
                        IconButton(onClick = { themeMenu = true }, modifier = Modifier.testTag("open-theme")) {
                            Icon(Icons.Filled.Palette,
                                contentDescription = localizations.text(language, "tooltip_theme"))
                        }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            themeOptions.forEachIndexed { index, item ->
                                DropdownMenuItem(text = { Text(localizations.theme(language, index)) },
                                    leadingIcon = { Icon(if (index == themeIndex) Icons.Filled.CheckCircle
                                        else Icons.Filled.RadioButtonUnchecked, contentDescription = null,
                                        tint = if (index == themeIndex) Color(0xFF2196F3)
                                        else MaterialTheme.colorScheme.onSurfaceVariant) },
                                    trailingIcon = { Icon(if (demoThemeIsDark(index)) Icons.Filled.DarkMode
                                        else Icons.Filled.LightMode, contentDescription = null,
                                        modifier = Modifier.size(16.dp)) }, onClick = {
                                    onThemeChange(index)
                                    themeMenu = false
                                }, modifier = Modifier.testTag("theme-$index"))
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pageId = exampleId }, modifier = Modifier.testTag("demo-back")) {
                        Text("‹ ${localizations.chrome(language, "examples")}")
                    }
                    Text(currentTitle, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("current-title"))
                }
            }
            if (isEditor) {
                Text("Scratch-style editor preview", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("editor-intro"))
                Text("Toolbar, slash commands, wikilinks, source and formatted modes, Markdown import/export, image selection, table editing, search, and focus mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("Hardware keyboard: Ctrl+E inline code · Ctrl+Alt+1–6 headings · Ctrl+Shift+B quote · Ctrl+Shift+7/8 lists.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("editor-shortcuts-help"))
                exportedLength?.let {
                    Text("Last export: $it characters",
                        modifier = Modifier.testTag("export-status"))
                }
                imagePickStatus?.let {
                    Text("Image: ${it.name.lowercase()}", modifier = Modifier.testTag("image-pick-status"))
                }
                hostActionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editor-host-error"))
                }
                pdfExportLength?.let {
                    Text("PDF export requested for $it characters",
                        modifier = Modifier.testTag("pdf-export-status"))
                }
                tappedWikilink?.let { Text("Wikilink: $it", modifier = Modifier.testTag("wikilink-tap-status")) }
                SmoothMarkdownEditor(
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    onPickImage = pickEditorImage,
                    onImagePickEvent = { event: MarkdownEditorImagePickEvent ->
                        imagePickStatus = event.status
                        if (event.status == MarkdownEditorImagePickStatus.PICKING) hostActionError = null
                    },
                    onImportMarkdown = importEditorMarkdown,
                    onExportMarkdown = { markdown ->
                        exportEditorMarkdown(markdown)
                        exportedLength = markdown.length
                    },
                    onHostActionError = { action: MarkdownEditorHostAction, error: Throwable ->
                        hostActionError = "${action.name.lowercase().replace('_', ' ')}: ${error.message ?: "failed"}"
                    },
                    imageBuilder = { source, alt, title ->
                        val model = resolveEditorImage(source)
                            ?: if (source.contains(':')) source else "file:///android_asset/${source.trimStart('/')}"
                        AsyncImage(model = model, contentDescription = alt?.ifBlank { title ?: "Image" } ?: title,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 240.dp),
                            contentScale = ContentScale.Fit)
                    },
                    onExportPdf = { markdown, _ -> pdfExportLength = markdown.length },
                    wikilinkSuggestions = listOf("Daily Notes", "Project Plan", "Research Index", "Scratch Reference"),
                    onTapWikilink = { tappedWikilink = it },
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
            } else if (pageId == "plugin") {
                PluginDemo(
                    markdown = currentMarkdown,
                    styleSheet = themeOptions[themeIndex].second,
                    plugins = plugins,
                    modifier = Modifier.weight(1f),
                )
            } else {
                SmoothMarkdown(currentMarkdown, Modifier.weight(1f).testTag("reader-scroll"), onLinkClick = openLink,
                    enableHtml = pageId == "html" || pageId == "details-summary",
                    styleSheet = themeOptions[themeIndex].second, plugins = plugins)
            }
        }
        if (isHome) FloatingActionButton(onClick = { showSource = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("open-source")) {
            Icon(Icons.Filled.Code, contentDescription = localizations.chrome(language, "source_title"))
        }
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
