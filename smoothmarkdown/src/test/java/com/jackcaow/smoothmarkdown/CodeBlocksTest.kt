package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlocksTest {
    @Test fun languageUsesFirstInfoWord() {
        assertEquals("kotlin", codeLanguage(" kotlin title=demo "))
        assertEquals("swift", codeLanguage("swift\tfile=main"))
        assertEquals(null, codeLanguage("  "))
    }

    @Test fun kotlinHighlightingDoesNotTreatStringOrCommentAsCode() {
        val code = "val text = \"fun inside\" // class note\nfun run() = 42"
        val tokens = codeTokens(code, "kt")
        fun words(kind: Int) = tokens.filter { it.kind == kind }.map { code.substring(it.start, it.end) }
        assertEquals(listOf("val", "fun"), words(CodeKind.KEYWORD_DECLARATION))
        assertEquals(listOf("\"fun inside\""), words(CodeKind.STRING))
        assertEquals(listOf("// class note"), words(CodeKind.COMMENT))
        assertEquals(listOf("42"), words(CodeKind.NUMBER))
        assertEquals(listOf("run"), words(CodeKind.FUNCTION_DEFINITION))
    }

    @Test fun commonLanguageAliasesAndUnknownLanguage() {
        assertTrue(codeTokens("let value = 1", "swift").any { it.kind == CodeKind.KEYWORD_DECLARATION })
        assertTrue(codeTokens("const value = true", "typescript").any { it.kind == CodeKind.KEYWORD_DECLARATION })
        assertTrue(codeTokens("SELECT * FROM notes", "sql").any { it.kind == CodeKind.KEYWORD })
        assertTrue(codeTokens("{\"ready\": true}", "json").any { it.kind == CodeKind.PROPERTY_KEY })
        assertTrue(codeTokens("def read(): # comment", "py").any { it.kind == CodeKind.COMMENT })
        assertTrue(codeTokens("echo 'hello' # comment", "bash").any { it.kind == CodeKind.COMMENT })
        assertFalse(codeTokens("fun main()", "unknown").isNotEmpty())
    }

    @Test fun highlightedTextKeepsCodeExactly() {
        val code = "fun main() {\n    println(\"hi\")\n}\n"
        val highlighted = highlightedCode(code, "kotlin", dark = false)
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
        assertEquals(code, highlightedCode(code, "not-supported", dark = true).text)
        assertTrue(highlightedCode(code, "not-supported", dark = true).spanStyles.isEmpty())
    }

    @Test fun linesKeepMultilineCommentColorsAndExpandTabs() {
        val colors = MarkdownSyntaxColors(androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Green, androidx.compose.ui.graphics.Color.Gray)
        val lines = highlightedLines("val a = 1 /* open\n\tstill */ val b\n", "kt", dark = false, syntaxColors = colors)
        assertEquals(listOf("val a = 1 /* open", "    still */ val b"), lines.map { it.text })
        val second = lines[1]
        assertTrue(second.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color.Green && second.text.substring(it.start, it.end).contains("still") })
        assertTrue(second.spanStyles.any { it.item.color == androidx.compose.ui.graphics.Color.Red && second.text.substring(it.start, it.end) == "val" })
    }

    @Test fun eofNotesKeepWordChangesAndMultilineHighlightingOnEachSide() {
        val patch = listOf(
            "@@ -5,2 +9,2 @@",
            " /* open",
            "-\t🙂 old */",
            "\\ No newline at end of file",
            "+\t🙂 new */",
            "\\ No newline at end of file",
        ).joinToString("\n")
        val colors = MarkdownSyntaxColors.light()
        val rows = highlightedDiff(patch, "kt", dark = false, syntaxColors = colors)
        assertEquals(listOf(CodeDiffRow.Kind.HUNK, CodeDiffRow.Kind.CONTEXT, CodeDiffRow.Kind.REMOVED,
            CodeDiffRow.Kind.NOTE, CodeDiffRow.Kind.ADDED, CodeDiffRow.Kind.NOTE), rows.map { it.kind })
        assertEquals(listOf(null, 9, 6, null, 10, null), rows.map { it.lineNumber })
        for ((row, word) in listOf(rows[2] to "old", rows[4] to "new")) {
            assertEquals("    🙂 $word */", row.text.text)
            assertEquals(word, row.changed?.let { row.text.text.substring(it) })
            val start = row.text.text.indexOf(word)
            assertTrue(row.text.spanStyles.any { it.item.color == colors.comment &&
                it.start <= start && it.end >= start + word.length })
        }
        assertTrue(rows.filter { it.kind == CodeDiffRow.Kind.NOTE }.all {
            it.text.text == "No newline at end of file" && it.changed == null && it.text.spanStyles.isEmpty()
        })
    }

    @Test fun diffRowsCarryNumbersAndWordChanges() {
        val patch = "diff --git a/a.js b/a.js\n--- a/a.js\n+++ b/a.js\n@@ -3,2 +3,2 @@\n const x = 1\n-let total = price * 2\n+let total = price * 3\n"
        val rows = highlightedDiff(patch, "a.js", dark = false)
        assertEquals(listOf(CodeDiffRow.Kind.HUNK, CodeDiffRow.Kind.CONTEXT, CodeDiffRow.Kind.REMOVED, CodeDiffRow.Kind.ADDED), rows.map { it.kind })
        assertEquals(listOf(null, 3, 4, 4), rows.map { it.lineNumber })
        assertEquals("let total = price * 3", rows[3].text.text)
        assertEquals("3", rows[3].changed?.let { rows[3].text.text.substring(it) })
    }
}
