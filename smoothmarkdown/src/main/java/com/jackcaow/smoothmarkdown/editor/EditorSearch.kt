package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** Non-overlapping search ranges in the original UTF-16 text shared by both editor modes. */
internal object EditorSearch {
    @JvmSynthetic
    fun ranges(text: String, query: String, caseSensitive: Boolean): Sequence<TextRange> = sequence {
        if (query.isEmpty()) return@sequence
        // İ is the one code point whose Unicode default lowercase expands to multiple code points.
        val lowerQuery = if (!caseSensitive && ('İ' in query || '\u0307' in query)) query.lowercase() else null
        val lowerText = lowerQuery?.let { text.lowercase() }
        var mappedUntil = 0
        var extraUnits = 0
        var cursor = 0
        while (cursor < text.length) {
            val at = if (lowerQuery == null) text.indexOf(query, cursor, ignoreCase = !caseSensitive)
                else text.indexOf(query.first(), cursor, ignoreCase = true)
            if (at < 0) break
            val end = if (lowerQuery == null || lowerText == null) at + query.length else {
                while (mappedUntil < at) {
                    if (text[mappedUntil++] == 'İ') extraUnits++
                }
                // Prefer the full dotted spelling so replacement also removes the combining dot.
                val fullEnd = if (lowerText.startsWith(lowerQuery, at + extraUnits))
                    originalEnd(text, at, lowerQuery.length) else null
                fullEnd ?: if (text.regionMatches(at, query, 0, query.length, ignoreCase = true)) at + query.length else null
            }
            if (end != null && safeUtf16Boundary(text, at) == at && safeUtf16Boundary(text, end) == end) {
                yield(TextRange(at, end))
                cursor = end
            } else {
                cursor = at + 1
            }
        }
    }

    /** Map a matched lowercase span back to source, rejecting a partial final expansion. */
    private fun originalEnd(text: String, start: Int, lowerLength: Int): Int? {
        var end = start
        var remaining = lowerLength
        while (remaining > 0) {
            remaining -= if (text[end++] == 'İ') 2 else 1
        }
        return if (remaining == 0) end else null
    }
}
