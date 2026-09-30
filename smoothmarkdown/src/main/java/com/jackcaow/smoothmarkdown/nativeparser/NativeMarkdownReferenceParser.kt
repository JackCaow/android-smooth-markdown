package com.jackcaow.smoothmarkdown.nativeparser

internal object NativeMarkdownReferenceParser {
    data class Definition(val label: String, val destination: String, val title: String?, val lineCount: Int)
    fun normalize(label: String) = NativeMarkdownInlineParser.normalizeReference(label)
    fun parse(source: String): Definition? {
        var cursor = 0
        fun horizontal(c: Char) = c == ' ' || c == '\t'
        fun count(end: Int) = source.take(end).count { it == '\n' } + 1
        while (cursor < source.length && source[cursor] == ' ') cursor++
        if (cursor > 3 || source.getOrNull(cursor) != '[') return null
        val labelStart = ++cursor
        while (cursor < source.length && source[cursor] != ']') {
            if (source[cursor] == '\\' && cursor + 1 < source.length) { cursor += 2; continue }
            if (source[cursor] == '[' || (source[cursor] == '\n' && source.drop(cursor + 1).dropWhile(::horizontal).firstOrNull() == '\n')) return null
            cursor++
        }
        if (cursor >= source.length || cursor - labelStart !in 1..999) return null
        val label = source.substring(labelStart, cursor)
        if (label.isBlank() || source.getOrNull(++cursor) != ':') return null
        cursor++; var endings = 0
        while (cursor < source.length && (horizontal(source[cursor]) || source[cursor] == '\n')) { if (source[cursor++] == '\n') endings++; if (endings > 1) return null }
        if (cursor >= source.length) return null
        val destinationStart: Int; val destinationEnd: Int
        if (source[cursor] == '<') {
            destinationStart = ++cursor
            while (cursor < source.length && source[cursor] != '>') {
                if (source[cursor] == '<' || source[cursor] == '\n') return null
                cursor += if (source[cursor] == '\\' && cursor + 1 < source.length && source[cursor + 1] != '\n') 2 else 1
            }
            if (cursor >= source.length) return null
            destinationEnd = cursor++
        } else {
            destinationStart = cursor; var depth = 0
            while (cursor < source.length && !horizontal(source[cursor]) && source[cursor] != '\n') {
                val c = source[cursor]
                if (c == '<' || c == '>' || c.code < 32) return null
                if (c == '\\' && cursor + 1 < source.length && source[cursor + 1] != '\n') { cursor += 2; continue }
                if (c == '(') depth++; if (c == ')') depth--
                if (depth < 0 || depth > 32) return null
                cursor++
            }
            if (cursor <= destinationStart || depth != 0) return null
            destinationEnd = cursor
        }
        val destination = NativeMarkdownTextDecoder.decode(source.substring(destinationStart, destinationEnd))
        val separator = cursor
        while (cursor < source.length && horizontal(source[cursor])) cursor++
        val destinationLineEnd = cursor
        val nextLine = source.getOrNull(cursor) == '\n'
        if (nextLine) { cursor++; while (cursor < source.length && horizontal(source[cursor])) cursor++ }
        fun withoutTitle(): Definition? = if (destinationLineEnd == source.length || source[destinationLineEnd] == '\n') Definition(label, destination, null, count(destinationLineEnd)) else null
        if (cursor >= source.length || cursor <= separator || source[cursor] !in "\"'(") return withoutTitle()
        val opening = source[cursor++]; val closing = if (opening == '(') ')' else opening
        val titleStart = cursor; var newline = false
        while (cursor < source.length && source[cursor] != closing) {
            val c = source[cursor]
            if (c == '\\' && cursor + 1 < source.length) { cursor += 2; newline = false; continue }
            if (opening == '(' && c == '(') return if (nextLine) withoutTitle() else null
            if (c == '\n') { if (newline) return if (nextLine) withoutTitle() else null; newline = true }
            else if (!horizontal(c)) newline = false
            cursor++
        }
        if (cursor >= source.length) return if (nextLine) withoutTitle() else null
        val title = NativeMarkdownTextDecoder.decode(source.substring(titleStart, cursor++))
        while (cursor < source.length && horizontal(source[cursor])) cursor++
        if (cursor != source.length && source[cursor] != '\n') return if (nextLine) withoutTitle() else null
        return Definition(label, destination, title, count(cursor))
    }
}
