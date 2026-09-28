package com.jackcaow.smoothmarkdown.editor

import com.jackcaow.smoothmarkdown.parseMarkdown
import org.commonmark.node.BulletList
import org.commonmark.node.Heading
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

    data class StructureEdit(val source: String, val targetPath: List<Int>)
    data class LiftEdit(val source: String, val paragraphOffset: Int)

    /** Lifts a simple root item into a paragraph between the remaining list fragments. */
    fun liftTopLevel(path: List<Int>): LiftEdit? {
        if (path.size != 1) return null
        val selected = item(path) ?: return null
        if (selected.lines.size != 1 || selected.parts.any { it !is Line }) return null
        val body = source.substring(selected.contentStart, selected.contentEnd)
        if (body.isBlank()) return null
        val before = source.substring(0, lineStart(selected))
        val after = source.substring(selected.contentEnd)
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val separator = newline + newline
        val prefix = when {
            before.isEmpty() || before.endsWith(separator) -> before
            before.endsWith(newline) -> before + newline
            else -> before + separator
        }
        val suffix = when {
            after.isEmpty() || after.startsWith(separator) -> after
            after.startsWith(newline) -> newline + after
            else -> separator + after
        }
        val replacement = prefix + body + suffix
        return LiftEdit(replacement, prefix.length)
    }

    /** Splits a visible line into sibling items while retaining every untouched source slice. */
    fun split(path: List<Int>, lineIndex: Int, visibleOffset: Int, enableWikilinks: Boolean): StructureEdit? {
        val selected = item(path) ?: return null
        val line = selected.lines.getOrNull(lineIndex) ?: return null
        val inline = MarkdownInlineEditing.parse(source.substring(line.start, line.end), enableWikilinks)
        if (visibleOffset !in 0..inline.visible.length) return null
        val before = inline.replaceVisible(inline.visible.substring(0, visibleOffset)) ?: return null
        val after = inline.replaceVisible(inline.visible.substring(visibleOffset)) ?: return null
        val markerStart = lineStart(selected)
        val indent = source.substring(markerStart, selected.contentStart).takeWhile { it == ' ' || it == '\t' }
        val number = selected.marker.dropLast(1).toIntOrNull()
        val nextMarker = if (number != null && number < 999_999_999) "${number + 1}${selected.marker.last()}" else selected.marker
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val nextPrefix = indent + nextMarker + source.substring(markerStart + indent.length + selected.marker.length, selected.contentStart)
            .replace(Regex("\\[([xX])\\]"), "[ ]")
        val updated = source.replaceRange(line.start, line.end, before + newline + nextPrefix + after)
        return StructureEdit(updated, path.dropLast(1) + (path.last() + 1))
    }

    /** Moves a complete item subtree under its previous sibling. */
    fun indent(path: List<Int>): StructureEdit? {
        val index = path.lastOrNull() ?: return null
        if (index == 0) return null
        val selected = item(path) ?: return null
        val previous = item(path.dropLast(1) + (index - 1)) ?: return null
        val start = lineStart(selected)
        val currentIndent = selected.contentStart - start - selected.marker.length - markerSuffixLength(selected)
        val previousIndent = previous.contentStart - lineStart(previous)
        val delta = previousIndent - currentIndent
        if (delta <= 0) return null
        val updated = shiftSubtree(path, delta) ?: return null
        val childCount = previous.parts.filterIsInstance<NestedList>().sumOf { it.items.size }
        return StructureEdit(updated, path.dropLast(1) + (index - 1) + childCount)
    }

    /** Moves a nested item one level up, keeping the other children under their parent. */
    fun outdent(path: List<Int>): StructureEdit? {
        if (path.size < 2) return null
        val selected = item(path) ?: return null
        val parentPath = path.dropLast(1)
        val parent = item(parentPath) ?: return null
        val siblings = parent.parts.filterIsInstance<NestedList>().flatMap { it.items }
        val start = lineStart(selected)
        if (parent.parts.any { it !is NestedList && it.offset > start }) return null
        val currentIndent = selected.contentStart - start - selected.marker.length - markerSuffixLength(selected)
        val targetIndent = parent.contentStart - lineStart(parent) - parent.marker.length - markerSuffixLength(parent)
        val delta = targetIndent - currentIndent
        if (delta >= 0) return null
        val updated = (if (path.last() == siblings.lastIndex) shiftSubtree(path, delta) else {
            val (from, until) = subtreeRange(path) ?: return null
            val lastPath = parentPath + siblings.lastIndex
            val insertion = subtreeRange(lastPath)?.second ?: return null
            val shifted = shiftIndent(source.substring(from, until), delta) ?: return null
            val between = source.substring(until, insertion)
            val newline = if (source.contains("\r\n")) "\r\n" else "\n"
            val spacer = if (between.endsWith('\n')) "" else newline
            val moved = if (insertion == source.length && !source.endsWith('\n')) shifted.trimEnd('\r', '\n') else shifted
            source.substring(0, from) + between + spacer + moved + source.substring(insertion)
        }) ?: return null
        return StructureEdit(updated, parentPath.dropLast(1) + (parentPath.last() + 1))
    }

    private fun markerSuffixLength(item: Item): Int {
        val start = lineStart(item)
        val prefix = source.substring(start, item.contentStart)
        val markerOffset = prefix.indexOf(item.marker)
        return if (markerOffset < 0) 0 else prefix.length - markerOffset - item.marker.length
    }

    private fun lineStart(item: Item): Int = source.lastIndexOf('\n', item.contentStart - 1) + 1

    private fun subtreeRange(path: List<Int>): Pair<Int, Int>? {
        val selected = item(path) ?: return null
        val start = lineStart(selected)
        var end = source.length
        for (depth in path.indices) {
            val prefix = path.take(depth)
            val siblings = if (depth == 0) items else item(prefix)?.parts?.filterIsInstance<NestedList>()?.flatMap { it.items } ?: return null
            siblings.getOrNull(path[depth] + 1)?.let { end = minOf(end, lineStart(it)) }
            if (depth > 0) {
                val parent = item(prefix) ?: return null
                parent.parts.filter { it !is NestedList && it.offset > start }.minOfOrNull { it.offset }
                    ?.let { end = minOf(end, source.lastIndexOf('\n', it - 1) + 1) }
            }
        }
        return start to end
    }

    private fun shiftSubtree(path: List<Int>, delta: Int): String? {
        val (start, end) = subtreeRange(path) ?: return null
        val shifted = shiftIndent(source.substring(start, end), delta) ?: return null
        return source.replaceRange(start, end, shifted)
    }

    private fun shiftIndent(section: String, delta: Int): String? {
        val matchIndent = Regex("(?m)^[ \\t]*(?=[^\\r\\n \\t])")
        var valid = true
        val shifted = matchIndent.replace(section) { match ->
            val old = match.value
            if ('\t' in old || old.length + delta < 0) { valid = false; old }
            else " ".repeat(old.length + delta)
        }
        return if (valid) shifted else null
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
                                is Heading -> {
                                    // CommonMark treats an empty indented "- " after list text as a setext
                                    // underline. The formatted editor treats that source line as an empty child.
                                    val lastSpan = contentNode.sourceSpans.lastOrNull()
                                    val nestedStart = lastSpan?.let { source.lastIndexOf('\n', it.inputIndex - 1) + 1 }
                                    val nestedEnd = nestedStart?.let { source.indexOfAny(charArrayOf('\r', '\n'), it).let { end -> if (end < 0) source.length else end } }
                                    val nestedMatch = if (nestedStart != null && nestedEnd != null && nestedStart > lineStart)
                                        marker.find(source.substring(nestedStart, nestedEnd)) else null
                                    if (nestedMatch != null && nestedStart != null && nestedEnd != null &&
                                        nestedMatch.value.length == nestedEnd - nestedStart &&
                                        nestedMatch.groupValues[1].length > match.groupValues[1].length) {
                                        val state = nestedMatch.groups[4]
                                        val emptyStart = nestedStart + nestedMatch.value.length
                                        parts += NestedList(listOf(Item(
                                            marker = nestedMatch.groupValues[2], contentStart = emptyStart,
                                            contentEnd = nestedEnd, taskStateOffset = state?.let { nestedStart + it.range.first },
                                            checked = state?.value?.equals("x", ignoreCase = true) == true,
                                            parts = listOf(Line(emptyStart, nestedEnd, 0)),
                                        )), nestedStart)
                                    } else {
                                        val spans = contentNode.sourceSpans
                                        if (spans.isNotEmpty()) {
                                            val start = spans.first().inputIndex
                                            val end = spans.last().let { it.inputIndex + it.length }
                                            if (start >= 0 && end <= source.length && end > start) parts += Raw(start, end)
                                        }
                                    }
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
