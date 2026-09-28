package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamMarkdownBufferTest {
    @Test fun batchesRapidChunksAndFlushesOnCompletion() {
        val buffer = StreamMarkdownBuffer(startMillis = 100)
        assertEquals(40L, buffer.append("Hello", 110))
        assertEquals("", buffer.visibleText)
        assertEquals(20L, buffer.append(" world", 130))
        buffer.flush(150)
        assertEquals("Hello world", buffer.visibleText)
        assertEquals(40L, buffer.append("!", 160))
        buffer.finish(161)
        assertEquals("Hello world!", buffer.visibleText)
    }

    @Test fun publishesImmediatelyAfterIntervalAndResetsForNewStream() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertNull(buffer.append("A", 50))
        assertEquals("A", buffer.visibleText)
        buffer.reset(200)
        assertEquals("", buffer.fullText)
        assertEquals(40L, buffer.append("B", 210))
        assertEquals("", buffer.visibleText)
    }

    @Test fun keepsPartialHtmlLookingTextVisibleByDefault() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertNull(buffer.append("lead <font colo", 50))
        assertEquals("lead <font colo", buffer.visibleText)
    }

    @Test fun htmlModeWithholdsPartialTagThenFlushesOnCompletion() {
        val buffer = StreamMarkdownBuffer(startMillis = 0, enableHtml = true)
        assertNull(buffer.append("lead <font colo", 50))
        assertEquals("lead ", buffer.visibleText)
        buffer.finish(51)
        assertEquals("lead <font colo", buffer.visibleText)
    }

    @Test fun splitHtmlTagStaysHiddenUntilItsClosingChunkAndSwitchDoesNotLoseSource() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertNull(buffer.append("lead <font colo", 50))
        val incomplete = StreamSnapshot(buffer.visibleText)
        assertEquals("lead ", incomplete.renderText(enableHtml = true))
        assertEquals("lead <font colo", incomplete.renderText(enableHtml = false))

        assertNull(buffer.append("r='red'>red</font> tail", 100))
        val completeTag = StreamSnapshot(buffer.visibleText)
        assertEquals("lead <font color='red'>red</font> tail", completeTag.renderText(enableHtml = true))
        assertEquals(completeTag.text, completeTag.renderText(enableHtml = false))
    }

    @Test fun completedStreamRevealsUnfinishedTagAsLiteralSource() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertNull(buffer.append("lead <font colo", 50))
        buffer.finish(51)
        assertEquals("lead <font colo", StreamSnapshot(buffer.visibleText, complete = true).renderText(enableHtml = true))
    }
}
