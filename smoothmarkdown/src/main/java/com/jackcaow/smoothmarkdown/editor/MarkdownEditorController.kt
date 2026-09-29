package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.parseMarkdown
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

data class MarkdownDocumentBlockSelection(
    val source: String,
    val anchorIndex: Int,
    val extentIndex: Int,
) {
    val firstIndex: Int get() = minOf(anchorIndex, extentIndex)
    val lastIndex: Int get() = maxOf(anchorIndex, extentIndex)
}

/** UTF-16 offset in the visible text of a formatted paragraph or heading. */
data class MarkdownFormattedTextPosition(val blockId: String, val offset: Int)

/** A source-revision-bound text selection across contiguous top-level prose blocks. */
data class MarkdownFormattedTextSelection(
    val source: String,
    val anchor: MarkdownFormattedTextPosition,
    val focus: MarkdownFormattedTextPosition,
)

/** A contiguous selection of sibling list items in one source revision. */
data class MarkdownListItemSelection(
    val source: String,
    val blockId: String,
    val anchorPath: List<Int>,
    val extentPath: List<Int>,
) {
    val parentPath: List<Int> get() = anchorPath.dropLast(1)
    val firstIndex: Int get() = minOf(anchorPath.last(), extentPath.last())
    val lastIndex: Int get() = maxOf(anchorPath.last(), extentPath.last())
}

data class MarkdownTableCellPosition(val rowIndex: Int, val columnIndex: Int)

/** Row zero is the table header; body rows start at one. */
data class MarkdownTableCellSelection(
    val source: String,
    val blockId: String,
    val anchor: MarkdownTableCellPosition,
    val extent: MarkdownTableCellPosition,
) {
    val firstRow: Int get() = minOf(anchor.rowIndex, extent.rowIndex)
    val lastRow: Int get() = maxOf(anchor.rowIndex, extent.rowIndex)
    val firstColumn: Int get() = minOf(anchor.columnIndex, extent.columnIndex)
    val lastColumn: Int get() = maxOf(anchor.columnIndex, extent.columnIndex)
}

