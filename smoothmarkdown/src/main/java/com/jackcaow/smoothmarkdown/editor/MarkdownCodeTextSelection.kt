package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.ParserPluginRegistry

/** Source-backed character endpoints when at least one endpoint is inside a fenced code body. */
internal class MarkdownCodeTextSelection private constructor(
    private val source: String,
    private val document: MarkdownDocument,
    private val firstIndex: Int,
    private val lastIndex: Int,
    private val first: Endpoint,
    private val last: Endpoint,
    private val plugins: ParserPluginRegistry?,
) {
    private data class Endpoint(
        val block: MarkdownDocumentBlock,
        val visible: String,
        val offset: Int,
        val inline: MarkdownInlineEditing?,
    ) {
        fun slice(start: Int, end: Int): String? {
            if (start == end) return ""
            val body = if (inline != null) inline.sliceVisibleRange(TextRange(start, end)) ?: return null
                else visible.substring(start, end)
            // Keep a complete code block byte-for-byte, including its fence trivia.
            if (block.kind == MarkdownBlockKind.CODE && start == 0 && end == visible.length) return block.source
            return MarkdownFormattedBlock.markdown(block, body)
        }

        fun before(): String? = inline?.splitVisibleRange(TextRange(offset, visible.length))?.before
            ?: if (inline == null) visible.substring(0, offset) else null

        fun after(): String? = inline?.splitVisibleRange(TextRange(0, offset))?.after
            ?: if (inline == null) visible.substring(offset) else null

        fun markdown(body: String): String? = MarkdownFormattedBlock.markdown(block, body)
    }

    data class Edit(val range: TextRange, val replacement: String, val caret: Int)

    fun copy(): String? {
        val fragments = (firstIndex..lastIndex).mapNotNull { index ->
            val block = document.blocks[index]
            val markdown = when {
                firstIndex == lastIndex -> first.slice(first.offset, last.offset)
                index == firstIndex -> first.slice(first.offset, first.visible.length)
                index == lastIndex -> last.slice(0, last.offset)
                else -> block.source
            } ?: return null
            if (markdown.isEmpty()) null else block to markdown
        }
        if (fragments.isEmpty()) return null
        return buildString {
            fragments.forEachIndexed { index, (block, markdown) ->
                if (index > 0) append(source.substring(fragments[index - 1].first.range.max, block.range.min))
                append(markdown)
            }
        }
    }

    fun edit(markdown: String): Edit? {
        if (!wellFormedUtf16(markdown)) return null
        val inserted = if (markdown.isEmpty()) emptyList() else MarkdownDocumentCodec.parse(markdown, plugins).blocks
        if (markdown.isNotEmpty() && (inserted.isEmpty() || inserted.first().range.min != 0 ||
                inserted.last().range.max != markdown.length)) return null
        val left = first.before() ?: return null
        val right = last.after() ?: return null
        val expected = mutableListOf<Pair<MarkdownBlockKind, String>>()
        val pieces = mutableListOf<String>()
        // A plain partial edit belongs to the existing code body. A whole-body Markdown
        // replacement may intentionally replace the fenced block with parsed blocks.
        val editsCodeBody = firstIndex == lastIndex && first.block.kind == MarkdownBlockKind.CODE &&
            (markdown.isEmpty() || first.offset > 0 || last.offset < first.visible.length) &&
            (markdown.isEmpty() || (inserted.size == 1 && inserted.single().kind == MarkdownBlockKind.PARAGRAPH))
        if (editsCodeBody) {
            val combined = left + markdown + right
            val replacement = first.markdown(combined) ?: return null
            expected += first.block.kind to replacement
            pieces += replacement
        } else {
            if (left.isNotEmpty()) {
                val block = first.markdown(left) ?: return null
                expected += first.block.kind to block
                pieces += block
            }
            if (inserted.isNotEmpty()) {
                expected += inserted.map { it.kind to it.source }
                pieces += markdown
            }
            if (right.isNotEmpty()) {
                val block = last.markdown(right) ?: return null
                expected += last.block.kind to block
                pieces += block
            }
        }
        val between = source.substring(first.block.range.max, last.block.range.min.coerceAtLeast(first.block.range.max))
        val separator = if (between.contains("\r\n") || first.block.source.contains("\r\n") || last.block.source.contains("\r\n")) "\r\n\r\n" else "\n\n"
        val replacement = pieces.joinToString(separator)
        val range = TextRange(first.block.range.min, last.block.range.max)
        if (replacement == source.substring(range.min, range.max)) return null
        val parsed = MarkdownDocumentCodec.parse(source.replaceRange(range.min, range.max, replacement), plugins).blocks
        val before = document.blocks.take(firstIndex)
        val after = document.blocks.drop(lastIndex + 1)
        if (parsed.size != before.size + expected.size + after.size ||
            before.zip(parsed).any { (old, next) -> old.kind != next.kind || old.source != next.source } ||
            expected.zip(parsed.drop(before.size)).any { (want, next) -> want.first != next.kind || want.second != next.source } ||
            after.zip(parsed.takeLast(after.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return null
        val caret = when {
            editsCodeBody -> replacement.length - (left + markdown + right).length + left.length + markdown.length
            markdown.isNotEmpty() -> (if (left.isNotEmpty()) pieces.first().length + separator.length else 0) + markdown.length
            left.isNotEmpty() -> pieces.first().length
            else -> 0
        }
        return Edit(range, replacement, caret.coerceIn(0, replacement.length))
    }

    companion object {
        fun resolve(source: String, document: MarkdownDocument, selected: MarkdownFormattedTextSelection,
                    enableWikilinks: Boolean, plugins: ParserPluginRegistry?): MarkdownCodeTextSelection? {
            if (selected.source != source || selected.anchor.listPath != null || selected.focus.listPath != null ||
                selected.anchor.listLineIndex != 0 || selected.focus.listLineIndex != 0) return null
            val anchorIndex = document.blocks.indexOfFirst { it.id == selected.anchor.blockId }
            val focusIndex = document.blocks.indexOfFirst { it.id == selected.focus.blockId }
            if (anchorIndex < 0 || focusIndex < 0) return null
            val positions = listOf(anchorIndex to selected.anchor.offset, focusIndex to selected.focus.offset)
                .sortedWith(compareBy({ it.first }, { it.second }))
            if (positions[0] == positions[1]) return null
            val firstIndex = positions[0].first
            val lastIndex = positions[1].first
            if (document.blocks[firstIndex].kind != MarkdownBlockKind.CODE &&
                document.blocks[lastIndex].kind != MarkdownBlockKind.CODE) return null
            if ((firstIndex..lastIndex).any { document.blocks[it].kind !in setOf(
                    MarkdownBlockKind.CODE, MarkdownBlockKind.TABLE, MarkdownBlockKind.BULLET_LIST,
                    MarkdownBlockKind.ORDERED_LIST, MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING,
                ) }) return null
            fun endpoint(index: Int, offset: Int): Endpoint? {
                val block = document.blocks[index]
                val inline = MarkdownFormattedBlock.inline(block, enableWikilinks)
                val visible = inline?.visible ?: MarkdownFormattedBlock.text(block) ?: return null
                if (block.kind != MarkdownBlockKind.CODE && inline == null) return null
                if (offset !in 0..visible.length || safeUtf16Boundary(visible, offset) != offset ||
                    offset in 1 until visible.length && visible[offset - 1] == '\r' && visible[offset] == '\n') return null
                if (inline != null && (inline.splitVisibleRange(TextRange(0, offset)) == null ||
                            inline.splitVisibleRange(TextRange(offset, visible.length)) == null)) return null
                return Endpoint(block, visible, offset, inline)
            }
            val first = endpoint(firstIndex, positions[0].second) ?: return null
            val last = endpoint(lastIndex, positions[1].second) ?: return null
            return MarkdownCodeTextSelection(source, document, firstIndex, lastIndex, first, last, plugins)
        }

        private fun wellFormedUtf16(value: String): Boolean {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (Character.isHighSurrogate(char)) {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
                    index++
                } else if (Character.isLowSurrogate(char)) return false
                index++
            }
            return true
        }
    }
}
