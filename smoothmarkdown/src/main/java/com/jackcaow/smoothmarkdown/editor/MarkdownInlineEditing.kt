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

internal enum class InlineMarkKind { BOLD, ITALIC, LINK, CODE }
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
) {
    fun annotated(linkColor: Color): AnnotatedString = buildAnnotatedString {
        append(visible)
        marks.forEach { mark ->
            if (mark.range.collapsed) return@forEach
            val style = when (mark.kind) {
                InlineMarkKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineMarkKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineMarkKind.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                InlineMarkKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
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
        val escaped = if (withinCode) insertion else escapeMarkdown(insertion)
        val candidate = source.replaceRange(rawStart, rawEnd, escaped)
        return if (parse(candidate).visible == nextVisible) candidate else escapeMarkdown(nextVisible)
    }

    fun wrap(selection: TextRange, kind: InlineMarkKind, destination: String? = null): String? {
        val lower = selection.min.coerceIn(0, visible.length)
        val upper = selection.max.coerceIn(lower, visible.length)
        val existing = marks.firstOrNull { it.kind == kind && it.range.min == lower && it.range.max == upper }
        if (existing != null && kind != InlineMarkKind.LINK) {
            val delimiterLength = if (kind == InlineMarkKind.BOLD) 2 else 1
            return source.replaceRange(existing.sourceStart, existing.sourceEnd,
                source.substring(existing.sourceStart + delimiterLength, existing.sourceEnd - delimiterLength))
        }
        if (kind == InlineMarkKind.LINK && marks.any {
                it.kind == InlineMarkKind.LINK && lower < it.range.max && upper > it.range.min
            }) return null
        if (marks.any { it.kind == InlineMarkKind.CODE && lower < it.range.max && upper > it.range.min }) return null
        val rawStart = if (lower < starts.size) starts[lower] else ends.lastOrNull() ?: 0
        val rawEnd = if (upper > lower) ends[upper - 1] else rawStart
        val body = source.substring(rawStart, rawEnd).ifEmpty {
            when (kind) {
                InlineMarkKind.BOLD -> "bold"
                InlineMarkKind.ITALIC -> "italic"
                InlineMarkKind.LINK -> "link"
                InlineMarkKind.CODE -> "code"
            }
        }
        val (prefix, suffix) = when (kind) {
            InlineMarkKind.BOLD -> "**" to "**"
            InlineMarkKind.ITALIC -> "*" to "*"
            InlineMarkKind.CODE -> "`" to "`"
            InlineMarkKind.LINK -> "[" to "](${destination?.takeIf(String::isNotBlank) ?: "https://example.com"})"
        }
        if (kind == InlineMarkKind.CODE && body.contains('`')) return null
        return source.replaceRange(rawStart, rawEnd, prefix + body + suffix)
    }

    companion object {
        fun parse(source: String): MarkdownInlineEditing {
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
                    if (source.startsWith(token, index) && (index == 0 || source[index - 1] != '\\') &&
                        (token[0] != '_' || index + token.length == until || !source[index + token.length].isLetterOrDigit())) return index
                    index++
                }
                return -1
            }
            fun parseRange(from: Int, until: Int) {
                var index = from
                while (index < until) {
                    if (source[index] == '\\' && index + 1 < until && source[index + 1] in "\\*_`[]") {
                        emit(source[index + 1], index, index + 2)
                        index += 2
                        continue
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
            return MarkdownInlineEditing(source, visible.toString(), marks, starts, ends)
        }

        private fun escapeMarkdown(text: String): String = buildString {
            text.forEach { char ->
                if (char in "\\*_`[]") append('\\')
                append(char)
            }
        }
    }
}
