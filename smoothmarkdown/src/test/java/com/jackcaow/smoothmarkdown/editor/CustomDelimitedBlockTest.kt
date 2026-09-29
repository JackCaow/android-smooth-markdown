package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.DelimitedBlockNode
import com.jackcaow.smoothmarkdown.DelimitedBlockPlugin
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.SourceBlockParseResult
import com.jackcaow.smoothmarkdown.SourceBlockParserPlugin
import com.jackcaow.smoothmarkdown.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDelimitedBlockTest {
    @Test fun registeredFenceIsOneRawSourceBlockWithExactUtf16Offsets() {
        val registry = ParserPluginRegistry().also { it.register(DelimitedBlockPlugin("custom")) }
        val custom = ":::custom id=42\r\nRaw *stars*\r\n\r\n- list item\r\n:::"
        val source = "😀 Intro\r\n\r\n$custom\r\n\r\nOutro"
        val document = MarkdownDocumentCodec.parse(source, registry)

        assertEquals(listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.RAW, MarkdownBlockKind.PARAGRAPH),
            document.blocks.map { it.kind })
        assertEquals(custom, document.blocks[1].source)
        assertEquals(TextRange(source.indexOf(custom), source.indexOf(custom) + custom.length), document.blocks[1].range)
        assertEquals(source, document.toMarkdown())
        val parsed = parseMarkdown(custom, registry).firstChild as DelimitedBlockNode
        assertEquals("id=42", parsed.info)
        assertEquals("custom", parsed.type)
    }

    @Test fun editorReplacementKeepsUntouchedNeighborsAndOneUndoStep() {
        val registry = ParserPluginRegistry().also { it.register(DelimitedBlockPlugin("custom")) }
        val original = "Before\r\n\r\n:::custom id=42\r\nOld\r\n:::\r\n\r\nAfter"
        val controller = MarkdownEditorController(original, parserPlugins = registry)
        val block = controller.semanticDocument().blocks[1]
        val replacement = ":::custom id=43\nNew *text*\n:::"

        assertEquals(MarkdownBlockKind.RAW, block.kind)
        assertTrue(controller.replaceCustomBlockMarkdown(original, block, replacement))
        assertEquals("Before\r\n\r\n$replacement\r\n\r\nAfter", controller.text)
        assertTrue(controller.undo())
        assertEquals(original, controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun unmatchedTagAndNoRegistryUseCommonMark() {
        val registry = ParserPluginRegistry().also { it.register(DelimitedBlockPlugin("custom")) }
        val unrelated = ":::customize\nordinary text\n:::"
        assertFalse(parseMarkdown(unrelated, registry).firstChild is DelimitedBlockNode)
        assertEquals(MarkdownDocumentCodec.parse(unrelated).blocks.map { it.kind },
            MarkdownDocumentCodec.parse(unrelated, registry).blocks.map { it.kind })
    }

    @Test fun failedOrOutOfRangeSourceConsumptionFallsBackWithoutChangingSource() {
        val source = ":::custom\nbody\n:::\n\nTail"
        val ordinary = MarkdownDocumentCodec.parse(source)
        for (consumed in listOf(null, 0, 999)) {
            val registry = ParserPluginRegistry().also { it.registerSourceBlock(object : SourceBlockParserPlugin {
                override val id = "test-source"
                override val name = "Test source"
                override fun canParse(line: String, lines: List<String>, index: Int) = line == ":::custom"
                override fun parse(lines: List<String>, startIndex: Int) = consumed?.let(::SourceBlockParseResult)
            }) }
            val result = MarkdownDocumentCodec.parse(source, registry)
            assertEquals(ordinary.blocks, result.blocks)
            assertEquals(source, result.toMarkdown())
        }
    }

    @Test fun sourceParserCanGroupUnrecognizedSyntaxAcrossCommonMarkBlocks() {
        val source = "Before\n\n:::custom id=42\nbody\n\n- list item\n:::\n\nAfter"
        val registry = ParserPluginRegistry().also { it.registerSourceBlock(object : SourceBlockParserPlugin {
            override val id = "source-custom"
            override val name = "Source custom"
            override fun canParse(line: String, lines: List<String>, index: Int) = line.startsWith(":::custom ")
            override fun parse(lines: List<String>, startIndex: Int): SourceBlockParseResult? {
                val closing = (startIndex + 1 until lines.size).firstOrNull { lines[it] == ":::" } ?: return null
                return SourceBlockParseResult(closing - startIndex + 1)
            }
        }) }

        val blocks = MarkdownDocumentCodec.parse(source, registry).blocks
        assertEquals(listOf(MarkdownBlockKind.PARAGRAPH, MarkdownBlockKind.RAW, MarkdownBlockKind.PARAGRAPH),
            blocks.map { it.kind })
        assertEquals(":::custom id=42\nbody\n\n- list item\n:::" , blocks[1].source)
        assertEquals(source, MarkdownDocumentCodec.parse(source, registry.copy()).toMarkdown())
    }

    @Test fun sourcePluginCannotConsumePartOfAnExistingCommonMarkBlock() {
        val source = ":::custom\nbody\n:::"
        val registry = ParserPluginRegistry().also { it.registerSourceBlock(object : SourceBlockParserPlugin {
            override val id = "partial"
            override val name = "Partial"
            override fun canParse(line: String, lines: List<String>, index: Int) = line == ":::custom"
            override fun parse(lines: List<String>, startIndex: Int) = SourceBlockParseResult(1)
        }) }
        assertEquals(MarkdownDocumentCodec.parse(source).blocks, MarkdownDocumentCodec.parse(source, registry).blocks)
    }
}
