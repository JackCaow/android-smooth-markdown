package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSlashCommandsTest {
    @Test fun sourceSlashCommandReplacesOnlyTriggerAndUndoRestoresCaret() {
        val controller = MarkdownEditorController("Before\n\n/he\n\nAfter")
        val triggerStart = controller.text.indexOf("/he")
        controller.setSelection(triggerStart + 3)
        val trigger = MarkdownSlashCommands.match(controller)
        assertEquals(MarkdownSlashTrigger(TextRange(triggerStart, triggerStart + 3), "he"), trigger)
        assertEquals(listOf(MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3), MarkdownSlashCommands.suggestions(trigger!!).map { it.second })
        assertTrue(MarkdownSlashCommands.apply(controller, trigger, MarkdownEditorCommand.HEADING2))
        assertEquals("Before\n\n## \n\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals("Before\n\n/he\n\nAfter", controller.text)
        assertEquals(TextRange(triggerStart + 3), controller.selection)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("Before\n\n## \n\nAfter", controller.text)
    }

    @Test fun formattedParagraphCanInvokeSlashCommandWithoutChangingNeighboringSource() {
        val controller = MarkdownEditorController("**Keep**\n\n/ta\n\nAfter")
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.first { it.source == "/ta" }
        controller.setFormattedSelection(block.id, TextRange(3))
        val trigger = MarkdownSlashCommands.match(controller)
        assertNotNull(trigger)
        assertTrue(MarkdownSlashCommands.apply(controller, trigger!!, MarkdownEditorCommand.TASK_LIST))
        assertEquals("**Keep**\n\n- [ ] \n\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals("**Keep**\n\n/ta\n\nAfter", controller.text)
    }

    @Test fun ignoresCodeBlocksWhitespaceAndStaleTriggers() {
        val code = MarkdownEditorController("```\n/he\n```")
        code.setSelection(code.text.indexOf("/he") + 3)
        assertNull(MarkdownSlashCommands.match(code))

        val spaced = MarkdownEditorController("/he there")
        spaced.setSelection(spaced.text.length)
        assertNull(MarkdownSlashCommands.match(spaced))

        val stale = MarkdownEditorController("/he")
        stale.setSelection(3)
        val trigger = MarkdownSlashCommands.match(stale)!!
        stale.text = "/different"
        assertFalse(MarkdownSlashCommands.apply(stale, trigger, MarkdownEditorCommand.HEADING1))
        assertEquals("/different", stale.text)
    }
}
