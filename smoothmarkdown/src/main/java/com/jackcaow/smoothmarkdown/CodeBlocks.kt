package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

data class CodeBlockOptions(
    val showCopyButton: Boolean = true,
    val showLanguageTag: Boolean = true,
    val enableSyntaxHighlighting: Boolean = true,
)

internal val LocalCodeBlockOptions = staticCompositionLocalOf { CodeBlockOptions() }
internal val LocalCodeBlockBuilder = staticCompositionLocalOf<(@Composable (String, String?) -> Unit)?> { null }
internal val LocalOnCodeCopied = staticCompositionLocalOf<((String) -> Unit)?> { null }
internal val LocalOnCodeCopiedWithMetadata = androidx.compose.runtime.staticCompositionLocalOf<((String, String?) -> Unit)?> { null }

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

internal fun highlightedCode(code: String, language: String?, dark: Boolean, syntaxColors: MarkdownSyntaxColors? = null): AnnotatedString = buildAnnotatedString {
    append(code)
    val colors = syntaxColors ?: if (dark) MarkdownSyntaxColors.dark() else MarkdownSyntaxColors.light()
    val palette = mapOf(CodeTokenKind.KEYWORD to colors.keyword, CodeTokenKind.STRING to colors.string,
        CodeTokenKind.COMMENT to colors.comment, CodeTokenKind.NUMBER to colors.number)
    codeTokens(code, language).forEach { token ->
        addStyle(SpanStyle(color = palette.getValue(token.kind)), token.start, token.end)
    }
}

internal data class ResolvedCodeBlockDecoration(
    val backgroundColor: Color,
    val borderColor: Color?,
    val borderWidth: Dp,
    val cornerRadius: Dp,
    val padding: PaddingValues,
)

internal fun resolveCodeBlockDecoration(sheet: MarkdownStyleSheet, fallback: Color): ResolvedCodeBlockDecoration {
    val decoration = sheet.codeBlockDecoration
    return ResolvedCodeBlockDecoration(
        backgroundColor = decoration?.backgroundColor ?: sheet.codeBackground ?: fallback,
        borderColor = decoration?.borderColor,
        borderWidth = decoration?.borderWidth ?: 0.dp,
        cornerRadius = decoration?.cornerRadius ?: 6.dp,
        padding = sheet.codeBlockPadding ?: PaddingValues(sheet.codePadding),
    )
}

