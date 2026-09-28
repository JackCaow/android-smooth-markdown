package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.ArtifactPlugin
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.StreamMarkdown
import com.jackcaow.smoothmarkdown.ThinkingPlugin
import com.jackcaow.smoothmarkdown.ToolCallPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val aiPurple = Color(0xFF667EEA)
private val aiBlue = Color(0xFF007AFF)
private val qwenModels = listOf(
    "qwen3-235b-a22b" to "Qwen3 Max (思考模式)",
    "qwen-max" to "Qwen Max",
    "qwen-plus" to "Qwen Plus",
    "qwen-turbo" to "Qwen Turbo",
)

private data class AIQuickPrompt(
    val id: String,
    val label: String,
    val description: String,
    val prompt: String,
    val response: String,
)

private data class AIChatFixture(
    val welcome: String,
    val quickPrompts: List<AIQuickPrompt>,
    val genericResponseTemplate: String,
    val chunkSizeUTF16: Int,
    val delayMillis: Long,
)

private data class AIChatMessage(
    val id: Long,
    val content: String,
    val user: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val streamSession: MarkdownStreamSession? = null,
)

/** Content is synchronized from Flutter's example/lib/ai_chat_demo.dart. */
private fun loadAIChatFixture(json: String): AIChatFixture {
    val root = JSONObject(json)
    val entries = root.getJSONArray("quickPrompts")
    val prompts = (0 until entries.length()).map { index ->
        val entry = entries.getJSONObject(index)
        AIQuickPrompt(
            entry.getString("id"), entry.getString("label"), entry.getString("description"),
            entry.getString("prompt"), entry.getString("response"),
        )
    }
    require(prompts.size == 6 && prompts.map { it.id }.toSet().size == 6) {
        "Expected Flutter's six AI Chat quick prompts"
    }
    return AIChatFixture(
        welcome = root.getString("welcome"),
        quickPrompts = prompts,
        genericResponseTemplate = root.getString("genericResponseTemplate"),
        chunkSizeUTF16 = root.getInt("chunkSizeUTF16").also { require(it > 0) },
        delayMillis = root.getLong("delayMillis").also { require(it >= 0) },
    )
}

class AIChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixture = runCatching {
            loadAIChatFixture(assets.open("examples/ai-chat/ai-chat.json").bufferedReader().use { it.readText() })
        }
        setContent {
            if (fixture.isFailure) {
                Text("Unable to load Flutter AI Chat example: ${fixture.exceptionOrNull()?.message}",
                    modifier = Modifier.safeDrawingPadding().testTag("ai-load-error"))
            } else {
                AIChatScreen(
                    fixture = fixture.getOrThrow(),
                    onBack = ::finish,
                    onLinkClick = { url ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                )
            }
        }
    }
}

