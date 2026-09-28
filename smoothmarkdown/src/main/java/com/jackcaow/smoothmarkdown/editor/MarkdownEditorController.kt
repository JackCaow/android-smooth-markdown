package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class MarkdownDocumentBlockSelection(
    val source: String,
    val anchorIndex: Int,
    val extentIndex: Int,
) {
    val firstIndex: Int get() = minOf(anchorIndex, extentIndex)
    val lastIndex: Int get() = maxOf(anchorIndex, extentIndex)
}

/** Source-backed editing commands. Offsets use UTF-16, matching Compose selections. */
class MarkdownEditorController(initialText: String = "", historyLimit: Int = 100) {
    var mode by mutableStateOf(MarkdownEditorMode.SOURCE)
    /** Controls editor parsing, suggestions and commands for Scratch-style note links. */
    var enableWikilinks by mutableStateOf(true)
    var value by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set
    var savedText by mutableStateOf(initialText)
        private set
    internal var activeFormattedBlockId by mutableStateOf<String?>(null)
        private set
    internal var formattedSelection by mutableStateOf(TextRange.Zero)
        private set
    internal var formattedComposition by mutableStateOf<TextRange?>(null)
        private set
    internal var activeFormattedListPath by mutableStateOf<List<Int>?>(null)
        private set
    internal var activeFormattedListLine by mutableIntStateOf(0)
        private set
    internal var formattedListSelection by mutableStateOf(TextRange.Zero)
        private set
    internal var formattedListComposition by mutableStateOf<TextRange?>(null)
        private set
    internal var formattedListFocusTarget by mutableStateOf<Pair<List<Int>, Int>?>(null)
        private set
    internal var formattedBlockFocusTarget by mutableStateOf<String?>(null)
        private set
    internal data class PendingListExit(val offset: Int, val beforeItemCount: Int)
    internal var pendingListExit by mutableStateOf<PendingListExit?>(null)
        private set
    /** A contiguous range of top-level formatted blocks, tied to one source revision. */
    var formattedBlockSelection by mutableStateOf<MarkdownDocumentBlockSelection?>(null)
        private set

