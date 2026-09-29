package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jackcaow.smoothmarkdown.SafeHtml
import org.commonmark.node.Emphasis
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.HardLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

internal enum class InlineMarkKind { BOLD, ITALIC, STRIKETHROUGH, LINK, CODE, WIKILINK }
internal data class InlineMark(
    val kind: InlineMarkKind,
    val range: TextRange,
    val destination: String? = null,
    val sourceStart: Int,
    val sourceEnd: Int,
)

/**
 * Bounded source-backed inline model for Formatted paragraphs and ATX headings.
 * Preserves delimiters around local edits; cross-mark edits fall back to escaped plain text.
 * Complex links, images, and multi-backtick code remain literal source in this editing pass.
 */
internal class MarkdownInlineEditing private constructor(
    val source: String,
    val visible: String,
    val marks: List<InlineMark>,
    private val starts: List<Int>,
    private val ends: List<Int>,
    private val enableWikilinks: Boolean,
) {
    data class SplitRange(val before: String, val after: String)

    /** UTF-16 source offset for a visible caret, including hidden Markdown delimiters. */
    fun sourceOffsetAtVisible(offset: Int): Int? {
        if (offset !in 0..visible.length) return null
        return if (offset == 0) starts.firstOrNull() ?: 0 else ends[offset - 1]
    }

    /**
     * Split a visible selection into standalone Markdown fragments. Delimiters active at either
     * edge are closed or reopened so bold, italic, and link text outside a block paste survives.
     */
    fun splitVisibleRange(range: TextRange): SplitRange? {
        val lower = range.min
        val upper = range.max
        if (lower < 0 || upper > visible.length || !validUtf16Boundary(lower) || !validUtf16Boundary(upper)) return null
        val start = boundary(lower) ?: return null
        val end = boundary(upper) ?: return null
        if (start.offset > end.offset) return null
        val before = source.substring(0, start.offset) + start.closeTokens
        val after = end.openTokens + source.substring(end.offset)
        if (before.isNotEmpty() && parse(before, enableWikilinks).visible != visible.substring(0, lower)) return null
        if (after.isNotEmpty() && parse(after, enableWikilinks).visible != visible.substring(upper)) return null
        return SplitRange(before, after)
    }

    fun annotated(linkColor: Color): AnnotatedString = buildAnnotatedString {
        append(visible)
        marks.forEach { mark ->
            if (mark.range.collapsed) return@forEach
            val style = when (mark.kind) {
                InlineMarkKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineMarkKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineMarkKind.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                InlineMarkKind.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                InlineMarkKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
                InlineMarkKind.WIKILINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            }
            addStyle(style, mark.range.min, mark.range.max)
        }
    }

    /** Rewrites only the changed visible range; surrounding syntax and link destinations stay untouched. */
    fun replaceVisible(nextVisible: String): String? {
        if (nextVisible == visible) return source
        val commonPrefix = visible.commonPrefixWith(nextVisible).length
        val oldTail = visible.substring(commonPrefix)
        val newTail = nextVisible.substring(commonPrefix)
        val commonSuffix = oldTail.commonSuffixWith(newTail).length
        val oldEnd = visible.length - commonSuffix
        val newEnd = nextVisible.length - commonSuffix
        var rawStart = if (oldEnd == commonPrefix && commonPrefix > 0) ends[commonPrefix - 1]
            else if (commonPrefix < starts.size) starts[commonPrefix] else ends.lastOrNull() ?: 0
        var rawEnd = if (oldEnd > commonPrefix) ends[oldEnd - 1] else rawStart
        val insertion = nextVisible.substring(commonPrefix, newEnd)
        if (insertion.contains('\n') && !source.contains('\n')) return null
        if (insertion.isEmpty() && oldEnd > commonPrefix) {
            val deletedMark = marks.filter { it.range.min == commonPrefix && it.range.max == oldEnd }
                .maxByOrNull { it.sourceEnd - it.sourceStart }
            if (deletedMark != null) {
                rawStart = deletedMark.sourceStart
                rawEnd = deletedMark.sourceEnd
            }
        }
        val withinCode = marks.any { it.kind == InlineMarkKind.CODE && commonPrefix >= it.range.min && oldEnd <= it.range.max }
        if (withinCode && insertion.contains('`')) return null
        val escaped = if (withinCode) insertion else escapeMarkdown(insertion, enableWikilinks)
        val candidate = source.replaceRange(rawStart, rawEnd, escaped)
        val candidateVisible = parse(candidate, enableWikilinks).visible
        val normalizedVisible = if (enableWikilinks) Regex("""\[\[([^\]\r\n]+)\]\]""").replace(nextVisible) { it.groupValues[1] }
            else nextVisible
        return if (candidateVisible == normalizedVisible) candidate else escapeMarkdown(nextVisible, enableWikilinks)
    }

    fun wrap(selection: TextRange, kind: InlineMarkKind, destination: String? = null): String? {
        val lower = selection.min.coerceIn(0, visible.length)
        val upper = selection.max.coerceIn(lower, visible.length)
        if (!validUtf16Boundary(lower) || !validUtf16Boundary(upper)) return null
        if (kind == InlineMarkKind.LINK && !safeMarkdownDestination(linkDestination(destination))) return null
        val existing = marks.firstOrNull { it.kind == kind && it.range.min == lower && it.range.max == upper }
        if (existing != null && kind != InlineMarkKind.LINK) {
            val delimiterLength = if (kind == InlineMarkKind.BOLD || kind == InlineMarkKind.STRIKETHROUGH || kind == InlineMarkKind.WIKILINK) 2 else 1
            return source.replaceRange(existing.sourceStart, existing.sourceEnd,
                source.substring(existing.sourceStart + delimiterLength, existing.sourceEnd - delimiterLength))
        }
        if (kind == InlineMarkKind.LINK || kind == InlineMarkKind.WIKILINK) if (marks.any {
                it.kind in setOf(InlineMarkKind.LINK, InlineMarkKind.WIKILINK) && lower < it.range.max && upper > it.range.min
            }) return null
        if (marks.any { it.kind == InlineMarkKind.CODE && lower < it.range.max && upper > it.range.min }) return null
        if (lower < upper && kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK) &&
            marks.all { it.kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK) }) {
            return wrapMappedRange(lower, upper, kind, destination)
        }
        val rawStart = if (lower < starts.size) starts[lower] else ends.lastOrNull() ?: 0
        val rawEnd = if (upper > lower) ends[upper - 1] else rawStart
        val body = source.substring(rawStart, rawEnd).ifEmpty {
            when (kind) {
                InlineMarkKind.BOLD -> "bold"
                InlineMarkKind.ITALIC -> "italic"
                InlineMarkKind.STRIKETHROUGH -> "strikethrough"
                InlineMarkKind.LINK -> "link"
                InlineMarkKind.CODE -> "code"
                InlineMarkKind.WIKILINK -> "Note"
            }
        }
        val (prefix, suffix) = when (kind) {
            InlineMarkKind.BOLD -> "**" to "**"
            InlineMarkKind.ITALIC -> "*" to "*"
            InlineMarkKind.STRIKETHROUGH -> "~~" to "~~"
            InlineMarkKind.CODE -> "`" to "`"
            InlineMarkKind.LINK -> "[" to "](${linkDestination(destination)})"
            InlineMarkKind.WIKILINK -> "[[" to "]]"
        }
        if (kind == InlineMarkKind.CODE && body.contains('`')) return null
        return source.replaceRange(rawStart, rawEnd, prefix + body + suffix)
    }

    private fun validUtf16Boundary(offset: Int): Boolean = offset == 0 || offset == visible.length ||
        !(visible[offset - 1].isHighSurrogate() && visible[offset].isLowSurrogate())

    private fun linkDestination(value: String?): String = value?.takeIf(String::isNotEmpty) ?: "https://example.com"

    private fun safeMarkdownDestination(value: String): Boolean = SafeHtml.isSafeLink(value) &&
        value.none { it.isWhitespace() || it.isISOControl() || it in "()\\<>[]" }

    /** Preserve each existing mark while wrapping a mapped visible range, including nested text. */
    private fun wrapMappedRange(lower: Int, upper: Int, kind: InlineMarkKind, destination: String?): String? {
        val before = semanticInline(source) ?: return null
        if (before.visible != visible || before.marks.coverage(visible.length) !=
            marks.map { it.semantic() }.coverage(visible.length)) return null
        val start = boundary(lower) ?: return null
        val end = boundary(upper) ?: return null
        if (start.offset > end.offset) return null
        val delimiters = when (kind) {
            InlineMarkKind.BOLD -> listOf("**" to "**", "__" to "__")
            InlineMarkKind.ITALIC -> listOf("*" to "*", "_" to "_")
            InlineMarkKind.LINK -> listOf("[" to "](${linkDestination(destination)})")
            else -> return null
        }
        val added = SemanticMark(kind, TextRange(lower, upper),
            if (kind == InlineMarkKind.LINK) linkDestination(destination) else null)
        val expected = (before.marks + added).coverage(visible.length)
        for (splitBoundaryMarks in listOf(false, true)) {
            for ((open, close) in delimiters) {
                val startPrefix = if (splitBoundaryMarks) start.closeTokens else ""
                val startSuffix = if (splitBoundaryMarks) start.openTokens else ""
                val endPrefix = if (splitBoundaryMarks) end.closeTokens else ""
                val endSuffix = if (splitBoundaryMarks) end.openTokens else ""
                val candidate = source.substring(0, start.offset) + startPrefix + open + startSuffix +
                    source.substring(start.offset, end.offset) + endPrefix + close + endSuffix +
                    source.substring(end.offset)
                val next = semanticInline(candidate) ?: continue
                if (next.visible != visible || next.marks.coverage(visible.length) != expected) continue
                val mapped = parse(candidate, enableWikilinks)
                if (mapped.visible == visible && mapped.marks.map { it.semantic() }.coverage(visible.length) == expected) {
                    return candidate
                }
            }
        }
        return null
    }

    private data class SemanticMark(val kind: InlineMarkKind, val range: TextRange, val destination: String? = null)
    private data class InlineSemantic(val visible: String, val marks: List<SemanticMark>)

    private fun InlineMark.semantic(): SemanticMark = SemanticMark(kind, range, destination)

    private fun List<SemanticMark>.coverage(length: Int): List<Map<Pair<InlineMarkKind, String?>, Int>> {
        val counts = MutableList(length) { mutableMapOf<Pair<InlineMarkKind, String?>, Int>() }
        for (mark in this) {
            if (mark.range.min < 0 || mark.range.max > length) return emptyList()
            for (index in mark.range.min until mark.range.max) {
                val key = mark.kind to mark.destination
                counts[index][key] = (counts[index][key] ?: 0) + 1
            }
        }
        return counts
    }

    private data class InlineBoundary(val offset: Int, val closeTokens: String, val openTokens: String)

    private fun boundary(visibleOffset: Int): InlineBoundary? {
        val default = when (visibleOffset) {
            0 -> 0
            visible.length -> source.length
            else -> ends[visibleOffset - 1]
        }
        val ending = marks.filter { it.range.max == visibleOffset && it.range.min < visibleOffset }
        val starting = marks.filter { it.range.min == visibleOffset && it.range.max > visibleOffset }
        val offset = ending.maxOfOrNull { it.sourceEnd } ?: starting.minOfOrNull { it.sourceStart } ?: default
        val active = marks.filter { it.range.min < visibleOffset && visibleOffset < it.range.max }
            .sortedWith(compareBy<InlineMark> { it.sourceStart }.thenByDescending { it.sourceEnd })
        val tokens = active.map { mark ->
            when (mark.kind) {
                InlineMarkKind.BOLD, InlineMarkKind.ITALIC -> {
                    val length = if (mark.kind == InlineMarkKind.BOLD) 2 else 1
                    source.substring(mark.sourceStart, mark.sourceStart + length) to
                        source.substring(mark.sourceEnd - length, mark.sourceEnd)
                }
                InlineMarkKind.LINK -> {
                    val closeAt = source.lastIndexOf("](", mark.sourceEnd - 1)
                    if (closeAt <= mark.sourceStart) return null
                    "[" to source.substring(closeAt, mark.sourceEnd)
                }
                else -> return null
            }
        }
        return InlineBoundary(offset, tokens.asReversed().joinToString("") { it.second },
            tokens.joinToString("") { it.first })
    }

    /** CommonMark is the authority for candidate syntax; reject unsupported inline node trees. */
    private fun semanticInline(markdown: String): InlineSemantic? {
        val document = Parser.builder().build().parse(markdown)
        val paragraph = document.firstChild as? Paragraph ?: return null
        if (paragraph !== document.lastChild) return null
        val text = StringBuilder()
        val semanticMarks = mutableListOf<SemanticMark>()
        lateinit var visit: (Node) -> Boolean
        fun children(node: Node): Boolean {
            var child = node.firstChild
            while (child != null) {
                if (!visit(child)) return false
                child = child.next
            }
            return true
        }
        fun mark(node: Node, kind: InlineMarkKind, destination: String? = null): Boolean {
            val start = text.length
            if (!children(node)) return false
            semanticMarks += SemanticMark(kind, TextRange(start, text.length), destination)
            return true
        }
        visit = { node -> when (node) {
            is Text -> { text.append(node.literal); true }
            is SoftLineBreak, is HardLineBreak -> { text.append('\n'); true }
            is StrongEmphasis -> mark(node, InlineMarkKind.BOLD)
            is Emphasis -> mark(node, InlineMarkKind.ITALIC)
            is Link -> mark(node, InlineMarkKind.LINK, node.destination)
            else -> false
        } }
        if (!children(paragraph)) return null
        return InlineSemantic(text.toString(), semanticMarks)
    }

    /** Complete-line batch wrapping for a single simple nested emphasis/link node. */
    fun wrapComplete(kind: InlineMarkKind, destination: String? = null, allowMixed: Boolean = false): String? {
        if (visible.isEmpty()) return null
        val full = TextRange(0, visible.length)
        if (marks.isEmpty() || (marks.size == 1 && marks[0].kind == kind && marks[0].range == full)) {
            return wrap(full, kind, destination)
        }
        if (kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC)) {
            val inner = marks.singleOrNull()
            if (inner != null && inner.range == full && inner.sourceStart == 0 && inner.sourceEnd == source.length &&
                inner.kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK) &&
                simpleInlineTreeMatches(source, inner.kind, visible, inner.destination)) {
                val delimiters = if (kind == InlineMarkKind.BOLD) listOf("__", "**") else listOf("_", "*")
                delimiters.firstNotNullOfOrNull { delimiter ->
                    val candidate = delimiter + source + delimiter
                    val parsed = parse(candidate, enableWikilinks)
                    val expected = setOf(
                        Triple(inner.kind, full, inner.destination),
                        Triple(kind, full, null),
                    )
                    val actual = parsed.marks.map { Triple(it.kind, it.range, it.destination) }.toSet()
                    if (parsed.visible == visible && parsed.marks.size == 2 && actual == expected &&
                        nestedInlineTreeMatches(candidate, kind, inner.kind, visible, inner.destination)) candidate else null
                }?.let { return it }
            }
        }
        if (allowMixed && kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK) &&
            marks.all { it.kind in setOf(InlineMarkKind.BOLD, InlineMarkKind.ITALIC, InlineMarkKind.LINK) }) {
            return wrap(full, kind, destination)
        }
        return null
    }

    private fun simpleInlineTreeMatches(source: String, kind: InlineMarkKind, text: String, destination: String?): Boolean {
        val node = singleInlineNode(source) ?: return false
        return matchesInnerNode(node, kind, text, destination)
    }

    private fun nestedInlineTreeMatches(source: String, outer: InlineMarkKind, inner: InlineMarkKind,
                                        text: String, destination: String?): Boolean {
        val node = singleInlineNode(source) ?: return false
        if (!matchesKind(node, outer) || node.firstChild == null || node.firstChild !== node.lastChild) return false
        return matchesInnerNode(node.firstChild, inner, text, destination)
    }

    private fun singleInlineNode(source: String): Node? {
        val document = Parser.builder().build().parse(source)
        val paragraph = document.firstChild as? Paragraph ?: return null
        if (paragraph !== document.lastChild || paragraph.firstChild !== paragraph.lastChild) return null
        return paragraph.firstChild
    }

    private fun matchesInnerNode(node: Node?, kind: InlineMarkKind, text: String, destination: String?): Boolean {
        if (node == null || !matchesKind(node, kind) || node.firstChild !== node.lastChild) return false
        if (kind == InlineMarkKind.LINK && (node as Link).destination != destination) return false
        return (node.firstChild as? Text)?.literal == text
    }

    private fun matchesKind(node: Node, kind: InlineMarkKind): Boolean = when (kind) {
        InlineMarkKind.BOLD -> node is StrongEmphasis
        InlineMarkKind.ITALIC -> node is Emphasis
        InlineMarkKind.LINK -> node is Link
        else -> false
    }

    /** Replace a typed `[[query` range with one semantic wikilink. */
    fun replaceVisibleRangeWithWikilink(range: TextRange, title: String): String? {
        if (!enableWikilinks || title.isEmpty() || title.any { it == ']' || it == '\n' || it == '\r' }) return null
        if (range.min < 0 || range.max > visible.length || range.min >= range.max) return null
        val rawStart = starts[range.min]
        val rawEnd = ends[range.max - 1]
        return source.replaceRange(rawStart, rawEnd, "[[$title]]")
    }

    companion object {
        internal fun escapedPlainText(text: String, enableWikilinks: Boolean): String =
            escapeMarkdown(text, enableWikilinks)

        fun parse(source: String, enableWikilinks: Boolean = false): MarkdownInlineEditing {
            val visible = StringBuilder()
            val starts = mutableListOf<Int>()
            val ends = mutableListOf<Int>()
            val marks = mutableListOf<InlineMark>()
            fun emit(char: Char, start: Int, end: Int) {
                visible.append(char)
                starts += start
                ends += end
            }
            fun closing(token: String, from: Int, until: Int): Int {
                var index = from
                while (index + token.length <= until) {
                    // A nested strong delimiter is not the closing delimiter for emphasis.
                    if (token.length == 1 && token[0] in "*_" && source.startsWith(token + token, index)) {
                        index += 2
                        continue
                    }
                    if (source.startsWith(token, index) && (index == 0 || source[index - 1] != '\\') &&
                        (token[0] != '_' || index + token.length == until || !source[index + token.length].isLetterOrDigit())) return index
                    index++
                }
                return -1
            }
            fun parseRange(from: Int, until: Int) {
                var index = from
                while (index < until) {
                    if (source[index] == '\\' && index + 1 < until && source[index + 1] in "\\*_`[]~") {
                        emit(source[index + 1], index, index + 2)
                        index += 2
                        continue
                    }
                    if (source.startsWith("~~", index)) {
                        val end = closing("~~", index + 2, until)
                        if (end > index + 2) {
                            val beginVisible = visible.length
                            parseRange(index + 2, end)
                            marks += InlineMark(InlineMarkKind.STRIKETHROUGH,
                                TextRange(beginVisible, visible.length), sourceStart = index, sourceEnd = end + 2)
                            index = end + 2
                            continue
                        }
                    }
                    val underscoreCanOpen = index == 0 || !source[index - 1].isLetterOrDigit()
                    val token = when {
                        source.startsWith("**", index) || source.startsWith("__", index) -> source.substring(index, index + 2)
                        source[index] == '*' || (source[index] == '_' && underscoreCanOpen) -> source[index].toString()
                        else -> null
                    }
                    if (token != null && (token[0] != '_' || underscoreCanOpen)) {
                        val end = closing(token, index + token.length, until)
                        if (end > index + token.length) {
                            val beginVisible = visible.length
                            parseRange(index + token.length, end)
                            marks += InlineMark(if (token.length == 2) InlineMarkKind.BOLD else InlineMarkKind.ITALIC,
                                TextRange(beginVisible, visible.length), sourceStart = index, sourceEnd = end + token.length)
                            index = end + token.length
                            continue
                        }
                    }
                    if (source[index] == '`') {
                        val end = closing("`", index + 1, until)
                        if (end > index + 1) {
                            val beginVisible = visible.length
                            for (cursor in index + 1 until end) emit(source[cursor], cursor, cursor + 1)
                            marks += InlineMark(InlineMarkKind.CODE, TextRange(beginVisible, visible.length),
                                sourceStart = index, sourceEnd = end + 1)
                            index = end + 1
                            continue
                        }
                    }
                    if (enableWikilinks && source.startsWith("[[", index)) {
                        val end = source.indexOf("]]", index + 2)
                        if (end > index + 2 && end + 2 <= until && ']' !in source.substring(index + 2, end)) {
                            val beginVisible = visible.length
                            for (cursor in index + 2 until end) emit(source[cursor], cursor, cursor + 1)
                            marks += InlineMark(InlineMarkKind.WIKILINK, TextRange(beginVisible, visible.length),
                                source.substring(index + 2, end), index, end + 2)
                            index = end + 2
                            continue
                        }
                        // An unfinished note link remains literal while the user is typing it.
                        emit('[', index, index + 1)
                        emit('[', index + 1, index + 2)
                        index += 2
                        continue
                    }
                    if (source[index] == '[' && (index == 0 || source[index - 1] != '!')) {
                        val mid = closing("](", index + 1, until)
                        if (mid > index + 1) {
                            val end = closing(")", mid + 2, until)
                            if (end > mid + 2) {
                                val beginVisible = visible.length
                                parseRange(index + 1, mid)
                                marks += InlineMark(InlineMarkKind.LINK, TextRange(beginVisible, visible.length),
                                    source.substring(mid + 2, end), index, end + 1)
                                index = end + 1
                                continue
                            }
                        }
                    }
                    emit(source[index], index, index + 1)
                    index++
                }
            }
            parseRange(0, source.length)
            return MarkdownInlineEditing(source, visible.toString(), marks, starts, ends, enableWikilinks)
        }

        private fun escapeMarkdown(text: String, enableWikilinks: Boolean): String = buildString {
            text.forEach { char ->
                if (char in "\\*_`~" || (!enableWikilinks && char in "[]")) append('\\')
                append(char)
            }
        }
    }
}
