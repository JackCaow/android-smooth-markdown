package com.jackcaow.smoothmarkdown.demo

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest

data class DemoExample(val id: String, val title: String, val markdown: String)

/** The manifest and Markdown files are copied verbatim from Flutter example/lib/main.dart. */
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

data class DemoPage(val id: String, val title: String, val subtitle: String = "")

val dedicatedPages = listOf(
    DemoPage("math", "Math", "Inline and display equations"),
    DemoPage("stream", "Streaming", "Incremental Markdown chunks"),
    DemoPage("footnote", "Footnotes", "References and definitions"),
    DemoPage("html", "HTML Tags", "Whitelisted HTML rendering"),
    DemoPage("chat-list", "Chat List", "Static Markdown preview; list virtualization pending"),
    DemoPage("ai", "AI Chat", "Offline Thinking, Artifact, ToolCall sample"),
    DemoPage("conversation-list", "Conversation List", "Static Markdown preview; gestures pending"),
    DemoPage("plugin", "Plugin System", "Mentions, emoji, admonitions"),
    DemoPage("mermaid", "Mermaid diagrams", "Native diagram gallery"),
    DemoPage("performance", "Long document benchmark"),
)

fun dedicatedMarkdown(id: String): String = when (id) {
    "math" -> """# Math Demo

Inline: ${'$'}E=mc^2${'$'} and ${'$'}a^2+b^2=c^2${'$'}.

Display:

${'$'}${'$'}\frac{a}{b} = \sqrt{x^2 + 1}${'$'}${'$'}
""".trimIndent()
    "stream" -> """# Streaming Markdown

This paragraph arrives in small chunks. **Formatting**, lists, and code remain readable while the stream grows.

- First item
- Second item

```kotlin
StreamMarkdown(chunks = incoming)
```
""".trimIndent()
    "footnote" -> """# Footnotes

A statement with a reference[^first] and another one[^second].

[^first]: Footnotes can contain **formatted** text.
[^second]: The second note follows the first.
""".trimIndent()
    "html" -> """# HTML Tags

<b>Bold</b>, <i>italic</i>, <u>underlined</u>, and <span style="color: red">colored</span> text.

<div align="center">Centered **Markdown** in a div.</div>

<details><summary>Expand HTML details</summary>Hidden **Markdown** content.</details>

<img src="smooth-markdown-mark.svg" alt="Bundled SVG" width="64" height="64">
""".trimIndent()
    "plugin" -> """# Plugin System

@alice and @bob discuss #android and #markdown. A shortcode: :rocket:.

::: tip Native plugin
This **admonition** is parsed by a registered plugin.
:::
""".trimIndent()
    "chat-list" -> """# Chat List Demo

Static Markdown preview. Flutter's chat-list virtualization and live message updates are not ported yet.

### Alice
Hello! This reply contains **Markdown** and `code`.

### Bob
> A quoted response with a [link](https://github.com/JackCaow/flutter-smooth-markdown).
""".trimIndent()
    "conversation-list" -> """# Conversation List

Static Markdown preview. Flutter's long-press menus, swipe actions, and multi-select are not ported yet.

### Project discussion
Latest message: **Build passed**.

### Design review
Latest message: Please check the [prototype](https://github.com/JackCaow/flutter-smooth-markdown).
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
