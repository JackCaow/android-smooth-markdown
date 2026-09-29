package com.jackcaow.smoothmarkdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonTextSelectionCopyTest {
    private val anchor = "smd" + "a".repeat(497)
    private val tagged = buildAnnotatedString {
        append(anchor)
        addStringAnnotation(nonTextAnchorAnnotationTag, anchor, 0, length)
    }

    @Test fun removesEveryLengthOfSelectedAnchorWithoutMatchingItsCharacters() {
        for (length in listOf(1, 2, 15, 16, 230, anchor.length)) {
            for (start in listOf(0, 211, anchor.length - length).filter { it + length <= anchor.length }) {
                val selected = tagged.subSequence(start, start + length)
                val result = visibleSelectedText(
                    listOf(AnnotatedString("before"), selected, AnnotatedString("after")), setOf(anchor))
                assertEquals("selection $start:$length", "before\nafter", result.text)
                assertTrue("selection $start:$length was not identified", result.hadAnchor)
            }
        }
    }

    @Test fun preservesLegalUserTextEvenWhenItMatchesRegisteredAnchor() {
        val legal = listOf(
            AnnotatedString(anchor),
            AnnotatedString(anchor.take(1)),
            AnnotatedString("before\n\u00A0\nafter"),
            AnnotatedString("smd"),
        )
        val result = visibleSelectedText(legal, setOf(anchor))
        assertEquals(legal.joinToString("\n") { it.text }, result.text)
        assertFalse(result.hadAnchor)
    }

    @Test fun ignoresUnregisteredAnnotationAndPreservesMixedText() {
        val mixed = buildAnnotatedString {
            append("before")
            val start = length
            append(anchor.take(2))
            addStringAnnotation(nonTextAnchorAnnotationTag, anchor, start, length)
            append("after")
        }
        assertEquals("beforeafter", visibleSelectedText(listOf(mixed), setOf(anchor)).text)
        assertEquals(mixed.text, visibleSelectedText(listOf(mixed), emptySet()).text)
    }

    @Test fun legacyToolbarCopiesAnnotatedSelectionAndDelegatesOrdinaryCopy() {
        val platform = RecordingTextToolbar()
        var ordinaryCopies = 0
        var copied: String? = null
        var selected = visibleSelectedText(listOf(tagged.subSequence(211, 212)), setOf(anchor))
        val toolbar = ReaderCopyTextToolbar(platform, { selected }, { copied = it }, true)
        toolbar.showMenu(Rect.Zero, { ordinaryCopies++ }, null, null, null)
        platform.copy!!.invoke()
        assertEquals("", copied)
        assertEquals(0, ordinaryCopies)

        selected = visibleSelectedText(listOf(AnnotatedString("hello")), setOf(anchor))
        platform.copy!!.invoke()
        assertEquals(1, ordinaryCopies)
        assertEquals("", copied)

        ReaderCopyTextToolbar(platform, { selected }, { copied = it }, false)
            .showMenu(Rect.Zero, { ordinaryCopies++ }, null, null, null)
        assertEquals(null, platform.copy)
    }

    private class RecordingTextToolbar : TextToolbar {
        var copy: (() -> Unit)? = null
        override val status: TextToolbarStatus = TextToolbarStatus.Hidden
        override fun hide() = Unit
        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) { copy = onCopyRequested }
    }
}
