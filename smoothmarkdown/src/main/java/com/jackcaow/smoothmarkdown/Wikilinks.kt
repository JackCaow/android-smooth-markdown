package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Text
import com.jackcaow.smoothmarkdown.ast.Link

/** Scratch-style note link. The entire contents of `[[...]]` are the target. */
class WikilinkNode(val target: String) : PluginInlineNode()

/** Opt-in parser for `[[target]]`; a closing bracket inside the target is invalid. */
class WikilinkPlugin : InlineParserPlugin {
    override val id = "wikilink"
    override val name = "Wikilink"
    override val priority = 100
    override val triggerCharacter = '['

    override fun canParse(text: String, index: Int): Boolean = text.startsWith("[[", index)

    override fun parse(text: String, startIndex: Int): InlineParseResult? {
        if (!canParse(text, startIndex)) return null
        val end = text.indexOf("]]", startIndex + 2)
        if (end <= startIndex + 2) return null
        val target = text.substring(startIndex + 2, end)
        if (']' in target) return null
        return InlineParseResult(WikilinkNode(target), end + 2 - startIndex)
    }

    override fun render(node: PluginInlineNode): InlinePluginPresentation? =
        (node as? WikilinkNode)?.let {
            InlinePluginPresentation(it.target, SpanStyle(textDecoration = TextDecoration.Underline))
        }
}

/** CommonMark claims '[' before custom inline parsers, so recognize note links in its text nodes. */
internal object WikilinkPostProcessor {
    fun process(root: Node, plugin: WikilinkPlugin): Node {
        fun visit(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                if (child is Text) replaceText(child, plugin)
                else if (child !is Link) visit(child)
                child = next
            }
        }
        visit(root)
        return root
    }

    private fun replaceText(node: Text, plugin: WikilinkPlugin) {
        val source = node.literal
        var cursor = 0
        var copied = 0
        var replaced = false
        while (true) {
            val start = source.indexOf("[[", cursor)
            if (start < 0) break
            val match = plugin.parse(source, start)
            if (match == null) { cursor = start + 2; continue }
            if (start > copied) node.insertBefore(Text(source.substring(copied, start)))
            node.insertBefore(match.node)
            copied = start + match.consumed
            cursor = copied
            replaced = true
        }
        if (replaced) {
            if (copied < source.length) node.insertBefore(Text(source.substring(copied)))
            node.unlink()
        }
    }
}
