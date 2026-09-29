package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditorThemeTest {
    @Test fun localEditorOverridesOnlySpecifiedAmbientFields() {
        val ambient = MarkdownEditorTheme(
            toolbarColor = Color(0xFF132030),
            toolbarIconColor = Color(0xFF223344),
            sourcePadding = 20.dp,
            tableBorderColor = Color(0xFF556677),
        )
        val local = MarkdownEditorTheme(
            toolbarIconColor = Color(0xFFABCDEF),
            sourcePadding = 8.dp,
            selectionColor = Color(0xFF998877),
        )

        val effective = ambient.merge(local)

        assertEquals(Color(0xFF132030), effective.toolbarColor)
        assertEquals(Color(0xFFABCDEF), effective.toolbarIconColor)
        assertEquals(8.dp, effective.sourcePadding)
        assertEquals(Color(0xFF556677), effective.tableBorderColor)
        assertEquals(Color(0xFF998877), effective.selectionColor)
        assertEquals(ambient, ambient.merge(null))
    }
}
