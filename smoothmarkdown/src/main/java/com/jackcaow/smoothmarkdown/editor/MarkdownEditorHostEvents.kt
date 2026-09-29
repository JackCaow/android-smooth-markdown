package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Separates committed source changes from IME composition and selection-only updates. */
internal class MarkdownEditorHostEvents(initialValue: TextFieldValue) {
    private var lastCommittedText = initialValue.text
    private var lastSelection = initialValue.selection

    fun accept(
        value: TextFieldValue,
        onChanged: ((String) -> Unit)?,
        onSelectionChanged: ((TextRange) -> Unit)?,
    ) {
        if (value.selection != lastSelection) {
            lastSelection = value.selection
            onSelectionChanged?.invoke(value.selection)
        }
        val composition = value.composition
        val isComposing = composition != null && !composition.collapsed &&
            composition.min >= 0 && composition.max <= value.text.length
        if (!isComposing && value.text != lastCommittedText) {
            lastCommittedText = value.text
            onChanged?.invoke(value.text)
        }
    }
}
