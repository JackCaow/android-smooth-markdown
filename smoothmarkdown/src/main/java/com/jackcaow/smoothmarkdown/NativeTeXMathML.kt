package com.jackcaow.smoothmarkdown

/** Offline standards-based MathML, with no executable input or remote resources. */
internal object NativeTeXMathML {
    fun html(latex: String, size: Float = 20f, display: Boolean, color: String = "#111111"): String =
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1"><style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}body{display:flex;align-items:center;min-height:100vh}#formula{display:inline-block;color:${escape(color)};font-family:serif;font-size:${size.coerceIn(1f,200f)}px;line-height:normal;white-space:nowrap}math{margin:0}</style></head><body><div id="formula"><math xmlns="http://www.w3.org/1998/Math/MathML" display="${if(display) "block" else "inline"}">${markup(NativeTeXParser.parse(latex),display)}</math></div></body></html>"""
    fun markup(node: NativeTeXNode, display: Boolean): String = when(node) {
        is NativeTeXNode.Text -> {
            val value = escape(node.value)
            val codePoints = node.value.codePoints().toArray()
            when {
                codePoints.size == 1 && Character.isDigit(codePoints[0]) -> "<mn>$value</mn>"
                codePoints.size == 1 && (Character.isLetter(codePoints[0]) || node.value in identifiers) -> "<mi>$value</mi>"
                node.value.isBlank() -> "<mspace width=\"0.3em\"/>"
                else -> "<mo stretchy=\"false\">$value</mo>"
            }
        }
        is NativeTeXNode.Row -> "<mrow>${node.nodes.joinToString("") { markup(it,display) }}</mrow>"
        is NativeTeXNode.Fraction -> "<mfrac${if(node.ruled) "" else " linethickness=\"0\""}>${markup(node.top,display)}${markup(node.bottom,display)}</mfrac>"
        is NativeTeXNode.Root -> if(node.degree == null) "<msqrt>${markup(node.value,display)}</msqrt>" else "<mroot>${markup(node.value,display)}${markup(node.degree,false)}</mroot>"
        is NativeTeXNode.Space -> if(node.mu < 0) "<mspace width=\"0em\" style=\"margin-left:${node.mu/18.0}em\"/>" else "<mspace width=\"${node.mu/18.0}em\"/>"
        is NativeTeXNode.Accent -> "<mover accent=\"true\">${markup(node.value,display)}<mo stretchy=\"true\">${escape(node.mark)}</mo></mover>"
        is NativeTeXNode.Alphabet -> "<mstyle mathvariant=\"${variants[node.name] ?: "normal"}\">${markup(node.value,display)}</mstyle>"
        is NativeTeXNode.LargeOperator -> "<mo largeop=\"${!node.value.codePoints().allMatch { Character.isLetter(it) }}\" movablelimits=\"${node.limits}\">${escape(node.value)}</mo>"
        is NativeTeXNode.Color -> "<mstyle ${if(node.background) "mathbackground" else "mathcolor"}=\"${escape(node.color)}\">${markup(node.value,display)}</mstyle>"
        is NativeTeXNode.Underline -> "<munder accentunder=\"true\">${markup(node.value,display)}<mo>¯</mo></munder>"
        is NativeTeXNode.Style -> {
            val mode = when(node.name) { "displaystyle", "display" -> true; "textstyle", "text", "scriptstyle", "scriptscriptstyle" -> false; else -> display }
            val level = when(node.name) { "scriptscriptstyle" -> 2; "scriptstyle" -> 1; else -> 0 }
            "<mstyle displaystyle=\"$mode\" scriptlevel=\"$level\">${markup(node.value,mode)}</mstyle>"
        }
        is NativeTeXNode.Delimited -> "<mrow><mo stretchy=\"true\">${escape(node.left)}</mo>${markup(node.value,display)}<mo stretchy=\"true\">${escape(node.right)}</mo></mrow>"
        is NativeTeXNode.Table -> {
            val table = table(node.rows,node.columns,display)
            when {
                node.name == "cases" -> "<mrow>${tableFence("{", node.rows.size)}$table</mrow>"
                node.left.isNotEmpty() || node.right.isNotEmpty() -> "<mrow>${tableFence(node.left,node.rows.size)}$table${tableFence(node.right,node.rows.size)}</mrow>"
                else -> "<mrow>$table</mrow>"
            }
        }
        is NativeTeXNode.Script -> {
            val limits = node.base is NativeTeXNode.LargeOperator && node.base.limits && display
            val tag = when { node.sub != null && node.sup != null -> if(limits) "munderover" else "msubsup"; node.sub != null -> if(limits) "munder" else "msub"; node.sup != null -> if(limits) "mover" else "msup"; else -> "mrow" }
            "<$tag>${markup(node.base,display)}${node.sub?.let { markup(it,false) } ?: ""}${node.sup?.let { markup(it,false) } ?: ""}</$tag>"
        }
    }
    // Android's built-in serif often lacks an OpenType MATH stretch assembly.
    // Explicit CSS size makes table fences span every row even with that system font.
    private fun tableFence(value: String, rows: Int): String {
        val size = maxOf(1.0, rows * 1.25)
        return "<mo fence=\"true\" stretchy=\"true\" symmetric=\"true\" lspace=\"0em\" rspace=\"0em\" style=\"font-size:${size}em;position:relative;top:0.35em\" minsize=\"${size}em\">${escape(value)}</mo>"
    }
    private fun table(rows: List<List<NativeTeXNode>>, columns: String, display: Boolean): String {
        val alignment = columns.mapNotNull { when(it) { 'l' -> "left"; 'c' -> "center"; 'r' -> "right"; else -> null } }.joinToString(" ")
        val attr = if(alignment.isEmpty()) "" else " columnalign=\"$alignment\""
        return "<mtable$attr>${rows.joinToString("") { row -> "<mtr>${row.joinToString("") { "<mtd>${markup(it,display)}</mtd>" }}</mtr>" }}</mtable>"
    }
    internal fun escape(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private val identifiers = setOf("∂", "𝜕", "∇", "ℏ", "∞", "∅", "ℵ", "ℑ", "ℜ", "℘", "ı", "ȷ")
    private val variants = mapOf("mathrm" to "normal", "mathit" to "italic", "mathnormal" to "italic", "mathbf" to "bold", "mathbb" to "double-struck", "mathfrak" to "fraktur", "mathcal" to "script", "mathsf" to "sans-serif", "mathtt" to "monospace", "bm" to "bold-italic")
}
