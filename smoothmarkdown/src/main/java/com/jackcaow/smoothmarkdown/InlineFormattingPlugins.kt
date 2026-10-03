package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownTextDecoder

/** Opt-in highlight syntax; code and malformed delimiter runs remain ordinary Markdown. */
class HighlightPlugin : InlineParserPlugin {
    override val id = "highlight"
    override val name = "Highlight Plugin"
    override val priority = 10
    override val triggerCharacter = '='
    override fun canParse(text: String, index: Int) = text.startsWith("==", index)
    override fun parse(text: String, startIndex: Int) = formattingMatch(text, startIndex, "==", true) { HighlightNode(it) }
    override fun render(node: PluginInlineNode) = (node as? HighlightNode)?.let {
        InlinePluginPresentation(it.text, SpanStyle(background = MarkdownStyleSheet.default().highlightColor))
    }
}

class SuperscriptPlugin : InlineParserPlugin {
    override val id = "superscript"
    override val name = "Superscript Plugin"
    override val priority = 10
    override val triggerCharacter = '^'
    override fun canParse(text: String, index: Int) = text.getOrNull(index) == '^'
    override fun parse(text: String, startIndex: Int) = formattingMatch(text, startIndex, "^", false) { SuperscriptNode(it) }
    override fun render(node: PluginInlineNode) = (node as? SuperscriptNode)?.let {
        InlinePluginPresentation(it.text, SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em))
    }
}

/** Numeric word-internal subscripts avoid consuming ordinary GFM tilde text. */
class SubscriptPlugin : InlineParserPlugin {
    override val id = "subscript"
    override val name = "Subscript Plugin"
    override val priority = 10
    override val triggerCharacter = '~'
    override fun canParse(text: String, index: Int) = text.getOrNull(index) == '~'
    override fun parse(text: String, startIndex: Int): InlineParseResult? {
        if (text.getOrNull(startIndex - 1)?.isLetterOrDigit() != true) return null
        val match = formattingMatch(text, startIndex, "~", false) { SubscriptNode(it) } ?: return null
        if (!(match.node as SubscriptNode).text.all { it.isDigit() } || text.getOrNull(startIndex + match.consumed)?.isLetterOrDigit() != true) return null
        return match
    }
    override fun render(node: PluginInlineNode) = (node as? SubscriptNode)?.let {
        InlinePluginPresentation(it.text, SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em))
    }
}

class HighlightNode(val text: String) : PluginInlineNode()
class SuperscriptNode(val text: String) : PluginInlineNode()
class SubscriptNode(val text: String) : PluginInlineNode()

private fun formattingMatch(text: String, index: Int, marker: String, allowSpaces: Boolean,
                            node: (String) -> PluginInlineNode): InlineParseResult? {
    if (index !in text.indices || !text.startsWith(marker, index) || text.getOrNull(index - 1) == marker[0]) return null
    val start = index + marker.length
    if (start >= text.length || text[start] == marker[0] || text[start].isWhitespace()) return null
    var cursor = start
    var escaped = false
    while (cursor < text.length) {
        val char = text[cursor]
        if (char == '\n' || char == '\r' || char == '`') return null
        if (!escaped && text.startsWith(marker, cursor)) {
            val end = cursor + marker.length
            if (cursor == start || text[cursor - 1].isWhitespace() || text.getOrNull(end) == marker[0]) return null
            val payload = text.substring(start, cursor)
            if (!allowSpaces && payload.any { it.isWhitespace() }) return null
            return InlineParseResult(node(NativeMarkdownTextDecoder.decode(payload)), end - index)
        }
        escaped = !escaped && char == '\\'
        cursor++
    }
    return null
}
