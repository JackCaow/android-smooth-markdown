package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em

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

    @Test fun splitSubscriptAndSuperscriptTagsRenderAtReducedSizeAfterCompletion() {
        val buffer = StreamMarkdownBuffer(startMillis = 0, enableHtml = true)
        assertNull(buffer.append("H<sub", 50))
        assertEquals("H", buffer.visibleText)
        assertNull(buffer.append(">2</sub>O x<sup", 100))
        assertEquals("H<sub>2</sub>O x", buffer.visibleText)
        assertNull(buffer.append(">2</sup>", 150))
        val rendered = inlineText(parseMarkdown(buffer.visibleText, enableHtml = true).firstChild!!, enableHtml = true)
        assertEquals("H2O x2", rendered.text)
        assertEquals(listOf(BaselineShift.Subscript, BaselineShift.Superscript),
            rendered.spanStyles.map { it.item.baselineShift })
        assertEquals(listOf(0.75.em, 0.75.em), rendered.spanStyles.map { it.item.fontSize })
    }

    @Test fun splitKeyboardTagAppearsAsOneInlineKeyAfterCompletion() {
        val buffer = StreamMarkdownBuffer(startMillis = 0, enableHtml = true)
        assertNull(buffer.append("Press <kb", 50))
        assertEquals("Press ", buffer.visibleText)
        assertNull(buffer.append("d>Ctrl</kbd>+C", 100))
        val rendered = inlineRender(parseMarkdown(buffer.visibleText, enableHtml = true).firstChild!!, enableHtml = true)
        assertEquals("Press Ctrl+C", rendered.text.text)
        assertEquals(listOf("Ctrl"), rendered.kbds.values.toList())
    }

    @Test fun completedStreamRevealsUnfinishedTagAsLiteralSource() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertNull(buffer.append("lead <font colo", 50))
        buffer.finish(51)
        assertEquals("lead <font colo", StreamSnapshot(buffer.visibleText, complete = true).renderText(enableHtml = true))
    }

    @Test fun cumulativePrefixHandlesConflationAndLateSubscription() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        assertEquals(40L, buffer.appendPrefix("Hello", 10))
        assertEquals(20L, buffer.appendPrefix("Hello world", 30))
        buffer.flush(50)
        assertEquals("Hello world", buffer.visibleText)
        assertEquals("Hello world", buffer.fullText)

        val late = StreamMarkdownBuffer(startMillis = 100)
        assertEquals(40L, late.appendPrefix("Hello world", 110))
        late.finish(150)
        assertEquals("Hello world", late.visibleText)
    }

    @Test fun replacingPrefixResetsPreviousConversation() {
        val buffer = StreamMarkdownBuffer(startMillis = 0)
        buffer.appendPrefix("First response", 50)
        assertNull(buffer.appendPrefix("New", 60))
        assertEquals("New", buffer.fullText)
        assertEquals("New", buffer.visibleText)
        buffer.finish(100)
        assertEquals("New", buffer.visibleText)
    }
}
