package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedSearchTest {
    private fun search(source: String, query: String) =
        FormattedSearch.find(MarkdownDocumentCodec.parse(source), query, enableWikilinks = true).matches

    @Test fun sourceAndFormattedFindReturnOriginalUtf16Ranges() {
        val source = "İX 😀 ΟΣ İx"
        val controller = MarkdownEditorController(source)
        val expected = mapOf(
            "x" to listOf(TextRange(1, 2), TextRange(10, 11)),
            "İ" to listOf(TextRange(0, 1), TextRange(9, 10)),
            "σ" to listOf(TextRange(7, 8)),
            "😀" to listOf(TextRange(3, 5)),
            "\uD83D" to emptyList(),
            "\uDE00" to emptyList(),
            "" to emptyList(),
        )
        expected.forEach { (query, ranges) ->
            assertEquals(query, ranges, controller.findMatches(query))
            assertEquals(query, ranges, search(source, query).map { it.sourceRange })
        }
        assertEquals(listOf(TextRange(1, 2)), controller.findMatches("X", caseSensitive = true))
        assertTrue(controller.findMatches("\uDE00", caseSensitive = true).isEmpty())
        controller.setSelection(0)
        assertEquals(TextRange(1, 2), controller.selectNextMatch("x"))
        assertEquals("X", controller.selectedText)
        assertEquals(TextRange(10, 11), controller.selectPreviousMatch("x"))
        assertEquals("x", controller.selectedText)
    }

    @Test fun matchesRemainNonOverlappingAndCappedInBothModes() {
        val source = "a".repeat(1002)
        val expected = List(500) { TextRange(it * 2, it * 2 + 2) }
        assertEquals(expected, MarkdownEditorController(source).findMatches("aa"))
        assertEquals(expected, search(source, "aa").map { it.sourceRange })
    }

    @Test fun headingFindUsesVisibleTextAndMapsAroundInlineMarkup() {
        val source = "# **Alpha** [link](https://alpha.example)\n\nPlain alpha"
        val matches = search(source, "ALPHA")
        assertEquals(2, matches.size)
        assertEquals(FormattedSearchTarget.Text("block-0"), matches[0].target)
        assertEquals("Alpha", source.substring(matches[0].sourceRange.min, matches[0].sourceRange.max))
        assertEquals(TextRange(0, 5), matches[0].visibleRange)
        assertEquals("alpha", source.substring(matches[1].sourceRange.min, matches[1].sourceRange.max))
        assertFalse(matches.any { source.substring(it.sourceRange.min, it.sourceRange.max).contains("https") })
        val label = search(source, "link").single()
        assertEquals("link", source.substring(label.sourceRange.min, label.sourceRange.max))
    }

    @Test fun listFindIncludesNestedAndContinuationLinesButNotMarkers() {
        val source = "- alpha\n  - **ALPHA**\n    continuation alpha"
        val matches = search(source, "alpha")
        assertEquals(3, matches.size)
        assertEquals(listOf(0), (matches[0].target as FormattedSearchTarget.ListLine).path)
        assertEquals(listOf(0, 0), (matches[1].target as FormattedSearchTarget.ListLine).path)
        assertEquals("ALPHA", source.substring(matches[1].sourceRange.min, matches[1].sourceRange.max))
        assertTrue(matches[2].target is FormattedSearchTarget.ListLine)
    }

    @Test fun quoteAndTableFindTargetDisplayedFields() {
        val source = "> **Alpha**\n> alpha\n\n| A\\|Alpha | alpha |\n| --- | --- |\n| alpha | z |"
        val matches = search(source, "alpha")
        assertEquals(5, matches.size)
        assertEquals(FormattedSearchTarget.QuoteLine("block-0", 0), matches[0].target)
        assertEquals(FormattedSearchTarget.QuoteLine("block-0", 1), matches[1].target)
        assertEquals(FormattedSearchTarget.TableCell("block-1", 0, 0), matches[2].target)
        assertEquals(FormattedSearchTarget.TableCell("block-1", 0, 1), matches[3].target)
        assertEquals(FormattedSearchTarget.TableCell("block-1", 1, 0), matches[4].target)
        matches.forEach { assertEquals("alpha", source.substring(it.sourceRange.min, it.sourceRange.max).lowercase()) }
    }

    @Test fun formattedNavigationKeepsModeAndSourceUndoHistory() {
        val source = "First alpha\n\nSecond alpha"
        val controller = MarkdownEditorController(source).apply { mode = MarkdownEditorMode.FORMATTED }
        val matches = FormattedSearch.find(controller.semanticDocument(), "alpha", false).matches
        controller.activateFormattedSearchMatch(matches.last())
        assertEquals(MarkdownEditorMode.FORMATTED, controller.mode)
        assertEquals(matches.last().sourceRange, controller.selection)
        assertEquals(matches.last().visibleRange, controller.formattedSelection)
        assertFalse(controller.canUndo)
        assertEquals(source, controller.text)
    }

    @Test fun codeFencesAndMarkdownSyntaxAreNotVisibleSearchHits() {
        val source = "# alpha\n\n```alpha\nalpha\n```\n\n[go](https://alpha.example)"
        val matches = search(source, "alpha")
        assertEquals(1, matches.size)
        assertEquals(FormattedSearchTarget.Text("block-0"), matches.single().target)
    }
}
