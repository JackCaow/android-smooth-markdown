package com.jackcaow.smoothmarkdown.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStreamSessionTest {
    @Test fun accumulatesSnapshotsAndFreezesCompletedBubble() {
        val session = MarkdownStreamSession()
        session.append("**Hel")
        session.append("lo**")
        assertEquals("**Hello**", session.prefixes.value)
        assertEquals("**Hello**", session.finish())
        session.append(" stale")
        assertEquals("**Hello**", session.prefixes.value)
        assertTrue(session.closed)
    }

    @Test fun cancelledSessionCannotLeakIntoNewConversation() {
        val old = MarkdownStreamSession()
        old.append("old")
        old.cancel()
        val replacement = MarkdownStreamSession()
        replacement.append("new")
        old.append(" delayed")
        assertEquals("old", old.prefixes.value)
        assertEquals("new", replacement.prefixes.value)
    }
}
