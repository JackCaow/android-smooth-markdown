package com.jackcaow.smoothmarkdown.demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.MermaidPlugin
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val conversationBlue = Color(0xFF007AFF)

internal data class ConversationMessage(val content: String, val isMe: Boolean, val secondsAgo: Long)
internal data class ConversationSample(
    val id: String,
    val name: String,
    val avatar: String,
    val avatarColor: Color,
    val unreadCount: Int,
    val lastMessage: String,
    val messages: List<ConversationMessage>,
)

/** This fixture is extracted from Flutter example/lib/conversation_list_demo.dart. */
internal fun loadConversationSamples(context: Context): List<ConversationSample> {
    val fixture = JSONObject(context.assets.open("examples/conversations/conversations.json")
        .bufferedReader().use { it.readText() })
    val entries = fixture.getJSONArray("conversations")
    val conversations = (0 until entries.length()).map { index ->
        val item = entries.getJSONObject(index)
        val messages = item.getJSONArray("messages")
        ConversationSample(
            id = item.getString("id"),
            name = item.getString("name"),
            avatar = item.getString("avatar"),
            avatarColor = Color(item.getString("avatarColorARGB").toLong(16)),
            unreadCount = item.getInt("unreadCount"),
            lastMessage = item.getString("lastMessage"),
            messages = (0 until messages.length()).map { messageIndex ->
                val message = messages.getJSONObject(messageIndex)
                ConversationMessage(message.getString("content"), message.getBoolean("isMe"),
                    message.getLong("secondsAgo"))
            },
        )
    }
    require(conversations.size == 12 && conversations.sumOf { it.messages.size } == 29) {
        "Incomplete Flutter conversation fixture"
    }
    require(conversations.map { it.id }.distinct().size == conversations.size)
    return conversations
}

class ConversationListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val samples = runCatching { loadConversationSamples(this) }
        setContent {
            if (samples.isFailure) {
                Text("Unable to load Flutter conversations: ${samples.exceptionOrNull()?.message}",
                    Modifier.safeDrawingPadding().testTag("conversation-load-error"))
            } else ConversationListScreen(
                samples = samples.getOrThrow(),
                onBack = ::finish,
                onCopy = { value ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Markdown message", value))
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                },
                onLinkClick = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }
    }
}

@Composable
private fun ConversationListScreen(
    samples: List<ConversationSample>,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = samples.firstOrNull { it.id == selectedId }
    var menuMessage by remember { mutableStateOf<ConversationMessage?>(null) }
    val openedAt = remember { System.currentTimeMillis() }
    BackHandler(selected != null) { selectedId = null }
    val background = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val chrome = if (dark) Color(0xFF2C2C2E) else Color.White
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(chrome).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (selected == null) onBack() else selectedId = null },
                        modifier = Modifier.testTag("conversation-back")) { Text("‹") }
                    selected?.let { Avatar(it.avatar, it.avatarColor, 32) }
                    if (selected != null) Spacer(Modifier.width(10.dp))
                    Text(selected?.name ?: "会话列表", modifier = Modifier.weight(1f),
                        fontSize = if (selected == null) 18.sp else 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (dark) Color.White else Color(0xFF222222))
                    if (selected == null) {
                        TextButton(onClick = { dark = !dark }, modifier = Modifier.testTag("conversation-theme")) {
                            Text(if (dark) "☀️" else "🌙")
                        }
                    } else {
                        TextButton(onClick = {
                            selected?.messages?.joinToString("\n\n---\n\n") { it.content }?.let(onCopy)
                        }, modifier = Modifier.testTag("conversation-copy-all")) { Text("复制全部文本") }
                    }
                }
                if (selected == null) {
                    LazyColumn(Modifier.fillMaxSize().testTag("conversation-list")) {
                        items(samples, key = { it.id }) { conversation ->
                            ConversationRow(conversation, dark, openedAt) { selectedId = conversation.id }
                        }
                    }
                } else {
                    val conversation = selected!!
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("conversation-detail"),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(conversation.messages.size) { index ->
                            ConversationBubble(conversation, conversation.messages[index], dark, openedAt,
                                onLinkClick) { menuMessage = conversation.messages[index] }
                        }
                    }
                }
            }
        }
        menuMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { menuMessage = null },
                title = { Text("消息操作") },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCopy(message.content); menuMessage = null },
                            modifier = Modifier.testTag("conversation-copy-message")) { Text("复制") }
                        Text("长按气泡正文可选择已渲染文字")
                    }
                },
                confirmButton = { TextButton(onClick = { menuMessage = null }) { Text("关闭") } },
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSample, dark: Boolean, openedAt: Long,
                            onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
            .testTag("conversation-row-${conversation.id}")
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Avatar(conversation.avatar, conversation.avatarColor, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.name, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (dark) Color.White else Color(0xFF222222))
                    Spacer(Modifier.width(8.dp))
                    Text(formatRelativeTime(conversation.messages.last().secondsAgo, openedAt),
                        fontSize = 12.sp, color = if (conversation.unreadCount > 0) conversationBlue else Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(previewText(conversation.lastMessage), modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = Color.Gray)
                    if (conversation.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = conversationBlue, shape = RoundedCornerShape(12.dp)) {
                            Text(if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = if (dark) Color(0xFF3A3A3C).copy(alpha = .5f) else Color(0xFFE5E5EA))
    }
}

