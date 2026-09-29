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
        fun words(kind: CodeTokenKind) = tokens.filter { it.kind == kind }.map { code.substring(it.start, it.end) }
        assertEquals(listOf("val", "fun"), words(CodeTokenKind.KEYWORD))
        assertEquals(listOf("\"fun inside\""), words(CodeTokenKind.STRING))
        assertEquals(listOf("// class note"), words(CodeTokenKind.COMMENT))
        assertEquals(listOf("42"), words(CodeTokenKind.NUMBER))
    }

    @Test fun commonLanguageAliasesAndUnknownLanguage() {
        assertTrue(codeTokens("let value = 1", "swift").any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(codeTokens("const value = true", "typescript").any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(codeTokens("SELECT * FROM notes", "sql").any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(codeTokens("{\"ready\": true}", "json").any { it.kind == CodeTokenKind.STRING })
        assertTrue(codeTokens("def read(): # comment", "py").any { it.kind == CodeTokenKind.COMMENT })
        assertTrue(codeTokens("echo 'hello' # comment", "bash").any { it.kind == CodeTokenKind.COMMENT })
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
}
