package com.jackcaow.smoothmarkdown.editor

import com.jackcaow.smoothmarkdown.parseMarkdown
import com.jackcaow.smoothmarkdown.ast.BlockQuote
import com.jackcaow.smoothmarkdown.ast.BulletList
import com.jackcaow.smoothmarkdown.ast.ListItem
import com.jackcaow.smoothmarkdown.ast.OrderedList
import com.jackcaow.smoothmarkdown.ast.Paragraph

/** Builds a replacement for complete, contiguous top-level prose blocks only. */
internal object FormattedBlockTransforms {
    fun replacement(
        source: String,
        blocks: List<MarkdownDocumentBlock>,
        command: MarkdownEditorCommand,
    ): String? {
        if (blocks.isEmpty() || blocks.any { it.kind !in setOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING) })
            return null
        val bodies = blocks.map { MarkdownFormattedBlock.text(it) ?: return null }
        val first = blocks.first()
        val last = blocks.last()
        val original = source.substring(first.range.min, last.range.max)
        val newline = lineEnding(original, source)
        val transformed = when (command) {
            MarkdownEditorCommand.PARAGRAPH -> bodies.mapIndexed { index, body ->
                val separator = if (index == 0) "" else {
                    val originalSeparator = source.substring(blocks[index - 1].range.max, blocks[index].range.min)
                    if (lineBreakCount(originalSeparator) >= 2) originalSeparator else newline + newline
                }
                separator + body
            }.joinToString("")
            MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3, MarkdownEditorCommand.HEADING4,
            MarkdownEditorCommand.HEADING5, MarkdownEditorCommand.HEADING6 -> {
                val level = command.ordinal - MarkdownEditorCommand.HEADING1.ordinal + 1
                bodies.mapIndexed { index, body ->
                    val separator = if (index == 0) "" else
                        source.substring(blocks[index - 1].range.max, blocks[index].range.min)
                    separator + "#".repeat(level) + " " + body
                }.joinToString("")
            }
            MarkdownEditorCommand.UNORDERED_LIST, MarkdownEditorCommand.ORDERED_LIST,
            MarkdownEditorCommand.TASK_LIST -> bodies.mapIndexed { index, body ->
                val marker = when (command) {
                    MarkdownEditorCommand.UNORDERED_LIST -> "- "
                    MarkdownEditorCommand.ORDERED_LIST -> "${index + 1}. "
                    else -> "- [ ] "
                }
                prefixLines(body, marker, " ".repeat(marker.length), newline)
            }.joinToString(newline)
            MarkdownEditorCommand.BLOCKQUOTE -> bodies.joinToString(newline + ">" + newline) { body ->
                prefixLines(body, "> ", "> ", newline)
            }
            else -> return null
        }
        if (transformed == original) return null
        return transformed.takeIf { structurallyMatches(it, bodies.size, command) }
    }

    private fun structurallyMatches(markdown: String, blockCount: Int, command: MarkdownEditorCommand): Boolean {
        val parsed = MarkdownDocumentCodec.parse(markdown).blocks
        if (parsed.isEmpty() || parsed.first().range.min != 0 || parsed.last().range.max != markdown.length) return false
        val expectedKind = when (command) {
            MarkdownEditorCommand.PARAGRAPH -> MarkdownBlockKind.PARAGRAPH
            MarkdownEditorCommand.HEADING1, MarkdownEditorCommand.HEADING2,
            MarkdownEditorCommand.HEADING3, MarkdownEditorCommand.HEADING4,
            MarkdownEditorCommand.HEADING5, MarkdownEditorCommand.HEADING6 -> MarkdownBlockKind.HEADING
            MarkdownEditorCommand.ORDERED_LIST -> MarkdownBlockKind.ORDERED_LIST
            MarkdownEditorCommand.UNORDERED_LIST, MarkdownEditorCommand.TASK_LIST -> MarkdownBlockKind.BULLET_LIST
            MarkdownEditorCommand.BLOCKQUOTE -> MarkdownBlockKind.QUOTE
            else -> return false
        }
        if (expectedKind in setOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.HEADING))
            return parsed.size == blockCount && parsed.all { it.kind == expectedKind &&
                (expectedKind != MarkdownBlockKind.HEADING || it.headingLevel ==
                    command.ordinal - MarkdownEditorCommand.HEADING1.ordinal + 1) }
        if (parsed.size != 1 || parsed.single().kind != expectedKind) return false
        val node = parseMarkdown(markdown).firstChild ?: return false
        if (node.next != null) return false
        val children = generateSequence(node.firstChild) { it.next }.toList()
        return when (command) {
            MarkdownEditorCommand.BLOCKQUOTE -> node is BlockQuote && children.size == blockCount &&
                children.all { it is Paragraph }
            MarkdownEditorCommand.ORDERED_LIST, MarkdownEditorCommand.UNORDERED_LIST,
            MarkdownEditorCommand.TASK_LIST -> {
                val correctList = if (command == MarkdownEditorCommand.ORDERED_LIST) node is OrderedList else node is BulletList
                if (!correctList || children.size != blockCount || children.any { it !is ListItem }) false
                else if (command == MarkdownEditorCommand.TASK_LIST) {
                    val list = MarkdownSourceList.parse(parsed.single()) ?: return false
                    list.items.size == blockCount && list.items.all { item ->
                        item.taskStateOffset != null && !item.checked && item.parts.all { it is MarkdownSourceList.Line }
                    }
                } else children.all { it.firstChild is Paragraph && it.firstChild?.next == null }
            }
            else -> false
        }
    }

    private fun prefixLines(body: String, first: String, rest: String, newline: String): String =
        body.split(Regex("\\r\\n|\\n|\\r")).mapIndexed { index, line ->
            (if (index == 0) first else rest) + line
        }.joinToString(newline)

    private fun lineBreakCount(value: String): Int = Regex("\\r\\n|\\n|\\r").findAll(value).count()

    private fun lineEnding(selected: String, fullSource: String): String {
        val sample = selected.ifEmpty { fullSource }
        return when {
            "\r\n" in sample -> "\r\n"
            '\n' in sample -> "\n"
            '\r' in sample -> "\r"
            "\r\n" in fullSource -> "\r\n"
            '\r' in fullSource && '\n' !in fullSource -> "\r"
            else -> "\n"
        }
    }
}