@Composable
private fun ConversationBubble(conversation: ConversationSample, message: ConversationMessage,
                               dark: Boolean, openedAt: Long, onLinkClick: (String) -> Unit,
                               onMenuClick: () -> Unit) {
    val own = message.isMe
    val bubble = if (own) if (dark) Color(0xFF0A84FF) else conversationBlue
        else if (dark) Color(0xFF2C2C2E) else Color.White
    val textColor = if (own) Color.White else if (dark) Color(0xFFEEEEEE) else Color(0xFF222222)
    val style = (if (dark) MarkdownStyleSheet.dark() else MarkdownStyleSheet.light()).copy(
        backgroundColor = null,
        contentPadding = 0.dp,
        textColor = textColor,
        headingColor = textColor,
        paragraphStyle = TextStyle(color = textColor),
    )
    val plugins = remember { ParserPluginRegistry().also { it.register(MermaidPlugin()) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top) {
        if (!own) { Avatar(conversation.avatar, conversation.avatarColor, 32); Spacer(Modifier.width(8.dp)) }
        Surface(color = bubble, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(.70f).widthIn(max = 460.dp)
                .testTag("conversation-bubble-${conversation.id}-${conversation.messages.indexOf(message)}")) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SmoothMarkdown(markdown = message.content, scrollable = false, styleSheet = style,
                    enableHtml = true, plugins = plugins, onLinkClick = onLinkClick)
                Text(formatClockTime(message.secondsAgo, openedAt), fontSize = 11.sp,
                    color = if (own) Color.White.copy(alpha = .6f) else Color.Gray,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        TextButton(onClick = onMenuClick,
            modifier = Modifier.testTag("conversation-message-menu-${conversation.id}-${conversation.messages.indexOf(message)}")) {
            Text("⋯")
        }
        if (own) { Spacer(Modifier.width(8.dp)); Avatar(conversation.avatar, conversation.avatarColor, 32) }
    }
}

@Composable
private fun Avatar(label: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = (size * .42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatRelativeTime(secondsAgo: Long, openedAt: Long): String {
    val seconds = secondsAgo + (System.currentTimeMillis() - openedAt) / 1000
    val minutes = seconds / 60
    val hours = seconds / 3600
    val days = seconds / 86400
    return when {
        minutes < 1 -> "刚刚"
        hours < 1 -> "${minutes}分钟前"
        days < 1 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        else -> {
            val date = Instant.ofEpochMilli(openedAt - secondsAgo * 1000)
                .atZone(ZoneId.systemDefault())
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }
}

private fun formatClockTime(secondsAgo: Long, openedAt: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").format(
        Instant.ofEpochMilli(openedAt - secondsAgo * 1000).atZone(ZoneId.systemDefault()))

private fun previewText(markdown: String): String = markdown
    .replace(Regex("```[\\s\\S]*?```"), "[代码]")
    .replace(Regex("`[^`]+`"), "")
    .replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), "[公式]")
    .replace(Regex("\\$[^$]+\\$"), "")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("!\\[.*?]\\(.*?\\)"), "[图片]")
    .replace(Regex("\\[([^]]*)]\\(.*?\\)"), "$1")
    .replace(Regex("[#*>|\\-]"), "")
    .replace(Regex("\\n+"), " ")
    .trim()
