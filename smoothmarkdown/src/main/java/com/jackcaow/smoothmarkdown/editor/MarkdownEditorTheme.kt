package com.jackcaow.smoothmarkdown.editor

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** Visual overrides for [SmoothMarkdownEditor]. Null values inherit the ambient Material theme. */
data class MarkdownEditorTheme(
    val editorColor: Color? = null,
    val editorBorderColor: Color? = null,
    val editorBorderRadius: Dp? = null,
    val toolbarColor: Color? = null,
    val toolbarIconColor: Color? = null,
    val toolbarActiveIconColor: Color? = null,
    val toolbarActiveBackgroundColor: Color? = null,
    val toolbarButtonRadius: Dp? = null,
    val dividerColor: Color? = null,
    val searchBarColor: Color? = null,
    val suggestionPanelColor: Color? = null,
    val suggestionSelectedBackgroundColor: Color? = null,
    val sourceColor: Color? = null,
    val sourceTextStyle: TextStyle? = null,
    val previewColor: Color? = null,
    val selectionColor: Color? = null,
    val blockColor: Color? = null,
    val blockBorderColor: Color? = null,
    val blockHeaderColor: Color? = null,
    val blockHeaderTextStyle: TextStyle? = null,
    val blockBorderRadius: Dp? = null,
    val tableBorderColor: Color? = null,
    val tableHeaderColor: Color? = null,
    val tableSelectionColor: Color? = null,
    val tableActiveBorderColor: Color? = null,
    val contentPadding: Dp? = null,
    val sourcePadding: Dp? = null,
    val previewPadding: Dp? = null,
    val blockPadding: Dp? = null,
    val tablePadding: Dp? = null,
) {
    /** Local values override inherited values, matching Flutter's editorTheme precedence. */
    fun merge(local: MarkdownEditorTheme?): MarkdownEditorTheme = if (local == null) this else copy(
        editorColor = local.editorColor ?: editorColor,
        editorBorderColor = local.editorBorderColor ?: editorBorderColor,
        editorBorderRadius = local.editorBorderRadius ?: editorBorderRadius,
        toolbarColor = local.toolbarColor ?: toolbarColor,
        toolbarIconColor = local.toolbarIconColor ?: toolbarIconColor,
        toolbarActiveIconColor = local.toolbarActiveIconColor ?: toolbarActiveIconColor,
        toolbarActiveBackgroundColor = local.toolbarActiveBackgroundColor ?: toolbarActiveBackgroundColor,
        toolbarButtonRadius = local.toolbarButtonRadius ?: toolbarButtonRadius,
        dividerColor = local.dividerColor ?: dividerColor,
        searchBarColor = local.searchBarColor ?: searchBarColor,
        suggestionPanelColor = local.suggestionPanelColor ?: suggestionPanelColor,
        suggestionSelectedBackgroundColor = local.suggestionSelectedBackgroundColor ?: suggestionSelectedBackgroundColor,
        sourceColor = local.sourceColor ?: sourceColor,
        sourceTextStyle = local.sourceTextStyle ?: sourceTextStyle,
        previewColor = local.previewColor ?: previewColor,
        selectionColor = local.selectionColor ?: selectionColor,
        blockColor = local.blockColor ?: blockColor,
        blockBorderColor = local.blockBorderColor ?: blockBorderColor,
        blockHeaderColor = local.blockHeaderColor ?: blockHeaderColor,
        blockHeaderTextStyle = local.blockHeaderTextStyle ?: blockHeaderTextStyle,
        blockBorderRadius = local.blockBorderRadius ?: blockBorderRadius,
        tableBorderColor = local.tableBorderColor ?: tableBorderColor,
        tableHeaderColor = local.tableHeaderColor ?: tableHeaderColor,
        tableSelectionColor = local.tableSelectionColor ?: tableSelectionColor,
        tableActiveBorderColor = local.tableActiveBorderColor ?: tableActiveBorderColor,
        contentPadding = local.contentPadding ?: contentPadding,
        sourcePadding = local.sourcePadding ?: sourcePadding,
        previewPadding = local.previewPadding ?: previewPadding,
        blockPadding = local.blockPadding ?: blockPadding,
        tablePadding = local.tablePadding ?: tablePadding,
    )
}

/** Install a shared editor theme above one or more editors; a per-editor theme takes precedence. */
val LocalMarkdownEditorTheme = compositionLocalOf { MarkdownEditorTheme() }