@Composable
private fun AIChatScreen(
    fixture: AIChatFixture,
    onBack: () -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val messages = remember(fixture) { mutableStateListOf(AIChatMessage(0L, fixture.welcome, false)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val qwen = remember { QwenChatClient() }
    val plugins = remember {
        ParserPluginRegistry().also {
            it.registerAll(listOf(ThinkingPlugin(), ArtifactPlugin(), ToolCallPlugin()))
        }
    }
    var input by remember { mutableStateOf("") }
    var dark by rememberSaveable { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(1L) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var conversationEpoch by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var sourceMessage by remember { mutableStateOf<AIChatMessage?>(null) }
    // Match Flutter's in-memory settings; never bundle or persist a credential.
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(qwenModels.first().first) }
    var enableThinking by remember { mutableStateOf(true) }
    var useRealAPI by remember { mutableStateOf(true) }

    DisposableEffect(Unit) { onDispose { qwen.cancel(); streamJob?.cancel() } }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || streaming) return
        messages += AIChatMessage(nextId++, text, true)
        input = ""
        val networkKey = apiKey.trim()
        val networkModel = selectedModel
        val networkThinking = enableThinking
        val useNetwork = useRealAPI && networkKey.isNotEmpty()
        val response = if (useNetwork) "" else (fixture.quickPrompts.firstOrNull {
            text.contains(it.prompt) || it.prompt.contains(text)
        }?.response ?: fixture.genericResponseTemplate.replace("{{prompt}}", text))
        val id = nextId++
        val epoch = conversationEpoch
        val session = MarkdownStreamSession()
        messages += AIChatMessage(id, "", false, streamSession = session)
        streaming = true
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                suspend fun append(chunk: String) {
                    if (epoch != conversationEpoch) return
                    val index = messages.indexOfFirst { it.id == id }
                    if (index >= 0) messages[index] = messages[index].copy(content = session.append(chunk))
                }
                if (useNetwork) {
                    qwen.stream(text, networkKey, networkModel, networkThinking) { chunk ->
                        withContext(Dispatchers.Main) { append(chunk) }
                    }
                } else {
                    // Kotlin String offsets, like Dart String offsets, count UTF-16 code units.
                    var offset = 0
                    while (offset < response.length) {
                        val end = (offset + fixture.chunkSizeUTF16).coerceAtMost(response.length)
                        append(response.substring(offset, end))
                        offset = end
                        delay(fixture.delayMillis)
                    }
                }
            } catch (_: CancellationException) {
                // New conversation or Activity disposal cancelled the active stream.
            } catch (error: Exception) {
                if (epoch == conversationEpoch) {
                    session.cancel()
                    val detail = if (error is QwenHttpException) "API Error: ${error.statusCode}"
                        else "Network Error: ${error.javaClass.simpleName}"
                    val index = messages.indexOfFirst { it.id == id }
                    if (index >= 0) messages[index] = messages[index].copy(
                        content = "⚠️ **错误**: $detail\n\n请检查 API Key 配置或网络连接。",
                        streamSession = null,
                    )
                }
            } finally {
                if (epoch == conversationEpoch) {
                    val index = messages.indexOfFirst { it.id == id }
                    if (index >= 0 && messages[index].streamSession === session) {
                        messages[index] = messages[index].copy(
                            content = session.finish(), streamSession = null,
                        )
                    }
                    streaming = false
                    streamJob = null
                }
            }
        }
        streamJob = job
        job.start()
    }

    fun newChat() {
        conversationEpoch++
        messages.forEach { it.streamSession?.cancel() }
        qwen.cancel()
        streamJob?.cancel()
        streamJob = null
        streaming = false
        input = ""
        messages.clear()
        messages += AIChatMessage(nextId++, fixture.welcome, false)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    val chrome = if (dark) Color(0xFF2C2C2E) else Color.White
    val background = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(color = background, contentColor = if (dark) Color.White else Color.Black) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(chrome).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("ai-back")) { Text("‹") }
                    Text("✨", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI Chat Demo", style = MaterialTheme.typography.titleMedium)
                        Text(if (streaming) "正在输入..." else if (useRealAPI && apiKey.isNotBlank()) {
                            selectedModel + if (enableThinking && selectedModel.startsWith("qwen3")) " (思考)" else ""
                        } else "模拟模式",
                            color = if (streaming) aiBlue else if (useRealAPI && apiKey.isNotBlank())
                                Color(0xFF34C759) else Color(0xFFFF9500),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("ai-status"))
                    }
                    TextButton(onClick = { newChat() }, modifier = Modifier.testTag("ai-new-chat")) {
                        Text("新对话")
                    }
                    TextButton(onClick = { dark = !dark }, modifier = Modifier.testTag("ai-theme")) {
                        Text(if (dark) "☀️" else "🌙")
                    }
                    TextButton(onClick = { showSettings = true }, modifier = Modifier.testTag("ai-settings")) {
                        Text("⚙️")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(chrome).horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fixture.quickPrompts.forEach { prompt ->
                        AssistChip(
                            onClick = { sendMessage(prompt.prompt) },
                            label = { Text(prompt.label) },
                            enabled = !streaming,
                            modifier = Modifier.testTag("ai-prompt-${prompt.id}"),
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("ai-messages"),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        AIMessageBubble(message, dark, plugins, onLinkClick, onShowSource = { sourceMessage = message })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(chrome).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !streaming,
                        placeholder = { Text("输入消息...") },
                        modifier = Modifier.weight(1f).testTag("ai-input"),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(input) }),
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { sendMessage(input) },
                        enabled = !streaming && input.isNotBlank(),
                        modifier = Modifier.testTag("ai-send"),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { Text(if (streaming) "■" else "↑") }
                }
            }
        }
        if (showSettings) AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("API 设置") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Qwen API Key") },
                        placeholder = { Text("sk-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai-api-key"),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("选择模型", style = MaterialTheme.typography.titleSmall)
                    qwenModels.forEach { (id, label) ->
                        TextButton(onClick = { selectedModel = id },
                            modifier = Modifier.testTag("ai-model-$id")) {
                            Text(if (selectedModel == id) "✓ $label" else label)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用思考模式")
                            Text(if (selectedModel.startsWith("qwen3")) "显示 AI 的推理过程"
                                else "仅 Qwen3 系列模型支持", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = enableThinking,
                            onCheckedChange = { enableThinking = it },
                            enabled = selectedModel.startsWith("qwen3"),
                            modifier = Modifier.testTag("ai-thinking"))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("使用真实 API")
                            Text("关闭时使用模拟响应", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = useRealAPI, onCheckedChange = { useRealAPI = it },
                            modifier = Modifier.testTag("ai-real-api"))
                    }
                    Text("模拟模式可测试所有 AI 格式解析功能",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("关闭") } },
        )
        sourceMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { sourceMessage = null },
                title = { Text("Markdown Source") },
                text = {
                    SelectionContainer {
                        Text(message.content, modifier = Modifier.heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = { TextButton(onClick = { sourceMessage = null }) { Text("关闭") } },
            )
        }
    }
}

