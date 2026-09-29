package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
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
    private val kind: MarkdownBlockKind,
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

    private fun siblings(parentPath: List<Int>): List<Item>? = if (parentPath.isEmpty()) items else
        item(parentPath)?.parts?.filterIsInstance<NestedList>()?.flatMap { it.items }

    fun lineContent(path: List<Int>, lineIndex: Int): String? = item(path)?.lines?.getOrNull(lineIndex)?.let {
        source.substring(it.start, it.end)
    }

    fun rawContent(raw: Raw): String = source.substring(raw.start, raw.end)

    fun replaceLine(path: List<Int>, lineIndex: Int, content: String): String? {
        if ('\n' in content || '\r' in content) return null
        val line = item(path)?.lines?.getOrNull(lineIndex) ?: return null
        return source.replaceRange(line.start, line.end, content)
    }

    data class BlockPasteEdit(
        val source: String,
        val selectionOffset: Int,
        val focusPath: List<Int>,
        val focusLine: Int,
    )

    data class PlainLinePasteEdit(
        val source: String,
        val selectionOffset: Int,
        val focusLine: Int,
        val focusVisibleOffset: Int,
    )

    data class SourcePasteEdit(val source: String, val selectionOffset: Int)

    /** Preserve an unsupported multiline paste as source, keeping following lines in this item. */
    fun replaceLineForSourcePaste(path: List<Int>, lineIndex: Int, before: String,
                                  pasted: String, after: String): SourcePasteEdit? {
        val selected = item(path) ?: return null
        val line = selected.lines.getOrNull(lineIndex) ?: return null
        val itemStart = source.lastIndexOf('\n', selected.contentStart - 1) + 1
        val itemEnd = source.indexOfAny(charArrayOf('\r', '\n'), itemStart)
            .let { if (it < 0) source.length else it }
        val markerMatch = marker.find(source.substring(itemStart, itemEnd))
        // Tabs and unusual markers are left literal; Source mode exposes their exact result.
        val indent = if (markerMatch?.range?.first == 0 && '\t' !in markerMatch.value)
            " ".repeat(markerMatch.groupValues[1].length + markerMatch.groupValues[2].length +
                markerMatch.groupValues[3].length) else ""
        val newline = if ("\r\n" in source) "\r\n" else "\n"
        val normalized = pasted.replace("\r\n", "\n").replace('\r', '\n')
        val pieces = normalized.split('\n')
        val inserted = buildString {
            pieces.forEachIndexed { index, piece ->
                if (index > 0) {
                    append(newline)
                    if (piece.isNotEmpty() || (index == pieces.lastIndex && after.isNotEmpty())) append(indent)
                }
                append(piece)
            }
        }
        val replacement = before + inserted + after
        return SourcePasteEdit(source.replaceRange(line.start, line.end, replacement),
            line.start + before.length + inserted.length)
    }

    /** Keeps pasted plain soft lines in the same list-item paragraph. */
    fun replaceLineWithPlainLines(path: List<Int>, lineIndex: Int, before: String,
                                  pasted: List<String>, after: String,
                                  expectedVisible: String, enableWikilinks: Boolean): PlainLinePasteEdit? {
        if (pasted.size < 2 || pasted.any { it.isBlank() }) return null
        val selected = item(path) ?: return null
        val line = selected.lines.getOrNull(lineIndex) ?: return null
        val itemStart = source.lastIndexOf('\n', selected.contentStart - 1) + 1
        val itemEnd = source.indexOfAny(charArrayOf('\r', '\n'), itemStart)
            .let { if (it < 0) source.length else it }
        val markerMatch = marker.find(source.substring(itemStart, itemEnd)) ?: return null
        if (markerMatch.range.first != 0 || '\t' in markerMatch.value) return null
        val indent = " ".repeat(markerMatch.groupValues[1].length + markerMatch.groupValues[2].length +
            markerMatch.groupValues[3].length)
        val newline = if ("\r\n" in source) "\r\n" else "\n"
        val escaped = pasted.map { MarkdownInlineEditing.escapedPlainText(it) }
        val inserted = escaped.joinToString(newline + indent)
        val replacement = before + inserted + after
        val candidate = source.replaceRange(line.start, line.end, replacement)
        val block = MarkdownDocumentCodec.parse(candidate).blocks.singleOrNull() ?: return null
        if (block.kind != kind || block.range != TextRange(0, candidate.length)) return null
        val parsed = parse(block) ?: return null
        for (depth in path.indices) {
            val parentPath = path.take(depth)
            val oldSiblings = siblings(parentPath) ?: return null
            val newSiblings = parsed.siblings(parentPath) ?: return null
            if (oldSiblings.map { it.marker } != newSiblings.map { it.marker }) return null
            for (index in oldSiblings.indices) {
                if (index != path[depth] && copySiblingItems(parentPath, index, index) !=
                    parsed.copySiblingItems(parentPath, index, index)) return null
            }
        }
        val parsedItem = parsed.item(path) ?: return null
        val insertedLines = parsedItem.lines.drop(lineIndex).take(pasted.size)
        if (insertedLines.size != pasted.size) return null
        val visible = insertedLines.joinToString("\n") { parsedLine ->
            MarkdownInlineEditing.parse(candidate.substring(parsedLine.start, parsedLine.end), enableWikilinks).visible
        }
        if (visible != expectedVisible.replace("\r\n", "\n").replace('\r', '\n')) return null
        val sourceCaret = line.start + before.length + inserted.length
        val focusLine = lineIndex + pasted.lastIndex
        return PlainLinePasteEdit(candidate, sourceCaret, focusLine, pasted.last().length)
    }

    /** Inserts parsed blocks under the active list item without rewriting its marker or siblings. */
    fun replaceLineWithBlocks(path: List<Int>, lineIndex: Int, before: String, blocks: String, after: String): BlockPasteEdit? {
        val selected = item(path) ?: return null
        val line = selected.lines.getOrNull(lineIndex) ?: return null
        val lineStart = source.lastIndexOf('\n', line.start - 1) + 1
        val prefix = source.substring(lineStart, line.start)
        if ('\t' in prefix) return null
        val itemStart = source.lastIndexOf('\n', selected.contentStart - 1) + 1
        val itemEnd = source.indexOfAny(charArrayOf('\r', '\n'), itemStart)
            .let { if (it < 0) source.length else it }
        val markerMatch = marker.find(source.substring(itemStart, itemEnd)) ?: return null
        if (markerMatch.range.first != 0 || '\t' in markerMatch.value) return null
        // A task checkbox belongs to paragraph content, not the CommonMark list marker.
        val contentIndent = markerMatch.groupValues[1].length + markerMatch.groupValues[2].length +
            markerMatch.groupValues[3].length
        val indent = " ".repeat(contentIndent)
        val newline = if ("\r\n" in source) "\r\n" else "\n"
        val normalized = blocks.replace("\r\n", "\n").replace('\r', '\n')
        val nested = normalized.split('\n').joinToString(newline) { if (it.isEmpty()) "" else indent + it }
        val leading = before + newline + nested
        val replacement = leading + if (after.isEmpty()) "" else newline + newline + indent + after
        val candidate = source.replaceRange(line.start, line.end, replacement)
        val block = MarkdownDocumentCodec.parse(candidate).blocks.singleOrNull() ?: return null
        if (block.kind != kind || block.range != TextRange(0, candidate.length)) return null
        val parsed = parse(block) ?: return null
        for (depth in path.indices) {
            val parentPath = path.take(depth)
            val oldSiblings = siblings(parentPath) ?: return null
            val newSiblings = parsed.siblings(parentPath) ?: return null
            if (oldSiblings.map { it.marker } != newSiblings.map { it.marker }) return null
            for (index in oldSiblings.indices) {
                if (index != path[depth] && copySiblingItems(parentPath, index, index) !=
                    parsed.copySiblingItems(parentPath, index, index)) return null
            }
        }
        val parsedItem = parsed.item(path) ?: return null
        val beforeLine = parsedItem.lines.getOrNull(lineIndex) ?: return null
        if (parsed.source.substring(beforeLine.start, beforeLine.end).trimEnd() != before.trimEnd()) return null
        val caret = line.start + leading.length
        if (after.isNotEmpty()) {
            val preservedAfter = parsedItem.lines.any { candidateLine ->
                candidateLine.start >= caret &&
                    parsed.source.substring(candidateLine.start, candidateLine.end).trimStart() == after.trimStart()
            }
            if (!preservedAfter) return null
        }
        val focus = parsed.editableLineEndingAt(caret) ?: return null
        if (focus.first.size <= path.size || focus.first.take(path.size) != path) return null
        return BlockPasteEdit(candidate, caret, focus.first, focus.second)
    }

    private fun editableLineEndingAt(offset: Int): Pair<List<Int>, Int>? {
        fun visit(items: List<Item>, prefix: List<Int>, indexBase: Int): Pair<List<Int>, Int>? {
            items.forEachIndexed { index, item ->
                val path = prefix + (indexBase + index)
                item.lines.forEachIndexed { lineIndex, line ->
                    if (line.end == offset) return path to lineIndex
                }
                var childBase = 0
                item.parts.filterIsInstance<NestedList>().forEach { nested ->
                    visit(nested.items, path, childBase)?.let { return it }
                    childBase += nested.items.size
                }
            }
            return null
        }
        return visit(items, emptyList(), 0)
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
    data class EmptyExitEdit(val source: String, val paragraphOffset: Int, val beforeItemCount: Int)

    /** Removes an empty root item and leaves a blank paragraph slot between its neighbors. */
    fun exitEmptyTopLevel(path: List<Int>): EmptyExitEdit? {
        if (path.size != 1) return null
        val selected = item(path) ?: return null
        if (selected.lines.size != 1 || selected.parts.any { it !is Line } ||
            source.substring(selected.contentStart, selected.contentEnd).isNotBlank()) return null
        val before = source.substring(0, lineStart(selected))
        val after = source.substring(selected.contentEnd)
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val prefix = if (before.isEmpty()) "" else before + if (before.endsWith(newline)) newline else newline + newline
        val suffix = if (after.isEmpty()) "" else if (after.startsWith(newline)) newline + after else newline + newline + after
        return EmptyExitEdit(prefix + suffix, prefix.length, path.first())
    }

    /** Lifts a root item's text and child lists between the remaining list fragments. */
    fun liftTopLevel(path: List<Int>): LiftEdit? {
        if (path.size != 1) return null
        val selected = item(path) ?: return null
        if (selected.parts.any { !it.isLiftable() }) return null
        val firstNested = selected.parts.indexOfFirst { it is NestedList }
        if (firstNested >= 0 && selected.parts.drop(firstNested).any { it is Line }) return null
        val (start, end) = subtreeRange(path) ?: return null
        val firstLine = source.substring(selected.contentStart, selected.contentEnd)
        if (firstLine.isBlank()) return null
        if ('\t' in source.substring(start, selected.contentStart)) return null
        val contentIndent = selected.contentStart - start
        val remaining = shiftIndent(source.substring(selected.contentEnd, end), -contentIndent) ?: return null
        val body = firstLine + remaining
        val blocks = generateSequence(parseMarkdown(body).firstChild) { it.next }.toList()
        if (blocks.firstOrNull() !is Paragraph && blocks.firstOrNull() !is Heading) return null
        if (blocks.count { it is BulletList || it is OrderedList } != selected.parts.count { it is NestedList }) return null
        val before = source.substring(0, start)
        val after = source.substring(end)
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val prefix = before + blockSeparator(before, body, newline)
        val replacement = prefix + body + blockSeparator(body, after, newline) + after
        val expectedBlocks = listOf(before, body, after).flatMap { fragment ->
            generateSequence(parseMarkdown(fragment).firstChild) { it.next }.map { it.javaClass }.toList()
        }
        val actualBlocks = generateSequence(parseMarkdown(replacement).firstChild) { it.next }
            .map { it.javaClass }.toList()
        if (actualBlocks != expectedBlocks) return null
        return LiftEdit(replacement, prefix.length)
    }

    private fun Part.isLiftable(): Boolean = when (this) {
        is Line -> true
        is NestedList -> items.all { item -> item.parts.all { it.isLiftable() } }
        is Raw -> false
    }

    private fun blockSeparator(before: String, after: String, newline: String): String {
        if (before.isEmpty() || after.isEmpty()) return ""
        val existing = before.takeLastWhile { it == '\r' || it == '\n' }.count { it == '\n' } +
            after.takeWhile { it == '\r' || it == '\n' }.count { it == '\n' }
        return newline.repeat((2 - existing).coerceAtLeast(0))
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

    /** Selects complete sibling subtrees, including their original line endings. */
    fun siblingRange(parentPath: List<Int>, firstIndex: Int, lastIndex: Int): Pair<Int, Int>? {
        if (firstIndex < 0 || lastIndex < firstIndex) return null
        val siblings = if (parentPath.isEmpty()) items else
            item(parentPath)?.parts?.filterIsInstance<NestedList>()?.flatMap { it.items } ?: return null
        if (lastIndex >= siblings.size) return null
        val first = subtreeRange(parentPath + firstIndex) ?: return null
        val last = subtreeRange(parentPath + lastIndex) ?: return null
        return first.first to last.second
    }

    fun copySiblingItems(parentPath: List<Int>, firstIndex: Int, lastIndex: Int): String? =
        siblingRange(parentPath, firstIndex, lastIndex)?.let { (start, end) ->
            source.substring(start, end).trimEnd('\r', '\n')
        }

    fun deleteSiblingItems(parentPath: List<Int>, firstIndex: Int, lastIndex: Int): String? {
        val (start, end) = siblingRange(parentPath, firstIndex, lastIndex) ?: return null
        var before = source.substring(0, start)
        val after = source.substring(end)
        if (after.isEmpty()) before = before.removeSuffix("\r\n").removeSuffix("\n")
        return before + after
    }

    /** Wraps the visible primary line in each item, preserving markers and child subtrees. */
    fun applyInlineToSiblingItems(
        parentPath: List<Int>, firstIndex: Int, lastIndex: Int,
        kind: InlineMarkKind, destination: String?, enableWikilinks: Boolean,
    ): String? {
        if (siblingRange(parentPath, firstIndex, lastIndex) == null) return null
        val patches = (firstIndex..lastIndex).mapNotNull { index ->
            val selected = item(parentPath + index) ?: return null
            val line = selected.lines.firstOrNull { it.start == selected.contentStart } ?: selected.lines.firstOrNull() ?: return null
            val inline = MarkdownInlineEditing.parse(source.substring(line.start, line.end), enableWikilinks)
            if (inline.visible.isEmpty()) return@mapNotNull null
            val wrapped = inline.wrapComplete(kind, destination) ?: return null
            if (wrapped == inline.source) null else Triple(line.start, line.end, wrapped)
        }
        if (patches.isEmpty()) return null
        return patches.sortedByDescending { it.first }.fold(source) { current, (start, end, replacement) ->
            current.replaceRange(start, end, replacement)
        }
    }

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
                                        val nestedStart = source.lastIndexOf('\n', span.inputIndex - 1) + 1
                                        val nestedEnd = source.indexOfAny(charArrayOf('\r', '\n'), nestedStart)
                                            .let { if (it < 0) source.length else it }
                                        val nestedMatch = if (nestedStart > lineStart)
                                            marker.find(source.substring(nestedStart, nestedEnd)) else null
                                        if (nestedMatch != null && nestedMatch.value.length == nestedEnd - nestedStart &&
                                            nestedMatch.groupValues[1].length > match.groupValues[1].length) {
                                            // CommonMark can absorb an empty ordered child as a parent paragraph continuation.
                                            val nestedState = nestedMatch.groups[4]
                                            parts += NestedList(listOf(Item(
                                                marker = nestedMatch.groupValues[2], contentStart = nestedEnd,
                                                contentEnd = nestedEnd,
                                                taskStateOffset = nestedState?.let { nestedStart + it.range.first },
                                                checked = nestedState?.value?.equals("x", ignoreCase = true) == true,
                                                parts = listOf(Line(nestedEnd, nestedEnd, 0)),
                                            )), nestedStart)
                                        } else {
                                            parts += Line(span.inputIndex, span.inputIndex + span.length, lineIndex++)
                                        }
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

            return parseItems(root).takeIf { it.isNotEmpty() }?.let { MarkdownSourceList(source, block.kind, it) }
        }
    }
}
