package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSearchInstrumentedTest {
    @Test fun navigationAndReplacementUseTheOriginalUtf16Spelling() {
        val controller = MarkdownEditorController("İX i\u0307X ΟΣ")
        assertEquals(listOf(TextRange(1, 2), TextRange(5, 6)), controller.findMatches("x"))
        assertEquals(listOf(TextRange(0, 2), TextRange(3, 6)), controller.findMatches("i\u0307X"))
        assertEquals(listOf(TextRange(8, 9)), controller.findMatches("ς"))
        assertEquals(emptyList<TextRange>(), MarkdownEditorController("İX").findMatches("ı\u0307X"))
        assertEquals(emptyList<TextRange>(), MarkdownEditorController("ı\u0307X").findMatches("İX"))
        controller.setSelection(2)
        assertEquals(TextRange(3, 6), controller.selectNextMatch("İX"))
        assertEquals("i\u0307X", controller.selectedText)
        controller.replaceSelection("new")
        assertEquals("İX new ΟΣ", controller.text)
    }
}
