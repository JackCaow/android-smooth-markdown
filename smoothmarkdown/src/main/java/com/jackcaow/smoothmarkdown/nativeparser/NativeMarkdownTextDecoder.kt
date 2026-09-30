package com.jackcaow.smoothmarkdown.nativeparser

internal object NativeMarkdownTextDecoder {
    private val punctuation = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    fun decode(source: String): String = buildString {
        var i = 0
        while (i < source.length) {
            if (source[i] == '\\' && i + 1 < source.length && source[i + 1] in punctuation) {
                append(source[i + 1]); i += 2; continue
            }
            if (source[i] == '&') {
                var end = i + 1
                while (end < source.length && end - i <= 33 && source[end] != ';' && !source[end].isWhitespace()) end++
                if (end < source.length && source[end] == ';') {
                    val value = reference(source.substring(i + 1, end))
                    if (value != null) { append(value); i = end + 1; continue }
                }
            }
            append(source[i++])
        }
    }
    fun codeSpan(source: String): String {
        val count = source.takeWhile { it == '`' }.length
        if (count == 0 || source.length < count * 2) return source
        val value = source.substring(count, source.length - count).replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return if (value.length >= 2 && value.first() == ' ' && value.last() == ' ' && value.any { it != ' ' }) value.substring(1, value.length - 1) else value
    }
    private fun reference(token: String): String? {
        if (!token.startsWith('#')) return NativeHTMLEntityTable.values[token]
        val hex = token.startsWith("#x", true)
        val digits = token.substring(if (hex) 2 else 1)
        if (digits.isEmpty() || digits.length > (if (hex) 6 else 7) || digits.any { if (hex) it !in "0123456789abcdefABCDEF" else it !in '0'..'9' }) return null
        val value = digits.toIntOrNull(if (hex) 16 else 10) ?: 0xFFFD
        return if (value == 0 || value in 0xD800..0xDFFF || value > 0x10FFFF) "\uFFFD" else String(Character.toChars(value))
    }
}
