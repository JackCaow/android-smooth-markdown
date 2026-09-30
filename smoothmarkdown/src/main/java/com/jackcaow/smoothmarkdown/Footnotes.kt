package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.CustomBlock
import com.jackcaow.smoothmarkdown.ast.CustomNode
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.Link
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Text

internal class FootnoteReferenceNode(val label: String) : CustomNode()

internal class FootnoteDefinitionNode(val label: String) : CustomBlock() {
    internal var rawInlineSource: String = ""
}

/** CommonMark consumes unknown square brackets before custom inline parsers run. */
internal class FootnoteReferencePostProcessor(private val source: String) {
    private val reference = Regex("\\[\\^([^]]+)]")
    private val definitionMatchCursors = java.util.IdentityHashMap<FootnoteDefinitionNode, Int>()

    fun process(node: Node): Node {
        visit(node)
        return node
    }

    private fun visit(node: Node) {
        var child = node.firstChild
        while (child != null) {
            val next = child.next
            if (child is Text && child.parent !is Link && child.parent !is Image) split(child)
            else visit(child)
            child = next
        }
    }

    private fun split(text: Text) {
        val literal = text.literal
        val matches = reference.findAll(literal).toList()
        if (matches.isEmpty()) return
        val definition = generateSequence(text.parent) { it.parent }.filterIsInstance<FootnoteDefinitionNode>().firstOrNull()
        val hasSourceSpans = text.sourceSpans.isNotEmpty()
        val raw = if (!hasSourceSpans && definition != null) definition.rawInlineSource else text.sourceSpans.joinToString("") { span ->
            source.substring(span.inputIndex, (span.inputIndex + span.length).coerceAtMost(source.length))
        }
        val sourceMatches = reference.findAll(raw).toList()
        var sourceIndex = if (!hasSourceSpans && definition != null) definitionMatchCursors[definition] ?: 0 else 0
        val accepted = matches.filter { rendered ->
            if (!hasSourceSpans && definition == null) return@filter true
            while (sourceIndex < sourceMatches.size && sourceMatches[sourceIndex].groupValues[1] != rendered.groupValues[1]) {
                sourceIndex++
            }
            val original = sourceMatches.getOrNull(sourceIndex++) ?: return@filter false
            val slashCount = raw.substring(0, original.range.first).takeLastWhile { it == '\\' }.length
            slashCount % 2 == 0
        }
        if (!hasSourceSpans && definition != null) definitionMatchCursors[definition] = sourceIndex
        if (accepted.isEmpty()) return
        var cursor = 0
        accepted.forEach { match ->
            if (match.range.first > cursor) text.insertBefore(Text(literal.substring(cursor, match.range.first)))
            text.insertBefore(FootnoteReferenceNode(match.groupValues[1]))
            cursor = match.range.last + 1
        }
        if (cursor < literal.length) text.insertBefore(Text(literal.substring(cursor)))
        text.unlink()
    }
}
