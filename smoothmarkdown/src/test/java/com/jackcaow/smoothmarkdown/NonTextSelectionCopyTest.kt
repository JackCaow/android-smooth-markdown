package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class NonTextSelectionCopyTest {
    @Test fun removesOnlyRegisteredCompleteOverlayRows() {
        val random = Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val anchor = "smd" + buildString { repeat(497) { append(alphabet[random.nextInt(alphabet.length)]) } }
        val other = "smd" + "b".repeat(497)
        val registered = setOf(anchor)
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before\n$anchor\nafter", registered))
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before\n$anchor\n$anchor\nafter", registered))
        assertEquals("before\n$other\nafter", removeNonTextSelectionOverlayLines("before\n$other\nafter", registered))
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before\n${anchor.drop(230)}\nafter", registered))
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before\n${anchor.take(230)}\nafter", registered))
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before${anchor.take(230)}\n${anchor.drop(230)}after", registered))
        assertEquals("before\nafter", removeNonTextSelectionOverlayLines("before\n$anchor\nafter", registered))
        assertEquals("before\u00A0after", removeNonTextSelectionOverlayLines("before\u00A0after", registered))
        assertEquals("before\n\u00A0\nafter", removeNonTextSelectionOverlayLines("before\n\u00A0\nafter", registered))
        assertEquals("before\n after", removeNonTextSelectionOverlayLines("before\n after", registered))
        assertEquals("normal prose", removeNonTextSelectionOverlayLines("normal prose", registered))
    }
}
