package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Source-backed editing commands. Offsets use UTF-16, matching Compose selections. */
class MarkdownEditorController(initialText: String = "", historyLimit: Int = 100) {
    var mode by mutableStateOf(MarkdownEditorMode.SOURCE)
    var value by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set
    var savedText by mutableStateOf(initialText)
        private set

    private val limit = historyLimit.coerceAtLeast(0)
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var historyRevision by mutableIntStateOf(0)
    private var transactionDepth = 0
    private var transactionBefore: TextFieldValue? = null

    var text: String
        get() = value.text
        set(next) {
            if (next != value.text) replaceRange(0, value.text.length, next)
        }

    val selection: TextRange get() = value.selection
    val selectedText: String get() = value.text.substring(selection.min, selection.max)
    val isDirty: Boolean get() = value.text != savedText
    val canUndo: Boolean get() = historyRevision.let { undoStack.isNotEmpty() }
    val canRedo: Boolean get() = historyRevision.let { redoStack.isNotEmpty() }

    fun markSaved(saved: String = value.text) { savedText = saved }
    fun clearHistory() { undoStack.clear(); redoStack.clear(); historyRevision++ }

    fun updateFromInput(next: TextFieldValue) {
        if (next.text != value.text) recordUndo(value)
        value = next
    }

    fun setSelection(start: Int, end: Int = start) {
        value = value.copy(selection = TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        redoStack.addLast(value)
        value = undoStack.removeLast()
        historyRevision++
        return true
    }

    fun redo(): Boolean {
        if (!canRedo) return false
        push(undoStack, value)
        value = redoStack.removeLast()
        historyRevision++
        return true
    }

    fun <T> transaction(block: () -> T): T {
        if (transactionDepth == 0) transactionBefore = value
        transactionDepth++
        try { return block() } finally {
            transactionDepth--
            if (transactionDepth == 0) {
                val before = transactionBefore
                transactionBefore = null
                if (before != null && before.text != value.text) {
                    push(undoStack, before)
                    redoStack.clear()
                    historyRevision++
                }
            }
        }
    }

    fun replaceSelection(replacement: String, selectedStart: Int? = null, selectedEnd: Int? = null) {
        replaceRange(selection.min, selection.max, replacement, selectedStart, selectedEnd)
    }

    fun replaceRange(start: Int, end: Int, replacement: String, selectedStart: Int? = null, selectedEnd: Int? = null) {
        val lower = start.coerceIn(0, text.length)
        val upper = end.coerceIn(lower, text.length)
        val updated = text.replaceRange(lower, upper, replacement)
        val caret = replacement.length
        val nextStart = lower + (selectedStart ?: caret).coerceIn(0, caret)
        val nextEnd = lower + (selectedEnd ?: selectedStart ?: caret).coerceIn(0, caret)
        updateValue(TextFieldValue(updated, TextRange(nextStart, nextEnd)))
    }

    /** A source-backed semantic snapshot for block-level editing. */
    fun semanticDocument(): MarkdownDocument = MarkdownDocumentCodec.parse(text)

    /** Replaces one complete semantic block through the existing source undo history. */
    fun replaceSemanticBlock(blockId: String, markdown: String): Boolean {
        val current = semanticDocument()
        val block = current.blockById(blockId) ?: return false
        val editor = MarkdownDocumentEditor(current.source)
        if (!editor.replaceBlockSource(blockId, markdown)) return false
        replaceRange(block.range.min, block.range.max, markdown)
        return true
    }

    fun insertMarkdown(markdown: String) = replaceSelection(markdown)

    fun insertMarkdownBlock(markdown: String) {
        val block = markdown.trim()
        if (block.isNotEmpty()) insertSeparatedBlock(block)
    }

    fun insertTable(rows: Int = 3, columns: Int = 3) {
        val rowCount = rows.coerceAtLeast(1)
        val columnCount = columns.coerceAtLeast(1)
        val header = List(columnCount) { "Column" }.joinToString(" | ")
        val separator = List(columnCount) { "---" }.joinToString(" | ")
        val body = List(rowCount - 1) { List(columnCount) { "Cell" }.joinToString(" | ") }
        insertSeparatedBlock((listOf("| $header |", "| $separator |") + body.map { "| $it |" }).joinToString("\n"))
    }

    /** Returns the GFM table under the current source selection. */
    fun tableAtSelection(): MarkdownSourceTable? = findSourceTable(text, selection.min)?.table

    fun replaceTableCellText(rowIndex: Int, columnIndex: Int, cellText: String, header: Boolean = false): Boolean =
        editSelectedTable { it.replaceCell(rowIndex, columnIndex, cellText, header) }

    fun insertTableRowBefore(rowIndex: Int): Boolean = editSelectedTable { it.insertRowBefore(rowIndex) }
    fun insertTableRowAfter(rowIndex: Int): Boolean = editSelectedTable { it.insertRowAfter(rowIndex) }
    fun deleteTableRow(rowIndex: Int): Boolean = editSelectedTable { it.deleteRow(rowIndex) }
    fun insertTableColumnBefore(columnIndex: Int): Boolean = editSelectedTable { it.insertColumnBefore(columnIndex) }
    fun insertTableColumnAfter(columnIndex: Int): Boolean = editSelectedTable { it.insertColumnAfter(columnIndex) }
    fun deleteTableColumn(columnIndex: Int): Boolean = editSelectedTable { it.deleteColumn(columnIndex) }
    fun setTableColumnAlignment(columnIndex: Int, alignment: MarkdownTableAlignment?): Boolean =
        editSelectedTable { it.setColumnAlignment(columnIndex, alignment) }

    fun deleteTableAtSelection(): Boolean {
        val located = findSourceTable(text, selection.min) ?: return false
        replaceRange(located.range.min, located.range.max, "")
        return true
    }

    private fun editSelectedTable(transform: (MarkdownSourceTable) -> MarkdownSourceTable): Boolean {
        val located = findSourceTable(text, selection.min) ?: return false
        val updated = transform(located.table)
        if (updated != located.table) {
            val replacement = updated.toMarkdown()
            val relativeCaret = (selection.min - located.range.min).coerceIn(0, replacement.length)
            replaceRange(located.range.min, located.range.max, replacement, relativeCaret)
        }
        return true
    }

    fun findMatches(query: String, caseSensitive: Boolean = false): List<TextRange> {
        if (query.isEmpty()) return emptyList()
        val haystack = if (caseSensitive) text else text.lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        val matches = mutableListOf<TextRange>()
        var index = haystack.indexOf(needle)
        while (index >= 0 && matches.size < 500) {
            matches += TextRange(index, index + needle.length)
            index = haystack.indexOf(needle, index + needle.length)
        }
        return matches
    }

    fun selectNextMatch(query: String, caseSensitive: Boolean = false): TextRange? {
        val matches = findMatches(query, caseSensitive)
        if (matches.isEmpty()) return null
        val next = matches.firstOrNull { it.start >= selection.max } ?: matches.first()
        setSelection(next.start, next.end)
        return next
    }

    fun applyCommand(command: MarkdownEditorCommand, argument: String? = null) {
        when (command) {
            MarkdownEditorCommand.PARAGRAPH -> transformLines { line ->
                listOf(
                    Regex("^\\s{0,3}#{1,6}\\s+"),
                    Regex("^\\s{0,3}>\\s?"),
                    Regex("^\\s{0,3}[-*+]\\s+\\[[ xX]\\]\\s+"),
                    Regex("^\\s{0,3}[-*+]\\s+"),
                    Regex("^\\s{0,3}\\d+[.)]\\s+"),
                ).fold(line) { value, pattern -> value.replaceFirst(pattern, "") }
            }
            MarkdownEditorCommand.BOLD -> wrap("**", "**", "bold")
            MarkdownEditorCommand.ITALIC -> wrap("*", "*", "italic")
            MarkdownEditorCommand.STRIKETHROUGH -> wrap("~~", "~~", "strikethrough")
            MarkdownEditorCommand.INLINE_CODE -> wrap("`", "`", "code")
            MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3, MarkdownEditorCommand.HEADING4,
            MarkdownEditorCommand.HEADING5, MarkdownEditorCommand.HEADING6 -> {
                val level = command.ordinal - MarkdownEditorCommand.HEADING1.ordinal + 1
                transformLines { "${"#".repeat(level)} " + it.replace(Regex("^ {0,3}#{1,6} +"), "") }
            }
            MarkdownEditorCommand.UNORDERED_LIST -> transformLines { "- $it" }
            MarkdownEditorCommand.ORDERED_LIST -> transformLinesIndexed { index, line -> "${index + 1}. $line" }
            MarkdownEditorCommand.TASK_LIST -> transformLines { "- [ ] $it" }
            MarkdownEditorCommand.BLOCKQUOTE -> transformLines { "> $it" }
            MarkdownEditorCommand.CODE_BLOCK -> wrapBlock("```" + (argument ?: ""), "```", "code")
            MarkdownEditorCommand.LINK -> wrap("[", "](${argument?.takeIf { it.isNotEmpty() } ?: "https://example.com"})", "link")
            MarkdownEditorCommand.IMAGE -> wrap("![", "](${argument?.takeIf { it.isNotEmpty() } ?: "image-url"})", "alt text")
            MarkdownEditorCommand.TABLE -> insertTable()
            MarkdownEditorCommand.BLOCK_MATH -> wrapBlock("$$", "$$", "E = mc^2")
            MarkdownEditorCommand.MERMAID_DIAGRAM -> insertSeparatedBlock("```mermaid\nflowchart TD\n  A[Start] --> B[End]\n```")
            MarkdownEditorCommand.HORIZONTAL_RULE -> insertSeparatedBlock("---")
            MarkdownEditorCommand.WIKILINK -> wrap("[[", "]]", "Note")
        }
    }

    private fun updateValue(next: TextFieldValue) {
        if (next.text != value.text) recordUndo(value)
        value = next
    }

    private fun recordUndo(previous: TextFieldValue) {
        if (transactionDepth == 0) {
            push(undoStack, previous)
            redoStack.clear()
            historyRevision++
        }
    }

    private fun push(stack: ArrayDeque<TextFieldValue>, snapshot: TextFieldValue) {
        if (limit == 0) return
        if (stack.size == limit) stack.removeFirst()
        stack.addLast(snapshot)
    }

    private fun wrap(prefix: String, suffix: String, placeholder: String) {
        val body = selectedText.ifEmpty { placeholder }
        replaceSelection(prefix + body + suffix, prefix.length, prefix.length + body.length)
    }

    private fun wrapBlock(opening: String, closing: String, placeholder: String) {
        val body = selectedText.ifEmpty { placeholder }
        replaceSelection("$opening\n$body\n$closing", opening.length + 1, opening.length + 1 + body.length)
    }

    private fun insertSeparatedBlock(block: String) {
        val before = text.substring(0, selection.min)
        val after = text.substring(selection.max)
        val leading = if (before.isEmpty() || before.endsWith("\n\n")) "" else if (before.endsWith("\n")) "\n" else "\n\n"
        val trailing = if (after.isEmpty() || after.startsWith("\n\n")) "" else if (after.startsWith("\n")) "\n" else "\n\n"
        replaceSelection(leading + block + trailing)
    }

    private fun transformLines(transform: (String) -> String) = transformLinesIndexed { _, line -> transform(line) }

    private fun transformLinesIndexed(transform: (Int, String) -> String) {
        val start = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0)).let { if (selection.min == 0) 0 else it + 1 }
        val effectiveEnd = if (selection.max > selection.min && text[selection.max - 1] == '\n') selection.max - 1 else selection.max
        val nextNewline = text.indexOf('\n', effectiveEnd)
        val end = if (nextNewline < 0) text.length else nextNewline
        val replacement = text.substring(start, end).split('\n').mapIndexed(transform).joinToString("\n")
        replaceRange(start, end, replacement, 0, replacement.length)
    }
}

enum class MarkdownEditorCommand {
    PARAGRAPH, BOLD, ITALIC, STRIKETHROUGH, INLINE_CODE,
    HEADING1, HEADING2, HEADING3, HEADING4, HEADING5, HEADING6,
    UNORDERED_LIST, ORDERED_LIST, TASK_LIST, BLOCKQUOTE, CODE_BLOCK,
    LINK, IMAGE, TABLE, BLOCK_MATH, MERMAID_DIAGRAM, HORIZONTAL_RULE, WIKILINK,
}

enum class MarkdownEditorMode { SOURCE, PREVIEW, SPLIT }
