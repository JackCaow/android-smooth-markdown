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
import com.jackcaow.smoothmarkdown.ast.Emphasis
import com.jackcaow.smoothmarkdown.ast.Link
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.SoftLineBreak
import com.jackcaow.smoothmarkdown.ast.HardLineBreak
import com.jackcaow.smoothmarkdown.ast.StrongEmphasis
import com.jackcaow.smoothmarkdown.ast.Text
import com.jackcaow.smoothmarkdown.NativeMarkdownParser as Parser
import com.jackcaow.smoothmarkdown.NativeMarkdownMarkupConverter
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownTextDecoder
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge

internal enum class InlineMarkKind { BOLD, ITALIC, STRIKETHROUGH, LINK, CODE, WIKILINK }
internal data class InlineMark(
    val kind: InlineMarkKind,
    val range: TextRange,
    val destination: String? = null,
    val sourceStart: Int,
    val sourceEnd: Int,
)

/**
 * Source-backed formatted text projected from the shared inline AST.
 * Preserves delimiters around local edits; cross-mark edits may become escaped plain text.
 * Images and embedded HTML retain their source spelling in the native text editor.
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

    /** Exact source characters underlying a non-empty visible range, excluding its opening delimiters. */
    fun sourceRangeForVisible(range: TextRange): TextRange? {
        if (range.min < 0 || range.max > visible.length || range.min >= range.max ||
            !validUtf16Boundary(range.min) || !validUtf16Boundary(range.max)) return null
        val start = starts.getOrNull(range.min) ?: return null
        val end = ends.getOrNull(range.max - 1) ?: return null
        return TextRange(start, end).takeIf { it.min < it.max }
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
        val prefix = source.substring(0, start.offset)
        val suffix = source.substring(end.offset)
        var before = prefix + start.closeTokens
        var after = end.openTokens + suffix
        val expectedBefore = visible.substring(0, lower)
        val expectedAfter = visible.substring(upper)
        // A generated emphasis delimiter cannot close after, or open before, whitespace.
        // Keep those original characters outside the closed/reopened fragment instead.
        if (before.isNotEmpty() && parse(before, enableWikilinks).visible != expectedBefore && start.closeTokens.isNotEmpty()) {
            val whitespace = prefix.takeLastWhile { it.isWhitespace() }
            if (whitespace.isNotEmpty()) before = prefix.dropLast(whitespace.length) + start.closeTokens + whitespace
        }
        if (after.isNotEmpty() && parse(after, enableWikilinks).visible != expectedAfter && end.openTokens.isNotEmpty()) {
            val whitespace = suffix.takeWhile { it.isWhitespace() }
            if (whitespace.isNotEmpty()) after = whitespace + end.openTokens + suffix.drop(whitespace.length)
        }
        if (before.isNotEmpty() && parse(before, enableWikilinks).visible != expectedBefore) return null
        if (after.isNotEmpty() && parse(after, enableWikilinks).visible != expectedAfter) return null
        return SplitRange(before, after)
    }

    /** A standalone Markdown fragment for exactly this rendered range. */
    fun sliceVisibleRange(range: TextRange): String? {
        val lower = range.min
        val upper = range.max
        if (lower < 0 || upper > visible.length || lower == upper ||
            !validUtf16Boundary(lower) || !validUtf16Boundary(upper)) return null
        val start = boundary(lower) ?: return null
        val end = boundary(upper) ?: return null
        if (start.offset > end.offset) return null
        val fragment = start.openTokens + source.substring(start.offset, end.offset) + end.closeTokens
        return fragment.takeIf { parse(it, enableWikilinks).visible == visible.substring(lower, upper) }
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
            val delimiterLength = when (kind) {
                InlineMarkKind.BOLD, InlineMarkKind.WIKILINK -> 2
                InlineMarkKind.STRIKETHROUGH -> source.substring(existing.sourceStart, existing.sourceEnd).takeWhile { it == '~' }.length.coerceIn(1, 2)
                InlineMarkKind.CODE -> source.substring(existing.sourceStart, existing.sourceEnd).takeWhile { it == '`' }.length
                else -> 1
            }
            val unwrapped = if (kind == InlineMarkKind.CODE) escapeMarkdown(visible.substring(existing.range.min, existing.range.max), enableWikilinks)
                else source.substring(existing.sourceStart + delimiterLength, existing.sourceEnd - delimiterLength)
            return source.replaceRange(existing.sourceStart, existing.sourceEnd, unwrapped)
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

    /** Shared inline grammar is the authority for candidate syntax and semantic coverage. */
    private fun semanticInline(markdown: String): InlineSemantic? {
        val root = parseNative(markdown, enableWikilinks) ?: return legacySemanticInline(markdown)
        val model = fromNativeAST(markdown, root, enableWikilinks)
        return InlineSemantic(model.visible, model.marks.map { it.semantic() })
    }

    /** Original source-checkout candidate validation, reached only when native FFI is unavailable. */
    private fun legacySemanticInline(markdown: String): InlineSemantic? {
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
        RustMarkdownBridge.parseInlineForEditor(source)?.let { root ->
            return root.children.singleOrNull()?.let { NativeMarkdownMarkupConverter(source).convert(it) }
        }
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
        internal fun escapedPlainText(text: String): String = buildString {
            text.forEach { char ->
                if (char in "\\*_`~<>&[]") append('\\')
                append(char)
            }
        }

        fun parse(source: String, enableWikilinks: Boolean = false): MarkdownInlineEditing {
            parseNative(source, enableWikilinks)?.let { return fromNativeAST(source, it, enableWikilinks) }
            return parseLegacy(source, enableWikilinks)
        }

        private fun wikilinks(source: String) = Regex("""\[\[([^\]\r\n]+)\]\]""").findAll(source).filter { match ->
            var slash = match.range.first - 1
            while (slash >= 0 && source[slash] == '\\') slash--
            (match.range.first - 1 - slash) % 2 == 0
        }

        private fun parseNative(source: String, enableWikilinks: Boolean): NativeMarkdownNode? {
            val root = RustMarkdownBridge.parseInlineForEditor(source) ?: return null
            if (!enableWikilinks) return root
            val opaque = mutableListOf<com.jackcaow.smoothmarkdown.nativeparser.SourceRange>()
            fun collect(node: NativeMarkdownNode) {
                if (node.kind in setOf(NativeMarkdownNode.Kind.INLINE_CODE, NativeMarkdownNode.Kind.IMAGE, NativeMarkdownNode.Kind.INLINE_HTML)) opaque += node.sourceRange
                else {
                    if (node.kind == NativeMarkdownNode.Kind.LINK && node.children.isNotEmpty()) {
                        opaque += com.jackcaow.smoothmarkdown.nativeparser.SourceRange(node.sourceRange.offset,
                            node.children.first().sourceRange.offset - node.sourceRange.offset)
                        opaque += com.jackcaow.smoothmarkdown.nativeparser.SourceRange(node.children.last().sourceRange.end,
                            node.sourceRange.end - node.children.last().sourceRange.end)
                    }
                    node.children.forEach(::collect)
                }
            }
            collect(root)
            val links = wikilinks(source).filter { match -> opaque.none { match.range.first >= it.offset && match.range.first < it.end } }.toList()
            if (links.isEmpty()) return root
            // A host note title is opaque to CommonMark. Keep its UTF-16 width for source offsets.
            val masked = source.toCharArray()
            links.forEach { match -> for (index in match.range) masked[index] = 'x' }
            return RustMarkdownBridge.parseInlineForEditor(String(masked))
        }

        /** Used only when native artifacts or the FFI are unavailable. */
        private fun parseLegacy(source: String, enableWikilinks: Boolean): MarkdownInlineEditing {
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
                    if (source[index] == '\\' && index + 1 < until && source[index + 1] in "\\*_`[]~<>&") {
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

        /** Syntax comes from the shared tree; this pass maps semantic UTF-16 units to original lexemes. */
        private fun fromNativeAST(source: String, root: NativeMarkdownNode, enableWikilinks: Boolean): MarkdownInlineEditing {
            val visible = StringBuilder()
            val starts = mutableListOf<Int>()
            val ends = mutableListOf<Int>()
            val marks = mutableListOf<InlineMark>()
            val wikiRanges = if (enableWikilinks) wikilinks(source).associateBy { it.range.first } else emptyMap()
            var opaqueUntil = 0
            fun emit(text: String, start: Int, end: Int) {
                text.forEach { visible.append(it); starts += start; ends += end }
            }
            fun emitRaw(start: Int, end: Int, allowWiki: Boolean = false) {
                var cursor = maxOf(start, opaqueUntil)
                while (cursor < end) {
                    val wiki = if (allowWiki) wikiRanges[cursor] else null
                    if (wiki != null) {
                        val begin = visible.length
                        for (index in cursor + 2 until wiki.range.last - 1) emit(source[index].toString(), index, index + 1)
                        marks += InlineMark(InlineMarkKind.WIKILINK, TextRange(begin, visible.length),
                            wiki.groupValues[1], cursor, wiki.range.last + 1)
                        opaqueUntil = wiki.range.last + 1
                        cursor = opaqueUntil
                    } else { emit(source[cursor].toString(), cursor, cursor + 1); cursor++ }
                }
            }
            fun emitLiteral(node: NativeMarkdownNode, begin: Int = node.sourceRange.offset,
                            finish: Int = node.sourceRange.end, literal: String = node.literalText ?: node.source,
                            allowWiki: Boolean = true, code: Boolean = false) {
                var cursor = begin
                var semantic = 0
                while (cursor < finish && semantic < literal.length) {
                    val wiki = if (allowWiki) wikiRanges[cursor] else null
                    if (wiki != null) {
                        emitRaw(cursor, wiki.range.last + 1, true)
                        val consumed = minOf(finish, wiki.range.last + 1) - cursor
                        cursor += consumed
                        semantic = minOf(literal.length, semantic + consumed)
                        continue
                    }
                    if (cursor < opaqueUntil) { cursor++; semantic++; continue }
                    if (!code && source[cursor] == '\\' && cursor + 1 < finish && literal[semantic] == source[cursor + 1]) {
                        emit(literal[semantic].toString(), cursor, cursor + 2); cursor += 2; semantic++; continue
                    }
                    if (!code && source[cursor] == '&') {
                        val close = source.indexOf(';', cursor + 1)
                        if (close in cursor + 1 until minOf(finish, cursor + 34)) {
                            val raw = source.substring(cursor, close + 1)
                            val decoded = NativeMarkdownTextDecoder.decode(raw)
                            if (decoded != raw && literal.startsWith(decoded, semantic)) {
                                emit(decoded, cursor, close + 1); cursor = close + 1; semantic += decoded.length; continue
                            }
                        }
                    }
                    if (source[cursor] == '\r' && cursor + 1 < finish && source[cursor + 1] == '\n' && literal[semantic] in " \n") {
                        emit(literal[semantic].toString(), cursor, cursor + 2); cursor += 2; semantic++; continue
                    }
                    emit(literal[semantic].toString(), cursor, cursor + 1)
                    cursor++; semantic++
                }
                while (semantic < literal.length) {
                    emit(literal[semantic++].toString(), maxOf(begin, finish - 1), finish)
                }
            }
            lateinit var visit: (NativeMarkdownNode) -> Unit
            fun marked(node: NativeMarkdownNode, kind: InlineMarkKind, destination: String? = null) {
                val begin = visible.length
                node.children.forEach(visit)
                marks += InlineMark(kind, TextRange(begin, visible.length), destination,
                    node.sourceRange.offset, node.sourceRange.end)
            }
            visit = { node ->
                if (node.sourceRange.end > opaqueUntil) when (node.kind) {
                    NativeMarkdownNode.Kind.DOCUMENT -> node.children.forEach(visit)
                    NativeMarkdownNode.Kind.TEXT -> emitLiteral(node)
                    NativeMarkdownNode.Kind.STRONG -> marked(node, InlineMarkKind.BOLD)
                    NativeMarkdownNode.Kind.EMPHASIS -> marked(node, InlineMarkKind.ITALIC)
                    NativeMarkdownNode.Kind.STRIKETHROUGH -> marked(node, InlineMarkKind.STRIKETHROUGH)
                    NativeMarkdownNode.Kind.LINK -> marked(node, InlineMarkKind.LINK, node.destination)
                    NativeMarkdownNode.Kind.INLINE_CODE -> {
                        val start = visible.length
                        val delimiter = node.source.takeWhile { it == '`' }.length
                        var begin = node.sourceRange.offset + delimiter
                        var finish = node.sourceRange.end - delimiter
                        val content = source.substring(begin, finish).replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
                        if (content.length >= 2 && content.first() == ' ' && content.last() == ' ' && content.any { it != ' ' }) { begin++; finish-- }
                        emitLiteral(node, begin, finish, node.literalText ?: NativeMarkdownTextDecoder.codeSpan(node.source), false, true)
                        marks += InlineMark(InlineMarkKind.CODE, TextRange(start, visible.length), sourceStart = node.sourceRange.offset, sourceEnd = node.sourceRange.end)
                    }
                    NativeMarkdownNode.Kind.SOFT_BREAK, NativeMarkdownNode.Kind.HARD_BREAK -> emit("\n", node.sourceRange.offset, node.sourceRange.end)
                    // Images, HTML, formulas and extension references stay source-backed in native text editing.
                    else -> emitRaw(node.sourceRange.offset, node.sourceRange.end)
                }
            }
            visit(root)
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
