package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.MarkdownSelectionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTextSelectionTest {
    @Test fun selectsOnlyParagraphUnderPress() {
        val first = target("第一段", 0f)
        val second = target("第二段", 40f)
        assertEquals(TextRange(3, 6), paragraphSelectionRange(Offset(25f, 50f),
            listOf(first, second), listOf(first.text, second.text)))
    }

    @Test fun duplicateTextUsesVisualOccurrence() {
        val first = target("重复", 0f)
        val second = target("重复", 40f)
        assertEquals(TextRange(2, 4), paragraphSelectionRange(Offset(25f, 50f),
            listOf(second, first), listOf(first.text, second.text)))
    }

    @Test fun includesOtherSelectableTextBeforeTargetInGlobalOffset() {
        val paragraph = target("正文", 40f)
        assertEquals(TextRange(2, 4), paragraphSelectionRange(Offset(25f, 50f),
            listOf(paragraph), listOf(AnnotatedString("•"), AnnotatedString(" "), paragraph.text)))
    }

    @Test fun multilineCodeSelectsLineUnderPress() {
        val code = MarkdownSelectionTarget(Any(), Rect(0f, 0f, 100f, 80f),
            AnnotatedString("one\ntwo\nthree")) { 5 }
        assertEquals(TextRange(4, 7), paragraphSelectionRange(Offset(25f, 45f),
            listOf(code), listOf(code.text)))
    }

    @Test fun doesNotSelectAnotherParagraphFromBlankArea() {
        val first = target("第一段", 0f)
        assertNull(paragraphSelectionRange(Offset(25f, 35f), listOf(first), listOf(first.text)))
    }

    private fun target(text: String, top: Float) = MarkdownSelectionTarget(
        key = Any(), boundsInWindow = Rect(0f, top, 100f, top + 20f), text = AnnotatedString(text))
}
