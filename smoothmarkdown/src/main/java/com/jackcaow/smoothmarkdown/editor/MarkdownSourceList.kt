package com.jackcaow.smoothmarkdown.editor

/** Source offsets for the primary line of each top-level list item. Nested lines stay untouched. */
internal class MarkdownSourceList private constructor(
    private val source: String,
    val items: List<Item>,
) {
    data class Item(
        val marker: String,
        val contentStart: Int,
        val contentEnd: Int,
        val taskStateOffset: Int?,
        val checked: Boolean,
    )

    fun content(index: Int): String? = items.getOrNull(index)?.let { source.substring(it.contentStart, it.contentEnd) }

    fun replaceContent(index: Int, content: String): String? {
        val item = items.getOrNull(index) ?: return null
        if ('\n' in content || '\r' in content) return null
        return source.replaceRange(item.contentStart, item.contentEnd, content)
    }

    fun setChecked(index: Int, checked: Boolean): String? {
        val item = items.getOrNull(index) ?: return null
        val offset = item.taskStateOffset ?: return null
        if (item.checked == checked) return null
        return source.replaceRange(offset, offset + 1, if (checked) "x" else " ")
    }

    companion object {
        private val marker = Regex("^([ \\t]*)([-+*]|[0-9]{1,9}[.)])([ \\t]+)(?:\\[([ xX])\\]([ \\t]+))?")

        fun parse(block: MarkdownDocumentBlock): MarkdownSourceList? {
            if (block.kind != MarkdownBlockKind.BULLET_LIST && block.kind != MarkdownBlockKind.ORDERED_LIST) return null
            val source = block.source
            val items = mutableListOf<Item>()
            var firstIndent: String? = null
            var start = 0
            while (start < source.length) {
                val lineEnd = source.indexOfAny(charArrayOf('\r', '\n'), start).let { if (it < 0) source.length else it }
                val match = marker.find(source.substring(start, lineEnd))
                if (match != null) {
                    val indent = match.groupValues[1]
                    val ordered = match.groupValues[2].first().isDigit()
                    val expectedOrdered = block.kind == MarkdownBlockKind.ORDERED_LIST
                    if (firstIndent == null) firstIndent = indent
                    if (indent == firstIndent && ordered == expectedOrdered) {
                        val state = match.groups[4]
                        items += Item(
                            marker = match.groupValues[2],
                            contentStart = start + match.value.length,
                            contentEnd = lineEnd,
                            taskStateOffset = state?.let { start + it.range.first },
                            checked = state?.value?.equals("x", ignoreCase = true) == true,
                        )
                    }
                }
                start = lineEnd + if (lineEnd < source.length && source[lineEnd] == '\r' && source.getOrNull(lineEnd + 1) == '\n') 2 else 1
            }
            return if (items.isEmpty()) null else MarkdownSourceList(source, items)
        }
    }
}
