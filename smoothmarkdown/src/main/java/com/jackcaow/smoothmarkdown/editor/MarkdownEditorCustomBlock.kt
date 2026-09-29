package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.Composable

/** Host view for an explicitly matched source-backed formatted block. */
typealias MarkdownEditorCustomBlockBuilder = @Composable (MarkdownEditorCustomBlockContext) -> Unit

/** Host editing surface for an explicitly matched source-backed formatted block. */
typealias MarkdownEditorCustomBlockEditorBuilder = @Composable (MarkdownEditorCustomBlockEditorContext) -> Unit

/** The callback is tied to one document snapshot; stale edits return false without changing source. */
class MarkdownEditorCustomBlockContext internal constructor(
    val blockId: String,
    val blockType: MarkdownBlockKind,
    val markdown: String,
    val plainText: String,
    val edit: () -> Unit,
    val replaceMarkdown: (String) -> Boolean,
    val delete: () -> Boolean,
)

/** Editing callback for a host-owned block. [finishEditing] keeps the source untouched. */
class MarkdownEditorCustomBlockEditorContext internal constructor(
    val blockId: String,
    val blockType: MarkdownBlockKind,
    val markdown: String,
    val plainText: String,
    val replaceMarkdown: (String) -> Boolean,
    val finishEditing: () -> Unit,
    val delete: () -> Boolean,
)
