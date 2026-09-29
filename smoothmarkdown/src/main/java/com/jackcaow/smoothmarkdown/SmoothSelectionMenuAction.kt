package com.jackcaow.smoothmarkdown

/** A host action appended to the Reader's native text-selection menu. */
data class SmoothSelectionMenuAction(
    /** Stable key, unique among actions supplied to one Reader. */
    val key: String,
    val label: String,
    /** Receives selected visible text after the Reader's plain-text anchor filter. */
    val onClick: (selectedText: String) -> Unit,
)
