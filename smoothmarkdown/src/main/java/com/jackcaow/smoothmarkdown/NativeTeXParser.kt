package com.jackcaow.smoothmarkdown

/** Original TeX math AST. Unknown commands retain their spelling. */
internal sealed interface NativeTeXNode {
    data class Text(val value: String) : NativeTeXNode
    data class Row(val nodes: List<NativeTeXNode>) : NativeTeXNode
    data class Fraction(val top: NativeTeXNode, val bottom: NativeTeXNode, val ruled: Boolean = true) : NativeTeXNode
    data class Root(val value: NativeTeXNode, val degree: NativeTeXNode? = null) : NativeTeXNode
    data class Space(val mu: Int) : NativeTeXNode
    data class Accent(val mark: String, val value: NativeTeXNode) : NativeTeXNode
    data class Alphabet(val name: String, val value: NativeTeXNode) : NativeTeXNode
    data class LargeOperator(val value: String, val limits: Boolean) : NativeTeXNode
    data class Color(val color: String, val value: NativeTeXNode, val background: Boolean = false) : NativeTeXNode
    data class Underline(val value: NativeTeXNode) : NativeTeXNode
    data class Style(val name: String, val value: NativeTeXNode) : NativeTeXNode
    data class Delimited(val left: String, val right: String, val value: NativeTeXNode) : NativeTeXNode
    data class Table(val name: String, val columns: String, val rows: List<List<NativeTeXNode>>, val left: String = "", val right: String = "") : NativeTeXNode
    data class Script(val base: NativeTeXNode, val sub: NativeTeXNode?, val sup: NativeTeXNode?) : NativeTeXNode
}

