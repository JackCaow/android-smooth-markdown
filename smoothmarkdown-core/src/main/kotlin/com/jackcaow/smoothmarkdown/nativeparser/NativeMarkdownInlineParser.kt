package com.jackcaow.smoothmarkdown.nativeparser

import java.util.Locale

data class NativeCustomInlineMatch(val node: NativeMarkdownNode, val consumed: Int)

/** Own CommonMark tokenizer; emphasis uses a delimiter stack after links/code are isolated. */
object NativeMarkdownInlineParser {
    fun normalizeReference(label: String): String = label.trim().split(Regex("[\\s\\p{Z}]+"))
        .joinToString(" ").replace('ẞ', 'ß').uppercase(Locale.ROOT).lowercase(Locale.ROOT)

    fun parse(source: String, offset: Int, references: Map<String, NativeMarkdownReference>, enableGFM: Boolean,
              trimTrailingWhitespace: Boolean = false, trimLeadingWhitespace: Boolean = false,
              customInline: ((source: String, index: Int, absoluteOffset: Int) -> NativeCustomInlineMatch?)? = null): List<NativeMarkdownNode> {
        val result = mutableListOf<NativeMarkdownNode>(); var index = 0; var plainStart = 0
        fun ending(c: Char) = c == '\n' || c == '\r'
        fun horizontal(c: Char) = c == ' ' || c == '\t'
        fun endOfLine(at: Int) = if (source[at] == '\r' && source.getOrNull(at + 1) == '\n') at + 2 else at + 1
        if (trimLeadingWhitespace) { while (index < source.length && horizontal(source[index])) index++; plainStart = index }
        fun append(kind: NativeMarkdownNode.Kind, start: Int, end: Int, contentStart: Int? = null, contentEnd: Int? = null,
                   parseContent: Boolean = true, destination: String = "", title: String? = null, label: String = "") {
            val children = if (contentStart != null && contentEnd != null) {
                val content = source.substring(contentStart, contentEnd)
                if (parseContent) parse(content, offset + contentStart, references, enableGFM, customInline = customInline)
                else listOf(NativeMarkdownNode(NativeMarkdownNode.Kind.TEXT, content, SourceRange(offset + contentStart, content.length), literalText = content))
            } else emptyList()
            result.add(NativeMarkdownNode(kind, source.substring(start, end), SourceRange(offset + start, end - start), children, destination = destination, title = title, label = label))
        }
        fun flush(end: Int) { if (plainStart < end) append(NativeMarkdownNode.Kind.TEXT, plainStart, end) }
        fun closing(marker: String, start: Int): Int? {
            var cursor = start
            while (cursor + marker.length <= source.length) {
                if (source[cursor] == '\\') { cursor += minOf(2, source.length - cursor); continue }
                if (source.startsWith(marker, cursor)) return cursor
                cursor++
            }
            return null
        }
        fun backticks(count: Int, start: Int): Int? {
            var cursor = start
            while (cursor < source.length) {
                if (source[cursor] != '`') { cursor++; continue }
                var end = cursor + 1; while (end < source.length && source[end] == '`') end++
                if (end - cursor == count) return cursor
                cursor = end
            }
            return null
        }
        fun bracket(start: Int): Int? {
            var depth = 0; var cursor = start
            while (cursor < source.length) {
                if (source[cursor] == '\\') { cursor += minOf(2, source.length - cursor); continue }
                if (source[cursor] == '`') {
                    var end = cursor + 1; while (end < source.length && source[end] == '`') end++
                    val close = backticks(end - cursor, end)
                    if (close != null) { cursor = close + end - cursor; continue }
                }
                if (source[cursor] == '<') {
                    val html = NativeMarkdownInlineHTML.length(source, cursor)
                    if (html != null) { cursor += html; continue }
                    val end = source.indexOf('>', cursor + 1)
                    if (end >= 0 && autolinkDestination(source.substring(cursor + 1, end)) != null) { cursor = end + 1; continue }
                }
                if (source[cursor] == '[') depth++
                if (source[cursor] == ']') { if (depth == 0) return cursor; depth-- }
                cursor++
            }
            return null
        }
        data class Tail(val destination: String, val title: String?, val end: Int)
        fun linkTail(opening: Int): Tail? {
            if (source.getOrNull(opening) != '(') return null
            var cursor = opening + 1
            fun skipSpace(): Boolean {
                var endings = 0
                while (cursor < source.length && (horizontal(source[cursor]) || ending(source[cursor]))) {
                    if (ending(source[cursor])) { if (++endings > 1) return false; cursor = endOfLine(cursor) } else cursor++
                }
                return true
            }
            if (!skipSpace() || cursor >= source.length) return null
            val start: Int; val end: Int
            if (source[cursor] == '<') {
                cursor++; start = cursor
                while (cursor < source.length) {
                    if (source[cursor] == '\\' && cursor + 1 < source.length) { cursor += 2; continue }
                    if (ending(source[cursor]) || source[cursor] == '<') return null
                    if (source[cursor] == '>') break
                    cursor++
                }
                if (source.getOrNull(cursor) != '>') return null
                end = cursor++
            } else {
                start = cursor; var depth = 0
                while (cursor < source.length) {
                    val c = source[cursor]
                    if (c == '\\' && cursor + 1 < source.length) { cursor += 2; continue }
                    if (horizontal(c) || ending(c) || c == '<' || c == '>' || c.code < 32 || c.code == 127) break
                    if (c == '(') { if (++depth > 32) return null }
                    else if (c == ')') { if (depth == 0) break; depth-- }
                    cursor++
                }
                if (depth != 0) return null
                end = cursor
            }
            val destination = NativeMarkdownTextDecoder.decode(source.substring(start, end))
            val separator = cursor
            if (!skipSpace() || cursor >= source.length) return null
            var title: String? = null
            if (cursor > separator && source[cursor] in "\"'(") {
                val delimiter = if (source[cursor] == '(') ')' else source[cursor]; cursor++
                val titleStart = cursor; var previousNewline = false
                while (cursor < source.length) {
                    if (source[cursor] == '\\' && cursor + 1 < source.length) { cursor += 2; previousNewline = false; continue }
                    if (source[cursor] == delimiter) break
                    if (ending(source[cursor])) { if (previousNewline) return null; previousNewline = true; cursor = endOfLine(cursor); continue }
                    else if (!horizontal(source[cursor])) previousNewline = false
                    cursor++
                }
                if (cursor >= source.length) return null
                title = NativeMarkdownTextDecoder.decode(source.substring(titleStart, cursor)); cursor++
                if (!skipSpace() || cursor >= source.length) return null
            }
            return if (source[cursor] == ')') Tail(destination, title, cursor + 1) else null
        }
        while (index < source.length) {
            if (source[index] == '\\' && index + 1 < source.length) {
                if (ending(source[index + 1])) {
                    flush(index); var end = endOfLine(index + 1)
                    while (end < source.length && horizontal(source[end])) end++
                    append(NativeMarkdownNode.Kind.HARD_BREAK, index, end); index = end; plainStart = index; continue
                }
                index += 2; continue
            }
            if (ending(source[index])) {
                val hard = index >= 2 && source[index - 1] == ' ' && source[index - 2] == ' '
                var start = index; while (start > plainStart && horizontal(source[start - 1])) start--
                var end = endOfLine(index); while (end < source.length && horizontal(source[end])) end++
                flush(start); append(if (hard) NativeMarkdownNode.Kind.HARD_BREAK else NativeMarkdownNode.Kind.SOFT_BREAK, start, end)
                index = end; plainStart = index; continue
            }
            if (source[index] == '`') {
                var end = index + 1; while (end < source.length && source[end] == '`') end++
                val close = backticks(end - index, end)
                if (close != null) { flush(index); val next = close + end - index; append(NativeMarkdownNode.Kind.INLINE_CODE, index, next); index = next; plainStart = index; continue }
                index = end; continue
            }
            val custom = customInline?.invoke(source, index, offset + index)
            if (custom != null && custom.consumed > 0 && custom.consumed <= source.length - index) {
                flush(index); result.add(custom.node); index += custom.consumed; plainStart = index; continue
            }
            if (source[index] == '<') {
                val end = source.indexOf('>', index + 1)
                if (end >= 0) {
                    val destination = autolinkDestination(source.substring(index + 1, end))
                    if (destination != null) { flush(index); append(NativeMarkdownNode.Kind.LINK, index, end + 1, index + 1, end, false, destination); index = end + 1; plainStart = index; continue }
                    val length = NativeMarkdownInlineHTML.length(source, index)
                    if (length != null) { flush(index); append(NativeMarkdownNode.Kind.INLINE_HTML, index, index + length); index += length; plainStart = index; continue }
                }
            }
            if (enableGFM && (index == 0 || source[index - 1].isWhitespace() || source[index - 1] in "*_~(")) {
                val bare = bareAutolink(source, index)
                if (bare != null) { flush(index); append(NativeMarkdownNode.Kind.LINK, index, bare.first, index, bare.first, false, bare.second); index = bare.first; plainStart = index; continue }
            }
            if (source[index] == '\$' && source.getOrNull(index + 1) != '\$') {
                val end = closing("\$", index + 1)
                if (end != null && end > index + 1 && source[end - 1] != ' ') { flush(index); append(NativeMarkdownNode.Kind.INLINE_MATH, index, end + 1); index = end + 1; plainStart = index; continue }
            }
            if (source.startsWith("[^", index)) {
                val close = closing("]", index + 2)
                if (close != null && close > index + 2 && source.substring(index + 2, close).none { it == '[' || it == '`' || it.isWhitespace() }) { flush(index); append(NativeMarkdownNode.Kind.FOOTNOTE_REFERENCE, index, close + 1, label = source.substring(index + 2, close)); index = close + 1; plainStart = index; continue }
            }
            if (source[index] == '!' || source[index] == '[') {
                val image = source[index] == '!'; val open = if (image) index + 1 else index
                if (source.getOrNull(open) == '[') {
                    val close = bracket(open + 1)
                    if (close != null) {
                        val label = source.substring(open + 1, close); var end = close + 1
                        var destination: String? = null; var title: String? = null
                        val tail = if (source.getOrNull(end) == '(') linkTail(end) else null
                        if (tail != null) { destination = tail.destination; title = tail.title; end = tail.end }
                        else if (source.getOrNull(end) == '[') {
                            val referenceEnd = closing("]", end + 1)
                            if (referenceEnd != null) {
                                val reference = source.substring(end + 1, referenceEnd).ifEmpty { label }
                                val match = references[normalizeReference(reference)]; destination = match?.destination; title = match?.title; end = referenceEnd + 1
                            }
                        } else {
                            val match = references[normalizeReference(label)]; destination = match?.destination; title = match?.title
                        }
                        val children = parse(label, offset + open + 1, references, enableGFM, customInline = customInline)
                        fun containsLink(node: NativeMarkdownNode): Boolean = node.kind == NativeMarkdownNode.Kind.LINK || node.children.any(::containsLink)
                        if (destination != null && (image || children.none(::containsLink))) {
                            flush(index); append(if (image) NativeMarkdownNode.Kind.IMAGE else NativeMarkdownNode.Kind.LINK, index, end, open + 1, close, destination = destination, title = title)
                            index = end; plainStart = index; continue
                        }
                    }
                }
            }
            if (enableGFM && source[index] == '~') {
                var after = index + 1; while (after < source.length && source[after] == '~') after++
                val run = after - index
                var close: Int? = null; var cursor = after
                if (run <= 2) while (cursor + run <= source.length) {
                    if (source[cursor] == '\\') { cursor += minOf(2, source.length - cursor); continue }
                    if (source[cursor] == '~' && source.getOrNull(cursor - 1) != '~' && source.getOrNull(cursor + run) != '~' && source.substring(cursor, cursor + run).all { it == '~' }) { close = cursor; break }
                    cursor++
                }
                if (close != null && close > after) { flush(index); append(NativeMarkdownNode.Kind.STRIKETHROUGH, index, close + run, after, close); index = close + run; plainStart = index; continue }
                index = after; continue
            }
            if (source[index] == '*' || source[index] == '_') {
                val marker = source[index]; var end = index + 1; while (end < source.length && source[end] == marker) end++
                flush(index); append(NativeMarkdownNode.Kind.TEXT, index, end); index = end; plainStart = index; continue
            }
            index++
        }
        var end = source.length
        if (trimTrailingWhitespace) while (end > plainStart && horizontal(source[end - 1])) end--
        flush(end)
        return NativeMarkdownEmphasisParser.resolve(result, source, offset)
    }
    private fun autolinkDestination(content: String): String? {
        if (content.isEmpty() || content.any { it.isWhitespace() || it == '<' || it == '>' }) return null
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]{1,31}:").containsMatchIn(content)) return content
        if (Regex("^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$").matches(content)) return "mailto:$content"
        return null
    }
    private val bareExpression = Regex("^(?:(?:https?|ftp)://|www\\.)[^\\s<>]+|^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+")
    private fun bareAutolink(source: String, start: Int): Pair<Int, String>? {
        if (start !in source.indices || !source[start].isLetterOrDigit()) return null
        var spelling = bareExpression.find(source.substring(start))?.value ?: return null
        spelling = spelling.replace(Regex("&[A-Za-z0-9]+;$"), "")
        spelling = spelling.trimEnd('.', ',', '!', '?', ';', ':')
        while (spelling.endsWith(')') && spelling.count { it == ')' } > spelling.count { it == '(' }) spelling = spelling.dropLast(1)
        if (spelling.isEmpty()) return null
        val destination = when {
            spelling.startsWith("www.") -> {
                val labels = spelling.drop(4).takeWhile { it !in "/:?#" }.split('.')
                if (labels.size < 2 || labels.any { it.isEmpty() } || labels.takeLast(2).any { '_' in it }) return null
                "http://$spelling"
            }
            '@' in spelling && "://" !in spelling -> {
                if (spelling.last() !in 'A'..'Z' && spelling.last() !in 'a'..'z' && spelling.last() !in '0'..'9' || '_' in spelling.substringAfterLast('@')) return null
                "mailto:$spelling"
            }
            else -> {
                // CommonMark permits escaped paths that java.net.URI rejects; validate the authority only.
                val authority = spelling.substringAfter("://", "").takeWhile { it !in "/?#" }
                if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null
                spelling
            }
        }
        return start + spelling.length to destination
    }
}
