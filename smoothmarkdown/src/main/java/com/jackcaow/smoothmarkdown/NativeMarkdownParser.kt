package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import com.jackcaow.smoothmarkdown.nativeparser.*

/** Parses directly into SmoothMarkdown's own AST. No external parser is used. */
class NativeMarkdownParser(
    private val enableGFM: Boolean = false,
    private val enableExtensions: Boolean = false,
    private val plugins: ParserPluginRegistry? = null,
) {
    // Core is a compiler friend module; its internal transport is library SPI, not application API.
    fun parse(source: String): Markup {
        val hasHostPlugins = plugins?.let {
            it.blockPlugins.isNotEmpty() || it.inlinePlugins.isNotEmpty()
        } ?: false
        val scanner = NativeMarkdownASTParser(enableGFM,
            customInline = if (enableExtensions || hasHostPlugins) ::inlinePlugin else null,
            customBlock = if (enableExtensions || hasHostPlugins) ::blockPlugin else null,
            enableNativeExtensions = enableExtensions)
        return convertTree(source, scanner.parse(source))
    }

    /** Internal streaming projection of the shared native scanner result. */
    @JvmSynthetic
    internal fun convertTree(source: String, tree: NativeMarkdownNode): Markup {
        val references = mutableMapOf<String, NativeMarkdownReference>()
        fun collect(node: NativeMarkdownNode) {
            if (node.kind == NativeMarkdownNode.Kind.REFERENCE_DEFINITION)
                references.putIfAbsent(NativeMarkdownReferenceParser.normalize(node.label), NativeMarkdownReference(node.destination, node.title))
            node.children.forEach(::collect)
        }
        collect(tree)
        return NativeMarkdownMarkupConverter(source) { node ->
            if (!enableExtensions) null else when (node.kind) {
                NativeMarkdownNode.Kind.INLINE_MATH -> InlineMathNode(node.literalText ?: node.source.removePrefix("\\(").removeSuffix("\\)").removePrefix("$").removeSuffix("$"))
                NativeMarkdownNode.Kind.BLOCK_MATH -> BlockMathNode(node.literalText ?: node.source.trim().removePrefix("\\[").removeSuffix("\\]").removePrefix("$$").removeSuffix("$$").trim())
                NativeMarkdownNode.Kind.FOOTNOTE_REFERENCE -> FootnoteReferenceNode(node.label)
                NativeMarkdownNode.Kind.FOOTNOTE_DEFINITION -> {
                    val lines = (node.literalText ?: node.source).lines()
                    val first = lines.firstOrNull().orEmpty().substringAfter("]:" ).trim()
                    val body = (listOf(first) + lines.drop(1).map { it.trim() }).filter { it.isNotEmpty() }.joinToString("\n")
                    val definition = FootnoteDefinitionNode(node.label).also { it.rawInlineSource = body }
                    val converter = NativeMarkdownMarkupConverter(body)
                    val inline = RustMarkdownBridge.parseInline(body, references = references, enableGFM = enableGFM,
                        enableExtensions = true, customInline = ::inlinePlugin)?.children
                        ?: NativeMarkdownInlineParser.parse(body, 0, references, enableGFM, customInline = ::inlinePlugin)
                    inline.forEach {
                        val child = converter.convert(it)
                        child.sourceSpans = emptyList()
                        child.descendants().forEach { descendant -> descendant.sourceSpans = emptyList() }
                        definition.appendChild(child)
                    }
                    definition
                }
                else -> null
            }
        }.convert(tree)
    }

    private fun inlinePlugin(source: String, index: Int, absoluteOffset: Int): NativeCustomInlineMatch? {
        if (enableExtensions && source.startsWith("\\(", index)) {
            var end = index + 2
            while (end < source.length - 1) {
                if (source.startsWith("\\)", end)) {
                    if (end > index + 2) return inlineMatch(source, index, absoluteOffset, end - index + 2,
                        InlineMathNode(source.substring(index + 2, end)))
                    break
                }
                end += if (source[end] == '\\' && end + 1 < source.length) 2 else 1
            }
        }
        if (enableExtensions && source[index] == '$' && (index == 0 || source[index - 1] != '$') && source.getOrNull(index + 1) != '$') {
            var cursor = index + 1
            var escaped = false
            while (cursor < source.length) {
                val char = source[cursor]
                if (char == '$' && !escaped) {
                    if (cursor > index + 1) return inlineMatch(source, index, absoluteOffset, cursor - index + 1,
                        InlineMathNode(source.substring(index + 1, cursor)))
                    break
                }
                escaped = char == '\\' && !escaped
                if (char != '\\') escaped = false
                cursor++
            }
        }
        for (plugin in plugins?.findInlinePlugins(source, index).orEmpty()) {
            val parsed = plugin.parse(source, index) ?: continue
            if (parsed.consumed !in 1..source.length - index) continue
            return inlineMatch(source, index, absoluteOffset, parsed.consumed, parsed.node)
        }
        return null
    }

    private fun inlineMatch(source: String, index: Int, offset: Int, consumed: Int, node: Markup) =
        NativeCustomInlineMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW,
            source.substring(index, index + consumed), SourceRange(offset, consumed), payload = node), consumed)

    private fun mathClosing(source: String, delimiter: String): Int? {
        if (delimiter == "$$") return source.indexOf(delimiter).takeIf { it >= 0 }
        var at = source.indexOf(delimiter)
        while (at >= 0) {
            var before = at - 1; var escapes = 0
            while (before >= 0 && source[before--] == '\\') escapes++
            if (escapes % 2 == 0) return at
            at = source.indexOf(delimiter, at + delimiter.length)
        }
        return null
    }
    private fun blockPlugin(lines: List<String>, index: Int, offset: Int): NativeCustomBlockMatch? {
        val opening = lines[index]
        if (enableExtensions) {
            val trimmed = opening.trim()
            if (trimmed.startsWith("$$")) {
                val closing = if (trimmed.startsWith("$$")) "$$" else "\\]"
                val rest = trimmed.drop(2)
                var end = index + 1
                var body = rest
                while (mathClosing(body, closing) == null && end < lines.size) body += "\n" + lines[end++]
                val close = mathClosing(body, closing)
                val latex = (if (close == null) body else body.substring(0, close)).trim()
                return blockMatch(lines, index, end, offset, BlockMathNode(latex))
            }
            val footnote = Regex("^ {0,3}\\[\\^([^]]+)]\\:\\s+(.+)$").matchEntire(opening)
            if (footnote != null) {
                var end = index + 1
                // Keep blank and indented continuation lines in the same definition.
                while (end < lines.size) {
                    val line = lines[end]
                    if (line.isBlank() || line.startsWith("    ") || line.startsWith('\t')) end++ else break
                }
                val raw = lines.subList(index, end).joinToString("\n")
                return NativeCustomBlockMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.FOOTNOTE_DEFINITION,
                    raw, SourceRange(offset, raw.length), literalText = raw, label = footnote.groupValues[1]), end - index)
            }
        }
        for (plugin in plugins?.findBlockPlugins(opening).orEmpty()) {
            val node = plugin.createNode(opening) ?: continue
            node.pluginId = plugin.id
            var end = index + 1
            while (end < lines.size && !plugin.isClosingLine(node, lines[end])) end++
            plugin.complete(node, lines.subList(index + 1, end))
            if (end < lines.size) end++
            return blockMatch(lines, index, end, offset, node)
        }
        if (!enableExtensions || !Regex("""(?i)^\s*<details(?: open)?>\s*$""").matches(opening)) return null
        var depth = 1
        var end = index + 1
        var fence: Char? = null
        var fenceLength = 0
        while (end < lines.size) {
            val trimmed = lines[end].trim()
            val fenceRun = trimmed.takeWhile { it == '`' || it == '~' }
            if (fence == null && fenceRun.length >= 3 && fenceRun.all { it == fenceRun.first() }) {
                fence = fenceRun.first(); fenceLength = fenceRun.length
            } else if (fence != null && trimmed.takeWhile { it == fence }.length >= fenceLength && trimmed.all { it == fence || it.isWhitespace() }) {
                fence = null
            } else if (fence == null) {
                if (Regex("(?i)^<details(?: open)?>$").matches(trimmed)) depth++
                if (trimmed.equals("</details>", true)) { depth--; if (depth == 0) break }
            }
            end++
        }
        val content = lines.subList(index + 1, end).joinToString("\n")
        val summaryMatch = Regex("(?is)<summary>(.*?)</summary>").find(content)
        val summary = summaryMatch?.groupValues?.get(1).orEmpty().trim()
        val body = if (summaryMatch == null) content else content.removeRange(summaryMatch.range)
        val node = DetailsNode(opening.trim().equals("<details open>", true)).also {
            it.summarySource = summary
            it.bodySource = body.trim()
            val paragraph = Paragraph()
            val converter = NativeMarkdownMarkupConverter(summary)
            val inline = RustMarkdownBridge.parseInline(summary, enableGFM = enableGFM,
                enableExtensions = enableExtensions, customInline = ::inlinePlugin)?.children
                ?: NativeMarkdownInlineParser.parse(summary, 0, emptyMap(), enableGFM, customInline = ::inlinePlugin)
            inline.forEach { child -> paragraph.appendChild(converter.convert(child)) }
            FootnoteReferencePostProcessor(summary).process(paragraph)
            it.summary = listOf(paragraph)
            it.body = parseMarkdown(it.bodySource, plugins, enableCache = false).children().toList()
        }
        if (end < lines.size) end++
        return blockMatch(lines, index, end, offset, node)
    }

    private fun blockMatch(lines: List<String>, start: Int, end: Int, offset: Int, node: Markup): NativeCustomBlockMatch {
        val raw = lines.subList(start, end).joinToString("\n")
        return NativeCustomBlockMatch(NativeMarkdownNode(NativeMarkdownNode.Kind.RAW, raw,
            SourceRange(offset, raw.length), payload = node), end - start)
    }

    class Builder internal constructor() {
        fun build(): NativeMarkdownParser = NativeMarkdownParser()
    }
    companion object { @JvmStatic fun builder(): Builder = Builder() }
}
