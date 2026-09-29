package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TableBordersTest {
    @Test fun sixEdgesOverrideLegacyColorAndKeepIndividualWidths() {
        val border = MarkdownTableBorder(
            top = MarkdownTableBorderSide(Color.Red, 2.dp),
            right = MarkdownTableBorderSide(Color.Green, 3.dp),
            bottom = MarkdownTableBorderSide(Color.Blue, 4.dp),
            left = MarkdownTableBorderSide(Color.Yellow, 5.dp),
            horizontalInside = MarkdownTableBorderSide(Color.Magenta, 6.dp),
            verticalInside = MarkdownTableBorderSide(Color.Cyan, 7.dp),
        )
        val resolved = resolveTableBorder(
            MarkdownStyleSheet(tableBorderColor = Color.Black, tableBorder = border),
            Color.White,
        )
        assertEquals(border, resolved)
        assertEquals(6.dp, resolved.horizontalInside?.width)
        assertEquals(7.dp, resolved.verticalInside?.width)
    }

    @Test fun defaultGridUsesOneDpAndLegacyColorStillApplies() {
        val fallback = resolveTableBorder(MarkdownStyleSheet.default(), Color.Green)
        assertEquals(MarkdownTableBorder.all(Color.Green), fallback)
        assertEquals(MarkdownTableBorder.all(Color.Red), resolveTableBorder(MarkdownStyleSheet(tableBorderColor = Color.Red), Color.Green))
        assertEquals(Color(0xFFD0D7DE), resolveTableBorder(MarkdownStyleSheet.light(), Color.Green).top?.color)
        assertEquals(Color(0xFF30363D), resolveTableBorder(MarkdownStyleSheet.dark(), Color.Green).bottom?.color)
        assertEquals(MarkdownTableBorder(), resolveTableBorder(MarkdownStyleSheet(tableBorder = MarkdownTableBorder()), Color.Green))
    }

    @Test fun insideRulesAreOwnedByOneCellAndOuterRulesStayOutside() {
        val border = MarkdownTableBorder.all(Color.Red, 3.dp)
        val topLeft = tableCellBorderEdges(border, 0, 2, 0, 2)
        val topRight = tableCellBorderEdges(border, 0, 2, 1, 2)
        val bottomLeft = tableCellBorderEdges(border, 1, 2, 0, 2)
        val bottomRight = tableCellBorderEdges(border, 1, 2, 1, 2)

        assertEquals(border.top, topLeft.top)
        assertEquals(border.left, topLeft.left)
        assertEquals(border.verticalInside, topLeft.right)
        assertEquals(border.horizontalInside, topLeft.bottom)
        assertNull(topRight.left)
        assertNull(bottomLeft.top)
        assertEquals(border.right, topRight.right)
        assertEquals(border.bottom, bottomLeft.bottom)
        assertNull(bottomRight.top)
        assertNull(bottomRight.left)
        assertEquals(border.right, bottomRight.right)
        assertEquals(border.bottom, bottomRight.bottom)
    }

    @Test fun missingSidesAndNegativeWidthsAreHandled() {
        val border = MarkdownTableBorder(horizontalInside = MarkdownTableBorderSide(Color.Blue, 0.dp))
        val cell = tableCellBorderEdges(border, 0, 2, 0, 2)
        assertNull(cell.top)
        assertNull(cell.left)
        assertNull(cell.right)
        assertEquals(0.dp, cell.bottom?.width)
        assertThrows(IllegalArgumentException::class.java) { MarkdownTableBorderSide(Color.Red, (-1).dp) }
        assertThrows(IllegalArgumentException::class.java) { tableCellBorderEdges(border, 2, 2, 0, 2) }
    }
}
