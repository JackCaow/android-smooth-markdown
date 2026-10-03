package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*

/** Claims complete disclosures from their authored source; code literals never close them. */
internal class HtmlDetailsPostProcessor(private val source: String, private val plugins: ParserPluginRegistry?) {
    fun process(root: Node) {
        var child = root.firstChild
        while (child != null) {
            val next = child.next
            if (child is DetailsNode) {
                val details = child
                details.summary.forEach { HtmlDetailsPostProcessor(details.summarySource, plugins).process(it) }
                details.body.forEach { HtmlDetailsPostProcessor(details.bodySource, plugins).process(it) }
            } else if (child is HtmlBlock) {
                val raw = child.literal
                val start = raw.indexOfFirst { !it.isWhitespace() }
                val match = if (start >= 0) disclosure(raw, start) else null
                if (match != null) {
                    child.insertBefore(makeNode(match))
                    val trailing = raw.substring(match.end)
                    parseMarkdown(trailing, plugins, enableCache = false, enableHtml = true).children().toList().forEach(child::insertBefore)
                    child.unlink()
                }
            } else if (child is Paragraph) {
                splitParagraph(child)
            } else process(child)
            child = next
        }
    }

    private fun splitParagraph(paragraph: Paragraph) {
        val children = paragraph.children().toList()
        // An invalid earlier opener must not suppress a later complete disclosure.
        val candidate = children.filterIsInstance<HtmlInline>().firstNotNullOfOrNull { opener ->
            val tag = SafeHtml.lexTag(opener.literal)
            if (tag?.name != "details" || tag.isClosing || tag.isSelfClosing) return@firstNotNullOfOrNull null
            val start = opener.sourceSpans.firstOrNull()?.inputIndex ?: return@firstNotNullOfOrNull null
            val match = disclosure(source, start) ?: return@firstNotNullOfOrNull null
            // Only claim an AST-matched closing token; Markdown code cannot supply a closer.
            val closer = children.filterIsInstance<HtmlInline>().firstOrNull {
                it.sourceSpans.firstOrNull()?.inputIndex == match.closeStart &&
                    SafeHtml.lexTag(it.literal)?.let { closing -> closing.name == "details" && closing.isClosing } == true
            } ?: return@firstNotNullOfOrNull null
            Triple(opener, match, closer)
        } ?: return
        val (opener, match, closer) = candidate
        val before = Paragraph()
        var child = paragraph.firstChild
        while (child != null && child !== opener) {
            val next = child.next
            before.appendChild(child)
            child = next
        }
        if (before.firstChild != null) paragraph.insertBefore(before)
        paragraph.insertBefore(makeNode(match))
        child = opener
        while (child != null) {
            val next = child.next
            child.unlink()
            if (child === closer) break
            child = next
        }
        if (paragraph.firstChild == null) paragraph.unlink() else splitParagraph(paragraph)
    }

    private data class Match(val summary: String, val body: String, val open: Boolean, val end: Int, val closeStart: Int)

    private fun makeNode(match: Match): DetailsNode = DetailsNode(match.open).also {
        it.summarySource = match.summary
        it.bodySource = match.body
        // Inline parsing preserves summary Markdown while avoiding accidental heading/list recognition.
        val paragraph = NativeMarkdownParser(enableGFM = true, enableExtensions = true, plugins = plugins)
            .parseInlineMarkup(match.summary)
        it.summary = listOf(paragraph)
        it.body = parseMarkdown(match.body, plugins, enableCache = false, enableHtml = true).children().toList()
        HtmlCodePostProcessor(match.summary).process(paragraph)
        FootnoteReferencePostProcessor(match.summary).process(paragraph)
    }

    private fun disclosure(raw: String, start: Int): Match? {
        val open = SafeHtml.lexTag(raw, start) ?: return null
        if (open.name != "details" || open.isClosing || open.isSelfClosing || open.attributes.keys.any { it != "open" }) return null
        var depth = 1
        var cursor = open.end
        var summaryStart: Int? = null
        var summaryEnd: Int? = null
        var bodyStart = open.end
        var codeRun = 0
        var htmlCode = false
        while (cursor < raw.length) {
            val char = raw[cursor]
            if (char == '\\') { cursor += minOf(2, raw.length - cursor); continue }
            if (char == '`' && !htmlCode) {
                var end = cursor + 1
                while (raw.getOrNull(end) == '`') end++
                val run = end - cursor
                if (codeRun == run) codeRun = 0
                else if (codeRun == 0 && hasClosingBacktick(raw, end, run)) codeRun = run
                cursor = end
                continue
            }
            val tag = if (codeRun == 0 && char == '<') SafeHtml.lexTag(raw, cursor) else null
            if (tag != null) {
                if (tag.name == "code") htmlCode = !tag.isClosing
                else if (!htmlCode && tag.name == "details") {
                    if (tag.isClosing) depth-- else if (!tag.isSelfClosing) depth++
                    if (depth == 0) return Match(
                        if (summaryStart != null && summaryEnd != null) raw.substring(summaryStart, summaryEnd) else "",
                        raw.substring(bodyStart, cursor), open.attributes.containsKey("open"), tag.end, cursor)
                } else if (!htmlCode && depth == 1 && tag.name == "summary") {
                    if (!tag.isClosing && summaryStart == null) summaryStart = tag.end
                    else if (tag.isClosing && summaryStart != null && summaryEnd == null) {
                        summaryEnd = cursor; bodyStart = tag.end
                    }
                }
                cursor = tag.end
            } else cursor++
        }
        return null
    }

    private fun hasClosingBacktick(raw: String, start: Int, run: Int): Boolean {
        var cursor = start
        while (cursor < raw.length) {
            if (raw[cursor] != '`') { cursor++; continue }
            val first = cursor
            while (raw.getOrNull(cursor) == '`') cursor++
            if (cursor - first == run) return true
        }
        return false
    }
}
