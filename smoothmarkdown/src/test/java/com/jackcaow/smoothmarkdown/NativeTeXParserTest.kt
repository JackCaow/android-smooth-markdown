package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTeXParserTest {
    private fun markup(source: String, display: Boolean = true) = NativeTeXMathML.markup(NativeTeXParser.parse(source), display)
    @Test fun nestedFractionsRootsAndScriptsStayStructured() {
        val math=markup("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
        assertTrue(math.contains("<mfrac>"));assertTrue(math.contains("<msqrt>"));assertTrue(math.contains("<msup>"));assertTrue(math.contains("±"))
        assertTrue(markup("\\sqrt[3]{x}").contains("<mroot>"))
    }
    @Test fun everySymbolAndNegationIsAvailable() {
        assertEquals(233,NativeTeXSymbols.glyphs.size)
        for((name,glyph) in NativeTeXSymbols.glyphs) {
            if(!name.all { it.isLetter() })continue
            val node=(NativeTeXParser.parse("\\$name") as NativeTeXNode.Row).nodes.first()
            val limits=NativeTeXSymbols.operatorLimits[name]
            assertEquals(name,if(limits==null) NativeTeXNode.Text(glyph) else NativeTeXNode.LargeOperator(glyph,limits),node)
        }
        for((name,glyph) in NativeTeXSymbols.negated) assertEquals(NativeTeXNode.Row(listOf(NativeTeXNode.Text(glyph))), NativeTeXParser.parse(if(name=="=")"\\not=" else "\\not\\$name"))
    }
    @Test fun everyMatrixAndAlignmentEnvironmentSupportsRows() {
        val names=listOf("matrix","pmatrix","bmatrix","Bmatrix","vmatrix","Vmatrix","smallmatrix","matrix*","pmatrix*","bmatrix*","Bmatrix*","vmatrix*","Vmatrix*","cases","array","aligned","align","split","eqalign","eqnarray","gather","displaylines")
        for(name in names) {
            val argument=if(name=="array")"{rl}" else if(name.endsWith('*'))"[r]" else ""
            val math=markup("\\begin{$name}${argument}a&b\\\\c&d\\end{$name}")
            assertTrue(name,math.contains("<mtable"));assertEquals(name,2,Regex("<mtr>").findAll(math).count());assertEquals(name,4,Regex("<mtd>").findAll(math).count())
        }
        assertTrue(markup("\\begin{cases}x&x>0\\\\-x&x<0\\end{cases}").contains("minsize=\"2.5em\""))
        assertTrue(markup("\\begin{array}{rl}x&=y\\end{array}").contains("columnalign=\"right left\""))
    }
    @Test fun nestedMatricesDoNotSplitOuterCell() {
        val row=NativeTeXParser.parse("\\begin{pmatrix}\\begin{matrix}a&b\\\\c&d\\end{matrix}&x\\\\y&z\\end{pmatrix}") as NativeTeXNode.Row
        val outer=row.nodes.single() as NativeTeXNode.Table
        assertEquals(listOf(2,2),outer.rows.map { it.size })
        val inner=(outer.rows[0][0] as NativeTeXNode.Row).nodes.single() as NativeTeXNode.Table
        assertEquals(listOf(2,2),inner.rows.map { it.size })
    }
    @Test fun limitsRespectDisplayModeAndOverrides() {
        assertTrue(markup("\\sum_{i=1}^{n}").contains("<munderover>"))
        assertTrue(markup("\\sum_{i=1}^{n}",false).contains("<msubsup>"))
        assertTrue(markup("\\sum\\nolimits_{i=1}^{n}").contains("<msubsup>"))
        assertTrue(markup("\\int\\limits_0^1").contains("<munderover>"))
        assertTrue(markup("\\lim_{x\\to0}").contains("largeop=\"false\""))
    }
    @Test fun spacingAndOrdinaryParenthesesDoNotStretch() {
        assertTrue(markup("f(x)+F(b)").contains("<mo stretchy=\"false\">(</mo>"))
        assertTrue(markup("\\left(\\frac12\\right)").contains("<mo stretchy=\"true\">(</mo>"))
        assertTrue(markup("a\\!b").contains("margin-left:-0.16666666666666666em"))
        assertTrue(markup("a\\,b").contains("width=\"0.16666666666666666em\""))
        assertFalse(markup("a b").contains("mspace"));assertTrue(markup("\\text{a b}").contains("mspace"))
    }
    @Test fun scopedStylesAccentsAlphabetsAndColorEscape() {
        for(name in listOf("mathrm","mathbf","mathbb","mathfrak","mathcal","mathtt","mathit","bm")) assertTrue(markup("\\$name{x}").contains("mathvariant="))
        for(name in listOf("hat","widehat","tilde","widetilde","vec","dot","ddot","overline")) assertTrue(markup("\\$name{x}").contains("<mover accent="))
        assertTrue(markup("\\underline{x}").contains("<munder accentunder="))
        assertTrue(markup("\\textcolor{#cc0000}{a}+\\colorbox{#00ff00}{b}").contains("mathbackground=\"#00ff00\""))
        assertTrue(markup("\\scriptstyle{x}").contains("scriptlevel=\"1\""))
        val html=NativeTeXMathML.html("a<b + \\unsupported{x}",display=false,color="\" onload=\"bad")
        assertTrue(html.contains("&lt;"));assertTrue(html.contains("\\unsupported"));assertTrue(html.contains("&quot;"));assertFalse(html.contains("<script"));assertFalse(html.contains("https://"))
    }
    @Test fun negativeSpaceNarrowsMetricsAndDisplayLimitsGrowHeight() {
        fun extent(source:String,display:Boolean=true)=NativeTeXMetrics.measure(NativeTeXParser.parse(source),20f,display){text,size->text.codePointCount(0,text.length)*size*.6f}
        assertTrue(extent("a\\!b").width<extent("ab").width)
        assertTrue(extent("\\sum_{i=1}^{n}").height>extent("\\sum_{i=1}^{n}",false).height)
        assertTrue(extent("\\begin{cases}a&b\\\\c&d\\end{cases}").height>extent("x").height)
    }
}
