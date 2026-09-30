package com.jackcaow.smoothmarkdown.nativeparser

object NativeMarkdownEmphasisParser {
    private class Token(var node: NativeMarkdownNode? = null) { var previous: Token? = null; var next: Token? = null }
    private class Delimiter(val token: Token, val marker: Char, var count: Int, val opens: Boolean, val closes: Boolean) {
        val originalCount = count; var active = true
    }
    fun resolve(nodes: List<NativeMarkdownNode>, source: String, offset: Int): List<NativeMarkdownNode> {
        fun punctuation(c: Int?): Boolean = c != null && Character.getType(c) in setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(), Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(), Character.OTHER_SYMBOL.toInt())
        fun space(c: Int?): Boolean = c == null || Character.isWhitespace(c) || Character.isSpaceChar(c)
        val head = Token(); var tail = head
        val delimiters = mutableListOf<Delimiter>()
        for (node in nodes) {
            val token = Token(node); token.previous = tail; tail.next = token; tail = token
            val marker = node.source.firstOrNull()
            if (node.kind == NativeMarkdownNode.Kind.TEXT && (marker == '*' || marker == '_') && node.source.all { it == marker }) {
                val start = node.sourceRange.offset - offset; val end = node.sourceRange.end - offset
                val before = if (start > 0) source.codePointBefore(start) else null
                val after = if (end < source.length) source.codePointAt(end) else null
                val left = !space(after) && (!punctuation(after) || space(before) || punctuation(before))
                val right = !space(before) && (!punctuation(before) || space(after) || punctuation(after))
                val opens = if (marker == '_') left && (!right || punctuation(before)) else left
                val closes = if (marker == '_') right && (!left || punctuation(after)) else right
                delimiters.add(Delimiter(token, marker, node.source.length, opens, closes))
            }
        }
        fun remove(token: Token) { token.previous?.next = token.next; token.next?.previous = token.previous; token.previous = null; token.next = null }
        fun spelling(range: SourceRange) = source.substring(range.offset - offset, range.end - offset)
        for (closerIndex in delimiters.indices) {
            val closer = delimiters[closerIndex]
            if (!closer.active || !closer.closes) continue
            while (closer.count > 0) {
                val openerIndex = (closerIndex - 1 downTo 0).firstOrNull { index ->
                    val opener = delimiters[index]
                    opener.active && opener.opens && opener.count > 0 && opener.marker == closer.marker &&
                        !((opener.closes || closer.opens) && (opener.originalCount + closer.originalCount) % 3 == 0 &&
                        (opener.originalCount % 3 != 0 || closer.originalCount % 3 != 0))
                } ?: break
                val opener = delimiters[openerIndex]
                val openNode = opener.token.node ?: break; val closeNode = closer.token.node ?: break
                val used = if (opener.count >= 2 && closer.count >= 2) 2 else 1
                val lower = openNode.sourceRange.end - used; val upper = closeNode.sourceRange.offset + used
                val range = SourceRange(lower, upper - lower)
                val children = mutableListOf<NativeMarkdownNode>(); var cursor = opener.token.next
                while (cursor != null && cursor !== closer.token) { cursor.node?.let { children.add(it) }; cursor = cursor.next }
                val wrapper = Token(NativeMarkdownNode(if (used == 2) NativeMarkdownNode.Kind.STRONG else NativeMarkdownNode.Kind.EMPHASIS, spelling(range), range, children))
                opener.token.next = wrapper; wrapper.previous = opener.token; wrapper.next = closer.token; closer.token.previous = wrapper
                for (index in openerIndex + 1 until closerIndex) delimiters[index].active = false
                opener.count -= used; closer.count -= used
                if (opener.count == 0) { opener.active = false; remove(opener.token) }
                else { val remaining = SourceRange(openNode.sourceRange.offset, opener.count); opener.token.node = NativeMarkdownNode(NativeMarkdownNode.Kind.TEXT, spelling(remaining), remaining) }
                if (closer.count == 0) { closer.active = false; remove(closer.token) }
                else { val remaining = SourceRange(upper, closer.count); closer.token.node = NativeMarkdownNode(NativeMarkdownNode.Kind.TEXT, spelling(remaining), remaining) }
            }
        }
        val result = mutableListOf<NativeMarkdownNode>(); var cursor = head.next
        while (cursor != null) {
            val node = cursor.node
            if (node != null) {
                val previous = result.lastOrNull()
                if (node.kind == NativeMarkdownNode.Kind.TEXT && previous?.kind == NativeMarkdownNode.Kind.TEXT && previous.sourceRange.end == node.sourceRange.offset) {
                    result[result.lastIndex] = previous.copy(source = previous.source + node.source, sourceRange = SourceRange(previous.sourceRange.offset, previous.sourceRange.length + node.sourceRange.length))
                } else result.add(node)
            }
            cursor = cursor.next
        }
        return result
    }
}
