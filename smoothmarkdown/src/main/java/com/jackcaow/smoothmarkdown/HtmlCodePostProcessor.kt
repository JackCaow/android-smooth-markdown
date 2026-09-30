package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Code
import com.jackcaow.smoothmarkdown.ast.HtmlInline
import com.jackcaow.smoothmarkdown.ast.Node

/** Preserve the source inside HTML code tags before Markdown inline rendering can interpret it. */
internal class HtmlCodePostProcessor(private val source: String) {
    fun process(root: Node) {
        visit(root)
    }

    private fun visit(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
            val next = child.next
            if (child is DetailsNode) {
                child.summary.forEach { HtmlCodePostProcessor(child.summarySource).process(it) }
                child.body.forEach { HtmlCodePostProcessor(child.bodySource).process(it) }
            }
            val replacement = if (child is HtmlInline && isOpeningCode(child)) replaceCode(child, parent) else null
            if (replacement != null) {
                child = replacement.next
            } else {
                visit(child)
                child = next
            }
        }
    }

    private fun replaceCode(open: HtmlInline, parent: Node): Code? {
        val openStart = open.sourceSpans.firstOrNull()?.inputIndex ?: return null
        val contentStart = openStart + open.literal.length
        if (openStart < 0 || contentStart > source.length || source.substring(openStart, contentStart) != open.literal) return null

        var depth = 1
        var close: Node? = null
        var sibling = open.next
        while (sibling != null) {
            if (sibling is HtmlInline) {
                val tag = SafeHtml.lexTag(sibling.literal)
                if (tag?.name == "code" && tag.end == sibling.literal.length) {
                    if (tag.isClosing) depth-- else if (!tag.isSelfClosing) depth++
                    if (depth == 0) { close = sibling; break }
                }
            }
            sibling = sibling.next
        }
        val contentEnd = if (close != null) close.sourceSpans.firstOrNull()?.inputIndex
            else parent.sourceSpans.lastOrNull()?.let { it.inputIndex + it.length }
        if (contentEnd == null || contentEnd !in contentStart..source.length) return null

        val replacement = Code(inlineSource(parent, contentStart, contentEnd))
        open.insertBefore(replacement)
        var current: Node? = open
        while (current != null) {
            val next = current.next
            current.unlink()
            if (current === close || (close == null && next == null)) break
            current = next
        }
        return replacement
    }

    private fun inlineSource(parent: Node, start: Int, end: Int): String {
        val spans = parent.sourceSpans
        if (spans.size < 2) return source.substring(start, end)
        return buildString {
            var previousEnd = start
            for (span in spans) {
                val segmentStart = maxOf(start, span.inputIndex)
                val segmentEnd = minOf(end, span.inputIndex + span.length)
                if (segmentStart >= segmentEnd) continue
                if (segmentStart > previousEnd && isNotEmpty()) {
                    val gap = source.substring(previousEnd, segmentStart)
                    append(if ("\r\n" in gap) "\r\n" else "\n")
                }
                append(source, segmentStart, segmentEnd)
                previousEnd = segmentEnd
            }
        }
    }

    private fun isOpeningCode(node: HtmlInline): Boolean = SafeHtml.lexTag(node.literal)?.let {
        it.name == "code" && !it.isClosing && !it.isSelfClosing && it.end == node.literal.length
    } == true
}
