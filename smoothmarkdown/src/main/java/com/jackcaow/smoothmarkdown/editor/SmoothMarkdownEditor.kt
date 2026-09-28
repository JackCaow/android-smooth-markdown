package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.SmoothMarkdown

/** Source editor, preview, split view, and a focused formatted-block editing surface. */
@Composable
fun SmoothMarkdownEditor(
    controller: MarkdownEditorController,
    modifier: Modifier = Modifier,
    onSave: ((String) -> Unit)? = null,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                MarkdownEditorMode.entries.forEach { mode ->
                    TextButton(onClick = { controller.mode = mode }) {
                        Text(mode.name.lowercase().replaceFirstChar(Char::uppercaseChar))
                    }
                }
            }
            if (onSave != null) {
                Button(onClick = {
                    onSave(controller.text)
                    controller.markSaved()
                }, enabled = controller.isDirty) { Text("Save") }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { controller.undo() }, enabled = controller.canUndo) { Text("Undo") }
            TextButton(onClick = { controller.redo() }, enabled = controller.canRedo) { Text("Redo") }
            listOf(
                "B" to MarkdownEditorCommand.BOLD,
                "I" to MarkdownEditorCommand.ITALIC,
                "H1" to MarkdownEditorCommand.HEADING1,
                "List" to MarkdownEditorCommand.UNORDERED_LIST,
                "Task" to MarkdownEditorCommand.TASK_LIST,
                (if (controller.mode == MarkdownEditorMode.FORMATTED) "Inline code" else "Code") to
                    (if (controller.mode == MarkdownEditorMode.FORMATTED) MarkdownEditorCommand.INLINE_CODE else MarkdownEditorCommand.CODE_BLOCK),
                "Link" to MarkdownEditorCommand.LINK,
                "Table" to MarkdownEditorCommand.TABLE,
            ).forEach { (label, command) ->
                TextButton(onClick = { controller.applyCommand(command) }) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (controller.mode) {
            MarkdownEditorMode.SOURCE -> SourcePane(controller, Modifier.weight(1f))
            MarkdownEditorMode.PREVIEW -> SmoothMarkdown(controller.text, Modifier.weight(1f))
            MarkdownEditorMode.SPLIT -> Row(Modifier.weight(1f)) {
                SourcePane(controller, Modifier.weight(1f))
                SmoothMarkdown(controller.text, Modifier.weight(1f))
            }
            MarkdownEditorMode.FORMATTED -> FormattedBlockPane(controller, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SourcePane(controller: MarkdownEditorController, modifier: Modifier) {
    BasicTextField(
        value = controller.value,
        onValueChange = controller::updateFromInput,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

/** Paragraphs, ATX headings, fenced code, and GFM tables expose source-backed content. */
@Composable
private fun FormattedBlockPane(controller: MarkdownEditorController, modifier: Modifier) {
    val blocks = controller.semanticDocument().blocks
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        blocks.forEach { block ->
            val editableText = MarkdownFormattedBlock.text(block)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                color = if (block.kind == MarkdownBlockKind.CODE) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = when (block.kind) {
                                MarkdownBlockKind.HEADING -> "Heading ${block.headingLevel}"
                                MarkdownBlockKind.CODE -> "Code${block.language?.let { " · $it" }.orEmpty()}"
                                else -> block.kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (block.kind == MarkdownBlockKind.HEADING && editableText != null) {
                            Row {
                                (1..3).forEach { level ->
                                    TextButton(onClick = { controller.setSemanticHeadingLevel(block.id, level) }) {
                                        Text("H$level")
                                    }
                                }
                            }
                        }
                    }
                    if (block.kind == MarkdownBlockKind.TABLE) {
                        val table = controller.semanticTable(block.id)
                        if (table != null) FormattedTable(controller, block.id, table)
                        else Text(block.source, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    } else if (editableText != null) {
                        val inline = MarkdownFormattedBlock.inline(block)
                        val rawSelection = if (controller.activeFormattedBlockId == block.id) controller.formattedSelection else TextRange.Zero
                        val visibleLength = inline?.visible?.length ?: editableText.length
                        val fieldSelection = TextRange(rawSelection.start.coerceIn(0, visibleLength), rawSelection.end.coerceIn(0, visibleLength))
                        val rawComposition = if (controller.activeFormattedBlockId == block.id) controller.formattedComposition else null
                        val fieldComposition = rawComposition?.let {
                            TextRange(it.start.coerceIn(0, visibleLength), it.end.coerceIn(0, visibleLength))
                        }
                        BasicTextField(
                            value = TextFieldValue(inline?.annotated(MaterialTheme.colorScheme.primary) ?: androidx.compose.ui.text.AnnotatedString(editableText), fieldSelection, fieldComposition),
                            onValueChange = { next ->
                                if (inline != null) {
                                    controller.setFormattedSelection(block.id, next.selection, next.composition)
                                    if (next.text != inline.visible) controller.replaceFormattedInlineText(block.id, next.text, next.selection, next.composition)
                                } else {
                                    controller.replaceFormattedBlockText(block.id, next.text)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onFocusChanged {
                                if (it.isFocused) controller.setFormattedSelection(block.id, fieldSelection)
                            },
                            textStyle = when (block.kind) {
                                MarkdownBlockKind.HEADING -> MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                MarkdownBlockKind.CODE -> MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                )
                                else -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                            },
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                    } else {
                        Text(
                            block.source,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattedTable(controller: MarkdownEditorController, blockId: String, table: MarkdownSourceTable) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        fun displayCell(raw: String) = raw.replace("\\|", "|")
        @Composable fun cell(raw: String, header: Boolean, rowIndex: Int, columnIndex: Int) {
            BasicTextField(
                value = displayCell(raw),
                onValueChange = { next ->
                    controller.editSemanticTable(blockId) { it.replaceCell(rowIndex, columnIndex, next, header) }
                },
                modifier = Modifier.width(140.dp).padding(4.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        Row {
            table.headers.forEachIndexed { column, raw -> cell(raw, true, 0, column) }
        }
        table.rows.forEachIndexed { row, cells ->
            Row { cells.forEachIndexed { column, raw -> cell(raw, false, row, column) } }
        }
        Row {
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.insertRowAfter(it.rows.lastIndex) } }) { Text("+ Row") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.insertColumnAfter(it.columnCount - 1) } }) { Text("+ Column") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.deleteRow(it.rows.lastIndex) } }, enabled = table.rows.isNotEmpty()) { Text("− Row") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.deleteColumn(it.columnCount - 1) } }, enabled = table.columnCount > 1) { Text("− Column") }
        }
    }
}
