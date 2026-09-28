package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class CodeBlockOptions(
    val showCopyButton: Boolean = true,
    val showLanguageTag: Boolean = true,
    val enableSyntaxHighlighting: Boolean = true,
)

internal val LocalCodeBlockOptions = staticCompositionLocalOf { CodeBlockOptions() }
internal val LocalCodeBlockBuilder = staticCompositionLocalOf<(@Composable (String, String?) -> Unit)?> { null }
internal val LocalOnCodeCopied = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal fun codeLanguage(info: String?): String? = info?.trim()?.substringBefore(' ')?.substringBefore('\t')
    ?.takeIf { it.isNotEmpty() }

internal enum class CodeTokenKind { KEYWORD, STRING, COMMENT, NUMBER }
internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

/** Small deterministic lexer for common fenced languages; unknown languages stay plain. */
internal fun codeTokens(code: String, language: String?): List<CodeToken> {
    val lang = when (language?.lowercase()) {
        "kt", "kotlin" -> "kotlin"
        "swift" -> "swift"
        "dart" -> "dart"
        "java" -> "java"
        "js", "javascript", "jsx" -> "javascript"
        "ts", "typescript", "tsx" -> "typescript"
        "py", "python" -> "python"
        "json" -> "json"
        "sh", "bash", "shell", "zsh" -> "bash"
        "sql" -> "sql"
        else -> return emptyList()
    }
    val keywords = when (lang) {
        "kotlin" -> "fun val var class object interface data sealed when if else return suspend private public internal import package null true false"
        "swift" -> "func let var class struct enum protocol extension if else guard return import nil true false async await"
        "dart" -> "class final var const void if else return import async await null true false required late"
        "java" -> "class interface enum public private protected static final void if else return new null true false import package"
        "javascript", "typescript" -> "const let var function class interface type if else return async await import export from new null true false"
        "python" -> "def class if elif else return import from as async await None True False for in while with lambda"
        "bash" -> "if then else fi for in do done function case esac export local"
        "sql" -> "select from where join left right inner outer on as insert into update delete create table values null and or order by group limit"
        else -> "true false null"
    }.split(' ').toSet()
    val tokens = mutableListOf<CodeToken>()
    var index = 0
    while (index < code.length) {
        val start = index
        val ch = code[index]
        val lineComment = when (lang) {
            "python", "bash" -> ch == '#'
            "sql" -> code.startsWith("--", index)
            "json" -> false
            else -> code.startsWith("//", index)
        }
        if (lineComment) {
            index = code.indexOf('\n', index).let { if (it < 0) code.length else it }
            tokens += CodeToken(start, index, CodeTokenKind.COMMENT)
            continue
        }
        if (lang !in setOf("python", "bash", "sql", "json") && code.startsWith("/*", index)) {
            val end = code.indexOf("*/", index + 2)
            index = if (end < 0) code.length else end + 2
            tokens += CodeToken(start, index, CodeTokenKind.COMMENT)
            continue
        }
        if (ch == '"' || ch == '\'' || (ch == '`' && lang in setOf("javascript", "typescript"))) {
            val quote = ch
            index++
            while (index < code.length) {
                if (code[index] == '\\') index = (index + 2).coerceAtMost(code.length)
                else if (code[index++] == quote) break
            }
            tokens += CodeToken(start, index, CodeTokenKind.STRING)
            continue
        }
        if (ch.isDigit() && (index == 0 || !code[index - 1].isLetterOrDigit())) {
            index++
            while (index < code.length && (code[index].isDigit() || code[index] == '.' || code[index] == '_')) index++
            tokens += CodeToken(start, index, CodeTokenKind.NUMBER)
            continue
        }
        if (ch.isLetter() || ch == '_') {
            index++
            while (index < code.length && (code[index].isLetterOrDigit() || code[index] == '_')) index++
            val word = code.substring(start, index)
            if ((if (lang == "sql") word.lowercase() else word) in keywords) {
                tokens += CodeToken(start, index, CodeTokenKind.KEYWORD)
            }
            continue
        }
        index++
    }
    return tokens
}

internal fun highlightedCode(code: String, language: String?, dark: Boolean): AnnotatedString = buildAnnotatedString {
    append(code)
    val palette = if (dark) mapOf(
        CodeTokenKind.KEYWORD to Color(0xFFFF7B72),
        CodeTokenKind.STRING to Color(0xFFA5D6FF),
        CodeTokenKind.COMMENT to Color(0xFF8B949E),
        CodeTokenKind.NUMBER to Color(0xFF79C0FF),
    ) else mapOf(
        CodeTokenKind.KEYWORD to Color(0xFFCF222E),
        CodeTokenKind.STRING to Color(0xFF0A3069),
        CodeTokenKind.COMMENT to Color(0xFF6E7781),
        CodeTokenKind.NUMBER to Color(0xFF0550AE),
    )
    codeTokens(code, language).forEach { token ->
        addStyle(SpanStyle(color = palette.getValue(token.kind)), token.start, token.end)
    }
}

@Composable
internal fun EnhancedCodeBlock(code: String, info: String?) {
    val sheet = LocalMarkdownStyleSheet.current
    val language = codeLanguage(info)
    val custom = LocalCodeBlockBuilder.current
    if (custom != null) {
        custom(code, language)
        return
    }
    val options = LocalCodeBlockOptions.current
    val onCodeCopied = LocalOnCodeCopied.current
    val clipboard = LocalClipboardManager.current
    var copied by androidx.compose.runtime.remember { mutableStateOf(false) }
    var copyCount by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            delay(2_000)
            copied = false
        }
    }
    val background = sheet.codeBackground ?: MaterialTheme.colorScheme.surfaceVariant
    val dark = background.luminance() < 0.5f
    val codeText = androidx.compose.runtime.remember(code, language, dark, options.enableSyntaxHighlighting) {
        if (options.enableSyntaxHighlighting) highlightedCode(code, language, dark) else AnnotatedString(code)
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = sheet.blockSpacing)
            .background(background, RoundedCornerShape(6.dp)),
    ) {
        if ((options.showLanguageTag && language != null) || options.showCopyButton) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp)) {
                Spacer(Modifier.weight(1f))
                if (options.showLanguageTag && language != null) {
                    Text(
                        language.uppercase(),
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = sheet.codeTextColor ?: MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (options.showCopyButton) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                        onCodeCopied?.invoke(code)
                        copied = true
                        copyCount++
                    }) {
                        Text(if (copied) "Copied!" else "Copy", color = if (copied) Color(0xFF2DA44E) else sheet.linkColor)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(sheet.codePadding)) {
            SelectionContainer {
                Text(
                    codeText,
                    softWrap = false,
                    style = (sheet.codeStyle ?: MaterialTheme.typography.bodyMedium).copy(
                        fontFamily = FontFamily.Monospace,
                        color = sheet.codeTextColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
