package com.jackcaow.smoothmarkdown.nativeparser

import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode.Kind

/** Kotlin port of SmoothMarkdown's original CommonMark/GFM source-preserving block scanner. */
internal class NativeMarkdownASTParser(
    private val enableGFM: Boolean = true,
    private val customInline: ((source: String, index: Int, absoluteOffset: Int) -> NativeCustomInlineMatch?)? = null,
    private val customBlock: ((lines: List<String>, startIndex: Int, sourceOffset: Int) -> NativeCustomBlockMatch?)? = null,
    private val enableNativeExtensions: Boolean = true
) {
    private data class Line(val text: String, val raw: String, val start: Int, val sourceEnd: Int? = null,
                            val projected: Boolean = false, val lazyContinuation: Boolean = false, val virtualIndent: Int = 0) {
        val end get() = sourceEnd ?: (start + raw.length)
        val isBlank get() = text.all { it == ' ' || it == '\t' }
    }
    private data class Fence(val marker: Char, val count: Int, val info: String)
    private data class Marker(val indent: Int, val prefix: String, val ordered: Boolean, val number: Int, val style: Char, val overflowSpaces: Int)
    fun parse(source: String): NativeMarkdownNode {
        val lines = sourceLines(source)
        val references = linkedMapOf<String, NativeMarkdownReference>()
        fun collect(node: NativeMarkdownNode) {
            if (node.kind == Kind.REFERENCE_DEFINITION) references.putIfAbsent(NativeMarkdownReferenceParser.normalize(node.label), NativeMarkdownReference(node.destination, node.title))
            node.children.forEach(::collect)
        }
        scan(lines, source, emptyMap()).forEach(::collect)
        return NativeMarkdownNode(Kind.DOCUMENT, source, SourceRange(0, source.length), scan(lines, source, references).map(::filterHTML))
    }
    private fun filterHTML(node: NativeMarkdownNode): NativeMarkdownNode {
        if (!enableGFM) return node
        return node.copy(children = node.children.map(::filterHTML), literalText = if (node.kind == Kind.INLINE_HTML || node.kind == Kind.HTML_BLOCK) NativeMarkdownHTMLTagFilter.filter(node.literalText ?: node.source) else node.literalText)
    }
    private fun sourceLines(source: String): List<Line> {
        var offset = 0
        val parts = source.split('\n')
        return parts.mapIndexed { i, part ->
            val raw = part + if (i < parts.lastIndex) "\n" else ""
            Line(part.removeSuffix("\r"), raw, offset).also { offset += raw.length }
        }
    }
    private fun scan(lines: List<Line>, source: String, references: Map<String, NativeMarkdownReference>): List<NativeMarkdownNode> {
        val result = mutableListOf<NativeMarkdownNode>(); var index = 0
        val hookLines = if (customBlock != null) lines.map { it.text } else emptyList()
        fun node(kind: Kind, first: Int, limit: Int, children: List<NativeMarkdownNode> = emptyList(), title: String? = null, literalText: String? = null): NativeMarkdownNode {
            val start = lines[first].start; val end = lines[limit - 1].end
            return NativeMarkdownNode(kind, source.substring(start, end), SourceRange(start, end - start), children, title = title, literalText = literalText)
        }
        while (index < lines.size) {
            if (lines[index].isBlank) { index++; continue }
            val start = index; val text = lines[index].text
            val definition = referenceDefinition(index, lines)
            if (definition != null && !definition.first.label.startsWith('^')) {
                val d = definition.first
                result += node(Kind.REFERENCE_DEFINITION, start, definition.second, title = d.title).copy(label = d.label, destination = d.destination)
                index = definition.second; continue
            }
            val fence = fenceOpen(text)
            if (fence != null) {
                index++; while (index < lines.size && !fenceClose(lines[index].text, fence)) index++
                if (index < lines.size) index++
                result += node(Kind.FENCED_CODE, start, index, literalText = projectedCodeText(lines.subList(start, index), true)).copy(info = fence.info); continue
            }
            val custom = if (indentation(text) < 4) customBlock?.invoke(hookLines, index, lines[index].start) else null
            if (custom != null && custom.linesConsumed > 0 && index + custom.linesConsumed <= lines.size) {
                result += custom.node; index += custom.linesConsumed; continue
            }
            val label = match("^ {0,3}\\[\\^([^]]+)\\]:", text)?.get(1)
            if (enableNativeExtensions && label != null) {
                index++; while (index < lines.size && !lines[index].isBlank && (lines[index].text.startsWith("    ") || lines[index].text.startsWith('\t'))) index++
                result += node(Kind.FOOTNOTE_DEFINITION, start, index).copy(label = label); continue
            }
            if (enableNativeExtensions && text.trim(' ', '\t').startsWith("$$")) {
                index++; while (index < lines.size && !lines[index].text.contains("$$")) index++
                if (index < lines.size) index++
                result += node(Kind.BLOCK_MATH, start, index); continue
            }
            val heading = heading(text)
            if (heading != null) {
                result += node(Kind.HEADING, start, start + 1, inline(heading.third, lines[start].start + heading.second.length, references)).copy(level = heading.first)
                index++; continue
            }
            if (isThematic(text)) { result += node(Kind.THEMATIC_BREAK, start, start + 1); index++; continue }
            if (enableGFM && isTable(index, lines)) {
                val alignments = tableSpans(lines[index + 1].text).map { range ->
                    val cell = lines[index + 1].text.substring(range.offset, range.end)
                    when { cell.startsWith(':') && cell.endsWith(':') -> "center"; cell.startsWith(':') -> "left"; cell.endsWith(':') -> "right"; else -> null }
                }
                index += 2
                while (index < lines.size && !lines[index].isBlank && !interruptsParagraph(index, lines)) index++
                val rows = (listOf(start) + (start + 2 until index)).map { rowIndex ->
                    val row = lines[rowIndex]; val cells = tableCells(row, references).take(alignments.size).toMutableList()
                    while (cells.size < alignments.size) cells += NativeMarkdownNode(Kind.TABLE_CELL, "", SourceRange(row.start + row.text.length, 0))
                    NativeMarkdownNode(Kind.TABLE_ROW, row.text, SourceRange(row.start, row.text.length), cells)
                }
                result += node(Kind.TABLE, start, index, rows).copy(tableAlignments = alignments); continue
            }
            if (listMarker(text) != null) { val parsed = list(index, lines, source, references); result += parsed.first; index = parsed.second; continue }
            if (quotePrefix(text) != null) {
                index++; var prior = quoteParagraphCanContinue(text.drop(quotePrefix(text)!!.length))
                while (index < lines.size) {
                    val prefix = quotePrefix(lines[index].text)
                    if (prefix != null) { prior = quoteParagraphCanContinue(lines[index].text.drop(prefix.length)); index++ }
                    else if (prior && !lines[index].isBlank && !interruptsParagraph(index, lines)) index++ else break
                }
                result += node(Kind.BLOCK_QUOTE, start, index, scan(lines.subList(start, index).map(::projectQuoteLine), source, references)); continue
            }
            if (indentation(text) >= 4) {
                index++; while (index < lines.size && (lines[index].isBlank || indentation(lines[index].text) >= 4)) index++
                result += node(Kind.INDENTED_CODE, start, index, literalText = projectedCodeText(lines.subList(start, index), false)); continue
            }
            val ending = NativeMarkdownHTMLBlock.end(text)
            if (ending != null) {
                index++
                if (!ending.matches(text)) while (index < lines.size) {
                    if (ending.matches(lines[index].text)) { if (ending.pattern != null) index++; break }; index++
                }
                val selected = lines.subList(start, index)
                result += node(Kind.HTML_BLOCK, start, index, literalText = if (selected.any { it.projected }) selected.joinToString("") { it.raw } else null); continue
            }
            index++
            while (index < lines.size && !lines[index].isBlank && (lines[index].lazyContinuation || (setextLevel(lines[index].text) == null && !interruptsParagraph(index, lines)))) index++
            val level = if (index < lines.size && !lines[index].lazyContinuation) setextLevel(lines[index].text) else null
            if (level != null) {
                val content = lines.subList(start, index).joinToString("\n") { it.text }
                result += node(Kind.HEADING, start, index + 1, inline(content, lines[start].start, references, true, true)).copy(level = level)
                index++; continue
            }
            result += node(Kind.PARAGRAPH, start, index, paragraphInlines(lines.subList(start, index), source, references))
        }
        return result
    }
    private fun projectedCodeText(lines: List<Line>, fenced: Boolean) = if (lines.any { it.projected }) NativeMarkdownCodeSemantics.text(lines.joinToString("") { it.raw }, fenced) else null
    private fun paragraphInlines(lines: List<Line>, source: String, references: Map<String, NativeMarkdownReference>): List<NativeMarkdownNode> {
        val first = lines.firstOrNull() ?: return emptyList()
        if ((lines.size > 1 && !first.projected && first.text.contains("](")) || lines.all { !it.projected && indentation(it.text) == 0 }) {
            val content = lines.joinToString("\n") { it.text + if (it.raw.endsWith("\r\n")) "\r" else "" }
            return inline(content, first.start, references, true, lines.size > 1 && first.text.contains("]("))
        }
        val children = mutableListOf<NativeMarkdownNode>()
        lines.forEachIndexed { position, line ->
            val leading = line.text.takeWhile { it == ' ' || it == '\t' }
            val removed = if (line.projected || position > 0) leading.length else minOf(3, leading.length)
            val body = line.text.drop(removed); val trailing = body.takeLastWhile { it == ' ' || it == '\t' }.length
            val hard = position < lines.lastIndex && (trailing >= 2 || (trailing == 0 && body.endsWith('\\')))
            val visible = body.dropLast(trailing + if (hard && trailing == 0) 1 else 0)
            children += inline(visible, line.start + removed - line.virtualIndent, references, position == lines.lastIndex)
            if (position < lines.lastIndex) {
                val breakStart = line.start + removed + visible.length
                if (line.end > breakStart) children += NativeMarkdownNode(if (hard) Kind.HARD_BREAK else Kind.SOFT_BREAK, source.substring(breakStart, line.end), SourceRange(breakStart, line.end - breakStart))
            }
        }
        return children
    }
    private fun inline(source: String, offset: Int, references: Map<String, NativeMarkdownReference>, trimTrailingWhitespace: Boolean = false, trimLeadingWhitespace: Boolean = false): List<NativeMarkdownNode> {
        var content = source; var start = offset
        if (trimLeadingWhitespace) { val leading = content.takeWhile { it == ' ' || it == '\t' }.length; content = content.drop(leading); start += leading }
        if (trimTrailingWhitespace) content = content.trimEnd(' ', '\t')
        return NativeMarkdownInlineParser.parse(content, start, references, enableGFM, customInline = customInline)
    }
    private fun heading(line: String): Triple<Int, String, String>? {
        val parts = match("^( {0,3})(#{1,6})(?:[ \\t]+|$)(.*)$", line) ?: return null
        val prefix = parts[1] + parts[2] + line.drop(parts[1].length + parts[2].length).takeWhile { it == ' ' || it == '\t' }
        val body = parts[3].replace(Regex("[ \\t]+#+[ \\t]*$"), "").trimEnd(' ', '\t')
        return Triple(parts[2].length, prefix, if (body.all { it == '#' }) "" else body)
    }
    private fun indentation(line: String): Int { var width = 0; for (c in line) { if (c == ' ') width++ else if (c == '\t') width += 4 - width % 4 else break }; return width }
    private fun displayColumn(text: String) = text.fold(0) { column, c -> column + if (c == '\t') 4 - column % 4 else 1 }
    private fun fenceOpen(line: String): Fence? {
        val trimmed = line.dropWhile { it == ' ' }; val marker = trimmed.firstOrNull() ?: return null
        if (line.length - trimmed.length > 3 || marker !in "`~") return null
        val count = trimmed.takeWhile { it == marker }.length; val info = trimmed.drop(count).trim(' ', '\t')
        if (count < 3 || (marker == '`' && info.contains('`'))) return null
        return Fence(marker, count, NativeMarkdownTextDecoder.decode(info))
    }
    private fun fenceClose(line: String, fence: Fence): Boolean {
        val trimmed = line.dropWhile { it == ' ' }; val run = trimmed.takeWhile { it == fence.marker }.length
        return line.length - trimmed.length <= 3 && run >= fence.count && trimmed.drop(run).all { it == ' ' || it == '\t' }
    }
    private fun isThematic(line: String) = match("^ {0,3}([*_-])(?:[ \\t]*\\1){2,}[ \\t]*$", line) != null
    private fun setextLevel(line: String): Int? = when { Regex("^ {0,3}=+[ \\t]*$").matches(line) -> 1; Regex("^ {0,3}-+[ \\t]*$").matches(line) -> 2; else -> null }
    private fun quotePrefix(line: String) = match("^( {0,3}>)", line)?.get(1)
    private fun projectQuoteLine(line: Line): Line {
        val prefix = quotePrefix(line.text) ?: return line.copy(projected = true, lazyContinuation = true, sourceEnd = line.end)
        var body = line.text.drop(prefix.length); var column = prefix.length; var offset = prefix.length; var virtual = line.virtualIndent
        if (body.firstOrNull() == ' ' || body.firstOrNull() == '\t') { val first = body[0]; body = body.drop(1); offset++; val width = if (first == '\t') 4 - column % 4 else 1; column++; body = " ".repeat(width - 1) + body; virtual += width - 1 }
        val expanded = StringBuilder(); var initial = true
        for (c in body) if (c == '\t' && initial) { val width = 4 - column % 4; expanded.append(" ".repeat(width)); virtual += width - 1; column += width } else { expanded.append(c); if (c == ' ') column++ else initial = false }
        return Line(expanded.toString(), expanded.toString() + if (line.raw.endsWith('\n')) "\n" else "", line.start + offset, line.end, true, virtualIndent = virtual)
    }
    private fun quoteParagraphCanContinue(body: String): Boolean {
        var content = body
        while (quotePrefix(content) != null) content = content.drop(quotePrefix(content)!!.length)
        val marker = listMarker(content)
        if (marker != null) { content = content.drop(marker.prefix.length); while (quotePrefix(content) != null) content = content.drop(quotePrefix(content)!!.length) }
        return content.isNotBlank() && !interruptsParagraph(0, listOf(Line(content, content, 0))) && !content.startsWith("    ") && !content.startsWith('\t')
    }
    private fun interruptsParagraph(index: Int, lines: List<Line>): Boolean {
        val text = lines[index].text; val marker = listMarker(text)
        return heading(text) != null || fenceOpen(text) != null || isThematic(text) || quotePrefix(text) != null || NativeMarkdownHTMLBlock.end(text, true) != null || (marker != null && text.length > marker.prefix.length && (!marker.ordered || marker.number == 1)) || (indentation(text) < 4 && customBlock?.invoke(lines.map { it.text }, index, lines[index].start) != null)
    }
    private fun isTable(index: Int, lines: List<Line>): Boolean {
        if (index + 1 >= lines.size || !lines[index].text.contains('|')) return false
        val cells = tableSpans(lines[index + 1].text)
        return cells.isNotEmpty() && cells.size == tableSpans(lines[index].text).size && cells.all { Regex("^:?-+:?$").matches(lines[index + 1].text.substring(it.offset, it.end)) }
    }
    private fun tableSpans(line: String): List<SourceRange> {
        val spans = mutableListOf<SourceRange>(); var start = 0; var slashes = 0
        line.forEachIndexed { i, c -> if (c == '|' && slashes % 2 == 0) { spans += SourceRange(start, i - start); start = i + 1 }; slashes = if (c == '\\') slashes + 1 else 0 }
        spans += SourceRange(start, line.length - start)
        val trimmed = spans.map { span -> var a = span.offset; var b = span.end; while (a < b && line[a] in " \t") a++; while (b > a && line[b - 1] in " \t") b--; SourceRange(a, b - a) }.toMutableList()
        if (trimmed.size > 1 && trimmed.first().length == 0) trimmed.removeAt(0)
        if (trimmed.size > 1 && trimmed.last().length == 0) trimmed.removeAt(trimmed.lastIndex)
        return trimmed
    }
    private fun tableCells(line: Line, references: Map<String, NativeMarkdownReference>): List<NativeMarkdownNode> {
        fun normalize(node: NativeMarkdownNode): NativeMarkdownNode = node.copy(children = node.children.map(::normalize), literalText = if (node.kind == Kind.INLINE_CODE) NativeMarkdownTextDecoder.codeSpan(node.source.replace("\\|", "|")) else node.literalText)
        return tableSpans(line.text).map { span -> val content = line.text.substring(span.offset, span.end); val offset = line.start + span.offset; NativeMarkdownNode(Kind.TABLE_CELL, content, SourceRange(offset, span.length), inline(content, offset, references).map(::normalize)) }
    }
    private fun listMarker(line: String, maxIndent: Int = 3): Marker? {
        val parts = match("^([ \\t]*)([-+*]|[0-9]{1,9}[.)])([ \\t]+|$)(.*)$", line) ?: return null
        val indent = indentation(parts[1]); if (indent > maxIndent) return null
        val marker = parts[2]; val spacing = parts[3]; var column = indent + marker.length
        for (c in spacing) column += if (c == '\t') 4 - column % 4 else 1
        val width = column - indent - marker.length; val overflow = width > 4
        val consumed = if (overflow || parts[4].isEmpty()) minOf(1, spacing.length) else spacing.length
        return Marker(indent, parts[1] + marker + spacing.take(consumed), marker.last() in ".)", marker.dropLast(1).toIntOrNull() ?: 1, marker.last(), if (overflow) width - 1 else 0)
    }
    private fun list(start: Int, lines: List<Line>, source: String, references: Map<String, NativeMarkdownReference>): Pair<NativeMarkdownNode, Int> {
        val first = listMarker(lines[start].text)!!; var siblingLimit = displayColumn(first.prefix); var index = start; var loose = false
        data class Item(val range: SourceRange, val checked: Boolean?, val blocks: List<NativeMarkdownNode>)
        val parsed = mutableListOf<Item>()
        while (index < lines.size) {
            val marker = listMarker(lines[index].text) ?: break
            if (isThematic(lines[index].text) || marker.indent >= siblingLimit || marker.ordered != first.ordered || marker.style != first.style) break
            val itemStart = index; val itemLine = lines[index++]; var body = itemLine.text.drop(marker.prefix.length)
            if (marker.overflowSpaces > 0) body = " ".repeat(marker.overflowSpaces) + body.dropWhile { it == ' ' || it == '\t' }
            val task = if (enableGFM) match("^\\[([ xX])\\][ \\t]+", body) else null
            val checked = task?.let { it[1].equals("x", true) }; if (task != null) body = body.drop(task[0].length)
            val prefixWidth = marker.prefix.length + (task?.get(0)?.length ?: 0)
            val contentIndent = maxOf(displayColumn(marker.prefix), marker.indent + (if (marker.ordered) marker.number.toString().length + 1 else 1) + 1)
            siblingLimit = contentIndent
            val contents = mutableListOf(Line(body, body + if (itemLine.raw.endsWith('\n')) "\n" else "", itemLine.start + prefixWidth, itemLine.end, true))
            var lastContent = itemStart; var activeFence = fenceOpen(body)
            while (index < lines.size) {
                val current = lines[index]; val next = listMarker(current.text)
                if (next != null && next.indent < contentIndent) break
                if (current.isBlank) {
                    var following = index + 1; while (following < lines.size && lines[following].isBlank) following++
                    val followingMarker = if (following < lines.size) listMarker(lines[following].text) else null
                    if (followingMarker != null && followingMarker.indent < contentIndent) { loose = true; index = following; break }
                    if (body.isBlank() && lastContent == itemStart) break
                    if (following >= lines.size || indentation(lines[following].text) < contentIndent) break
                    val previousMarker = listMarker(lines[lastContent].text, Int.MAX_VALUE)
                    val previousNested = previousMarker != null && previousMarker.indent >= contentIndent && indentation(lines[following].text) > previousMarker.indent
                    if (activeFence == null && !previousNested) loose = true
                    while (index < following) contents += project(lines[index++], 0)
                    continue
                }
                val indent = indentation(current.text)
                if (indent >= contentIndent) {
                    val projected = project(current, minOf(contentIndent, indent)); contents += projected
                    activeFence = if (activeFence != null) { if (fenceClose(projected.text, activeFence)) null else activeFence } else fenceOpen(projected.text)
                    lastContent = index++; continue
                }
                if (!interruptsParagraph(index, lines) && body.isNotEmpty()) { contents += project(current, minOf(indent, contentIndent)).copy(lazyContinuation = true); lastContent = index++; continue }
                break
            }
            val range = SourceRange(itemLine.start, lines[lastContent].end - itemLine.start)
            parsed += Item(range, checked, scan(contents, source, references))
            val next = if (index < lines.size) listMarker(lines[index].text) else null
            if (index >= lines.size || isThematic(lines[index].text) || (next?.indent ?: Int.MAX_VALUE) >= siblingLimit || next?.style != first.style) break
        }
        val items = parsed.map { item -> NativeMarkdownNode(Kind.LIST_ITEM, source.substring(item.range.offset, item.range.end), item.range, if (loose) item.blocks else item.blocks.flatMap { if (it.kind == Kind.PARAGRAPH) it.children else listOf(it) }, checked = item.checked) }
        val end = items.lastOrNull()?.sourceRange?.end ?: lines[start].end; val range = SourceRange(lines[start].start, end - lines[start].start)
        return NativeMarkdownNode(Kind.LIST, source.substring(range.offset, range.end), range, items, ordered = first.ordered, isTight = !loose, listStart = if (first.ordered) first.number else null) to index
    }
    private fun project(line: Line, width: Int): Line {
        var removed = 0; var cursor = 0
        while (cursor < line.text.length && removed < width && line.text[cursor] in " \t") { removed += if (line.text[cursor] == '\t') 4 - removed % 4 else 1; cursor++ }
        val leading = line.text.takeWhile { it in " \t" }; val residual = maxOf(0, indentation(line.text) - width)
        val body = " ".repeat(residual) + line.text.drop(leading.length); val adjustment = residual - (leading.length - cursor)
        return Line(body, body + if (line.raw.endsWith('\n')) "\n" else "", maxOf(line.start, line.start + cursor - adjustment), line.end, true)
    }
    private fun referenceDefinition(start: Int, lines: List<Line>): Pair<NativeMarkdownReferenceParser.Definition, Int>? {
        if (start !in lines.indices || !Regex("^ {0,3}\\[").containsMatchIn(lines[start].text)) return null
        var spelling = lines[start].text; var next = start + 1
        while (next < lines.size && !lines[next].isBlank) { spelling += "\n" + lines[next].text; next++ }
        val definition = NativeMarkdownReferenceParser.parse(spelling) ?: return null
        return definition to (start + definition.lineCount)
    }
    private fun match(pattern: String, source: String): List<String>? = Regex(pattern).find(source)?.groupValues
}
internal data class NativeCustomBlockMatch(val node: NativeMarkdownNode, val linesConsumed: Int)
