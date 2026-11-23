package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** Non-overlapping search ranges in the original UTF-16 text shared by both editor modes. */
internal object EditorSearch {
    @JvmSynthetic
    fun ranges(text: String, query: String, caseSensitive: Boolean): Sequence<TextRange> = sequence {
        if (query.isEmpty()) return@sequence
        // İ is the one code point whose Unicode default lowercase expands to multiple code points.
        val expanded = if (!caseSensitive && ('İ' in query || '\u0307' in query))
            query.replace("İ", "i\u0307") else null
        var cursor = 0
        while (cursor < text.length) {
            val at = if (expanded == null) text.indexOf(query, cursor, ignoreCase = !caseSensitive)
                else text.indexOf(query.first(), cursor, ignoreCase = true)
            if (at < 0) break
            val end = if (expanded == null) at + query.length else {
                // Prefer the full dotted spelling so replacement also removes the combining dot.
                expandedEnd(text, at, expanded)
                    ?: if (text.regionMatches(at, query, 0, query.length, ignoreCase = true)) at + query.length else null
            }
            if (end != null && safeUtf16Boundary(text, at) == at && safeUtf16Boundary(text, end) == end) {
                yield(TextRange(at, end))
                cursor = end
            } else {
                cursor = at + 1
            }
        }
    }

    /** Compare complete source code points, keeping both ends in the original coordinate space. */
    private fun expandedEnd(text: String, start: Int, expanded: String): Int? {
        var sourceOffset = start
        var queryOffset = 0
        while (queryOffset < expanded.length && sourceOffset < text.length) {
            if (text[sourceOffset] == 'İ') {
                if (!expanded.regionMatches(queryOffset, "i\u0307", 0, 2, ignoreCase = true)) return null
                sourceOffset++
                queryOffset += 2
            } else {
                val width = Character.charCount(text.codePointAt(sourceOffset))
                if (!text.regionMatches(sourceOffset, expanded, queryOffset, width, ignoreCase = true)) return null
                sourceOffset += width
                queryOffset += width
            }
        }
        return if (queryOffset == expanded.length) sourceOffset else null
    }
}
