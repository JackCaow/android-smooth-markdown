package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorFormatShortcutMappingTest {
    @Test fun flutterFormattingBindingsIncludeNumberRowAndNumpad() {
        val headings = listOf(
            MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3, MarkdownEditorCommand.HEADING4,
            MarkdownEditorCommand.HEADING5, MarkdownEditorCommand.HEADING6,
        )
        val row = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
        val numpad = listOf(Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6)
        headings.indices.forEach { index ->
            assertEquals(headings[index], formatShortcutCommand(row[index], shift = false, alt = true))
            assertEquals(headings[index], formatShortcutCommand(numpad[index], shift = false, alt = true))
        }
        assertEquals(MarkdownEditorCommand.CODE_BLOCK, formatShortcutCommand(Key.C, shift = false, alt = true))
        assertEquals(MarkdownEditorCommand.BLOCKQUOTE, formatShortcutCommand(Key.B, shift = true, alt = false))
        assertEquals(MarkdownEditorCommand.ORDERED_LIST, formatShortcutCommand(Key.Seven, shift = true, alt = false))
        assertEquals(MarkdownEditorCommand.ORDERED_LIST, formatShortcutCommand(Key.NumPad7, shift = true, alt = false))
        assertEquals(MarkdownEditorCommand.UNORDERED_LIST, formatShortcutCommand(Key.Eight, shift = true, alt = false))
        assertEquals(MarkdownEditorCommand.UNORDERED_LIST, formatShortcutCommand(Key.NumPad8, shift = true, alt = false))
        assertEquals(MarkdownEditorCommand.INLINE_CODE, formatShortcutCommand(Key.E, shift = false, alt = false))
    }

    @Test fun unrelatedAndConflictingModifierChordsAreLeftToTheHost() {
        assertNull(formatShortcutCommand(Key.E, shift = true, alt = false))
        assertNull(formatShortcutCommand(Key.B, shift = true, alt = true))
        assertNull(formatShortcutCommand(Key.Seven, shift = false, alt = false))
        assertNull(formatShortcutCommand(Key.C, shift = false, alt = false))
        assertEquals(MarkdownEditorCommand.BOLD, formatShortcutCommand(Key.B, shift = false, alt = false))
        assertEquals(MarkdownEditorCommand.ITALIC, formatShortcutCommand(Key.I, shift = false, alt = false))
        assertEquals(MarkdownEditorCommand.LINK, formatShortcutCommand(Key.K, shift = false, alt = false))
    }
}
