package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jackcaow.smoothmarkdown.mermaid.MermaidDiagramView
import com.jackcaow.smoothmarkdown.mermaid.MermaidParser
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.Node

/** Opt-in native renderer for supported Mermaid diagrams; unsupported fences stay code blocks. */
class MermaidPlugin(val onNodeTap: ((String) -> Unit)? = null) : BlockParserPlugin {
    override val id = "mermaid"
    override val name = "Mermaid Diagram Plugin"
    override val priority = 10

    override fun selectionMode(node: PluginBlockNode) = MarkdownBlockSelectionMode.NON_TEXT

    override fun parseFencedCodeBlock(block: FencedCodeBlock): PluginBlockNode? {
        val info = block.info.orEmpty().trim()
        if (!info.split(Regex("\\s+"), limit = 2).firstOrNull().equals("mermaid", ignoreCase = true)) return null
        val code = block.literal.trim()
        if (code.isEmpty() || MermaidParser.parse(code) == null) return null
        val theme = Regex("\\btheme\\s*=\\s*(\\w+)", RegexOption.IGNORE_CASE).find(info)?.groupValues?.get(1)
        val fence = if (block.fenceChar == '~') "~~~" else "```"
        return MermaidDiagramNode(code, theme, fence, info)
    }

    @Composable
    override fun RenderBlock(node: PluginBlockNode, renderChild: @Composable (Node) -> Unit) {
        val diagram = node as MermaidDiagramNode
        val tokens = LocalMarkdownStyleSheet.current.designTokens.mermaid
        MermaidDiagramView(diagram.code,
            Modifier.fillMaxWidth().heightIn(max = tokens.maxHeight).padding(tokens.outerPadding), onNodeTap,
            theme = diagram.theme, style = tokens)
    }
}

class MermaidDiagramNode(
    val code: String,
    val theme: String? = null,
    val fence: String = "```",
    val info: String = "mermaid",
) : PluginBlockNode()
