package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color

/** Resolves the newer six-edge API before falling back to the legacy color alias. */
internal fun resolveTableBorder(sheet: MarkdownStyleSheet, defaultColor: Color): MarkdownTableBorder =
    sheet.tableBorder ?: MarkdownTableBorder.all(sheet.tableBorderColor ?: defaultColor)

/** Assign each shared rule to exactly one cell, so adjacent cells never double its width. */
internal fun tableCellBorderEdges(
    border: MarkdownTableBorder,
    rowIndex: Int,
    rowCount: Int,
    columnIndex: Int,
    columnCount: Int,
): MarkdownTableBorder {
    require(rowIndex in 0 until rowCount && columnIndex in 0 until columnCount)
    return MarkdownTableBorder(
        top = border.top.takeIf { rowIndex == 0 },
        right = if (columnIndex == columnCount - 1) border.right else border.verticalInside,
        bottom = if (rowIndex == rowCount - 1) border.bottom else border.horizontalInside,
        left = border.left.takeIf { columnIndex == 0 },
    )
}
