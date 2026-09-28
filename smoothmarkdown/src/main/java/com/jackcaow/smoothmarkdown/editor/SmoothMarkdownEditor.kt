package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.SmoothMarkdown

/** Source editor with preview and split layouts; formatted-block editing is still pending. */
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
