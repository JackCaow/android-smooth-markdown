package com.jackcaow.smoothmarkdown.editor

/** Editable display text for the first source-backed formatted block types. */
internal object MarkdownFormattedBlock {
    fun text(block: MarkdownDocumentBlock): String? = when (block.kind) {
        MarkdownBlockKind.PARAGRAPH -> block.source
        MarkdownBlockKind.HEADING -> headingMatch(block.source)?.let { match ->
            block.source.substring(match.value.length).removeSuffix(headingClosing(block.source.substring(match.value.length)))
        }
        MarkdownBlockKind.CODE -> fencedParts(block.source)?.body
        else -> null
    }

    fun markdown(block: MarkdownDocumentBlock, text: String): String? = when (block.kind) {
        MarkdownBlockKind.PARAGRAPH -> text
        MarkdownBlockKind.HEADING -> headingMatch(block.source)?.let { match ->
            match.value + text + headingClosing(block.source.substring(match.value.length))
        }
        MarkdownBlockKind.CODE -> fencedParts(block.source)?.let { parts ->
            parts.open + text + (if (text.isNotEmpty() && !text.endsWith("\n") && !text.endsWith("\r")) parts.newline else "") + parts.close
        }
        else -> null
    }

    private fun headingMatch(source: String) = Regex("^ {0,3}#{1,6}[ \\t]+").find(source)

    private fun headingClosing(body: String): String = Regex("[ \\t]+#+[ \\t]*$").find(body)?.value.orEmpty()

    private data class FencedParts(val open: String, val body: String, val close: String, val newline: String)

    private fun fencedParts(source: String): FencedParts? {
        val firstNewline = source.indexOf('\n')
        if (firstNewline < 0) return null
        val open = source.substring(0, firstNewline + 1)
        val opening = Regex("^ {0,3}(`{3,}|~{3,})[^\\r\\n]*\\r?\\n$").matchEntire(open) ?: return null
        val marker = opening.groupValues[1]
        val finalLineStart = source.lastIndexOf('\n') + 1
        if (finalLineStart <= firstNewline) return null
        val close = source.substring(finalLineStart)
        val closing = Regex("^ {0,3}(`{3,}|~{3,})[ \\t]*$").matchEntire(close) ?: return null
        val closingMarker = closing.groupValues[1]
        if (closingMarker.first() != marker.first() || closingMarker.length < marker.length) return null
        val newline = if (open.endsWith("\r\n")) "\r\n" else "\n"
        val rawBody = source.substring(firstNewline + 1, finalLineStart)
        val body = when {
            rawBody.endsWith("\r\n") -> rawBody.dropLast(2)
            rawBody.endsWith("\n") -> rawBody.dropLast(1)
            else -> rawBody
        }
        return FencedParts(open, body, close, newline)
    }
}
