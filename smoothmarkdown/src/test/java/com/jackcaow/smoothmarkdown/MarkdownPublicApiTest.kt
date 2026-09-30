package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Text
import org.junit.Assert.*
import org.junit.Test

class MarkdownPublicApiTest {
    @Test fun explicitTokensOverrideAllLegacyStyleSourcesAndResolutionIsIdempotent() {
        val sheet = MarkdownStyleSheet(
            paragraphStyle = TextStyle(fontSize = 12.sp), quoteBackground = Color.Red,
            blockquoteDecoration = MarkdownBlockquoteDecoration(backgroundColor = Color.Blue, borderWidth = 7.dp),
            codeBackground = Color.Red, codeBlockDecoration = MarkdownCodeBlockDecoration(backgroundColor = Color.Blue),
            designTokens = MarkdownDesignTokens(
                typography = MarkdownTypographyTokens(paragraph = TextStyle(fontSize = 21.sp)),
                quote = MarkdownQuoteTokens(backgroundColor = Color.Green),
                code = MarkdownCodeTokens(backgroundColor = Color.Yellow),
            ),
        ).resolved()
        assertEquals(21.sp, sheet.paragraphStyle?.fontSize)
        assertEquals(Color.Green, sheet.blockquoteDecoration?.backgroundColor)
        assertEquals(7.dp, sheet.blockquoteDecoration?.borderWidth)
        assertEquals(Color.Yellow, sheet.codeBlockDecoration?.backgroundColor)
        assertEquals(sheet, sheet.resolved())
    }

    @Test fun documentTokensWinOverLegacyDecorationAndRemovingOverrideRestoresOriginal() {
        val legacy = MarkdownStyleSheet(quoteBackground = Color.Red,
            blockquoteDecoration = MarkdownBlockquoteDecoration(backgroundColor = Color.Blue),
            textColor = Color.Black,
        )
        val overridden = legacy.copy(designTokens = legacy.designTokens.copy(document = MarkdownDocumentTokens(quoteBackground = Color.Green, textColor = Color.Yellow)))
        assertEquals(Color.Green, overridden.resolved().blockquoteDecoration?.backgroundColor)
        assertEquals(Color.Yellow, overridden.resolved().textColor)
        val restored = overridden.copy(designTokens = overridden.designTokens.copy(document = MarkdownDocumentTokens()))
        assertEquals(Color.Blue, restored.resolved().blockquoteDecoration?.backgroundColor)
        assertEquals(Color.Black, restored.resolved().textColor)
        assertEquals(Color.Blue, legacy.blockquoteDecoration?.backgroundColor)
    }

    @Test fun semanticTokensOverrideLegacyInlineStylesAndTableEdges() {
        val sheet = MarkdownStyleSheet(linkStyle = androidx.compose.ui.text.SpanStyle(color = Color.Red),
            inlineCodeStyle = androidx.compose.ui.text.SpanStyle(color = Color.Red, background = Color.Blue),
            highlightStyle = androidx.compose.ui.text.SpanStyle(background = Color.Blue),
            tableBorder = MarkdownTableBorder.all(Color.Red, 3.dp),
            designTokens = MarkdownDesignTokens(document = MarkdownDocumentTokens(linkColor = Color.Green,
                inlineCodeTextColor = Color.Yellow, inlineCodeBackground = Color.Green,
                highlightColor = Color.Yellow, tableBorderColor = Color.Green)),
        ).resolved()
        assertEquals(Color.Green, sheet.linkStyle?.color)
        assertEquals(Color.Yellow, sheet.inlineCodeStyle?.color)
        assertEquals(Color.Green, sheet.inlineCodeStyle?.background)
        assertEquals(Color.Yellow, sheet.highlightStyle?.background)
        assertEquals(Color.Green, sheet.tableBorder?.top?.color)
        assertEquals(3.dp, sheet.tableBorder?.top?.width)
    }

    @Test fun invalidValuesAndMutatedLegacyHeadingCollectionsNormalizeSafely() {
        assertEquals(0.dp, MarkdownStyleSheet(blockSpacing = Float.POSITIVE_INFINITY.dp).resolved().blockSpacing)
        assertEquals(0.dp, MarkdownStyleSheet(codeBlockDecoration = MarkdownCodeBlockDecoration(cornerRadius = Float.NaN.dp)).resolved().codeBlockDecoration?.cornerRadius)
        val sheet = MarkdownStyleSheet(designTokens = MarkdownDesignTokens(typography = MarkdownTypographyTokens(paragraph = TextStyle(fontSize = (-1).sp)))).resolved()
        assertEquals(androidx.compose.ui.unit.TextUnit.Unspecified, sheet.paragraphStyle?.fontSize)
        val styles = MutableList(6) { TextStyle.Default }
        val legacy = MarkdownStyleSheet(headingStyles = styles)
        styles.removeAt(0)
        assertEquals(6, legacy.resolved().headingStyles?.size)
    }

    @Test fun selectionHasOneOwnerAndInvalidCombinationsAreRejected() {
        assertEquals(MarkdownSelectionMode.DISABLED, MarkdownSelectionOptions().mode)
        val controller = SmoothSelectionController()
        assertNull(MarkdownSelectionOptions(controller = controller).resolved().controller)
        assertNull(MarkdownSelectionOptions(mode = MarkdownSelectionMode.OUTER_CONTAINER, controller = controller).resolved().controller)
        assertSame(controller, MarkdownSelectionOptions(mode = MarkdownSelectionMode.DOCUMENT, controller = controller).resolved().controller)
        assertEquals(-1L, MarkdownStreamOptions(throttleMillis = -1).throttleMillis)
    }

    @Test fun textOnlyBuilderUpdatesInlineTextWithoutReplacingItsParagraph() {
        val paragraph = parseMarkdown("original").firstChild!!
        val builder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Text
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
            override fun renderInline(node: Node) = MarkdownInlinePresentation.Text("updated")
        }
        val registry = MarkdownBuilderRegistry().register(Text::class, builder)
        assertNull(registry.findBuilder(paragraph))
        assertSame(builder, registry.findBuilder(paragraph.firstChild!!))
        assertEquals("updated", inlineRender(paragraph, false, builders = registry).text.text)
        registry.clear()
        assertEquals("original", inlineRender(paragraph, false, builders = registry).text.text)
    }

    @Test fun registryVersionIsSnapshotObservableAndNoOpMutationsDoNotInvalidate() {
        val plugins = ParserPluginRegistry()
        var reads = 0
        Snapshot.observe({ reads++ }, null) { plugins.version }
        assertTrue(reads > 0)
        plugins.register(MentionPlugin())
        val registered = plugins.version
        assertTrue(registered > 0)
        assertFalse(plugins.unregisterInline("missing"))
        assertEquals(registered, plugins.version)
        plugins.unregisterInline("mention")
        assertTrue(plugins.version > registered)
        val builder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = true
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
        }
        val builders = MarkdownBuilderRegistry().register(Text::class, builder)
        val first = builders.version
        builders.register(Text::class, builder)
        assertEquals(first, builders.version)
        builders.unregister(Text::class)
        assertTrue(builders.version > first)
    }
}
