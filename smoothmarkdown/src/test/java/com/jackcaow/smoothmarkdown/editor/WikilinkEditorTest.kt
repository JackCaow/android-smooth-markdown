package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikilinkEditorTest {
    @Test fun triggerRequiresScratchPrefixAndLimitsFilteredResults() {
        val titles = (1..12).map { "Note %02d".format(it) } + "Other"
        val match = WikilinkAutocomplete.match("See [[nOtE", TextRange(10))!!
        assertEquals(TextRange(4, 10), match.range)
        assertEquals(titles.take(10), WikilinkAutocomplete.suggestions(match, titles))
        assertNull(WikilinkAutocomplete.match("Word[[Note", TextRange(10)))
        assertNull(WikilinkAutocomplete.match("See [[Note]]", TextRange(12)))
        assertNull(WikilinkAutocomplete.match("See [[Note", TextRange(4, 10)))
        assertNull(WikilinkAutocomplete.match("See [[\nNote", TextRange(11)))
        val code = MarkdownInlineEditing.parse("`[[Note`", true)
        assertNull(WikilinkAutocomplete.match(code.visible, TextRange(code.visible.length), code.marks))
    }

    @Test fun selectionCreatesOneSourceEditAndPreservesNeighbors() {
        val original = "😀 **before** See [[Pro and [web](https://example.com)"
        val controller = MarkdownEditorController(original)
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.single()
        val model = MarkdownFormattedBlock.inline(block, true)!!
        controller.setFormattedSelection(block.id, TextRange(model.visible.indexOf("[[Pro") + 5))
        assertTrue(controller.insertWikilinkSuggestion("Project Plan"))
        assertEquals("😀 **before** See [[Project Plan]] and [web](https://example.com)", controller.text)
        assertEquals("😀 before See Project Plan and web", MarkdownFormattedBlock.inline(controller.semanticDocument().blocks.single(), true)!!.visible)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun completedTypedLinkParsesAndDisabledModeKeepsLiteral() {
        val enabled = MarkdownInlineEditing.parse("Open [[Note", true)
        assertEquals("Open [[Note]]", enabled.replaceVisible("Open [[Note]]"))
        assertEquals("Open Note", MarkdownInlineEditing.parse("Open [[Note]]", true).visible)

        val controller = MarkdownEditorController("Open [[Pro")
        controller.mode = MarkdownEditorMode.FORMATTED
        controller.enableWikilinks = false
        val block = controller.semanticDocument().blocks.single()
        controller.setFormattedSelection(block.id, TextRange(10))
        assertFalse(controller.insertWikilinkSuggestion("Project Plan"))
        controller.applyCommand(MarkdownEditorCommand.WIKILINK)
        assertEquals("Open [[Pro", controller.text)
        val literal = MarkdownEditorController("Open ")
        literal.enableWikilinks = false
        val literalBlock = literal.semanticDocument().blocks.single()
        assertTrue(literal.replaceFormattedInlineText(literalBlock.id, "Open [[Project Plan]]", TextRange(21)))
        assertEquals("Open \\[\\[Project Plan\\]\\]", literal.text)
    }

    @Test fun toolbarWrapsUtf16SelectionAndOneUndoRestoresSource() {
        val controller = MarkdownEditorController("😀 Daily Notes")
        controller.mode = MarkdownEditorMode.FORMATTED
        val block = controller.semanticDocument().blocks.single()
        controller.setFormattedSelection(block.id, TextRange(3, 14))
        controller.applyCommand(MarkdownEditorCommand.WIKILINK)
        assertEquals("😀 [[Daily Notes]]", controller.text)
        assertTrue(controller.undo())
        assertEquals("😀 Daily Notes", controller.text)
    }
}
