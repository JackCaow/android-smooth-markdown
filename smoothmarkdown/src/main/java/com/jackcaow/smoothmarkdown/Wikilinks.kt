package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

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
