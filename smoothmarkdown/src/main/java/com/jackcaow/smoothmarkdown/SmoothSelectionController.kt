package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.ui.text.TextRange

/** Programmatic control of the reader's selectable text region. */
class SmoothSelectionController {
    private var region: SelectionState? = null

    /** The currently selected plain text, including visible Markdown blocks. */
    val selectedText: String get() = region?.selectedTexts?.joinToString("") { it.text }.orEmpty()

    /** Select all text currently registered with the Compose selection region. */
    fun selectAll() { region?.selectAll() }

    /** Select a range in the region's currently registered text. */
    fun select(range: TextRange) { region?.select(range) }

    /** Clear the current text selection. */
    fun clear() { region?.clear() }

    internal fun attach(state: SelectionState) { region = state }

    internal fun detach(state: SelectionState) { if (region === state) region = null }
}
