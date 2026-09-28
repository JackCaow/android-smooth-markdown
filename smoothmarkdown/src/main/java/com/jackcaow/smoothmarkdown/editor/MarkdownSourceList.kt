package com.jackcaow.smoothmarkdown.editor

import com.jackcaow.smoothmarkdown.parseMarkdown
import org.commonmark.node.BulletList
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph

/** Source-backed list tree. Every editable line points at its own original source slice. */
internal class MarkdownSourceList private constructor(
    private val source: String,
    val items: List<Item>,
) {
    sealed interface Part {
        val offset: Int
    }

    data class Line(val start: Int, val end: Int, val index: Int) : Part {
        override val offset: Int get() = start
    }

    data class NestedList(val items: List<Item>, override val offset: Int) : Part

    data class Raw(val start: Int, val end: Int) : Part {
        override val offset: Int get() = start
    }

    data class Item(
        val marker: String,
        val contentStart: Int,
        val contentEnd: Int,
        val taskStateOffset: Int?,
        val checked: Boolean,
        val parts: List<Part>,
    ) {
        val lines: List<Line> get() = parts.filterIsInstance<Line>()
    }

    fun item(path: List<Int>): Item? {
        if (path.isEmpty()) return null
        var item = items.getOrNull(path.first()) ?: return null
        for (depth in 1 until path.size) {
            item = item.parts.filterIsInstance<NestedList>().flatMap { it.items }.getOrNull(path[depth]) ?: return null
        }
        return item
    }

    fun lineContent(path: List<Int>, lineIndex: Int): String? = item(path)?.lines?.getOrNull(lineIndex)?.let {
        source.substring(it.start, it.end)
    }

    fun rawContent(raw: Raw): String = source.substring(raw.start, raw.end)

    fun replaceLine(path: List<Int>, lineIndex: Int, content: String): String? {
        if ('\n' in content || '\r' in content) return null
        val line = item(path)?.lines?.getOrNull(lineIndex) ?: return null
        return source.replaceRange(line.start, line.end, content)
    }

    fun content(index: Int): String? = items.getOrNull(index)?.let { source.substring(it.contentStart, it.contentEnd) }

    fun replaceContent(index: Int, content: String): String? {
        val item = items.getOrNull(index) ?: return null
        if ('\n' in content || '\r' in content) return null
        return source.replaceRange(item.contentStart, item.contentEnd, content)
    }

    fun setChecked(index: Int, checked: Boolean): String? = setChecked(listOf(index), checked)

    fun setChecked(path: List<Int>, checked: Boolean): String? {
        val item = item(path) ?: return null
        val offset = item.taskStateOffset ?: return null
        if (item.checked == checked) return null
        return source.replaceRange(offset, offset + 1, if (checked) "x" else " ")
    }

    companion object {
        private val marker = Regex("^([ \\t]*)([-+*]|[0-9]{1,9}[.)])([ \\t]+)(?:\\[([ xX])\\]([ \\t]+))?")

        fun parse(block: MarkdownDocumentBlock): MarkdownSourceList? {
            if (block.kind != MarkdownBlockKind.BULLET_LIST && block.kind != MarkdownBlockKind.ORDERED_LIST) return null
            val source = block.source
            val root = parseMarkdown(source).firstChild
            if (root !is BulletList && root !is OrderedList) return null

            fun parseItems(list: Node): List<Item> {
                val items = mutableListOf<Item>()
                var child = list.firstChild
                while (child != null) {
                    if (child is ListItem) {
                        val markerOffset = child.sourceSpans.firstOrNull()?.inputIndex ?: return emptyList()
                        val lineStart = source.lastIndexOf('\n', markerOffset - 1) + 1
                        val lineEnd = source.indexOfAny(charArrayOf('\r', '\n'), lineStart).let { if (it < 0) source.length else it }
                        val match = marker.find(source.substring(lineStart, lineEnd)) ?: return emptyList()
                        val state = match.groups[4]
                        val contentStart = lineStart + match.value.length
                        val parts = mutableListOf<Part>()
                        var contentNode = child.firstChild
                        var lineIndex = 0
                        while (contentNode != null) {
                            when (contentNode) {
                                is Paragraph -> contentNode.sourceSpans.forEach { span ->
                                    if (span.inputIndex >= contentStart && span.inputIndex + span.length <= source.length) {
                                        parts += Line(span.inputIndex, span.inputIndex + span.length, lineIndex++)
                                    }
                                }
                                is BulletList, is OrderedList -> {
                                    val nested = parseItems(contentNode)
                                    val offset = contentNode.sourceSpans.firstOrNull()?.inputIndex
                                    if (offset != null && nested.isNotEmpty()) parts += NestedList(nested, offset)
                                }
                                else -> {
                                    val spans = contentNode.sourceSpans
                                    if (spans.isNotEmpty()) {
                                        val start = spans.first().inputIndex
                                        val end = spans.last().let { it.inputIndex + it.length }
                                        if (start >= 0 && end <= source.length && end > start) parts += Raw(start, end)
                                    }
                                }
                            }
                            contentNode = contentNode.next
                        }
                        if (parts.none { it is Line && it.start == contentStart }) {
                            parts += Line(contentStart, lineEnd, lineIndex)
                        }
                        items += Item(
                            marker = match.groupValues[2],
                            contentStart = contentStart,
                            contentEnd = lineEnd,
                            taskStateOffset = state?.let { lineStart + it.range.first },
                            checked = state?.value?.equals("x", ignoreCase = true) == true,
                            parts = parts.sortedBy(Part::offset),
                        )
                    }
                    child = child.next
                }
                return items
            }

            return parseItems(root).takeIf { it.isNotEmpty() }?.let { MarkdownSourceList(source, it) }
        }
    }
}
