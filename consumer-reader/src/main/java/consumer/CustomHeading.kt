package consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.*
import com.jackcaow.smoothmarkdown.ast.*
import com.jackcaow.smoothmarkdown.ast.Text as MarkdownText

class CustomHeading : MarkdownNodeBuilder {
    override fun canBuild(node: Node) = node is Heading
    override fun documentText(node: Node): String = semanticText(node)
    override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NATIVE_TEXT

    @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
        val clipboard = LocalClipboardManager.current
        Column(Modifier.padding(12.dp).background(context.styleSheet.linkColor.copy(alpha = 0.08f))) {
            // Retains inline marks, links and custom inline builders in the native selection surface.
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) { context.renderInlineChildren(node) }
            TextButton(onClick = { clipboard.setText(AnnotatedString(semanticText(node))) }) {
                Text("Copy heading")
            }
        }
    }
    private fun semanticText(node: Node): String = when (node) {
        is MarkdownText -> node.literal
        is Code -> node.literal
        is HtmlInline -> node.literal
        is SoftLineBreak, is HardLineBreak -> "\n"
        else -> buildString {
            var child = node.firstChild
            while (child != null) { append(semanticText(child)); child = child.next }
        }
    }
}

@Composable fun CustomizedReader() {
    val registry = remember { MarkdownBuilderRegistry().register<Heading>(CustomHeading()) }
    val style = MarkdownStyleSheet.light().let { base -> base.copy(designTokens = base.designTokens.copy(
        mermaid = base.designTokens.mermaid.copy(charts = MarkdownMermaidChartTokens(
            seriesColors = listOf(Color.Magenta, Color.Cyan),
        )),
    )) }
    SmoothMarkdown("# Agent **result**", renderOptions = MarkdownRenderOptions(),
        styleSheet = style, builders = MarkdownBuilders(nodes = registry),
        selectionOptions = MarkdownSelectionOptions(mode = MarkdownSelectionMode.DOCUMENT))
}