    private val limit = historyLimit.coerceAtLeast(0)
    private data class EditorSnapshot(
        val value: TextFieldValue,
        val blockId: String?,
        val selection: TextRange,
        val composition: TextRange?,
        val listPath: List<Int>?,
        val listLine: Int,
        val listSelection: TextRange,
        val listComposition: TextRange?,
        val pendingListExit: PendingListExit?,
        val blockSelection: MarkdownDocumentBlockSelection?,
    )

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var historyRevision by mutableIntStateOf(0)
    private var transactionDepth = 0
    private var transactionBefore: EditorSnapshot? = null

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
        if (next.text != value.text) {
            recordUndo(value)
            pendingListExit = null
            formattedBlockSelection = null
        }
        value = next
    }

    fun setSelection(start: Int, end: Int = start) {
        value = value.copy(selection = TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))
    }

    internal fun setFormattedSelection(blockId: String, selection: TextRange, composition: TextRange? = null) {
        activeFormattedBlockId = blockId
        formattedSelection = selection
        formattedComposition = composition
        activeFormattedListPath = null
    }

    internal fun setFormattedListSelection(blockId: String, path: List<Int>, lineIndex: Int, selection: TextRange, composition: TextRange? = null) {
        activeFormattedBlockId = blockId
        activeFormattedListPath = path
        activeFormattedListLine = lineIndex
        formattedListSelection = selection
        formattedListComposition = composition
    }

    internal fun clearFormattedListFocusTarget(path: List<Int>, lineIndex: Int) {
        if (formattedListFocusTarget == (path to lineIndex)) formattedListFocusTarget = null
    }

    internal fun clearFormattedBlockFocusTarget(blockId: String) {
        if (formattedBlockFocusTarget == blockId) formattedBlockFocusTarget = null
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        redoStack.addLast(snapshot())
        restore(undoStack.removeLast())
        historyRevision++
        return true
    }

    fun redo(): Boolean {
        if (!canRedo) return false
        push(undoStack, snapshot())
        restore(redoStack.removeLast())
        historyRevision++
        return true
    }

    fun <T> transaction(block: () -> T): T {
        if (transactionDepth == 0) transactionBefore = snapshot()
        transactionDepth++
        try { return block() } finally {
            transactionDepth--
            if (transactionDepth == 0) {
                val before = transactionBefore
                transactionBefore = null
                if (before != null && before.value.text != value.text) {
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

    /** Tap one block to anchor; tap another to extend the inclusive range. */
    fun selectFormattedBlock(blockId: String): Boolean {
        val document = semanticDocument()
        val index = document.blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return false
        val old = formattedBlockSelection?.takeIf { it.source == text }
        formattedBlockSelection = if (old == null) MarkdownDocumentBlockSelection(text, index, index)
            else old.copy(extentIndex = index)
        return true
    }

    fun clearFormattedBlockSelection() { formattedBlockSelection = null }

    /** Includes the exact source between the first and last selected blocks. */
    fun copyFormattedBlockSelectionAsMarkdown(): String? {
        val document = selectedFormattedDocument() ?: return null
        val selection = formattedBlockSelection ?: return null
        val first = document.blocks[selection.firstIndex]
        val last = document.blocks[selection.lastIndex]
        return text.substring(first.range.min, last.range.max)
    }

    fun deleteFormattedBlockSelection(): Boolean = replaceFormattedBlockSelectionWithMarkdown("")

    /** One source edit and one undo step. Refuses replacements that change untouched block parsing. */
    fun replaceFormattedBlockSelectionWithMarkdown(markdown: String): Boolean {
        val document = selectedFormattedDocument() ?: return false
        val selection = formattedBlockSelection ?: return false
        val replacementBlocks = if (markdown.isEmpty()) emptyList() else {
            val parsed = MarkdownDocumentCodec.parse(markdown).blocks
            if (parsed.isEmpty() || parsed.first().range.min != 0 || parsed.last().range.max != markdown.length) return false
            parsed
        }
        val first = document.blocks[selection.firstIndex]
        val last = document.blocks[selection.lastIndex]
        val candidate = text.replaceRange(first.range.min, last.range.max, markdown)
        val reparsed = MarkdownDocumentCodec.parse(candidate).blocks
        val before = document.blocks.take(selection.firstIndex)
        val after = document.blocks.drop(selection.lastIndex + 1)
        if (reparsed.size != before.size + replacementBlocks.size + after.size) return false
        if (before.zip(reparsed).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        if (replacementBlocks.zip(reparsed.drop(before.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        if (after.zip(reparsed.takeLast(after.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        replaceRange(first.range.min, last.range.max, markdown)
        return true
    }

    private fun selectedFormattedDocument(): MarkdownDocument? {
        val selection = formattedBlockSelection ?: return null
        if (selection.source != text) return null
        val document = semanticDocument()
        return document.takeIf { selection.firstIndex >= 0 && selection.lastIndex < it.blocks.size }
    }

    /** Replaces one complete semantic block through the existing source undo history. */
    fun replaceSemanticBlock(blockId: String, markdown: String): Boolean {
        val current = semanticDocument()
        val block = current.blockById(blockId) ?: return false
        val editor = MarkdownDocumentEditor(current.source)
        if (!editor.replaceBlockSource(blockId, markdown)) return false
        replaceRange(block.range.min, block.range.max, markdown)
        return true
    }

    /** Changes the visible text of a supported formatted block using source history. */
    fun replaceFormattedBlockText(blockId: String, visibleText: String): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val markdown = MarkdownFormattedBlock.markdown(block, visibleText) ?: return false
        return replaceSemanticBlock(blockId, markdown)
    }

    /** Applies a visible inline edit without rewriting untouched Markdown delimiters. */
    internal fun replaceFormattedInlineText(blockId: String, visibleText: String, selection: TextRange, composition: TextRange? = null): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val model = MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return false
        val body = model.replaceVisible(visibleText) ?: return false
        val markdown = MarkdownFormattedBlock.markdown(block, body) ?: return false
        if (markdown != block.source && !replaceSemanticBlock(blockId, markdown)) return false
        setFormattedSelection(blockId, selection, composition)
        return true
    }

    internal fun applyFormattedInlineMark(command: MarkdownEditorCommand, destination: String? = null): Boolean {
        val blockId = activeFormattedBlockId ?: return false
        val block = semanticDocument().blockById(blockId) ?: return false
        if (command == MarkdownEditorCommand.WIKILINK && !enableWikilinks) return false
        val model = MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return false
        val kind = when (command) {
            MarkdownEditorCommand.BOLD -> InlineMarkKind.BOLD
            MarkdownEditorCommand.ITALIC -> InlineMarkKind.ITALIC
            MarkdownEditorCommand.LINK -> InlineMarkKind.LINK
            MarkdownEditorCommand.INLINE_CODE -> InlineMarkKind.CODE
            MarkdownEditorCommand.WIKILINK -> InlineMarkKind.WIKILINK
            else -> return false
        }
        val body = model.wrap(formattedSelection, kind, destination) ?: return false
        val markdown = MarkdownFormattedBlock.markdown(block, body) ?: return false
        if (!replaceSemanticBlock(blockId, markdown)) return false
        val placeholderLength = if (formattedSelection.collapsed) when (kind) {
            InlineMarkKind.BOLD -> 4
            InlineMarkKind.ITALIC -> 6
            InlineMarkKind.LINK -> 4
            InlineMarkKind.CODE -> 4
            InlineMarkKind.WIKILINK -> 4
        } else formattedSelection.max - formattedSelection.min
        formattedSelection = TextRange(formattedSelection.min, formattedSelection.min + placeholderLength)
        formattedComposition = null
        return true
    }

    /** Changes an ATX heading level while preserving its text and surrounding source. */
    fun setSemanticHeadingLevel(blockId: String, level: Int): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val editor = MarkdownDocumentEditor(text)
        if (!editor.setHeadingLevel(blockId, level)) return false
        val replacement = editor.document.blocks.firstOrNull { it.id == blockId }?.source ?: return false
        replaceRange(block.range.min, block.range.max, replacement)
        return true
    }

    /** Edits a table by semantic block ID, without depending on the source cursor position. */
    fun semanticTable(blockId: String): MarkdownSourceTable? {
        val block = semanticDocument().blockById(blockId) ?: return null
        if (block.kind != MarkdownBlockKind.TABLE) return null
        return MarkdownSourceTable.parse(block.source)
    }

    fun editSemanticTable(blockId: String, transform: (MarkdownSourceTable) -> MarkdownSourceTable): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        if (block.kind != MarkdownBlockKind.TABLE) return false
        val table = MarkdownSourceTable.parse(block.source) ?: return false
        val updated = transform(table)
        if (updated == table) return false
        val patch = table.sourcePatchForCell(block.source, updated)
        if (patch != null) {
            val replacement = block.source.replaceRange(patch.start, patch.end, patch.replacement)
            val priorSelection = selection
            if (!replaceSemanticBlock(blockId, replacement)) return false
            val absoluteStart = block.range.min + patch.start
            val absoluteEnd = block.range.min + patch.end
            setSelection(
                mappedPosition(priorSelection.start, absoluteStart, absoluteEnd, patch.replacement.length),
                mappedPosition(priorSelection.end, absoluteStart, absoluteEnd, patch.replacement.length),
            )
            return true
        }
        return replaceSemanticBlock(blockId, updated.toMarkdown())
    }

    /** Edits one list item's visible primary text while retaining its marker and neighboring source. */
    fun replaceFormattedListItemText(blockId: String, itemIndex: Int, visibleText: String): Boolean {
        return replaceFormattedListLineText(blockId, listOf(itemIndex), 0, visibleText)
    }

    /** Edits a direct paragraph line of a list item, including nested items and continuations. */
    fun replaceFormattedListLineText(blockId: String, itemPath: List<Int>, lineIndex: Int, visibleText: String): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val raw = list.lineContent(itemPath, lineIndex) ?: return false
        val updated = MarkdownInlineEditing.parse(raw, enableWikilinks).replaceVisible(visibleText) ?: return false
        val markdown = list.replaceLine(itemPath, lineIndex, updated) ?: return false
        return markdown != block.source && replaceSemanticBlock(blockId, markdown)
    }

    /** Toggles a task marker in one list item using the source undo history. */
    fun setFormattedTaskChecked(blockId: String, itemIndex: Int, checked: Boolean): Boolean {
        return setFormattedTaskChecked(blockId, listOf(itemIndex), checked)
    }

    fun setFormattedTaskChecked(blockId: String, itemPath: List<Int>, checked: Boolean): Boolean {
        val list = semanticDocument().blockById(blockId)?.let(MarkdownSourceList::parse) ?: return false
        val markdown = list.setChecked(itemPath, checked) ?: return false
        return replaceSemanticBlock(blockId, markdown)
    }

    /** Splits a formatted list line at its visible caret, preserving source around the edit. */
    fun splitFormattedListLine(blockId: String, itemPath: List<Int>, lineIndex: Int, visibleOffset: Int): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val item = list.item(itemPath) ?: return false
        if (lineIndex == 0 && item.lines.size == 1 && item.parts.all { it is MarkdownSourceList.Line } &&
            list.lineContent(itemPath, 0).orEmpty().isBlank()) {
            return if (itemPath.size > 1) outdentFormattedListItem(blockId, itemPath)
            else exitEmptyTopLevelListItem(block, list, itemPath)
        }
        val edit = list.split(itemPath, lineIndex, visibleOffset, enableWikilinks) ?: return false
        val target = validatedListTarget(block, edit) ?: return false
        val line = list.item(itemPath)?.lines?.getOrNull(lineIndex) ?: return false
        val rawOffset = MarkdownInlineEditing.parse(list.lineContent(itemPath, lineIndex).orEmpty(), enableWikilinks)
            .sourceOffsetAtVisible(visibleOffset) ?: return false
        val previousSelection = selection
        setSelection(block.range.min + line.start + rawOffset)
        if (!replaceSemanticBlock(blockId, edit.source)) {
            setSelection(previousSelection.start, previousSelection.end)
            return false
        }
        val targetLine = target.lines.firstOrNull() ?: return false
        val rawCaret = MarkdownInlineEditing.parse(edit.source.substring(targetLine.start, targetLine.end), enableWikilinks)
            .sourceOffsetAtVisible(0) ?: 0
        setSelection(block.range.min + targetLine.start + rawCaret)
        setFormattedListSelection(blockId, edit.targetPath, 0, TextRange.Zero)
        formattedListFocusTarget = edit.targetPath to 0
        return true
    }

    private fun exitEmptyTopLevelListItem(block: MarkdownDocumentBlock, list: MarkdownSourceList, path: List<Int>): Boolean {
        val edit = list.exitEmptyTopLevel(path) ?: return false
        val offset = block.range.min + edit.paragraphOffset
        replaceRange(block.range.min, block.range.max, edit.source)
        pendingListExit = PendingListExit(offset, edit.beforeItemCount)
        activeFormattedBlockId = null
        activeFormattedListPath = null
        formattedListFocusTarget = null
        setSelection(offset)
        return true
    }

    /** Commits text entered into the empty paragraph created by leaving a root list item. */
    internal fun completePendingListExit(visibleText: String, selection: TextRange): Boolean {
        val pending = pendingListExit ?: return false
        if (visibleText.isEmpty()) return true
        val offset = pending.offset
        replaceRange(offset, offset, visibleText)
        val paragraph = semanticDocument().blocks.firstOrNull {
            it.kind == MarkdownBlockKind.PARAGRAPH && it.range.min <= offset && offset + visibleText.length <= it.range.max
        }
        if (paragraph != null) {
            val caret = TextRange(selection.start.coerceIn(0, visibleText.length), selection.end.coerceIn(0, visibleText.length))
            setFormattedSelection(paragraph.id, caret)
            formattedBlockFocusTarget = paragraph.id
            setSelection(offset + caret.start, offset + caret.end)
        }
        return true
    }

    /** Indents the active item subtree under its preceding sibling. */
    fun indentFormattedListItem(blockId: String, itemPath: List<Int>): Boolean = editFormattedListStructure(blockId, itemPath) { it.indent(itemPath) }

    /** Outdents a nested item's subtree one level. */
    fun outdentFormattedListItem(blockId: String, itemPath: List<Int>): Boolean {
        if (itemPath.size > 1) return editFormattedListStructure(blockId, itemPath) { it.outdent(itemPath) }
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val lift = list.liftTopLevel(itemPath) ?: return false
        val candidate = text.replaceRange(block.range.min, block.range.max, lift.source)
        val targetOffset = block.range.min + lift.paragraphOffset
        val target = MarkdownDocumentCodec.parse(candidate).blocks.firstOrNull {
            it.range.min <= targetOffset && targetOffset < it.range.max &&
                it.kind in setOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING)
        } ?: return false
        val visibleSelection = if (activeFormattedBlockId == blockId && activeFormattedListPath == itemPath)
            formattedListSelection else TextRange.Zero
        replaceRange(block.range.min, block.range.max, lift.source)
        val inline = MarkdownFormattedBlock.inline(target, enableWikilinks)
        val offset = visibleSelection.start.coerceIn(0, inline?.visible?.length ?: 0)
        setFormattedSelection(target.id, TextRange(offset))
        formattedBlockFocusTarget = target.id
        val rawOffset = inline?.sourceOffsetAtVisible(offset) ?: 0
        val bodyOffset = target.source.indexOf(MarkdownFormattedBlock.text(target).orEmpty()).coerceAtLeast(0)
        setSelection(targetOffset + bodyOffset + rawOffset)
        return true
    }

    private fun editFormattedListStructure(
        blockId: String,
        itemPath: List<Int>,
        transform: (MarkdownSourceList) -> MarkdownSourceList.StructureEdit?,
    ): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val edit = transform(list) ?: return false
        val target = validatedListTarget(block, edit) ?: return false
        val oldLineIndex = if (activeFormattedBlockId == blockId && activeFormattedListPath == itemPath) activeFormattedListLine else 0
        val oldSelection = if (activeFormattedBlockId == blockId && activeFormattedListPath == itemPath) formattedListSelection else TextRange.Zero
        val oldLine = list.item(itemPath)?.lines?.getOrNull(oldLineIndex) ?: return false
        val sourceOffset = MarkdownInlineEditing.parse(list.lineContent(itemPath, oldLineIndex).orEmpty(), enableWikilinks)
            .sourceOffsetAtVisible(oldSelection.start) ?: return false
        val previousSelection = selection
        setSelection(block.range.min + oldLine.start + sourceOffset)
        if (!replaceSemanticBlock(blockId, edit.source)) {
            setSelection(previousSelection.start, previousSelection.end)
            return false
        }
        val targetLineIndex = oldLineIndex.coerceAtMost(target.lines.lastIndex)
        val targetLine = target.lines[targetLineIndex]
        val inline = MarkdownInlineEditing.parse(edit.source.substring(targetLine.start, targetLine.end), enableWikilinks)
        val caretStart = inline.sourceOffsetAtVisible(oldSelection.start.coerceIn(0, inline.visible.length)) ?: 0
        val caretEnd = inline.sourceOffsetAtVisible(oldSelection.end.coerceIn(0, inline.visible.length)) ?: caretStart
        setSelection(block.range.min + targetLine.start + caretStart, block.range.min + targetLine.start + caretEnd)
        setFormattedListSelection(blockId, edit.targetPath, targetLineIndex, oldSelection)
        formattedListFocusTarget = edit.targetPath to activeFormattedListLine
        return true
    }

    private fun validatedListTarget(block: MarkdownDocumentBlock, edit: MarkdownSourceList.StructureEdit): MarkdownSourceList.Item? {
        val parsed = MarkdownDocumentCodec.parse(edit.source)
        if (parsed.blocks.size != 1 || parsed.blocks.single().range != TextRange(0, edit.source.length) ||
            parsed.blocks.single().kind != block.kind) return null
        return MarkdownSourceList.parse(parsed.blocks.single())?.item(edit.targetPath)
    }

    fun insertMarkdown(markdown: String) = replaceSelection(markdown)

    /** Select a suggestion in the active formatted block as one undoable source edit. */
    fun insertWikilinkSuggestion(title: String): Boolean {
        if (!enableWikilinks || mode != MarkdownEditorMode.FORMATTED) return false
        val blockId = activeFormattedBlockId ?: return false
        val block = semanticDocument().blockById(blockId) ?: return false
        val model = MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return false
        val match = WikilinkAutocomplete.match(model.visible, formattedSelection, model.marks) ?: return false
        val replacement = model.replaceVisibleRangeWithWikilink(match.range, title) ?: return false
        if (!replaceSemanticBlock(blockId, MarkdownFormattedBlock.markdown(block, replacement) ?: return false)) return false
        setFormattedSelection(blockId, TextRange(match.range.min + title.length))
        return true
    }

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
            val original = text.substring(located.range.min, located.range.max)
            val patch = located.table.sourcePatchForCell(original, updated)
            if (patch != null) {
                val absoluteStart = located.range.min + patch.start
                val absoluteEnd = located.range.min + patch.end
                val priorSelection = selection
                replaceRange(absoluteStart, absoluteEnd, patch.replacement)
                setSelection(
                    mappedPosition(priorSelection.start, absoluteStart, absoluteEnd, patch.replacement.length),
                    mappedPosition(priorSelection.end, absoluteStart, absoluteEnd, patch.replacement.length),
                )
                return true
            }
            val replacement = updated.toMarkdown()
            val relativeCaret = (selection.min - located.range.min).coerceIn(0, replacement.length)
            replaceRange(located.range.min, located.range.max, replacement, relativeCaret)
        }
        return true
    }

    private fun mappedPosition(position: Int, start: Int, end: Int, replacementLength: Int): Int = when {
        position <= start -> position
        position >= end -> position + replacementLength - (end - start)
        else -> start + (position - start).coerceAtMost(replacementLength)
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

    fun selectPreviousMatch(query: String, caseSensitive: Boolean = false): TextRange? {
        val matches = findMatches(query, caseSensitive)
        if (matches.isEmpty()) return null
        val previous = matches.lastOrNull { it.end <= selection.min } ?: matches.last()
        setSelection(previous.start, previous.end)
        return previous
    }

    fun applyCommand(command: MarkdownEditorCommand, argument: String? = null) {
        if (mode == MarkdownEditorMode.FORMATTED && command in setOf(
                MarkdownEditorCommand.BOLD, MarkdownEditorCommand.ITALIC,
                MarkdownEditorCommand.LINK, MarkdownEditorCommand.INLINE_CODE, MarkdownEditorCommand.WIKILINK,
            )) {
            applyFormattedInlineMark(command, argument)
            return
        }
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
            MarkdownEditorCommand.WIKILINK -> if (enableWikilinks) wrap("[[", "]]", "Note") else Unit
        }
    }

    private fun updateValue(next: TextFieldValue) {
        if (next.text != value.text) recordUndo(value)
        value = next
        pendingListExit = null
        if (next.text != formattedBlockSelection?.source) formattedBlockSelection = null
    }

    private fun snapshot(): EditorSnapshot = EditorSnapshot(
        value, activeFormattedBlockId, formattedSelection, formattedComposition,
        activeFormattedListPath, activeFormattedListLine, formattedListSelection, formattedListComposition,
        pendingListExit,
        formattedBlockSelection,
    )

    private fun restore(snapshot: EditorSnapshot) {
        value = snapshot.value
        activeFormattedBlockId = snapshot.blockId
        formattedSelection = snapshot.selection
        formattedComposition = snapshot.composition
        activeFormattedListPath = snapshot.listPath
        activeFormattedListLine = snapshot.listLine
        formattedListSelection = snapshot.listSelection
        formattedListComposition = snapshot.listComposition
        pendingListExit = snapshot.pendingListExit
        formattedBlockSelection = snapshot.blockSelection
        formattedListFocusTarget = snapshot.listPath?.let { it to snapshot.listLine }
        formattedBlockFocusTarget = if (snapshot.listPath == null) snapshot.blockId else null
    }

    private fun recordUndo(previous: TextFieldValue) {
        if (transactionDepth == 0) {
            push(undoStack, snapshot().copy(value = previous))
            redoStack.clear()
            historyRevision++
        }
    }

    private fun push(stack: ArrayDeque<EditorSnapshot>, snapshot: EditorSnapshot) {
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

enum class MarkdownEditorMode { SOURCE, PREVIEW, SPLIT, FORMATTED }
