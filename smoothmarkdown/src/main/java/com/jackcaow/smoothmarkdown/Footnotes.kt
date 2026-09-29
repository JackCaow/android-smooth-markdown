package com.jackcaow.smoothmarkdown

import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.InlineParser
import org.commonmark.parser.PostProcessor
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

internal class FootnoteReferenceNode(val label: String) : CustomNode()

internal class FootnoteDefinitionNode(val label: String) : CustomBlock() {
    internal var rawInlineSource: String = ""
}

/** CommonMark consumes unknown square brackets before custom inline parsers run. */
internal class FootnoteReferencePostProcessor(private val source: String) : PostProcessor {
    private val reference = Regex("\\[\\^([^]]+)]")
    private val definitionMatchCursors = java.util.IdentityHashMap<FootnoteDefinitionNode, Int>()

    override fun process(node: Node): Node {
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

/** Parses [^label]: content with indented continuation lines into one block. */
internal class FootnoteDefinitionParserFactory : BlockParserFactory {
    private val definition = Regex("^\\[\\^([^]]+)]\\:\\s+(.+)$")

    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        if (state.indent >= 4) return null
        val line = state.line.content.toString()
        val match = definition.matchEntire(line.substring(state.nextNonSpaceIndex)) ?: return null
        return BlockStart.of(FootnoteDefinitionParser(match.groupValues[1], match.groupValues[2]))
            .atIndex(line.length)
    }
}

private class FootnoteDefinitionParser(label: String, firstLine: String) : AbstractBlockParser() {
    private val footnote = FootnoteDefinitionNode(label)
    private val lines = mutableListOf(firstLine)

    override fun getBlock(): FootnoteDefinitionNode = footnote

    override fun tryContinue(state: ParserState): BlockContinue? {
        val line = state.line.content.toString()
        return when {
            line.isBlank() -> BlockContinue.atIndex(line.length)
            line.startsWith("    ") -> BlockContinue.atIndex(4)
            line.startsWith('\t') -> BlockContinue.atIndex(1)
            else -> null
        }
    }

    override fun addLine(line: SourceLine) {
        val content = line.content.toString().trim()
        if (content.isNotEmpty()) lines += content
    }

    override fun parseInlines(inlineParser: InlineParser) {
        footnote.rawInlineSource = lines.joinToString("\n")
        val source = SourceLines.of(lines.map { SourceLine.of(it, null) })
        inlineParser.parse(source, footnote)
    }
}
