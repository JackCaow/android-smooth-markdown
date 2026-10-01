package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class MainstreamCodeHighlightTest {
    @Test fun twentyLanguagesAndAdditionalFencesRetainSource() {
        val cases = listOf(
            "javascript" to "function run() { return true; }", "typescript" to "interface Box { value: number }",
            "python" to "def run():\n    return True", "java" to "class Box { void run() {} }",
            "kotlin" to "fun run() = true", "swift" to "func run() -> Bool { true }",
            "c" to "int main() { return 0; }", "cpp" to "class Box { public: void run(); }",
            "csharp" to "class Box { public void Run() {} }", "go" to "func main() { return }",
            "rust" to "fn main() { let value = 1; }", "php" to "<?php function run() { return true; }",
            "ruby" to "def run\nend", "dart" to "class App { void main() {} }",
            "scala" to "object Main { def run() = 1 }", "sql" to "SELECT name FROM users -- ok",
            "shell" to "if true; then echo ok; fi", "lua" to "function run() return true end",
            "r" to "function(x) { return(x) } # ok", "objectivec" to "@interface Box : NSObject @end",
            "json" to "{\"name\": 1}", "yaml" to "name: true", "html" to "<!-- comment -->",
            "css" to "@media screen { color: red; }", "markdown" to "TODO", "diff" to "+added\n-removed",
        )
        for ((language, code) in cases) {
            assertTrue(language, codeTokens(code, language).isNotEmpty())
            assertEquals(code, highlightedCode(code, language, false).text)
        }
    }
    @Test fun aliasesFenceMetadataDiffHeadersAndUnicode() {
        val aliases = mapOf("tsx" to "typescript", "node" to "javascript", "h" to "c", "c++" to "cpp", "c#" to "csharp", "golang" to "go", "rs" to "rust", "phtml" to "php", "rb" to "ruby", "sc" to "scala", "postgresql" to "sql", "objective-c" to "objectivec", "ps1" to "shell", "jsonc" to "json", "yml" to "yaml", "svg" to "markup", "scss" to "css", "patch" to "diff")
        for ((alias, language) in aliases) assertEquals(language, normalizedCodeLanguage("$alias\tlineNumbers"))
        assertEquals("json", normalizedCodeLanguage("json\nmeta"))
        val code = "🙂 const 名称 = run(\"值\"); Box"
        fun values(kind: CodeTokenKind) = codeTokens(code, "typescript").filter { it.kind == kind }.map { code.substring(it.start, it.end) }
        assertEquals(listOf("run"), values(CodeTokenKind.FUNCTION))
        assertEquals(listOf("Box"), values(CodeTokenKind.TYPE))
        assertEquals(code, highlightedCode(code, "typescript", false).text)
        val diff = "--- a/file\n+++ b/file\n-old\n+new\n@@ range @@"
        val tokens = codeTokens(diff, "patch")
        assertEquals(listOf("-old"), tokens.filter { it.kind == CodeTokenKind.DIFF_REMOVE }.map { diff.substring(it.start, it.end) })
        assertEquals(listOf("+new"), tokens.filter { it.kind == CodeTokenKind.DIFF_ADD }.map { diff.substring(it.start, it.end) })
        assertTrue(codeTokens("const answer = 1", "madeup").isEmpty())
        assertTrue(codeTokens("const answer = 1", "plain").isEmpty())
    }
    @Test fun optionalAccentsAndExistingConstructorDefaults() {
        val colors = MarkdownSyntaxColors.light()
        assertNull(colors.type); assertNull(colors.function); assertNull(colors.property)
        colors.type = Color.Yellow; colors.function = Color.Green; colors.property = Color.Magenta
        colors.operator = Color.Gray; colors.punctuation = Color.Gray; colors.diffAdd = Color.Green; colors.diffRemove = Color.Red
        val code = "const answer = run(\"ok\"); Box"
        val result = highlightedCode(code, "typescript", false, colors)
        assertEquals(code, result.text)
        assertTrue(result.spanStyles.any { it.item.color == Color.Yellow })
        assertTrue(result.spanStyles.any { it.item.color == Color.Green })
        val json = highlightedCode("{\"count\": 2}", "json", false, colors)
        assertTrue(json.spanStyles.any { it.item.color == Color.Magenta })
    }
    @Test fun copyAndThemeEqualityIncludeEveryOptionalAccent() {
        val original = MarkdownSyntaxColors.light().apply {
            type = Color.Yellow; function = Color.Green; property = Color.Magenta
            operator = Color.Gray; punctuation = Color.Cyan; diffAdd = Color.Green; diffRemove = Color.Red
        }
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())
        assertEquals(original.type, copy.type); assertEquals(original.function, copy.function)
        assertEquals(original.property, copy.property); assertEquals(original.operator, copy.operator)
        assertEquals(original.punctuation, copy.punctuation); assertEquals(original.diffAdd, copy.diffAdd)
        assertEquals(original.diffRemove, copy.diffRemove)
        val changed = copy.copy().apply { function = Color.Blue }
        assertNotEquals(original, changed)
        val before = MarkdownCodeTokens(syntaxColors = original)
        val after = before.copy(syntaxColors = changed)
        assertNotEquals(before, after)
        val code = "run()"
        assertEquals(Color.Green, highlightedCode(code, "typescript", false, before.syntaxColors).spanStyles.first().item.color)
        assertEquals(Color.Blue, highlightedCode(code, "typescript", false, after.syntaxColors).spanStyles.first().item.color)
        val (keyword, string, comment, number) = original
        assertEquals(original.keyword, keyword); assertEquals(original.string, string)
        assertEquals(original.comment, comment); assertEquals(original.number, number)
    }

}
