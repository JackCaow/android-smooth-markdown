package com.jackcaow.smoothmarkdown

import org.commonmark.node.CustomBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.parser.InlineParser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

internal class DetailsNode(val isOpen: Boolean) : CustomBlock() {
    var summary: List<Node> = emptyList()
        internal set
    var body: List<Node> = emptyList()
        internal set
}

/** Matches the Flutter parser's two complete opening lines, independent of enableHtml. */
internal class DetailsParserFactory : BlockParserFactory {
    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        val line = state.line.content.toString()
        val opening = line.trim().lowercase()
        if (opening != "<details>" && opening != "<details open>") return null
        return BlockStart.of(DetailsParser(isOpen = opening == "<details open>"))
            .atIndex(line.length)
    }
}

private class DetailsParser(isOpen: Boolean) : AbstractBlockParser() {
    private val details = DetailsNode(isOpen)
    private val lines = mutableListOf<String>()
    private var firstLine = true
    private var closed = false
    private var summaryText = ""
    private var bodyMarkdown = ""

    override fun getBlock(): DetailsNode = details

    override fun tryContinue(state: ParserState): BlockContinue? {
        if (closed) return null
        val line = state.line.content.toString()
        if (line.trim().equals("</details>", ignoreCase = true)) {
            closed = true
            return BlockContinue.atIndex(line.length)
        }
        return BlockContinue.atIndex(0)
    }

    override fun addLine(line: SourceLine) {
        if (firstLine) {
            firstLine = false
            return
        }
        if (!closed) lines += line.content.toString()
    }

    override fun closeBlock() {
        var foundSummary = false
        var inSummary = false
        val summaryLines = mutableListOf<String>()
        val bodyLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            if (lower.startsWith("<summary>")) {
                foundSummary = true
                inSummary = true
                val rest = trimmed.substring("<summary>".length)
                val end = rest.lowercase().indexOf("</summary>")
                summaryLines += (if (end >= 0) rest.substring(0, end) else rest).trim()
                if (end >= 0) inSummary = false
            } else if (inSummary) {
                val end = lower.indexOf("</summary>")
                summaryLines += (if (end >= 0) trimmed.substring(0, end) else trimmed).trim()
                if (end >= 0) inSummary = false
            } else if (foundSummary) {
                bodyLines += line
            }
        }
        summaryText = summaryLines.filter { it.isNotEmpty() }.joinToString(" ")
        bodyMarkdown = bodyLines.joinToString("\n")
    }

    override fun parseInlines(inlineParser: InlineParser) {
        val summary = Paragraph()
        if (summaryText.isNotEmpty()) {
            inlineParser.parse(SourceLines.of(SourceLine.of(summaryText, null)), summary)
            FootnoteReferencePostProcessor(summaryText).process(summary)
        }
        details.summary = listOf(summary)
        details.body = if (bodyMarkdown.isEmpty()) emptyList() else parseMarkdown(bodyMarkdown).children()
    }

    private fun Node.children(): List<Node> = buildList {
        var child = firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }
}
