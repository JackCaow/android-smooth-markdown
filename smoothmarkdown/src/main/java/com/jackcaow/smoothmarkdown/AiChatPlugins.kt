package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.ast.Node

class ThinkingNode(val isCollapsed: Boolean = true) : PluginBlockNode() {
    var content: String = ""
        internal set
    internal var markdownStyle: Boolean = false
}

/** Opt-in parser for `<thinking>`, `<think>`, and `<|thinking|>` blocks. */
class ThinkingPlugin : BlockParserPlugin {
    override val id = "thinking"
    override val name = "Thinking Plugin"
    override val priority = 20
    private val xmlStart = Regex("^<(thinking|think)>$", RegexOption.IGNORE_CASE)
    private val xmlEnd = Regex("^</(thinking|think)>$", RegexOption.IGNORE_CASE)
    private val mdStart = Regex("^<\\|(thinking|think)\\|>$", RegexOption.IGNORE_CASE)
    private val mdEnd = Regex("^<\\|/(thinking|think)\\|>$", RegexOption.IGNORE_CASE)
    override fun canStart(line: String): Boolean = xmlStart.matches(line.trim()) || mdStart.matches(line.trim())
    override fun createNode(openingLine: String): PluginBlockNode? =
        if (canStart(openingLine)) ThinkingNode().also { it.markdownStyle = mdStart.matches(openingLine.trim()) } else null
    override fun isClosingLine(line: String): Boolean = xmlEnd.matches(line.trim()) || mdEnd.matches(line.trim())
    override fun isClosingLine(node: PluginBlockNode, line: String): Boolean =
        if ((node as ThinkingNode).markdownStyle) mdEnd.matches(line.trim()) else xmlEnd.matches(line.trim())
    override fun complete(node: PluginBlockNode, contentLines: List<String>) {
        (node as ThinkingNode).content = contentLines.joinToString("\n").trim()
    }
    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        val thinking = node as ThinkingNode
        var collapsed by rememberSaveable(thinking) { mutableStateOf(thinking.isCollapsed) }
        val shape = RoundedCornerShape(6.dp)
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            Row(Modifier.fillMaxWidth()
                .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (collapsed) "Expand thinking" else "Collapse thinking",
                ) { collapsed = !collapsed }.padding(12.dp)) {
                Text(if (collapsed) "›" else "⌄", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Thinking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (!collapsed) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectableMarkdownContent {
                    Text(thinking.content, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

enum class ArtifactType { CODE, DOCUMENT, HTML, SVG, COMPONENT, MERMAID, CUSTOM }

class ArtifactNode(
    val identifier: String,
    val artifactType: ArtifactType,
    val title: String? = null,
    val language: String? = null,
    val customType: String? = null,
) : PluginBlockNode() {
    var content: String = ""
        internal set
}

/** Opt-in parser for Claude-style `<artifact ...>` blocks. Content is shown as text. */
class ArtifactPlugin : BlockParserPlugin {
    override val id = "artifact"
    override val name = "Artifact Plugin"
    override val priority = 20
    private val start = Regex("^<artifact\\s+(.+?)>$", RegexOption.IGNORE_CASE)
    private val end = Regex("^</artifact>$", RegexOption.IGNORE_CASE)
    private val attribute = Regex("(\\w+)=[\"']([^\"']*)[\"']")
    override fun canStart(line: String): Boolean = start.matches(line.trim())
    override fun createNode(openingLine: String): PluginBlockNode? {
        val attrText = start.matchEntire(openingLine.trim())?.groupValues?.get(1) ?: return null
        val attrs = attribute.findAll(attrText).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val id = attrs["identifier"] ?: attrs["id"] ?: "unnamed"
        val typeName = attrs["type"]?.lowercase() ?: "custom"
        val type = when (typeName) {
            "code", "application/vnd.ant.code" -> ArtifactType.CODE
            "document", "text/markdown", "text/plain" -> ArtifactType.DOCUMENT
            "html", "text/html" -> ArtifactType.HTML
            "svg", "image/svg+xml" -> ArtifactType.SVG
            "component", "application/vnd.ant.react" -> ArtifactType.COMPONENT
            "mermaid" -> ArtifactType.MERMAID
            else -> ArtifactType.CUSTOM
        }
        return ArtifactNode(id, type, attrs["title"], attrs["language"] ?: attrs["lang"], typeName.takeIf { type == ArtifactType.CUSTOM })
    }
    override fun isClosingLine(line: String): Boolean = end.matches(line.trim())
    override fun complete(node: PluginBlockNode, contentLines: List<String>) {
        (node as ArtifactNode).content = contentLines.joinToString("\n")
    }
    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        val artifact = node as ArtifactNode
        val shape = RoundedCornerShape(6.dp)
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text(artifact.title?.takeIf(String::isNotBlank) ?: artifact.identifier,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${artifact.artifactType.name.lowercase()} · ${artifact.identifier}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (artifact.content.isNotEmpty()) {
                if (artifact.artifactType == ArtifactType.CODE) {
                    EnhancedCodeBlock(artifact.content, artifact.language)
                } else {
                    SelectableMarkdownContent {
                        Text(artifact.content, modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}

enum class ToolCallStatus { RUNNING, COMPLETED, FAILED, CANCELLED, PENDING }

class ToolCallNode(
    var toolName: String = "unknown",
    var toolId: String? = null,
    var parameters: String? = null,
    var result: String? = null,
    var status: ToolCallStatus = ToolCallStatus.PENDING,
    var errorMessage: String? = null,
) : PluginBlockNode()

/** Opt-in parser for `<tool_use>` metadata and input blocks. No tool is executed. */
class ToolCallPlugin : BlockParserPlugin {
    override val id = "tool_call"
    override val name = "Tool Call Plugin"
    override val priority = 25
    private val start = Regex("^<tool_use>$", RegexOption.IGNORE_CASE)
    private val end = Regex("^</tool_use>$", RegexOption.IGNORE_CASE)
    private val toolNamePattern = Regex("<tool_name>([^<]+)</tool_name>", RegexOption.IGNORE_CASE)
    private val toolId = Regex("<tool_id>([^<]+)</tool_id>", RegexOption.IGNORE_CASE)
    override fun canStart(line: String): Boolean = start.matches(line.trim())
    override fun createNode(openingLine: String): PluginBlockNode? = if (canStart(openingLine)) ToolCallNode() else null
    override fun isClosingLine(line: String): Boolean = end.matches(line.trim())
    override fun complete(node: PluginBlockNode, contentLines: List<String>) {
        val tool = node as ToolCallNode
        val content = contentLines.joinToString("\n")
        tool.toolName = toolNamePattern.find(content)?.groupValues?.get(1)?.trim() ?: "unknown"
        tool.toolId = toolId.find(content)?.groupValues?.get(1)?.trim()
        val open = content.indexOf("<input>")
        val close = content.indexOf("</input>")
        if (open >= 0 && close > open) tool.parameters = content.substring(open + "<input>".length, close).trim()
    }
    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        val tool = node as ToolCallNode
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text("Tool: ${tool.toolName}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(tool.status.name.lowercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            tool.toolId?.let { Text("ID: $it", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall) }
            tool.parameters?.let {
                SelectableMarkdownContent {
                    Text(it, modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}
