package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge

/** HTML export directly from the library-owned AST; no parser or renderer dependency is required. */
class NativeMarkdownHTMLSerializer(private val escapeHtml: Boolean = false) {
    fun render(root: Markup): String = RustMarkdownBridge.renderHtml(root, escapeHtml)
        ?: buildString { write(root, this) }

    private fun children(node: Markup, output: StringBuilder) {
        var child = node.firstChild
        while (child != null) {
            write(child, output)
            child = child.next
        }
    }

    private fun write(node: Markup, output: StringBuilder) {
        when (node) {
            is Document -> children(node, output)
            is LinkReferenceDefinition -> Unit
            is Paragraph -> {
                val item = node.parent as? ListItem
                val tight = (item?.parent as? ListBlock)?.isTight == true
                if (!tight) output.append("<p>")
                children(node, output)
                if (!tight) output.append("</p>\n")
            }
            is Text -> output.append(escape(node.literal))
            is Heading -> {
                output.append("<h").append(node.level).append('>')
                children(node, output)
                output.append("</h").append(node.level).append(">\n")
            }
            is Emphasis -> tagged("em", node, output)
            is StrongEmphasis -> tagged("strong", node, output)
            is Strikethrough -> tagged("del", node, output)
            is Code -> output.append("<code>").append(escape(node.literal)).append("</code>")
            is SoftLineBreak -> output.append('\n')
            is HardLineBreak -> output.append("<br />\n")
            is ThematicBreak -> output.append("<hr />\n")
            is FencedCodeBlock -> {
                val language = node.info.orEmpty().trim().takeWhile { !it.isWhitespace() }
                output.append("<pre><code")
                if (language.isNotEmpty()) output.append(" class=\"language-").append(escape(language)).append('"')
                output.append('>').append(escape(node.literal)).append("</code></pre>\n")
            }
            is IndentedCodeBlock -> output.append("<pre><code>").append(escape(node.literal)).append("</code></pre>\n")
            is HtmlInline -> output.append(if (escapeHtml) escape(node.literal) else node.literal)
            is HtmlBlock -> {
                output.append(if (escapeHtml) escape(node.literal) else node.literal)
                if (!node.literal.endsWith('\n')) output.append('\n')
            }
            is BlockQuote -> {
                output.append("<blockquote>\n")
                children(node, output)
                output.append("</blockquote>\n")
            }
            is ListBlock -> {
                val tag = if (node is OrderedList) "ol" else "ul"
                output.append('<').append(tag)
                if (node is OrderedList && node.startNumber != 1) {
                    output.append(" start=\"").append(node.startNumber).append('"')
                }
                output.append(">\n")
                children(node, output)
                output.append("</").append(tag).append(">\n")
            }
            is ListItem -> {
                val tight = (node.parent as? ListBlock)?.isTight == true
                val marker = node.children().filterIsInstance<TaskListItemMarker>().firstOrNull()
                val blocks = node.children().filter { it !is TaskListItemMarker }.toList()
                output.append("<li>")
                if (blocks.isNotEmpty() && !(tight && blocks.first() is Paragraph)) output.append('\n')
                var markerWritten = false
                blocks.forEachIndexed { index, child ->
                    if (marker != null && !markerWritten) {
                        if (!tight && child is Paragraph) {
                            output.append("<p>")
                            write(marker, output)
                            children(child, output)
                            output.append("</p>\n")
                            markerWritten = true
                            return@forEachIndexed
                        }
                        write(marker, output)
                        markerWritten = true
                    }
                    write(child, output)
                    if (tight && child is Paragraph && index < blocks.lastIndex) output.append('\n')
                }
                if (marker != null && !markerWritten) write(marker, output)
                output.append("</li>\n")
            }
            is Link -> {
                output.append("<a href=\"").append(destination(node.destination)).append('"')
                title(node.title, output)
                output.append('>')
                children(node, output)
                output.append("</a>")
            }
            is Image -> {
                output.append("<img src=\"").append(destination(node.destination)).append("\" alt=\"")
                    .append(escape(plainText(node))).append('"')
                title(node.title, output)
                output.append(" />")
            }
            is TableBlock -> {
                output.append("<table>\n")
                children(node, output)
                output.append("</table>\n")
            }
            is TableHead -> blockTagged("thead", node, output)
            is TableBody -> if (node.firstChild != null) blockTagged("tbody", node, output)
            is TableRow -> blockTagged("tr", node, output)
            is TableCell -> {
                val tag = if (node.isHeader || node.parent?.parent is TableHead) "th" else "td"
                output.append('<').append(tag)
                node.alignment?.let { output.append(" align=\"").append(it.name.lowercase()).append('"') }
                output.append('>')
                children(node, output)
                output.append("</").append(tag).append(">\n")
            }
            is TaskListItemMarker -> {
                output.append("<input ")
                if (node.isChecked) output.append("checked=\"\" ")
                output.append("disabled=\"\" type=\"checkbox\"> ")
            }
            else -> children(node, output)
        }
    }

    private fun tagged(tag: String, node: Markup, output: StringBuilder) {
        output.append('<').append(tag).append('>')
        children(node, output)
        output.append("</").append(tag).append('>')
    }

    private fun blockTagged(tag: String, node: Markup, output: StringBuilder) {
        output.append('<').append(tag).append(">\n")
        children(node, output)
        output.append("</").append(tag).append(">\n")
    }

    private fun title(title: String?, output: StringBuilder) {
        if (title != null) output.append(" title=\"").append(escape(title)).append('"')
    }

    companion object {
        fun escape(text: String): String = buildString(text.length) {
            text.forEach {
                append(when (it) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    else -> it.toString()
                })
            }
        }

        /** UTF-8 URI escaping while preserving valid percent escapes and URI punctuation. */
        fun destination(url: String): String {
            val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#@!$&'()*+,;=%"
            val hex = "0123456789ABCDEF"
            return escape(buildString {
                url.toByteArray(Charsets.UTF_8).forEach { byte ->
                    val value = byte.toInt() and 255
                    if (value < 128 && allowed.indexOf(value.toChar()) >= 0) append(value.toChar())
                    else append('%').append(hex[value shr 4]).append(hex[value and 15])
                }
            })
        }

        fun plainText(node: Markup): String = when (node) {
            is Text -> node.literal
            is Code -> node.literal
            is SoftLineBreak, is HardLineBreak -> "\n"
            is HtmlInline -> node.literal
            else -> node.children().joinToString("") { plainText(it) }
        }
    }
}
