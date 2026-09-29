package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MarkdownSlashCommandsTest {
    @Test fun sourceSlashCommandReplacesOnlyTriggerAndUndoRestoresCaret() {
        val controller = MarkdownEditorController("Before\n\n/he\n\nAfter")
        val triggerStart = controller.text.indexOf("/he")
        controller.setSelection(triggerStart + 3)
        val trigger = MarkdownSlashCommands.match(controller)
        assertEquals(MarkdownSlashTrigger(TextRange(triggerStart, triggerStart + 3), "he"), trigger)
        assertEquals((1..6).map { MarkdownEditorCommand.valueOf("HEADING$it") } + MarkdownEditorCommand.TASK_LIST,
            MarkdownSlashCommands.suggestions(trigger!!).map { it.command })
        assertTrue(MarkdownSlashCommands.apply(controller, trigger, MarkdownEditorCommand.HEADING2))
        assertEquals("Before\n\n## \n\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals("Before\n\n/he\n\nAfter", controller.text)
        assertEquals(TextRange(triggerStart + 3), controller.selection)
        assertFalse(controller.canUndo)
        assertTrue(controller.redo())
        assertEquals("Before\n\n## \n\nAfter", controller.text)
    }

    @Test fun builtInMenuMatchesFlutterExampleOrderAndSearchAliases() {
        val expected = listOf("Text", "Heading 1", "Heading 2", "Heading 3", "Heading 4", "Heading 5",
            "Heading 6", "Bullet List", "Numbered List", "Task List", "Blockquote", "Code Block",
            "Mermaid Diagram", "Block Math", "Horizontal Rule", "Image", "Table", "Wikilink")
        val emptyQuery = MarkdownSlashTrigger(TextRange(0, 1), "")
        assertEquals(expected, MarkdownSlashCommands.suggestions(emptyQuery).map { it.title })
        assertEquals(expected.dropLast(1), MarkdownSlashCommands.suggestions(emptyQuery, enableWikilinks = false).map { it.title })
        assertEquals(listOf("Task List"), MarkdownSlashCommands.suggestions(emptyQuery.copy(query = "todo")).map { it.title })
        assertEquals(listOf("Image"), MarkdownSlashCommands.suggestions(emptyQuery.copy(query = "photo")).map { it.title })
        assertEquals(listOf("Wikilink"), MarkdownSlashCommands.suggestions(emptyQuery.copy(query = "[[")).map { it.title })
    }

    @Test fun missingBuiltInsUseExistingSourceCommandsAndWikilinkOpensAutocomplete() {
        val cases = listOf(
            MarkdownEditorCommand.HEADING4 to "#### ",
            MarkdownEditorCommand.HEADING5 to "##### ",
            MarkdownEditorCommand.HEADING6 to "###### ",
            MarkdownEditorCommand.IMAGE to "![alt text](image-url)",
            MarkdownEditorCommand.WIKILINK to "[[",
        )
        cases.forEach { (command, expected) ->
            val controller = MarkdownEditorController("/x")
            controller.setSelection(2)
            val trigger = MarkdownSlashCommands.match(controller)!!
            assertTrue(command.name, MarkdownSlashCommands.apply(controller, trigger, command))
            assertEquals(command.name, expected, controller.text)
            assertTrue(command.name, controller.undo())
            assertEquals(command.name, "/x", controller.text)
        }
    }

    @Test fun formattedWikilinkSlashLeavesCaretAtAutocompleteTrigger() {
        val controller = MarkdownEditorController("/wiki")
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.single()
        controller.setFormattedSelection(block.id, TextRange(block.source.length))
        assertTrue(MarkdownSlashCommands.apply(controller, MarkdownSlashCommands.match(controller)!!,
            MarkdownEditorCommand.WIKILINK))
        assertEquals("[[", controller.text)
        assertEquals(TextRange(2), controller.formattedSelection)
        assertTrue(controller.insertWikilinkSuggestion("Daily Notes"))
        assertEquals("[[Daily Notes]]", controller.text)
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

    @Test fun capabilitiesFilterBuiltInsAndRejectDisabledExecution() {
        val capabilities = MarkdownEditorCapabilities(setOf(MarkdownEditorCommand.HEADING1))
        val controller = MarkdownEditorController("/he")
        controller.setSelection(3)
        val trigger = MarkdownSlashCommands.match(controller)!!
        assertFalse(MarkdownSlashCommands.suggestions(trigger, capabilities = capabilities)
            .any { it.command == MarkdownEditorCommand.HEADING1 })
        assertFalse(MarkdownSlashCommands.apply(controller, trigger, MarkdownEditorCommand.HEADING1, capabilities))
        assertEquals("/he", controller.text)
        assertTrue(MarkdownSlashCommands.apply(controller, trigger, MarkdownEditorCommand.HEADING2, capabilities))
        assertEquals("## ", controller.text)
    }

    @Test fun customCommandFiltersAndInsertsAsOneUndoStep() = runBlocking {
        val controller = MarkdownEditorController("Before\n\n/ca\n\nAfter")
        controller.setSelection(controller.text.indexOf("/ca") + 3)
        val trigger = MarkdownSlashCommands.match(controller)!!
        val command = MarkdownEditorSlashCommand("Callout", "admonition note", onSelected = { query ->
            assertEquals("ca", query)
            "> Note"
        })
        val suggestions = MarkdownSlashCommands.allSuggestions(
            trigger, true, MarkdownEditorCapabilities.All, listOf(command))
        assertEquals(listOf("Callout"), suggestions.map { it.title })
        assertTrue(MarkdownSlashCommands.applyCustom(controller, trigger, command))
        assertEquals("Before\n\n> Note\n\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals("Before\n\n/ca\n\nAfter", controller.text)
    }

    @Test fun customCallbackCannotOverwriteChangedTrigger() = runBlocking {
        val controller = MarkdownEditorController("/insert")
        controller.setSelection(controller.text.length)
        val trigger = MarkdownSlashCommands.match(controller)!!
        val command = MarkdownEditorSlashCommand("Insert", "", onSelected = {
            controller.text = "changed while waiting"
            "# Replaced"
        })
        assertFalse(MarkdownSlashCommands.applyCustom(controller, trigger, command))
        assertEquals("changed while waiting", controller.text)
    }

    @Test fun formattedCustomSlashCommandReplacesPlainParagraph() = runBlocking {
        val controller = MarkdownEditorController("Before\n\n/ca\n\nAfter")
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.first { it.source == "/ca" }
        controller.setFormattedSelection(block.id, TextRange(3))
        val trigger = MarkdownSlashCommands.match(controller)!!
        val command = MarkdownEditorSlashCommand("Callout", "note", markdown = "> Note")
        assertTrue(MarkdownSlashCommands.applyCustom(controller, trigger, command))
        assertEquals("Before\n\n> Note\n\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals("Before\n\n/ca\n\nAfter", controller.text)
    }
}
