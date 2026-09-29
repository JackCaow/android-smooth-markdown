package com.jackcaow.smoothmarkdown.demo

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest

data class DemoExample(val id: String, val title: String, val markdown: String)

/** The manifest and Markdown files match Flutter example/lib/main.dart's runtime strings. */
fun loadExamples(assets: AssetManager): List<DemoExample> {
    val manifest = JSONObject(assets.open("examples/manifest.json").bufferedReader().use { it.readText() })
    val entries = manifest.getJSONArray("examples")
    val examples = (0 until entries.length()).map { index ->
        val entry = entries.getJSONObject(index)
        val filename = entry.getString("file")
        val bytes = assets.open("examples/$filename").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(hash == entry.getString("sha256")) { "Example checksum mismatch: $filename" }
        DemoExample(
            id = entry.getString("id"),
            title = entry.getString("title"),
            markdown = bytes.toString(Charsets.UTF_8),
        )
    }
    require(examples.isNotEmpty()) { "No Markdown examples in manifest" }
    return examples
}

/** Exact runtime strings from Flutter's static feature demos. */
fun loadDemoPageMarkdown(assets: AssetManager): Map<String, String> {
    val manifest = JSONObject(assets.open("examples/pages/pages.json").bufferedReader().use { it.readText() })
    val entries = manifest.getJSONArray("pages")
    require(entries.length() == 5) { "Expected five Flutter demo page fixtures" }
    val pages = (0 until entries.length()).associate { index ->
        val entry = entries.getJSONObject(index)
        val filename = entry.getString("file")
        val bytes = assets.open("examples/pages/$filename").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(hash == entry.getString("sha256")) { "Demo page checksum mismatch: $filename" }
        entry.getString("id") to bytes.toString(Charsets.UTF_8)
    }
    require(pages.keys == setOf("math", "footnote", "html", "plugin", "editor")) {
        "Incomplete Flutter demo page catalog"
    }
    return pages
}

data class DemoPage(val id: String, val title: String, val subtitle: String = "")

val dedicatedPages = listOf(
    DemoPage("math", "Math", "Inline and display equations"),
    DemoPage("stream", "Streaming", "Incremental Markdown chunks"),
    DemoPage("footnote", "Footnotes", "References and definitions"),
    DemoPage("html", "HTML Tags Demo", "Whitelisted HTML rendering"),
    DemoPage("chat-list", "Chat List Demo", "Performance optimizations"),
    DemoPage("ai", "AI Chat Demo", "DeepSeek API + Thinking/Artifact/Tool"),
    DemoPage("conversation-list", "Conversation List", "长按菜单 · 滑动操作 · 多选"),
    DemoPage("plugin", "Plugin System", "@mention #hashtag :emoji:"),
    DemoPage("mermaid", "Mermaid 图表", "流程图、时序图"),
    DemoPage("performance", "Long document benchmark"),
)

fun dedicatedMarkdown(id: String): String = when (id) {
    "stream" -> """# Streaming Markdown

This paragraph arrives in small chunks. **Formatting**, lists, and code remain readable while the stream grows.

- First item
- Second item

```kotlin
StreamMarkdown(chunks = incoming)
```
""".trimIndent()
    "ai" -> """# AI Chat Blocks

This is an offline renderer sample; it does not call a model.

<thinking>
Review the question and choose a concise answer.
</thinking>

<artifact id="demo-code" type="code" language="kotlin" title="Generated snippet">
println("Hello from Android")
</artifact>

<tool_use>
<tool_name>search</tool_name>
<tool_id>demo-1</tool_id>
<input>
query: native markdown
</input>
</tool_use>
""".trimIndent()
    else -> ""
}
