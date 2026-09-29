package com.jackcaow.smoothmarkdown.demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.mermaid.MermaidDiagramView
import com.jackcaow.smoothmarkdown.mermaid.MermaidParser
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Flutter example's Mermaid gallery, backed by its synchronized source fixtures. */
class MermaidDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val examples = runCatching { loadMermaidGallery(assets) }
        setContent {
            if (examples.isSuccess) MermaidGallery(examples.getOrThrow())
            else MaterialTheme {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                    Text("无法加载 Mermaid 示例：${examples.exceptionOrNull()?.message ?: "未知错误"}")
                }
            }
        }
    }
}

internal data class MermaidGalleryExample(
    val index: Int,
    val category: String,
    val title: String,
    val description: String,
    val code: String,
)

internal fun loadMermaidGallery(assets: AssetManager): List<MermaidGalleryExample> {
    val directory = "examples/mermaid"
    val manifest = JSONObject(assets.open("$directory/gallery.json").bufferedReader().use { it.readText() })
    val entries = manifest.getJSONArray("examples")
    require(entries.length() == 40) { "预期 40 个示例，实际 ${entries.length()} 个" }
    return List(entries.length()) { position ->
        val entry = entries.getJSONObject(position)
        val index = entry.getInt("index")
        require(index == position + 1) { "示例顺序错误：$index" }
        val file = entry.getString("file")
        require(Regex("mermaid-\\d{2}\\.mmd").matches(file)) { "无效的示例文件：$file" }
        MermaidGalleryExample(
            index = index,
            category = entry.getString("category"),
            title = entry.getString("title"),
            description = entry.getString("description"),
            code = assets.open("$directory/$file").bufferedReader().use { it.readText() },
        )
    }
}

private val categoryNames = mapOf(
    "flowchart" to "流程图 (Flowchart)",
    "sequence" to "时序图 (Sequence)",
    "pie" to "饼图 (Pie Chart)",
    "gantt" to "甘特图 (Gantt Chart)",
    "timeline" to "时间线 (Timeline)",
    "kanban" to "看板 (Kanban)",
    "complex" to "复杂示例",
    "radar" to "雷达图 (Radar Chart)",
    "xy" to "XY 图 (XY Chart)",
)

@Composable
private fun MermaidGallery(examples: List<MermaidGalleryExample>) {
    val context = LocalContext.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var darkMode by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val example = examples[selectedIndex]
    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("Mermaid 图表", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
                    Text("${examples.size} 个示例", modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                    HorizontalDivider()
                    LazyColumn {
                        itemsIndexed(examples) { index, item ->
                            // Manifest categories correct the Flutter drawer's stale hardcoded offsets.
                            if (index == 0 || examples[index - 1].category != item.category) {
                                Text(
                                    categoryNames[item.category] ?: item.category,
                                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                item.title,
                                modifier = Modifier.fillMaxWidth().selectable(
                                    selected = selectedIndex == index,
                                    onClick = { selectedIndex = index; scope.launch { drawerState.close() } },
                                ).padding(horizontal = 20.dp, vertical = 12.dp)
                                    .testTag("mermaid-nav-${item.index}"),
                                fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("mermaid-open-navigation")) { Text("☰ 目录") }
                    Text("Mermaid 图表测试", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { darkMode = !darkMode },
                        modifier = Modifier.testTag("mermaid-theme")) { Text(if (darkMode) "☀ 浅色" else "☾ 深色") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .testTag("mermaid-content-scroll")) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(example.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(example.description, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${example.index}/${examples.size}", color = MaterialTheme.colorScheme.primary)
                    }
                    val supported = remember(example.code) {
                        runCatching { MermaidParser.parse(example.code) != null }.getOrDefault(false)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().height(600.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        if (supported) MermaidDiagramView(
                            example.code,
                            Modifier.fillMaxSize(),
                            onNodeTap = { nodeId ->
                                Toast.makeText(context, "点击了节点: $nodeId", Toast.LENGTH_SHORT).show()
                            },
                        )
                        else Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("此图表暂不支持原生预览，请查看下方 Mermaid 源码。")
                        }
                    }
                    SourceCard(example.code, darkMode)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { selectedIndex-- }, enabled = selectedIndex > 0) { Text("← 上一个") }
                    Text("${selectedIndex + 1} / ${examples.size}", style = MaterialTheme.typography.labelLarge)
                    Button(onClick = { selectedIndex++ }, enabled = selectedIndex < examples.lastIndex,
                        modifier = Modifier.testTag("mermaid-next")) { Text("下一个 →") }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(code: String, darkMode: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().height(250.dp).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (darkMode) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.inverseSurface),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mermaid 代码", modifier = Modifier.weight(1f),
                color = if (darkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Mermaid 代码", code))
                Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
            }) {
                Text("复制代码", color = if (darkMode) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
        SelectionContainer {
            Text(code, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                color = if (darkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}
