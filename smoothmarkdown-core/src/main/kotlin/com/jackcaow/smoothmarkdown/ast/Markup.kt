package com.jackcaow.smoothmarkdown.ast

/** UTF-16 source coordinates into the original Markdown input, with zero-based lines and columns. */
data class SourceSpan(val lineIndex: Int, val columnIndex: Int, val inputIndex: Int, val length: Int) {
    init { require(lineIndex >= 0 && columnIndex >= 0 && inputIndex >= 0 && length >= 0) }
    companion object {
        @JvmStatic fun of(lineIndex: Int, columnIndex: Int, inputIndex: Int, length: Int) =
            SourceSpan(lineIndex, columnIndex, inputIndex, length)
    }
}

/**
 * A library-owned mutable Markdown syntax tree. Moving a node detaches it from its previous parent.
 * Sibling links are maintained by the mutation methods; callers cannot assign inconsistent links.
 * Tree mutation is intended for one thread, before rendering or publishing the document.
 */
open class Markup {
    var parent: Markup? = null
        private set
    var firstChild: Markup? = null
        private set
    var lastChild: Markup? = null
        private set
    var previous: Markup? = null
        private set
    var next: Markup? = null
        private set
    var sourceSpans: List<SourceSpan> = emptyList()

    fun addSourceSpan(span: SourceSpan) { sourceSpans = sourceSpans + span }

    fun appendChild(child: Markup) {
        checkCanAdopt(child)
        child.unlink()
        child.parent = this
        child.previous = lastChild
        lastChild?.next = child
        if (firstChild == null) firstChild = child
        lastChild = child
    }

    fun prependChild(child: Markup) {
        checkCanAdopt(child)
        child.unlink()
        child.parent = this
        child.next = firstChild
        firstChild?.previous = child
        if (lastChild == null) lastChild = child
        firstChild = child
    }

    /** Insert [sibling] immediately before this node. This node must have a parent. */
    fun insertBefore(sibling: Markup) {
        if (sibling === this) return
        val container = requireNotNull(parent) { "Cannot insert a sibling beside a detached node" }
        container.checkCanAdopt(sibling)
        sibling.unlink()
        sibling.parent = container
        sibling.previous = previous
        sibling.next = this
        previous?.next = sibling
        if (container.firstChild === this) container.firstChild = sibling
        previous = sibling
    }

    /** Insert [sibling] immediately after this node. This node must have a parent. */
    fun insertAfter(sibling: Markup) {
        if (sibling === this) return
        val container = requireNotNull(parent) { "Cannot insert a sibling beside a detached node" }
        container.checkCanAdopt(sibling)
        sibling.unlink()
        sibling.parent = container
        sibling.previous = this
        sibling.next = next
        next?.previous = sibling
        if (container.lastChild === this) container.lastChild = sibling
        next = sibling
    }

    fun unlink() {
        previous?.next = next
        next?.previous = previous
        parent?.let {
            if (it.firstChild === this) it.firstChild = next
            if (it.lastChild === this) it.lastChild = previous
        }
        parent = null
        previous = null
        next = null
    }

    fun children(): Sequence<Markup> = generateSequence(firstChild) { it.next }

    /** Iterative pre-order traversal, excluding this node. */
    fun descendants(): Sequence<Markup> = sequence {
        var current = firstChild
        while (current != null) {
            yield(current)
            if (current.firstChild != null) {
                current = current.firstChild
            } else {
                var tail: Markup? = current
                while (tail != null && tail !== this@Markup && tail.next == null) tail = tail.parent
                current = if (tail == null || tail === this@Markup) null else tail.next
            }
        }
    }

    private fun checkCanAdopt(child: Markup) {
        var ancestor: Markup? = this
        while (ancestor != null) {
            require(ancestor !== child) { "A Markdown tree cannot contain a cycle" }
            ancestor = ancestor.parent
        }
    }
}

/** Compatibility spelling for existing builder and plugin integrations. */
typealias Node = Markup

open class Block : Markup()
class Document : Block()
class Paragraph : Block()
class Heading(var level: Int = 1) : Block()
class Text(var literal: String = "") : Markup()
class Emphasis(var openingDelimiter: String = "*", var closingDelimiter: String = openingDelimiter) : Markup()
class StrongEmphasis(var openingDelimiter: String = "**", var closingDelimiter: String = openingDelimiter) : Markup()
class Code(var literal: String = "") : Markup()
class SoftLineBreak : Markup()
class HardLineBreak : Markup()
class Link(var destination: String = "", var title: String? = null) : Markup()
class Image(var destination: String = "", var title: String? = null) : Markup()
class FencedCodeBlock(var info: String = "", var literal: String = "") : Block() {
    var fenceChar: Char = '`'
    var fenceLength: Int = 3
    var fenceIndent: Int = 0
}
class IndentedCodeBlock(var literal: String = "") : Block()
class HtmlBlock(var literal: String = "") : Block()
class HtmlInline(var literal: String = "") : Markup()
class BlockQuote : Block()
class ThematicBreak : Block()
open class ListBlock(var isTight: Boolean = true) : Block()
class BulletList(var bulletMarker: Char = '-', isTight: Boolean = true) : ListBlock(isTight)
class OrderedList(var startNumber: Int = 1, var delimiter: Char = '.', isTight: Boolean = true) : ListBlock(isTight) {
    var markerStartNumber: Int
        get() = startNumber
        set(value) { startNumber = value }
    var markerDelimiter: Char
        get() = delimiter
        set(value) { delimiter = value }
}
class ListItem : Block()
open class CustomNode : Markup()
open class CustomBlock : Block()
class Strikethrough : Markup()
class TableBlock : CustomBlock()
class TableHead : CustomBlock()
class TableBody : CustomBlock()
class TableRow : CustomBlock()
class TableCell(var alignment: Alignment? = null, var isHeader: Boolean = false) : CustomNode() {
    enum class Alignment { LEFT, CENTER, RIGHT }
}
class TaskListItemMarker(var isChecked: Boolean = false) : CustomNode()
class LinkReferenceDefinition(var label: String = "", var destination: String = "", var title: String? = null) : Block()
