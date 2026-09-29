package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** The typed, still-open `[[query` range in a formatted text block. */
internal data class WikilinkTrigger(val range: TextRange, val query: String)

internal object WikilinkAutocomplete {
    fun match(visible: String, selection: TextRange, marks: List<InlineMark> = emptyList()): WikilinkTrigger? {
        if (!selection.collapsed || selection.min !in 0..visible.length) return null
        val cursor = selection.min
        val open = visible.lastIndexOf("[[", cursor.coerceAtMost(visible.lastIndex))
        val close = visible.lastIndexOf("]]", cursor.coerceAtMost(visible.lastIndex))
        if (open < 0 || open <= close || open + 2 > cursor) return null
        if (open > 0 && visible[open - 1] !in " \n\r") return null
        val query = visible.substring(open + 2, cursor)
        if ('\n' in query || '\r' in query) return null
        if (marks.any { it.kind == InlineMarkKind.CODE && open >= it.range.min && cursor <= it.range.max }) return null
        return WikilinkTrigger(TextRange(open, cursor), query)
    }

    fun suggestions(trigger: WikilinkTrigger, titles: List<String>): List<String> =
        titles.filter { it.contains(trigger.query, ignoreCase = true) }.take(10)
}
