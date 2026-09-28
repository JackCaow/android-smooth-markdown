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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                "Code" to MarkdownEditorCommand.CODE_BLOCK,
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

/** Paragraphs, ATX headings, and fenced code expose their content; other blocks stay source-visible. */
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
                    if (editableText != null) {
                        BasicTextField(
                            value = editableText,
                            onValueChange = { controller.replaceFormattedBlockText(block.id, it) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