internal object NativeTeXParser {
    fun parse(source: String): NativeTeXNode = NativeTeXNode.Row(Parser(source).row())
    private class Parser(val source: String) {
        var index = 0; var spacesAllowed = false
        val current get() = source.getOrNull(index)
        fun take(): Char? = source.getOrNull(index)?.also { index++ }
        fun consume(value: String): Boolean { if (!source.startsWith(value, index)) return false; index += value.length; return true }
        fun word(value: String): Boolean { if (!source.startsWith(value, index) || source.getOrNull(index + value.length)?.isLetter() == true) return false; index += value.length; return true }
        fun group(): NativeTeXNode = if (consume("{")) NativeTeXNode.Row(row('}')).also { consume("}") } else atom() ?: NativeTeXNode.Text("")
        fun row(until: Char? = null): List<NativeTeXNode> {
            val nodes = mutableListOf<NativeTeXNode>()
            while (current != null && current != until) {
                val c = current!!
                if (c == '}') break
                if (c.isWhitespace()) { take(); if (spacesAllowed) nodes.add(NativeTeXNode.Space(5)); continue }
                val style = listOf("displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle").firstOrNull { word("\\$it") }
                if (style != null) { nodes.add(NativeTeXNode.Style(style, NativeTeXNode.Row(row(until)))); return nodes }
                val infix = listOf("over", "atop", "choose", "brack", "brace").firstOrNull { word("\\$it") }
                if (infix != null) {
                    val fraction = NativeTeXNode.Fraction(NativeTeXNode.Row(nodes), NativeTeXNode.Row(row(until)), infix == "over")
                    return listOf(when(infix) { "choose" -> NativeTeXNode.Delimited("(", ")", fraction); "brack" -> NativeTeXNode.Delimited("[", "]", fraction); "brace" -> NativeTeXNode.Delimited("{", "}", fraction); else -> fraction })
                }
                var node = atom() ?: break
                if (node is NativeTeXNode.LargeOperator) {
                    if (word("\\limits")) node = node.copy(limits = true)
                    else if (word("\\nolimits")) node = node.copy(limits = false)
                }
                var sub: NativeTeXNode? = null; var sup: NativeTeXNode? = null
                while (current == '_' || current == '^') { val marker = take(); val value = group(); if (marker == '_') sub = value else sup = value }
                if (sub != null || sup != null) node = NativeTeXNode.Script(node, sub, sup)
                nodes.add(node)
            }
            return nodes
        }
        fun atom(): NativeTeXNode? {
            val c = take() ?: return null
            if (c == '{') return NativeTeXNode.Row(row('}')).also { consume("}") }
            if (c != '\\') {
                // Preserve supplementary Unicode glyphs as a single math atom.
                if (c.isHighSurrogate() && current?.isLowSurrogate() == true) return NativeTeXNode.Text("$c${take()}")
                return NativeTeXNode.Text(c.toString())
            }
            val next = current ?: return NativeTeXNode.Text("\\")
            if (!next.isLetter()) { take(); val command = next.toString(); return NativeTeXSymbols.spacingMu[command]?.let { NativeTeXNode.Space(it) } ?: NativeTeXNode.Text(NativeTeXSymbols.glyphs[command] ?: command) }
            val start = index; while (current?.isLetter() == true) take(); val name = source.substring(start, index)
            return when (name) {
                "frac", "dfrac", "tfrac", "cfrac", "binom" -> {
                    if (name == "cfrac" && consume("[") && current != null) { take(); consume("]") }
                    val fraction = NativeTeXNode.Fraction(group(), group(), name != "binom")
                    when(name) { "binom" -> NativeTeXNode.Delimited("(", ")", fraction); "tfrac" -> NativeTeXNode.Style("text", fraction); "dfrac", "cfrac" -> NativeTeXNode.Style("display", fraction); else -> fraction }
                }
                "sqrt" -> if (consume("[")) { val degreeStart = index; while (current != null && current != ']') take(); val degree = source.substring(degreeStart,index); consume("]"); NativeTeXNode.Root(group(), parse(degree)) } else NativeTeXNode.Root(group())
                "begin" -> environment(rawGroup())
                "left" -> {
                    val left = delimiter(); val bodyStart = index; var depth = 1; var result: NativeTeXNode? = null
                    while (current != null) {
                        if (word("\\left")) { depth++; continue }
                        if (word("\\right")) { if (--depth == 0) { val body = source.substring(bodyStart,index - 6); result = NativeTeXNode.Delimited(left, delimiter(), parse(body)); break }; continue }
                        take()
                    }
                    result ?: NativeTeXNode.Text("\\left$left${source.substring(bodyStart)}")
                }
                "right" -> NativeTeXNode.Text("\\right")
                "color", "textcolor", "colorbox" -> {
                    val color = rawGroup()
                    if (!validColor(color)) NativeTeXNode.Text("\\$name{$color}") else NativeTeXNode.Color(color, group(), name == "colorbox")
                }
                "underline" -> NativeTeXNode.Underline(group())
                "substack" -> NativeTeXNode.Table("substack", "c", rawGroup().split("\\\\").map { listOf(parse(it)) })
                "pmod" -> NativeTeXNode.Delimited("(", ")", NativeTeXNode.Row(listOf(NativeTeXNode.Text("mod"), NativeTeXNode.Space(6), group())))
                "not" -> if (consume("\\")) { val begin = index; while (current?.isLetter() == true) take(); val command = source.substring(begin,index); NativeTeXNode.Text(NativeTeXSymbols.negated[command] ?: "\\not\\$command") } else if (consume("=")) NativeTeXNode.Text("≠") else NativeTeXNode.Text("\\not")
                in accents.keys -> NativeTeXNode.Accent(accents.getValue(name), group())
                in alphabets -> {
                    val alias = aliases[name] ?: name
                    if (name == "text" || name == "operatorname") { val previous = spacesAllowed; spacesAllowed = true; val value = group(); spacesAllowed = previous; NativeTeXNode.Alphabet(aliases[name] ?: "mathrm", value) }
                    else NativeTeXNode.Alphabet(alias, group())
                }
                "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle" -> NativeTeXNode.Style(name, group())
                else -> {
                    val resolved = NativeTeXSymbols.aliases[name] ?: name
                    NativeTeXSymbols.spacingMu[resolved]?.let { NativeTeXNode.Space(it) }
                        ?: NativeTeXSymbols.operatorLimits[resolved]?.let { NativeTeXNode.LargeOperator(NativeTeXSymbols.glyphs[resolved] ?: resolved, it) }
                        ?: NativeTeXNode.Text(NativeTeXSymbols.glyphs[resolved] ?: "\\$name")
                }
            }
        }
        fun environment(name: String): NativeTeXNode {
            if (name !in matrices && name !in environments) return NativeTeXNode.Text("\\begin{$name}")
            val columns = when(name) { "array" -> rawGroup(); "aligned", "align", "split", "eqalign" -> "rl"; "eqnarray" -> "rcl"; "cases" -> "ll"; else -> "c" }
            val alignment = if (name.endsWith('*') && consume("[")) (take() ?: 'c').toString().also { consume("]") } else ""
            val opening = "\\begin{$name}"; val closing = "\\end{$name}"; var nesting = 1; val body = StringBuilder()
            while (current != null && nesting > 0) {
                if (consume(opening)) { nesting++; body.append(opening); continue }
                if (consume(closing)) { nesting--; if (nesting > 0) body.append(closing); continue }
                body.append(take())
            }
            val rows = splitTopLevel(body.toString(), true).map { splitTopLevel(it, false).map(::parse) }
            val base = name.removeSuffix("*")
            val (left, right) = when(base) { "pmatrix" -> "(" to ")"; "bmatrix" -> "[" to "]"; "Bmatrix" -> "{" to "}"; "vmatrix" -> "|" to "|"; "Vmatrix" -> "‖" to "‖"; else -> "" to "" }
            val table = NativeTeXNode.Table(name, if (alignment.isNotEmpty()) alignment.repeat(rows.maxOfOrNull { it.size } ?: 1) else columns, rows, left, right)
            return if (name == "smallmatrix") NativeTeXNode.Style("scriptstyle", table) else table
        }
        fun delimiter(): String {
            val next = current ?: return ""
            if (next != '\\') { take(); return if (next == '.') "" else next.toString() }
            take(); val start = index; while (current?.isLetter() == true) take()
            // Escaped braces are control symbols, not control words.
            if (index == start && current != null) return take().toString()
            val command = source.substring(start,index)
            return delimiters[command] ?: "\\$command"
        }
        fun rawGroup(): String {
            if (!consume("{")) return ""
            val value = StringBuilder(); var depth = 1
            while (current != null) { val c = take()!!; if (c == '{') depth++; if (c == '}') depth--; if (depth == 0) break; value.append(c) }
            return value.toString()
        }
    }
    internal fun splitTopLevel(source: String, rows: Boolean): List<String> {
        val pieces = mutableListOf<String>(); val part = StringBuilder(); var braces = 0; var environments = 0; var index = 0
        while (index < source.length) {
            if (source.startsWith("\\begin{",index)) environments++
            if (source.startsWith("\\end{",index)) environments = maxOf(0,environments - 1)
            if (source[index] == '\\' && index + 1 < source.length) {
                if (braces == 0 && environments == 0 && rows) {
                    if (source[index + 1] == '\\') { pieces.add(part.toString()); part.clear(); index += 2; continue }
                    if (source.startsWith("\\cr",index) && source.getOrNull(index + 3)?.isLetter() != true) { pieces.add(part.toString()); part.clear(); index += 3; continue }
                }
                if (source[index + 1] == '\\' || !source[index + 1].isLetter()) { part.append(source[index]).append(source[index + 1]); index += 2; continue }
            }
            if (braces == 0 && environments == 0 && !rows && source[index] == '&') { pieces.add(part.toString()); part.clear(); index++; continue }
            if (source[index] == '{') braces++
            if (source[index] == '}') braces = maxOf(0,braces - 1)
            part.append(source[index++])
        }
        pieces.add(part.toString()); return pieces
    }
    private fun validColor(value: String) = Regex("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|[A-Za-z]{1,24}").matches(value)
    private val matrices = setOf("matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix", "smallmatrix", "matrix*", "pmatrix*", "bmatrix*", "Bmatrix*", "vmatrix*", "Vmatrix*")
    private val environments = setOf("cases", "array", "aligned", "align", "split", "eqalign", "eqnarray", "gather", "displaylines")
    private val accents = mapOf("grave" to "`", "acute" to "´", "hat" to "ˆ", "widehat" to "ˆ", "tilde" to "˜", "widetilde" to "˜", "bar" to "¯", "breve" to "˘", "dot" to "˙", "ddot" to "¨", "check" to "ˇ", "vec" to "→", "overline" to "¯")
    private val alphabets = setOf("mathnormal", "mathrm", "textrm", "rm", "mathbf", "bf", "textbf", "mathcal", "cal", "mathtt", "texttt", "mathit", "textit", "mit", "mathsf", "textsf", "mathfrak", "frak", "mathbb", "mathbfit", "bm", "text", "operatorname")
    private val aliases = mapOf("textrm" to "mathrm", "rm" to "mathrm", "bf" to "mathbf", "textbf" to "mathbf", "cal" to "mathcal", "texttt" to "mathtt", "textit" to "mathit", "mit" to "mathit", "textsf" to "mathsf", "frak" to "mathfrak", "mathbfit" to "bm", "text" to "mathrm")
    private val delimiters = mapOf("langle" to "⟨", "rangle" to "⟩", "lbrace" to "{", "rbrace" to "}", "lvert" to "|", "rvert" to "|", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉", "Vert" to "‖", "vert" to "|")
}