/** Source-backed editing commands. Offsets use UTF-16, matching Compose selections. */
class MarkdownEditorController(
    initialText: String = "",
    historyLimit: Int = 100,
    val parserPlugins: ParserPluginRegistry? = null,
) {
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
    var formattedListItemSelection by mutableStateOf<MarkdownListItemSelection?>(null)
        private set
    var formattedTableCellSelection by mutableStateOf<MarkdownTableCellSelection?>(null)
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
        val listItemSelection: MarkdownListItemSelection?,
        val tableCellSelection: MarkdownTableCellSelection?,
    )

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private val valueObservers = linkedSetOf<(TextFieldValue) -> Unit>()
    private val pendingValueNotifications = ArrayDeque<TextFieldValue>()
    private var notifyingValueObservers = false
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
            formattedListItemSelection = null
            formattedTableCellSelection = null
        }
        publishValue(next)
    }

    fun setSelection(start: Int, end: Int = start) {
        publishValue(value.copy(selection = TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))))
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
    fun semanticDocument(): MarkdownDocument = MarkdownDocumentCodec.parse(text, parserPlugins)

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

    /** A long press starts a fresh range; drag updates keep its original anchor. */
    fun beginFormattedBlockDrag(blockId: String): Boolean {
        formattedBlockSelection = null
        return extendFormattedBlockDrag(blockId)
    }

    fun extendFormattedBlockDrag(blockId: String): Boolean {
        val document = semanticDocument()
        val index = document.blocks.indexOfFirst { it.id == blockId && it.kind in setOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING) }
        if (index < 0) return false
        val previous = formattedBlockSelection?.takeIf { it.source == text }
        if (previous != null && document.blocks.subList(minOf(previous.anchorIndex, index), maxOf(previous.anchorIndex, index) + 1)
                .any { it.kind != MarkdownBlockKind.PARAGRAPH && it.kind != MarkdownBlockKind.HEADING }) return false
        formattedBlockSelection = if (previous == null) MarkdownDocumentBlockSelection(text, index, index)
            else previous.copy(extentIndex = index)
        formattedListItemSelection = null
        formattedTableCellSelection = null
        return true
    }

    fun clearFormattedBlockSelection() { formattedBlockSelection = null }

    /** Tap one sibling item to anchor, then another item to extend. */
    fun selectFormattedListItem(blockId: String, path: List<Int>): Boolean {
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        if (list.item(path) == null || path.isEmpty()) return false
        val old = formattedListItemSelection?.takeIf {
            it.source == text && it.blockId == blockId && it.parentPath == path.dropLast(1)
        }
        formattedListItemSelection = if (old == null) MarkdownListItemSelection(text, blockId, path, path)
            else old.copy(extentPath = path)
        return true
    }

    fun beginFormattedListItemDrag(blockId: String, path: List<Int>): Boolean {
        formattedListItemSelection = null
        return extendFormattedListItemDrag(blockId, path)
    }

    fun extendFormattedListItemDrag(blockId: String, path: List<Int>): Boolean {
        if (path.isEmpty()) return false
        val list = semanticDocument().blockById(blockId)?.let(MarkdownSourceList::parse) ?: return false
        if (list.item(path) == null) return false
        val previous = formattedListItemSelection?.takeIf {
            it.source == text && it.blockId == blockId && it.parentPath == path.dropLast(1)
        }
        if (formattedListItemSelection != null && previous == null) return false
        formattedListItemSelection = if (previous == null) MarkdownListItemSelection(text, blockId, path, path)
            else previous.copy(extentPath = path)
        formattedBlockSelection = null
        formattedTableCellSelection = null
        return true
    }

    fun clearFormattedListItemSelection() { formattedListItemSelection = null }

    fun copyFormattedListItemSelectionAsMarkdown(): String? {
        val (list, selected) = selectedFormattedListItems() ?: return null
        return list.copySiblingItems(selected.parentPath, selected.firstIndex, selected.lastIndex)
    }

    /** Deletes complete sibling subtrees as one source edit and one undo step. */
    fun deleteFormattedListItemSelection(): Boolean {
        val (list, selected) = selectedFormattedListItems() ?: return false
        val block = semanticDocument().blockById(selected.blockId) ?: return false
        val replacement = list.deleteSiblingItems(selected.parentPath, selected.firstIndex, selected.lastIndex) ?: return false
        if (replacement == block.source) return false
        if (replacement.isNotEmpty()) {
            val parsed = MarkdownDocumentCodec.parse(replacement, parserPlugins).blocks
            if (parsed.size != 1 || parsed.single().range != TextRange(0, replacement.length) ||
                parsed.single().kind != block.kind) return false
        }
        return replaceCustomBlockMarkdown(text, block, replacement)
    }

    /** Formats every selected item's primary line in one source edit. */
    fun applyInlineCommandToFormattedListItemSelection(command: MarkdownEditorCommand, destination: String? = null): Boolean {
        val kind = when (command) {
            MarkdownEditorCommand.BOLD -> InlineMarkKind.BOLD
            MarkdownEditorCommand.ITALIC -> InlineMarkKind.ITALIC
            MarkdownEditorCommand.STRIKETHROUGH -> InlineMarkKind.STRIKETHROUGH
            MarkdownEditorCommand.INLINE_CODE -> InlineMarkKind.CODE
            MarkdownEditorCommand.LINK -> InlineMarkKind.LINK
            MarkdownEditorCommand.WIKILINK -> if (enableWikilinks) InlineMarkKind.WIKILINK else return false
            else -> return false
        }
        val (list, selected) = selectedFormattedListItems() ?: return false
        val replacement = list.applyInlineToSiblingItems(
            selected.parentPath, selected.firstIndex, selected.lastIndex, kind, destination, enableWikilinks,
        ) ?: return false
        return replaceSemanticBlock(selected.blockId, replacement)
    }

    private fun selectedFormattedListItems(): Pair<MarkdownSourceList, MarkdownListItemSelection>? {
        val selected = formattedListItemSelection ?: return null
        if (selected.source != text || selected.anchorPath.isEmpty() || selected.extentPath.isEmpty() ||
            selected.parentPath != selected.extentPath.dropLast(1)) return null
        val list = semanticDocument().blockById(selected.blockId)?.let(MarkdownSourceList::parse) ?: return null
        if (list.siblingRange(selected.parentPath, selected.firstIndex, selected.lastIndex) == null) return null
        return list to selected
    }

    fun selectFormattedTableCell(blockId: String, rowIndex: Int, columnIndex: Int): Boolean {
        val table = semanticTable(blockId) ?: return false
        if (rowIndex !in 0..table.rows.size || columnIndex !in table.headers.indices) return false
        val position = MarkdownTableCellPosition(rowIndex, columnIndex)
        val old = formattedTableCellSelection?.takeIf { it.source == text && it.blockId == blockId }
        formattedTableCellSelection = if (old == null) MarkdownTableCellSelection(text, blockId, position, position)
            else old.copy(extent = position)
        return true
    }

    fun beginFormattedTableCellDrag(blockId: String, rowIndex: Int, columnIndex: Int): Boolean {
        formattedTableCellSelection = null
        return extendFormattedTableCellDrag(blockId, rowIndex, columnIndex)
    }

    fun extendFormattedTableCellDrag(blockId: String, rowIndex: Int, columnIndex: Int): Boolean {
        val table = semanticTable(blockId) ?: return false
        if (rowIndex !in 0..table.rows.size || columnIndex !in table.headers.indices) return false
        val position = MarkdownTableCellPosition(rowIndex, columnIndex)
        val previous = formattedTableCellSelection?.takeIf { it.source == text && it.blockId == blockId }
        if (formattedTableCellSelection != null && previous == null) return false
        formattedTableCellSelection = if (previous == null) MarkdownTableCellSelection(text, blockId, position, position)
            else previous.copy(extent = position)
        formattedBlockSelection = null
        formattedListItemSelection = null
        return true
    }

    fun resetFormattedTableCellSelection() { formattedTableCellSelection = null }

    fun copyFormattedTableCellSelectionAsTsv(): String? {
        val (table, selected) = selectedFormattedTableCells() ?: return null
        return (selected.firstRow..selected.lastRow).joinToString("\n") { row ->
            (selected.firstColumn..selected.lastColumn).joinToString("\t") { column ->
                if (row == 0) table.headers[column] else table.rows[row - 1][column]
            }
        }
    }

    /** Clears a rectangular range with independent source patches in a single undo step. */
    fun clearFormattedTableCellSelection(): Boolean {
        val (table, selected) = selectedFormattedTableCells() ?: return false
        val block = semanticDocument().blockById(selected.blockId) ?: return false
        var updated = table
        for (row in selected.firstRow..selected.lastRow) {
            for (column in selected.firstColumn..selected.lastColumn) {
                updated = updated.replaceCell(if (row == 0) 0 else row - 1, column, "", row == 0)
            }
        }
        if (updated == table) return false
        val patches = table.sourcePatchesForCells(block.source, updated) ?: return false
        val replacement = patches.sortedByDescending { it.start }.fold(block.source) { source, patch ->
            source.replaceRange(patch.start, patch.end, patch.replacement)
        }
        return replaceSemanticBlock(selected.blockId, replacement)
    }

    /** Pastes a same-sized TSV rectangle while retaining untouched cell padding and separators. */
    fun replaceFormattedTableCellSelectionFromTsv(tsv: String): Boolean {
        val (table, selected) = selectedFormattedTableCells() ?: return false
        val rows = tsv.replace("\r\n", "\n").split('\n').map { it.split('\t') }
        if (rows.size != selected.lastRow - selected.firstRow + 1 ||
            rows.any { it.size != selected.lastColumn - selected.firstColumn + 1 }) return false
        val block = semanticDocument().blockById(selected.blockId) ?: return false
        var updated = table
        rows.forEachIndexed { rowOffset, cells ->
            cells.forEachIndexed { columnOffset, value ->
                val row = selected.firstRow + rowOffset
                updated = updated.replaceCell(if (row == 0) 0 else row - 1,
                    selected.firstColumn + columnOffset, value, row == 0)
            }
        }
        if (updated == table) return false
        val patches = table.sourcePatchesForCells(block.source, updated) ?: return false
        val replacement = patches.sortedByDescending { it.start }.fold(block.source) { source, patch ->
            source.replaceRange(patch.start, patch.end, patch.replacement)
        }
        return replaceSemanticBlock(selected.blockId, replacement)
    }

    private fun selectedFormattedTableCells(): Pair<MarkdownSourceTable, MarkdownTableCellSelection>? {
        val selected = formattedTableCellSelection ?: return null
        if (selected.source != text) return null
        val table = semanticTable(selected.blockId) ?: return null
        if (selected.firstRow < 0 || selected.lastRow > table.rows.size ||
            selected.firstColumn < 0 || selected.lastColumn >= table.columnCount) return null
        return table to selected
    }

    /** Formats every cell in the selected rectangle as one source edit. */
    fun applyInlineCommandToFormattedTableCellSelection(
        command: MarkdownEditorCommand,
        destination: String? = null,
    ): Boolean {
        if (command !in setOf(MarkdownEditorCommand.BOLD, MarkdownEditorCommand.ITALIC,
                MarkdownEditorCommand.STRIKETHROUGH, MarkdownEditorCommand.INLINE_CODE)) return false
        val kind = inlineMarkKind(command) ?: return false
        val (table, selected) = selectedFormattedTableCells() ?: return false
        val block = semanticDocument().blockById(selected.blockId) ?: return false
        var updated = table
        var changed = false
        for (row in selected.firstRow..selected.lastRow) {
            for (column in selected.firstColumn..selected.lastColumn) {
                val source = if (row == 0) updated.headers[column] else updated.rows[row - 1][column]
                if (MarkdownInlineEditing.parse(source, enableWikilinks).visible.isEmpty()) continue
                val wrapped = wrapCompleteInlineSource(source, kind, destination) ?: return false
                if (wrapped != source) changed = true
                updated = if (row == 0) {
                    updated.copy(headers = updated.headers.toMutableList().also { it[column] = wrapped })
                } else {
                    updated.copy(rows = updated.rows.toMutableList().also { rows ->
                        rows[row - 1] = rows[row - 1].toMutableList().also { it[column] = wrapped }
                    })
                }
            }
        }
        if (!changed) return false
        val patches = table.sourcePatchesForCells(block.source, updated) ?: return false
        val replacement = patches.sortedByDescending { it.start }.fold(block.source) { source, patch ->
            source.replaceRange(patch.start, patch.end, patch.replacement)
        }
        return replaceSemanticBlock(selected.blockId, replacement)
    }

    /** Includes the exact source between the first and last selected blocks. */
    fun copyFormattedBlockSelectionAsMarkdown(): String? {
        val document = selectedFormattedDocument() ?: return null
        val selection = formattedBlockSelection ?: return null
        val first = document.blocks[selection.firstIndex]
        val last = document.blocks[selection.lastIndex]
        return text.substring(first.range.min, last.range.max)
    }

    /** Applies an inline mark to complete visible text in selected prose blocks. */
    fun applyInlineCommandToFormattedBlockSelection(
        command: MarkdownEditorCommand,
        destination: String? = null,
    ): Boolean {
        val document = selectedFormattedDocument() ?: return false
        val selection = formattedBlockSelection ?: return false
        val blocks = document.blocks.subList(selection.firstIndex, selection.lastIndex + 1)
        val ranges = blocks.map { block ->
            val source = MarkdownFormattedBlock.text(block) ?: return false
            val visibleLength = MarkdownInlineEditing.parse(source, enableWikilinks).visible.length
            block to TextRange(0, visibleLength)
        }
        return applyInlineToFormattedTextRanges(document, ranges, command, destination)
    }

    /** Applies one inline command to visible text across paragraph/heading blocks in one undo step. */
    fun applyInlineCommandToFormattedTextSelection(
        selected: MarkdownFormattedTextSelection,
        command: MarkdownEditorCommand,
        destination: String? = null,
    ): Boolean {
        if (selected.source != text) return false
        val document = semanticDocument()
        val anchorIndex = document.blocks.indexOfFirst { it.id == selected.anchor.blockId }
        val focusIndex = document.blocks.indexOfFirst { it.id == selected.focus.blockId }
        if (anchorIndex < 0 || focusIndex < 0) return false
        val firstIndex = minOf(anchorIndex, focusIndex)
        val lastIndex = maxOf(anchorIndex, focusIndex)
        val bounds = listOf(anchorIndex to selected.anchor.offset, focusIndex to selected.focus.offset)
            .sortedWith(compareBy({ it.first }, { it.second }))
        if (bounds[0] == bounds[1]) return false
        val ranges = (firstIndex..lastIndex).map { index ->
            val block = document.blocks[index]
            val source = MarkdownFormattedBlock.text(block) ?: return false
            val visibleLength = MarkdownInlineEditing.parse(source, enableWikilinks).visible.length
            val start = if (index == firstIndex) bounds[0].second else 0
            val end = if (index == lastIndex) bounds[1].second else visibleLength
            if (start !in 0..visibleLength || end !in start..visibleLength) return false
            block to TextRange(start, end)
        }
        return applyInlineToFormattedTextRanges(document, ranges, command, destination)
    }

    private fun applyInlineToFormattedTextRanges(
        document: MarkdownDocument,
        ranges: List<Pair<MarkdownDocumentBlock, TextRange>>,
        command: MarkdownEditorCommand,
        destination: String?,
    ): Boolean {
        val kind = inlineMarkKind(command) ?: return false
        if (ranges.isEmpty() || ranges.any { (block, _) ->
                block.kind !in setOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING)
            }) return false
        val replacements = ranges.mapNotNull { (block, range) ->
            if (range.collapsed) return@mapNotNull null
            val source = MarkdownFormattedBlock.text(block) ?: return false
            val inline = MarkdownInlineEditing.parse(source, enableWikilinks)
            val wrapped = if (range.min == 0 && range.max == inline.visible.length)
                wrapCompleteInlineSource(source, kind, destination, allowMixed = true)
            else inline.wrap(range, kind, destination)
            if (wrapped == null || MarkdownInlineEditing.parse(wrapped, enableWikilinks).visible != inline.visible) return false
            val markdown = MarkdownFormattedBlock.markdown(block, wrapped) ?: return false
            block to markdown
        }
        if (replacements.isEmpty()) return false
        if (replacements.all { (block, markdown) -> block.source == markdown }) return false
        val first = ranges.first().first
        val last = ranges.last().first
        val replacement = replacements.asReversed().fold(text.substring(first.range.min, last.range.max)) { current, (block, markdown) ->
            current.replaceRange(block.range.min - first.range.min, block.range.max - first.range.min, markdown)
        }
        val candidate = text.replaceRange(first.range.min, last.range.max, replacement)
        val reparsed = MarkdownDocumentCodec.parse(candidate, parserPlugins).blocks
        if (reparsed.size != document.blocks.size) return false
        if (document.blocks.zip(reparsed).any { (old, next) ->
                val selected = old.range.min >= first.range.min && old.range.max <= last.range.max
                old.kind != next.kind || (!selected && old.source != next.source)
            }) return false
        if (replacements.any { (block, markdown) ->
                reparsed.getOrNull(document.blocks.indexOf(block))?.source != markdown
            }) return false
        replaceRange(first.range.min, last.range.max, replacement)
        return true
    }

    /** Complete-line wrap with a bounded nested emphasis/link case. */
    private fun wrapCompleteInlineSource(source: String, kind: InlineMarkKind, destination: String?, allowMixed: Boolean = false): String? {
        val inline = MarkdownInlineEditing.parse(source, enableWikilinks)
        val wrapped = inline.wrapComplete(kind, destination, allowMixed) ?: return null
        if (MarkdownInlineEditing.parse(wrapped, enableWikilinks).visible != inline.visible) return null
        return wrapped
    }

    private fun inlineMarkKind(command: MarkdownEditorCommand): InlineMarkKind? = when (command) {
        MarkdownEditorCommand.BOLD -> InlineMarkKind.BOLD
        MarkdownEditorCommand.ITALIC -> InlineMarkKind.ITALIC
        MarkdownEditorCommand.STRIKETHROUGH -> InlineMarkKind.STRIKETHROUGH
        MarkdownEditorCommand.INLINE_CODE -> InlineMarkKind.CODE
        MarkdownEditorCommand.LINK -> InlineMarkKind.LINK
        MarkdownEditorCommand.WIKILINK -> if (enableWikilinks) InlineMarkKind.WIKILINK else null
        else -> null
    }

    fun deleteFormattedBlockSelection(): Boolean = replaceFormattedBlockSelectionWithMarkdown("")

    /** One source edit and one undo step. Refuses replacements that change untouched block parsing. */
    fun replaceFormattedBlockSelectionWithMarkdown(markdown: String): Boolean {
        val document = selectedFormattedDocument() ?: return false
        val selection = formattedBlockSelection ?: return false
        val replacementBlocks = if (markdown.isEmpty()) emptyList() else {
            val parsed = MarkdownDocumentCodec.parse(markdown, parserPlugins).blocks
            if (parsed.isEmpty() || parsed.first().range.min != 0 || parsed.last().range.max != markdown.length) return false
            parsed
        }
        val first = document.blocks[selection.firstIndex]
        val last = document.blocks[selection.lastIndex]
        val candidate = text.replaceRange(first.range.min, last.range.max, markdown)
        val reparsed = MarkdownDocumentCodec.parse(candidate, parserPlugins).blocks
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
        val editor = MarkdownDocumentEditor(current.source, parserPlugins)
        if (!editor.replaceBlockSource(blockId, markdown)) return false
        replaceRange(block.range.min, block.range.max, markdown)
        return true
    }

    /** Replaces one host-owned block, allowing its syntax to change while preserving neighbors. */
    fun replaceCustomBlockMarkdown(expectedSource: String, block: MarkdownDocumentBlock, markdown: String): Boolean {
        if (text != expectedSource) return false
        val current = semanticDocument()
        val index = current.blocks.indexOfFirst { it.id == block.id }
        if (index < 0 || current.blocks[index] != block) return false
        val replacement = if (markdown.isEmpty()) emptyList() else {
            MarkdownDocumentCodec.parse(markdown, parserPlugins).blocks.also {
                if (it.isEmpty() || it.first().range.min != 0 || it.last().range.max != markdown.length) return false
            }
        }
        val candidate = text.replaceRange(block.range.min, block.range.max, markdown)
        val reparsed = MarkdownDocumentCodec.parse(candidate, parserPlugins).blocks
        val before = current.blocks.take(index)
        val after = current.blocks.drop(index + 1)
        if (reparsed.size != before.size + replacement.size + after.size) return false
        if (before.zip(reparsed).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        if (replacement.zip(reparsed.drop(before.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        if (after.zip(reparsed.takeLast(after.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        if (candidate == text) return true
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

    /**
     * A multi-line Markdown paste into a formatted paragraph becomes sibling parsed blocks.
     * Unchanged inline Markdown on either side is split into valid standalone fragments, and
     * untouched top-level blocks must reparse identically before the single source edit commits.
     */
    internal fun replaceFormattedTextWithBlocks(blockId: String, nextVisible: String): Boolean {
        if (mode != MarkdownEditorMode.FORMATTED) return false
        if ('\n' !in nextVisible && '\r' !in nextVisible) return false
        if (!hasWellFormedUtf16(nextVisible)) return false
        val document = semanticDocument()
        val index = document.blocks.indexOfFirst { it.id == blockId }
        val block = document.blocks.getOrNull(index) ?: return false
        if (block.kind != MarkdownBlockKind.PARAGRAPH && block.kind != MarkdownBlockKind.HEADING) return false
        val inline = MarkdownFormattedBlock.inline(block, enableWikilinks) ?: return false
        val commonPrefix = inline.visible.commonPrefixWith(nextVisible).length
        val oldTail = inline.visible.substring(commonPrefix)
        val newTail = nextVisible.substring(commonPrefix)
        val commonSuffix = oldTail.commonSuffixWith(newTail).length
        val replacedRange = TextRange(commonPrefix, inline.visible.length - commonSuffix)
        val pasted = nextVisible.substring(commonPrefix, nextVisible.length - commonSuffix)
        if ('\n' !in pasted && '\r' !in pasted) return false
        val markdown = pasted.trim('\r', '\n')
        if (markdown.isBlank()) return false
        val pastedDocument = MarkdownDocumentCodec.parse(markdown, parserPlugins)
        if (pastedDocument.blocks.isEmpty() || pastedDocument.blocks.first().range.min != 0 ||
            pastedDocument.blocks.last().range.max != markdown.length ||
            pastedDocument.blocks.all { it.kind == MarkdownBlockKind.PARAGRAPH }) return false
        val split = inline.splitVisibleRange(replacedRange) ?: return false
        val before = split.before.takeIf(String::isNotEmpty)?.let { MarkdownFormattedBlock.markdown(block, it) }
        val after = split.after.takeIf(String::isNotEmpty)?.let { MarkdownFormattedBlock.markdown(block, it) }
        if ((split.before.isNotEmpty() && before == null) || (split.after.isNotEmpty() && after == null)) return false
        val replacement = listOfNotNull(before, markdown, after).joinToString("\n\n")
        val candidate = text.replaceRange(block.range.min, block.range.max, replacement)
        val parsed = MarkdownDocumentCodec.parse(candidate, parserPlugins).blocks
        val expectedSources = listOfNotNull(before, after)
        val beforeBlocks = document.blocks.take(index)
        val afterBlocks = document.blocks.drop(index + 1)
        if (parsed.size != beforeBlocks.size + pastedDocument.blocks.size + expectedSources.size + afterBlocks.size) return false
        if (beforeBlocks.zip(parsed).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        val replacedBlocks = parsed.drop(beforeBlocks.size).take(pastedDocument.blocks.size + expectedSources.size)
        var cursor = 0
        if (before != null) {
            if (replacedBlocks[cursor].source != before || replacedBlocks[cursor].kind != block.kind) return false
            cursor++
        }
        if (pastedDocument.blocks.zip(replacedBlocks.drop(cursor)).any { (old, next) ->
                old.kind != next.kind || old.source != next.source
            }) return false
        cursor += pastedDocument.blocks.size
        if (after != null && (replacedBlocks[cursor].source != after || replacedBlocks[cursor].kind != block.kind)) return false
        if (afterBlocks.zip(parsed.takeLast(afterBlocks.size)).any { (old, next) -> old.kind != next.kind || old.source != next.source }) return false
        val selectionOffset = (before?.length?.plus(2) ?: 0) + markdown.length
        replaceRange(block.range.min, block.range.max, replacement, selectedStart = selectionOffset)
        activeFormattedBlockId = null
        activeFormattedListPath = null
        formattedSelection = TextRange.Zero
        formattedComposition = null
        formattedBlockFocusTarget = null
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
            MarkdownEditorCommand.STRIKETHROUGH -> InlineMarkKind.STRIKETHROUGH
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
            InlineMarkKind.STRIKETHROUGH -> 13
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
        val editor = MarkdownDocumentEditor(text, parserPlugins)
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

    /** A formatted list-line paste becomes child list items while retaining the parent marker. */
    internal fun replaceFormattedListLineWithBlocks(blockId: String, itemPath: List<Int>, lineIndex: Int, nextVisible: String): Boolean {
        if (mode != MarkdownEditorMode.FORMATTED || ('\n' !in nextVisible && '\r' !in nextVisible)) return false
        if (!hasWellFormedUtf16(nextVisible)) return false
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val raw = list.lineContent(itemPath, lineIndex) ?: return false
        val inline = MarkdownInlineEditing.parse(raw, enableWikilinks)
        val commonPrefix = inline.visible.commonPrefixWith(nextVisible).length
        val oldTail = inline.visible.substring(commonPrefix)
        val newTail = nextVisible.substring(commonPrefix)
        val commonSuffix = oldTail.commonSuffixWith(newTail).length
        val selectedRange = TextRange(commonPrefix, inline.visible.length - commonSuffix)
        val pasted = nextVisible.substring(commonPrefix, nextVisible.length - commonSuffix)
        if ('\n' !in pasted && '\r' !in pasted) return false
        val markdown = pasted.trim('\r', '\n')
        if (markdown.isBlank()) return false
        val parsedBlocks = MarkdownDocumentCodec.parse(markdown, parserPlugins).blocks
        if (parsedBlocks.isEmpty() || parsedBlocks.first().range.min != 0 ||
            parsedBlocks.last().range.max != markdown.length ||
            parsedBlocks.any { it.kind != MarkdownBlockKind.BULLET_LIST && it.kind != MarkdownBlockKind.ORDERED_LIST }) return false
        val split = inline.splitVisibleRange(selectedRange) ?: return false
        val edit = list.replaceLineWithBlocks(itemPath, lineIndex, split.before, markdown, split.after) ?: return false
        val nextBlock = MarkdownDocumentCodec.parse(edit.source, parserPlugins).blocks.singleOrNull() ?: return false
        val nextList = MarkdownSourceList.parse(nextBlock) ?: return false
        val focusRaw = nextList.lineContent(edit.focusPath, edit.focusLine) ?: return false
        val focusLength = MarkdownInlineEditing.parse(focusRaw, enableWikilinks).visible.length
        replaceRange(block.range.min, block.range.max, edit.source, selectedStart = edit.selectionOffset)
        setFormattedListSelection(blockId, edit.focusPath, edit.focusLine, TextRange(focusLength))
        formattedListFocusTarget = edit.focusPath to edit.focusLine
        formattedSelection = TextRange.Zero
        formattedComposition = null
        formattedBlockFocusTarget = null
        return true
    }

    /** Plain multi-line paste remains one soft-break paragraph inside the current list item. */
    internal fun replaceFormattedListLineWithPlainLines(blockId: String, itemPath: List<Int>, lineIndex: Int,
                                                        nextVisible: String): Boolean {
        if (mode != MarkdownEditorMode.FORMATTED || ('\n' !in nextVisible && '\r' !in nextVisible) ||
            !hasWellFormedUtf16(nextVisible)) return false
        val block = semanticDocument().blockById(blockId) ?: return false
        val list = MarkdownSourceList.parse(block) ?: return false
        val raw = list.lineContent(itemPath, lineIndex) ?: return false
        val inline = MarkdownInlineEditing.parse(raw, enableWikilinks)
        val commonPrefix = inline.visible.commonPrefixWith(nextVisible).length
        val oldTail = inline.visible.substring(commonPrefix)
        val newTail = nextVisible.substring(commonPrefix)
        val commonSuffix = oldTail.commonSuffixWith(newTail).length
        val selectedRange = TextRange(commonPrefix, inline.visible.length - commonSuffix)
        val pasted = nextVisible.substring(commonPrefix, nextVisible.length - commonSuffix)
            .replace("\r\n", "\n").replace('\r', '\n')
        val lines = pasted.split('\n')
        if (lines.size < 2 || lines.any { it.isBlank() } ||
            lines.any { MarkdownInlineEditing.parse(it, enableWikilinks).marks.isNotEmpty() }) return false
        val parsedPaste = MarkdownDocumentCodec.parse(pasted, parserPlugins).blocks
        if (parsedPaste.size != 1 || parsedPaste.single().kind != MarkdownBlockKind.PARAGRAPH ||
            parsedPaste.single().range != TextRange(0, pasted.length)) return false
        val paragraph = parseMarkdown(pasted, parserPlugins).firstChild as? Paragraph ?: return false
        if (paragraph.next != null) return false
        var inlineNode = paragraph.firstChild
        while (inlineNode != null) {
            if (inlineNode !is Text && inlineNode !is SoftLineBreak) return false
            inlineNode = inlineNode.next
        }
        val split = inline.splitVisibleRange(selectedRange) ?: return false
        val edit = list.replaceLineWithPlainLines(itemPath, lineIndex, split.before, lines, split.after,
                                                  nextVisible, enableWikilinks) ?: return false
        replaceRange(block.range.min, block.range.max, edit.source, selectedStart = edit.selectionOffset)
        setFormattedListSelection(blockId, itemPath, edit.focusLine, TextRange(edit.focusVisibleOffset))
        formattedListFocusTarget = itemPath to edit.focusLine
        formattedSelection = TextRange.Zero
        formattedComposition = null
        formattedBlockFocusTarget = null
        return true
    }

    private fun hasWellFormedUtf16(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            when {
                value[index].isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
                    index += 2
                }
                value[index].isLowSurrogate() -> return false
                else -> index++
            }
        }
        return true
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
        val target = MarkdownDocumentCodec.parse(candidate, parserPlugins).blocks.firstOrNull {
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
        val parsed = MarkdownDocumentCodec.parse(edit.source, parserPlugins)
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
        publishValue(next)
        pendingListExit = null
        if (next.text != formattedBlockSelection?.source) formattedBlockSelection = null
        if (next.text != formattedListItemSelection?.source) formattedListItemSelection = null
        if (next.text != formattedTableCellSelection?.source) formattedTableCellSelection = null
    }

    private fun snapshot(): EditorSnapshot = EditorSnapshot(
        value, activeFormattedBlockId, formattedSelection, formattedComposition,
        activeFormattedListPath, activeFormattedListLine, formattedListSelection, formattedListComposition,
        pendingListExit,
        formattedBlockSelection,
        formattedListItemSelection,
        formattedTableCellSelection,
    )

    private fun restore(snapshot: EditorSnapshot) {
        publishValue(snapshot.value)
        activeFormattedBlockId = snapshot.blockId
        formattedSelection = snapshot.selection
        formattedComposition = snapshot.composition
        activeFormattedListPath = snapshot.listPath
        activeFormattedListLine = snapshot.listLine
        formattedListSelection = snapshot.listSelection
        formattedListComposition = snapshot.listComposition
        pendingListExit = snapshot.pendingListExit
        formattedBlockSelection = snapshot.blockSelection
        formattedListItemSelection = snapshot.listItemSelection
        formattedTableCellSelection = snapshot.tableCellSelection
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

    /** Synchronous changes let a mounted host observe every edit, even within one Compose frame. */
    internal fun addValueObserver(observer: (TextFieldValue) -> Unit) { valueObservers += observer }
    internal fun removeValueObserver(observer: (TextFieldValue) -> Unit) { valueObservers -= observer }

    private fun publishValue(next: TextFieldValue) {
        if (next == value) return
        value = next
        pendingValueNotifications.addLast(next)
        if (notifyingValueObservers) return
        notifyingValueObservers = true
        try {
            while (pendingValueNotifications.isNotEmpty()) {
                val emitted = pendingValueNotifications.removeFirst()
                valueObservers.toList().forEach { it(emitted) }
            }
        } finally {
            pendingValueNotifications.clear()
            notifyingValueObservers = false
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
