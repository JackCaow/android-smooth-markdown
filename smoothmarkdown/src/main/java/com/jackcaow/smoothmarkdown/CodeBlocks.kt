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


/** Highlights [code] with the same lexer and palette as enhanced code blocks, for source and diff viewers. */
fun highlightedCode(code: String, language: String?, dark: Boolean, syntaxColors: MarkdownSyntaxColors? = null): AnnotatedString = buildAnnotatedString {
    append(code)
    val colors = syntaxColors ?: if (dark) MarkdownSyntaxColors.dark() else MarkdownSyntaxColors.light()
    for (token in codeTokens(code, language)) nativeStyle(token.kind, colors)?.let { addStyle(it, token.start, token.end) }
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
    val codeText = androidx.compose.runtime.remember(code, language, dark, options.enableSyntaxHighlighting, tokens.syntaxColors, tokens.syntaxColors?.type, tokens.syntaxColors?.function, tokens.syntaxColors?.property, tokens.syntaxColors?.operator, tokens.syntaxColors?.punctuation, tokens.syntaxColors?.diffAdd, tokens.syntaxColors?.diffRemove) {
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
            val sourceOrder = rememberReaderSelectionOrder()
            RetainReaderSelectionTarget(selectionKey)
            DisposableEffect(selectionKey, selectionOptions.onTextDisposed) {
                onDispose { selectionOptions.onTextDisposed?.invoke(selectionKey) }
            }
            val codeLayout = androidx.compose.runtime.remember(codeText) { mutableStateOf<TextLayoutResult?>(null) }
            val tracking = selectionOptions.onTextPositioned?.let { callback ->
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = androidx.compose.ui.geometry.Rect(
                coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero),
                androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
            )
                    callback(MarkdownSelectionTarget(
                        selectionKey, bounds, codeText,
                        offsetAtWindowPosition = { windowPoint ->
                            codeLayout.value?.getOffsetForPosition(windowPoint - bounds.topLeft) ?: 0
                        },
                    ).apply {
                        this.sourceOrder = sourceOrder
                        layoutResult = codeLayout.value
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
                    modifier = tracking.then(readerSelectionHighlight(selectionKey)),
                    onTextLayout = { codeLayout.value = it },
                    softWrap = false,
                    style = (sheet.codeStyle ?: MaterialTheme.typography.bodyMedium).copy(
                        fontFamily = sheet.codeStyle?.fontFamily ?: FontFamily.Monospace,
                        color = sheet.codeTextColor ?: sheet.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            if (LocalReaderSelectionState.current != null) DisableSelection { content() }
            else if (LocalMarkdownSelectionOptions.current.outerRegion || !LocalMarkdownSelectionOptions.current.selectable) content()
            else SelectionContainer { content() }
        }
        // Reserve scrollbar space from the first layout, before scroll measurements arrive.
        if (tokens.showScrollbar) {
            val trackColor = tokens.scrollbarTrackColor ?: (sheet.codeTextColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = tokens.scrollbarTrackAlpha)
            val thumbColor = tokens.scrollbarThumbColor ?: trackColor.copy(alpha = tokens.scrollbarThumbAlpha)
            Canvas(Modifier.fillMaxWidth().padding(tokens.scrollbarPadding).height(tokens.scrollbarThickness)) {
                if (horizontalState.maxValue <= 0) return@Canvas
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

/** One display row of a unified diff (see [highlightedDiff]). */
class CodeDiffRow(
    val kind: Kind,
    /** New-file line for context and added rows, old-file line for removed rows. */
    val lineNumber: Int?,
    /** Syntax-highlighted display text without the `+`/`-` prefix, tabs expanded. */
    val text: AnnotatedString,
    /** The differing middle of a paired removed/added line, for word-level emphasis. */
    val changed: IntRange?,
) {
    enum class Kind { HUNK, CONTEXT, ADDED, REMOVED, NOTE }
}

private fun styledLine(line: com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge.CodeLine, colors: MarkdownSyntaxColors): AnnotatedString =
    buildAnnotatedString {
        append(line.text)
        val t = line.tokens
        var i = 0
        while (i + 2 < t.size) {
            val start = t[i]; val end = start + t[i + 1]
            if (start >= 0 && end <= line.text.length && end > start) nativeStyle(t[i + 2], colors)?.let { addStyle(it, start, end) }
            i += 3
        }
    }

/** Highlights a whole file once and returns its display lines (tabs expanded to [tabWidth] spaces). */
fun highlightedLines(code: String, language: String?, dark: Boolean, syntaxColors: MarkdownSyntaxColors? = null, tabWidth: Int = 4): List<AnnotatedString> {
    val colors = syntaxColors ?: if (dark) MarkdownSyntaxColors.dark() else MarkdownSyntaxColors.light()
    val tag = language?.trim()?.split(Regex("[\\s,]+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
    val lines = tag?.let { com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge.highlightLines(code, it, tabWidth) }
        ?: return code.removeSuffix("\n").split('\n').map { AnnotatedString(it) }
    return lines.map { styledLine(it, colors) }
}

/** Display rows of a unified diff: hunk headers, highlighted code rows with line numbers and word-level changes, and notes. */
fun highlightedDiff(patch: String, language: String?, dark: Boolean, syntaxColors: MarkdownSyntaxColors? = null, tabWidth: Int = 4): List<CodeDiffRow> {
    val colors = syntaxColors ?: if (dark) MarkdownSyntaxColors.dark() else MarkdownSyntaxColors.light()
    val tag = language?.trim()?.split(Regex("[\\s,]+"))?.firstOrNull().orEmpty()
    val rows = com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge.diff(patch, tag, tabWidth) ?: return emptyList()
    return rows.map { row ->
        val changed = if (row.changedStart < 0 || row.changedLength <= 0) null else row.changedStart until row.changedStart + row.changedLength
        CodeDiffRow(CodeDiffRow.Kind.entries.getOrElse(row.kind) { CodeDiffRow.Kind.NOTE }, row.number, styledLine(row.line, colors), changed)
    }
}

/** Style for a native token kind (the Rust `Kind` enum shared with iOS). */
internal fun nativeStyle(kind: Int, c: MarkdownSyntaxColors): SpanStyle? = when (kind) {
    16, 17, 18, 19, 20, 25, 50 -> SpanStyle(color = c.keyword)                                  // keywords, preproc, self/this
    37, 39, 40, 44, 52 -> SpanStyle(color = c.string)                                           // strings, regex, literals, URIs
    38 -> SpanStyle(color = c.escape ?: c.number)                                               // escapes
    32 -> SpanStyle(color = c.escape ?: c.keyword)                                              // interpolation delimiters
    3, 42 -> SpanStyle(color = c.comment)
    4 -> SpanStyle(color = c.comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) // doc comments
    23 -> SpanStyle(color = c.number)
    2, 6 -> SpanStyle(color = c.number)                                                         // booleans, null
    5 -> SpanStyle(color = c.constant ?: c.number)
    46, 47, 7, 43, 22, 34, 35 -> (c.type ?: c.keyword).let { SpanStyle(color = it) }           // types, constructors, components, namespaces, selectors
    13, 15 -> c.function?.let { SpanStyle(color = it) }
    14 -> SpanStyle(color = c.function ?: androidx.compose.ui.graphics.Color.Unspecified, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    26, 27, 21 -> c.property?.let { SpanStyle(color = it) }                                     // properties, keys, labels
    1 -> SpanStyle(color = c.attribute ?: c.type ?: c.keyword)                                  // annotations, attributes
    41, 33 -> SpanStyle(color = c.tag ?: c.keyword)                                             // tags, element selectors
    36 -> SpanStyle(color = c.keyword)                                                          // pseudo selectors
    24 -> c.operator?.let { SpanStyle(color = it) }
    28, 29, 30, 31 -> c.punctuation?.let { SpanStyle(color = it) }
    49 -> c.parameter?.let { SpanStyle(color = it) }
    45, 12 -> SpanStyle(color = c.keyword, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)  // headings, strong
    11 -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    8 -> SpanStyle(color = c.diffRemove ?: c.keyword)
    9 -> SpanStyle(color = c.diffAdd ?: c.string)
    else -> null                                                                                 // variables, embedded, plain
}
