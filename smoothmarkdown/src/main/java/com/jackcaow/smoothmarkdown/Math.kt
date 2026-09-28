package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.ratex.RaTeXRenderer
import io.ratex.RaTeXEngine
import io.ratex.RaTeXFontLoader
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.SourceLine
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

internal class InlineMathNode(val latex: String) : CustomNode()
internal class BlockMathNode(var latex: String = "") : CustomBlock()

internal class MathInlineParserFactory : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = setOf('$')
    override fun create(): InlineContentParser = InlineContentParser { state: InlineParserState ->
        val scanner = state.scanner()
        val start = scanner.position()
        if (scanner.peekPreviousCodePoint() == '$'.code) return@InlineContentParser ParsedInline.none()
        scanner.next()
        if (scanner.peek() == '$') {
            scanner.setPosition(start)
            return@InlineContentParser ParsedInline.none()
        }
        val latex = StringBuilder()
        var escaped = false
        while (scanner.hasNext()) {
            val char = scanner.peek()
            scanner.next()
            if (char == '$' && !escaped) {
                if (latex.isEmpty()) break
                return@InlineContentParser ParsedInline.of(InlineMathNode(latex.toString()), scanner.position())
            }
            latex.append(char)
            escaped = char == '\\' && !escaped
            if (char != '\\') escaped = false
        }
        scanner.setPosition(start)
        ParsedInline.none()
    }
}

internal class MathBlockParserFactory : BlockParserFactory {
    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        val line = state.line.content.toString()
        val trimmed = line.trim()
        if (!trimmed.startsWith("$$")) return null
        val rest = trimmed.drop(2)
        val singleLine = rest.endsWith("$$")
        val parser = MathBlockParser(if (singleLine) rest.dropLast(2).trim() else rest, singleLine)
        return BlockStart.of(parser).atIndex(line.length)
    }
}

private class MathBlockParser(firstContent: String, initiallyClosed: Boolean) : AbstractBlockParser() {
    private val node = BlockMathNode()
    private val lines = mutableListOf<String>()
    private var firstLine = true
    private var closed = initiallyClosed

    init {
        if (firstContent.isNotEmpty()) lines += firstContent
    }

    override fun getBlock(): BlockMathNode = node

    override fun tryContinue(state: ParserState): BlockContinue? {
        if (closed) return null
        val line = state.line.content.toString()
        if (line.trim().startsWith("$$")) {
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
        node.latex = lines.joinToString("\n").trim()
    }
}

/** Native Android Canvas TeX renderer. Invalid or unsupported TeX keeps its source visible. */
@Composable
internal fun rememberMathRenderer(latex: String, displayMode: Boolean): RaTeXRenderer? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSizePx = with(density) { (if (displayMode) 22.dp else 18.dp).toPx() }
    return remember(latex, displayMode, color, fontSizePx) {
        runCatching {
            RaTeXFontLoader.ensureLoaded(context)
            val displayList = RaTeXEngine.parseBlocking(latex, displayMode, color)
            RaTeXRenderer(displayList, fontSizePx, RaTeXFontLoader::getTypeface)
        }.getOrNull()
    }
}

@Composable
internal fun NativeMath(latex: String, displayMode: Boolean, modifier: Modifier = Modifier) {
    if (latex.isEmpty()) return
    val density = LocalDensity.current
    val renderer = rememberMathRenderer(latex, displayMode)
    if (renderer == null) {
        Text(if (displayMode) "$$$latex$$" else "$$latex$", modifier = modifier)
        return
    }
    val width = with(density) { renderer.widthPx.coerceAtLeast(1f).toDp() }
    val height = with(density) { renderer.totalHeightPx.coerceAtLeast(1f).toDp() }
    Canvas(modifier.size(width, height)) { renderer.draw(drawContext.canvas.nativeCanvas) }
}

@Composable
internal fun BlockMath(node: BlockMathNode) {
    if (node.latex.isEmpty()) return
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        NativeMath(node.latex, displayMode = true)
    }
}