@Composable
internal fun EnhancedCodeBlock(code: String, info: String?) {
    val sheet = LocalMarkdownStyleSheet.current
    val tokens = sheet.designTokens.code
    val language = codeLanguage(info)
    val custom = LocalCodeBlockBuilder.current
    if (custom != null) {
        custom(code, language)
        return
    }
    val options = LocalCodeBlockOptions.current
    val onCodeCopied = LocalOnCodeCopied.current
    val onCodeCopiedWithMetadata = LocalOnCodeCopiedWithMetadata.current
    val clipboard = LocalClipboardManager.current
    var copied by androidx.compose.runtime.remember { mutableStateOf(false) }
    val strings = LocalMarkdownStrings.current
    var copyCount by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            delay(tokens.copyFeedbackMillis)
            copied = false
        }
    }
    val decoration = resolveCodeBlockDecoration(sheet, MaterialTheme.colorScheme.surfaceVariant)
    val dark = decoration.backgroundColor.luminance() < 0.5f
    val codeText = androidx.compose.runtime.remember(code, language, dark, options.enableSyntaxHighlighting, tokens.syntaxColors) {
        if (options.enableSyntaxHighlighting) highlightedCode(code, language, dark, tokens.syntaxColors) else AnnotatedString(code)
    }
    val shape = RoundedCornerShape(decoration.cornerRadius)
    val codeContainer = Modifier.fillMaxWidth().padding(bottom = sheet.blockSpacing)
        .background(decoration.backgroundColor, shape)
        .let { base ->
            if (decoration.borderColor != null && decoration.borderWidth > 0.dp)
                base.border(decoration.borderWidth, decoration.borderColor, shape)
            else base
        }
    val horizontalState = rememberScrollState()
    Column(codeContainer) {
        if ((options.showLanguageTag && language != null) || options.showCopyButton) {
            DisableSelection {
                Row(Modifier.fillMaxWidth().padding(tokens.headerPadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (options.showLanguageTag && language != null) {
                        Text(
                            language.uppercase(),
                            modifier = Modifier,
                            style = (tokens.languageStyle ?: MaterialTheme.typography.labelSmall).copy(
                                color = tokens.languageStyle?.color?.takeUnless { it == Color.Unspecified } ?: sheet.codeTextColor ?: MaterialTheme.colorScheme.primary,
                                fontWeight = tokens.languageStyle?.fontWeight ?: FontWeight.SemiBold,
                            ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (options.showCopyButton) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(code))
                            onCodeCopied?.invoke(code)
                            onCodeCopiedWithMetadata?.invoke(code, language)
                            copied = true
                            copyCount++
                        }) {
                            Text(if (copied) tokens.copiedLabel ?: strings.copied else tokens.copyLabel ?: strings.copy, style = tokens.copyStyle ?: MaterialTheme.typography.labelMedium, color = if (copied) tokens.copiedColor else tokens.copyColor ?: tokens.copyStyle?.color?.takeUnless { it == Color.Unspecified } ?: sheet.linkColor)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(horizontalState).padding(decoration.padding)) {
            val selectionOptions = LocalMarkdownSelectionOptions.current
            val selectionKey = androidx.compose.runtime.remember { Any() }
            DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
                onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
            }
            val codeLayout = androidx.compose.runtime.remember(codeText) { mutableStateOf<TextLayoutResult?>(null) }
            val tracking = selectionOptions.onTextPositioned?.let { callback ->
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    callback(MarkdownSelectionTarget(
                        selectionKey, bounds, codeText,
                        offsetAtWindowPosition = { windowPoint ->
                            codeLayout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                        },
                    ).apply {
                        wordBoundaryAtWindowPosition = { windowPoint ->
                            codeLayout.value?.let { result ->
                                result.getWordBoundary(result.getOffsetForPosition(windowPoint - bounds.topLeft))
                            } ?: TextRange(0)
                        }
                        containsTextAtWindowPosition = { windowPoint ->
                            textLayoutContainsWindowPoint(codeLayout.value, windowPoint, bounds)
                        }
                    })
                }
            } ?: Modifier
            val content: @Composable () -> Unit = {
                Text(
                    codeText,
                    modifier = tracking,
                    onTextLayout = { codeLayout.value = it },
                    softWrap = false,
                    style = (sheet.codeStyle ?: MaterialTheme.typography.bodyMedium).copy(
                        fontFamily = sheet.codeStyle?.fontFamily ?: FontFamily.Monospace,
                        color = sheet.codeTextColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            if (LocalMarkdownSelectionOptions.current.outerRegion || !LocalMarkdownSelectionOptions.current.selectable) content()
            else SelectionContainer { content() }
        }
        if (tokens.showScrollbar && horizontalState.maxValue > 0) {
            val trackColor = tokens.scrollbarTrackColor ?: (sheet.codeTextColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = tokens.scrollbarTrackAlpha)
            val thumbColor = tokens.scrollbarThumbColor ?: trackColor.copy(alpha = tokens.scrollbarThumbAlpha)
            Canvas(Modifier.fillMaxWidth().padding(tokens.scrollbarPadding).height(tokens.scrollbarThickness)) {
                drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                val viewport = horizontalState.viewportSize.toFloat()
                val total = viewport + horizontalState.maxValue
                val thumbWidth = (size.width * viewport / total).coerceAtLeast(tokens.scrollbarMinThumbWidth.toPx()).coerceAtMost(size.width)
                val left = (size.width - thumbWidth) * horizontalState.value / horizontalState.maxValue
                drawRoundRect(thumbColor, topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            }
        }
    }
}
