package com.jackcaow.smoothmarkdown

import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

/** A single inline key cap, including the plain text that should remain selectable. */
internal class HtmlKbdNode(val label: String) : CustomNode()

/** Groups `<kbd>` contents before Compose builds one inline widget for the key. */
internal class HtmlKbdPostProcessor {
    fun process(root: Node) = visit(root)

    private fun visit(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
            if (child is DetailsNode) {
                child.summary.forEach(::visit)
                child.body.forEach(::visit)
            }
            val replacement = if (child is HtmlInline && isOpeningKbd(child)) replaceKbd(child) else null
            if (replacement != null) {
                child = replacement.next
            } else {
                val next = child.next
                visit(child)
                child = next
            }
        }
    }

    private fun replaceKbd(open: HtmlInline): HtmlKbdNode {
        val contents = mutableListOf<Node>()
        var depth = 1
        var close: Node? = null
        var sibling = open.next
        while (sibling != null) {
            val inline = sibling as? HtmlInline
            val tag = inline?.literal?.let(SafeHtml::lexTag)
            if (inline != null && tag?.name == "kbd" && tag.end == inline.literal.length) {
                if (tag.isClosing) depth-- else if (!tag.isSelfClosing) depth++
                if (depth == 0) { close = sibling; break }
            }
            contents += sibling
            sibling = sibling.next
        }
        val key = HtmlKbdNode(contents.joinToString("") { it.plainText() })
        open.insertBefore(key)
        var current: Node? = open
        while (current != null) {
            val next = current.next
            current.unlink()
            if (current === close || (close == null && next == null)) break
            current = next
        }
        return key
    }

    private fun Node.plainText(): String = when (this) {
        is Text -> literal
        is Code -> literal
        is SoftLineBreak, is HardLineBreak -> " "
        is HtmlInline -> ""
        else -> buildString {
            var child = firstChild
            while (child != null) {
                append(child.plainText())
                child = child.next
            }
        }
    }

    private fun isOpeningKbd(node: HtmlInline): Boolean = SafeHtml.lexTag(node.literal)?.let {
        it.name == "kbd" && !it.isClosing && !it.isSelfClosing && it.end == node.literal.length
    } == true
}
