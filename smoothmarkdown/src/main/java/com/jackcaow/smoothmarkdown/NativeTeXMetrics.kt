package com.jackcaow.smoothmarkdown

internal data class NativeMathExtent(val width: Float, val height: Float)

/** Conservative native pre-layout; WebView's own contentHeight refines height after layout. */
internal object NativeTeXMetrics {
    fun preferredExtent(node: NativeTeXNode, size: Float, display: Boolean, textWidth: (String, Float) -> Float): NativeMathExtent {
        val extent = measure(node, size, display, textWidth)
        // System MathML's display fractions keep full-size denominator scripts;
        // Android Paint may also select a narrower fallback for Greek math glyphs.
        // Give display blocks room for those differences without widening inline runs.
        val width = if (display) extent.width * 1.2f + size * .8f else extent.width * 1.05f + size * .35f
        return NativeMathExtent(width.coerceIn(1f, 16000f),
            (extent.height + size * .1f).coerceAtLeast(size * 1.25f))
    }

    fun measure(node: NativeTeXNode, size: Float, display: Boolean, textWidth: (String, Float) -> Float): NativeMathExtent {
        fun child(value: NativeTeXNode, scale: Float = 1f, mode: Boolean = display) = measure(value, size * scale, mode, textWidth)
        return when(node) {
            is NativeTeXNode.Text -> NativeMathExtent(textWidth(node.value,size) + if(node.value.any { it in "+-=<>" }) size * .3f else 0f, size * 1.2f)
            is NativeTeXNode.Row -> { val children=node.nodes.map { child(it) }; NativeMathExtent(children.sumOf { it.width.toDouble() }.toFloat(), children.maxOfOrNull { it.height } ?: size * 1.2f) }
            is NativeTeXNode.Space -> NativeMathExtent(size * node.mu / 18f, size * 1.2f)
            is NativeTeXNode.Fraction -> { val scale=if(display).9f else .71f;val a=child(node.top,scale,false);val b=child(node.bottom,scale,false);NativeMathExtent(maxOf(a.width,b.width)+size*.3f,a.height+b.height+size*.2f) }
            is NativeTeXNode.Root -> { val value=child(node.value);val degree=node.degree?.let { child(it,.6f) };NativeMathExtent(value.width+size*.8f+(degree?.width?:0f),value.height+size*.25f) }
            is NativeTeXNode.Accent -> child(node.value).let { NativeMathExtent(it.width+size*.1f,it.height+size*.5f) }
            is NativeTeXNode.Underline -> child(node.value).let { NativeMathExtent(it.width,it.height+size*.2f) }
            is NativeTeXNode.Alphabet -> child(node.value).let { NativeMathExtent(it.width*1.12f,it.height) }
            is NativeTeXNode.Color -> child(node.value)
            is NativeTeXNode.Style -> child(node.value, when(node.name) { "scriptstyle" -> .72f; "scriptscriptstyle" -> .52f; else -> 1f }, when(node.name) { "displaystyle","display" -> true; "textstyle","text","scriptstyle","scriptscriptstyle" -> false; else -> display })
            is NativeTeXNode.LargeOperator -> { val scale=if(display&&node.value.any { !it.isLetter() })1.5f else 1f;NativeMathExtent(textWidth(node.value,size*scale)+size*.3f,size*scale*1.4f) }
            is NativeTeXNode.Delimited -> child(node.value).let { NativeMathExtent(it.width+size*.9f,it.height) }
            is NativeTeXNode.Script -> {
                val base=child(node.base);val sub=node.sub?.let { child(it,.72f,false) };val sup=node.sup?.let { child(it,.72f,false) }
                if(node.base is NativeTeXNode.LargeOperator && node.base.limits && display) NativeMathExtent(maxOf(base.width,sub?.width?:0f,sup?.width?:0f),base.height+(sub?.height?:0f)+(sup?.height?:0f))
                else NativeMathExtent(base.width+maxOf(sub?.width?:0f,sup?.width?:0f),base.height+(if(sub!=null)sub.height*.35f else 0f)+(if(sup!=null)sup.height*.35f else 0f))
            }
            is NativeTeXNode.Table -> {
                val rows=node.rows.map { row->row.map { child(it) } };val count=rows.maxOfOrNull { it.size } ?: 0
                val widths=(0 until count).map { col->rows.maxOfOrNull { it.getOrNull(col)?.width?:0f } ?:0f }
                val fenceCount = if(node.name=="cases") 1 else (if(node.left.isNotEmpty()) 1 else 0) + (if(node.right.isNotEmpty()) 1 else 0)
                val fenceWidth = size * maxOf(1f,node.rows.size * 1.25f) * .4f * fenceCount
                val width=widths.sum()+size*maxOf(0,count-1)+fenceWidth
                val cellsHeight = rows.sumOf { row->(row.maxOfOrNull { it.height }?:size*1.2f).toDouble() }.toFloat()+size*.3f*maxOf(0,rows.size-1)
                val fenceHeight = if(fenceCount > 0) size * maxOf(1f,node.rows.size*1.25f)*1.4f + size else 0f
                NativeMathExtent(width,maxOf(cellsHeight,fenceHeight))
            }
        }
    }
}
