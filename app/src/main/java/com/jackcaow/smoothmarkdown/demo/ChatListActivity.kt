package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.SmoothMarkdownCache
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val REPLY_DELAY_MS = 500L
private val replyNames = listOf("code", "features", "performance", "table")
private val chatBlue = Color(0xFF007AFF)

private data class ChatMessage(
    val id: Long,
    val content: String,
    val user: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val streamSession: MarkdownStreamSession? = null,
)

class ChatListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val markdown = (listOf("welcome") + replyNames).associateWith { name ->
            assets.open("examples/chat-list/$name.md").bufferedReader().use { it.readText() }
        }
        // A fixed response makes device checks repeatable; normal navigation stays random like Flutter.
        val fixedReply = intent.getIntExtra("responseIndex", -1).takeIf { it in replyNames.indices }
        setContent {
            ChatListScreen(
                markdown = markdown,
                fixedReply = fixedReply,
                onBack = ::finish,
                onLinkClick = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }
    }
}

@Composable
private fun ChatListScreen(
    markdown: Map<String, String>,
    fixedReply: Int?,
    onBack: () -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val messages = remember { mutableStateListOf(ChatMessage(0L, markdown.getValue("welcome"), false)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(1L) }
    val background = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val chrome = if (dark) Color(0xFF2C2C2E) else Color.White

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || streaming) return
        messages += ChatMessage(nextId++, text, true)
        input = ""
        scope.launch {
            delay(REPLY_DELAY_MS)
            if (streaming) return@launch // Flutter ignores a response request during an active stream.
            streaming = true
            val id = nextId++
            val session = MarkdownStreamSession()
            messages += ChatMessage(id, "", false, streamSession = session)
            val response = markdown.getValue(replyNames[fixedReply ?: Random.nextInt(replyNames.size)])
            try {
                var offset = 0
                while (offset < response.length) {
                    val end = (offset + 3 + Random.nextInt(3)).coerceAtMost(response.length)
                    val index = messages.indexOfFirst { it.id == id }
                    if (index < 0) break
                    messages[index] = messages[index].copy(content = session.append(response.substring(offset, end)))
                    offset = end
                    delay((20 + Random.nextInt(30)).toLong())
                }
            } finally {
                val index = messages.indexOfFirst { it.id == id }
                if (index >= 0 && messages[index].streamSession === session) {
                    messages[index] = messages[index].copy(content = session.finish(), streamSession = null)
                }
                streaming = false
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(color = background, contentColor = if (dark) Color.White else Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().background(chrome).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("chat-back")) { Text("‹ Back") }
                Text("🤖", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI Assistant", style = MaterialTheme.typography.titleMedium)
                    Text(if (streaming) "Typing..." else "Online",
                        color = if (streaming) chatBlue else Color(0xFF2EAD55),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("chat-status"))
                }
                IconButton(onClick = { dark = !dark }, modifier = Modifier.testTag("chat-theme")) {
                    Text(if (dark) "☀️" else "🌙")
                }
                IconButton(onClick = { showStats = true }, modifier = Modifier.testTag("chat-cache")) {
                    Text("📊")
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message, dark, onLinkClick)
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
                    placeholder = { Text("Message AI Assistant...") },
                    modifier = Modifier.weight(1f).testTag("chat-input"),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    maxLines = 4,
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { sendMessage() },
                    enabled = !streaming && input.isNotBlank(),
                    modifier = Modifier.testTag("chat-send"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) { Text("↑") }
            }
        }
        }
        if (showStats) {
            val stats = SmoothMarkdownCache.statistics
            AlertDialog(
                onDismissRequest = { showStats = false },
                title = { Text("Cache Statistics") },
                text = { Column {
                    Text("Cached Entries: ${stats.size}")
                    Text("Max Capacity: ${stats.maxSize}")
                    Text("Utilization: ${"%.1f".format(stats.utilization * 100)}%")
                } },
                confirmButton = {
                    TextButton(onClick = { showStats = false }) { Text("Close") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        SmoothMarkdownCache.clear()
                        showStats = false
                    }) { Text("Clear Cache") }
                },
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, dark: Boolean, onLinkClick: (String) -> Unit) {
    val bubbleColor = if (message.user) {
        if (dark) Color(0xFF0A84FF) else chatBlue
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
            Avatar("🤖", chatBlue)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 460.dp)
                .testTag(if (message.user) "chat-user-${message.id}" else "chat-assistant-${message.id}"),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (message.streamSession != null) {
                    StreamMarkdown(prefixes = message.streamSession.prefixes,
                        styleSheet = style, onLinkClick = onLinkClick, scrollable = false)
                } else if (message.content.isNotEmpty()) {
                    SmoothMarkdown(markdown = message.content, styleSheet = style,
                        scrollable = false, onLinkClick = onLinkClick)
                }
                Text(
                    if (System.currentTimeMillis() - message.timestamp < 30_000) "Just now" else "Earlier",
                    color = if (message.user) Color.White.copy(alpha = 0.7f)
                        else if (dark) Color.LightGray else Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (message.user) {
            Spacer(Modifier.width(8.dp))
            Avatar("👤", Color.Gray)
        }
    }
}

@Composable
private fun Avatar(label: String, color: Color) {
    Box(
        Modifier.width(32.dp).background(color, CircleShape).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
}
