package com.jackcaow.smoothmarkdown.nativeparser

object NativeMarkdownCodeSemantics {
    fun text(source: String, fenced: Boolean): String {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList()
        if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
        var indent = 4
        if (fenced) {
            val opening = lines.firstOrNull() ?: return ""
            indent = opening.takeWhile { it == ' ' }.length
            val fence = opening.drop(indent).takeWhile { it == '`' || it == '~' }
            lines.removeAt(0)
            val last = lines.lastOrNull()
            if (last != null && fence.isNotEmpty()) {
                val closing = last.dropWhile { it == ' ' }
                val run = closing.takeWhile { it == fence[0] }.length
                if (last.length - closing.length <= 3 && run >= fence.length && closing.drop(run).all { it == ' ' || it == '\t' }) lines.removeAt(lines.lastIndex)
            }
        } else while (lines.lastOrNull()?.all { it == ' ' || it == '\t' } == true) lines.removeAt(lines.lastIndex)
        return if (lines.isEmpty()) "" else lines.joinToString("\n") { removingIndent(it, indent) } + "\n"
    }
    private fun removingIndent(line: String, columns: Int): String {
        var i = 0; var width = 0
        while (i < line.length && width < columns && (line[i] == ' ' || line[i] == '\t')) { width += if (line[i] == '\t') 4 - width % 4 else 1; i++ }
        return " ".repeat(maxOf(0, width - columns)) + line.substring(i)
    }
}
object NativeMarkdownHTMLTagFilter {
    private val pattern = Regex("(?i)<(?=/?(?:title|textarea|style|xmp|iframe|noembed|noframes|script|plaintext)(?:[\\t\\n\\r ]|/?>))")
    fun filter(html: String) = pattern.replace(html, "&lt;")
}