@Composable
private fun AIMessageBubble(
    message: AIChatMessage,
    dark: Boolean,
    plugins: ParserPluginRegistry,
    onLinkClick: (String) -> Unit,
    onShowSource: () -> Unit,
) {
    val bubbleColor = if (message.user) {
        if (dark) Color(0xFF0A84FF) else aiBlue
    } else {
        if (dark) Color(0xFF2C2C2E) else Color.White
    }
    val style = (if (dark) MarkdownStyleSheet.dark() else MarkdownStyleSheet.light()).copy(
        backgroundColor = null,
        contentPadding = 0.dp,
        textColor = if (message.user) Color.White else if (dark) Color(0xFFEEEEEE) else Color(0xFF212121),
        headingColor = if (message.user) Color.White else if (dark) Color.White else Color.Black,
        paragraphStyle = TextStyle(color = if (message.user) Color.White else Color.Unspecified),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = if (message.user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.user) {
            Text("✨", modifier = Modifier.background(aiPurple, RoundedCornerShape(12.dp)).padding(8.dp))
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 460.dp)
                .testTag(if (message.user) "ai-user-${message.id}" else "ai-assistant-${message.id}"),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (message.streamSession != null) {
                    StreamMarkdown(
                        prefixes = message.streamSession.prefixes,
                        styleSheet = style,
                        plugins = plugins,
                        onLinkClick = onLinkClick,
                        scrollable = false,
                    )
                } else if (message.content.isNotEmpty()) {
                    SmoothMarkdown(markdown = message.content, styleSheet = style,
                        scrollable = false, plugins = plugins, onLinkClick = onLinkClick)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(aiTimestamp(message.timestamp), color = if (message.user) Color.White.copy(alpha = 0.7f)
                        else if (dark) Color.LightGray else Color.DarkGray,
                        style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onShowSource, modifier = Modifier.testTag("ai-source-${message.id}")) {
                        Text("源码", style = MaterialTheme.typography.labelSmall,
                            color = if (message.user) Color.White else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (message.user) {
            Spacer(Modifier.width(8.dp))
            Text("👤", modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun aiTimestamp(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp) / 1_000).coerceAtLeast(0)
    return when {
        seconds < 30 -> "刚刚"
        seconds < 60 -> "${seconds}秒前"
        seconds < 3_600 -> "${seconds / 60}分钟前"
        seconds < 86_400 -> "${seconds / 3_600}小时前"
        else -> "${seconds / 86_400}天前"
    }
}
