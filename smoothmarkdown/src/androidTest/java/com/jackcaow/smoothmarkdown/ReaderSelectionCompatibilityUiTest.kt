package com.jackcaow.smoothmarkdown

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.text.AnnotatedString
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSelectionCompatibilityUiTest {
    @get:Rule val compose = createComposeRule()

    private val rightCell = "right-start these words occupy several wrapped lines within a narrow table cell"
    private val table = "| A header | B header |\n| --- | --- |\n| left-only | $rightCell |"
    private val tableCopy = "A header\nB header\nleft-only\n$rightCell"

    @Test fun unevenTableCellHeightsPreserveRowSourceOrder() {
        val controller = SmoothSelectionController()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    SmoothMarkdown(table, selectable = true, scrollable = false,
                        selectionController = controller)
                }
            }
        }
        val left = compose.onNodeWithText("left-only", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithText(rightCell, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("fixture must contain a multiline right cell", right.height > left.height)
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals(tableCopy, controller.selectedText) }
    }

    @Test fun rightToLeftTablePreservesMarkdownColumnOrder() {
        val controller = SmoothSelectionController()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        SmoothMarkdown(table, selectable = true, scrollable = false,
                            selectionController = controller)
                    }
                }
            }
        }
        val left = compose.onNodeWithText("left-only", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithText(rightCell, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("fixture must contain a multiline right cell", right.height > left.height)
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals(tableCopy, controller.selectedText) }
    }

    @Test fun selectingRepeatedRtlTableTextKeepsHandlesOnTappedCell() {
        val controller = SmoothSelectionController()
        val targets = mutableMapOf<Any, MarkdownSelectionTarget>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        SmoothMarkdown("| same | same |\n| --- | --- |\n| one | two |",
                            selectable = true, scrollable = false, selectionController = controller,
                            onTextPositioned = { targets[it.key] = it })
                    }
                }
            }
        }
        val tapped = compose.runOnIdle {
            targets.values.filter { it.text.text == "same" }.maxBy { it.boundsInWindow.left }
        }
        compose.runOnIdle { controller.selectWordAt(tapped.boundsInWindow.topLeft + requireNotNull(tapped.layoutResult).getBoundingBox(0).center) }
        compose.runOnIdle { assertEquals("same", controller.selectedText) }
        val handle = compose.onNodeWithTag("reader-selection-start-handle", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        assertTrue("handle selected the other repeated table cell: $handle versus ${tapped.boundsInWindow}",
            handle.center.x >= tapped.boundsInWindow.left && handle.center.x <= tapped.boundsInWindow.right)
    }

    @Test fun systemBackClosesSelectionWithoutReopeningAfterStyleChange() {
        val controller = SmoothSelectionController()
        var changedStyle by mutableStateOf(false)
        var menu: ReaderSelectionMenuSnapshot? = null
        compose.setContent {
            CompositionLocalProvider(LocalReaderSelectionMenuObserver provides { menu = it }) {
                MaterialTheme {
                    SmoothMarkdown("alpha beta gamma", selectable = true, scrollable = false,
                        selectionController = controller,
                        styleSheet = MarkdownStyleSheet.default().copy(
                            backgroundColor = if (changedStyle) Color.LightGray else Color.White))
                }
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitUntil(timeoutMillis = 5_000) { menu != null }
        // Exercise Android's real ActionMode dismissal, rather than the internal action hook.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedText.isEmpty() && menu == null }
        compose.runOnIdle { changedStyle = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("", controller.selectedText)
            assertEquals(null, menu)
        }
    }

    @Test fun nativeTextBuilderRenderingContextChildrenUsesReaderSelection() {
        val controller = SmoothSelectionController()
        val builder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NATIVE_TEXT
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
                context.renderInlineChildren(node)
            }
        }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("alpha beta\n\ngamma delta", selectable = true, scrollable = false,
                    selectionController = controller,
                    builderRegistry = MarkdownBuilderRegistry().register(Paragraph::class, builder))
            }
        }
        val text = compose.onNodeWithText("alpha beta", useUnmergedTree = true)
        val firstGlyph = text.glyphCenterLocal()
        text.performTouchInput { longClick(firstGlyph) }
        compose.runOnIdle {
            assertTrue("context children used an isolated Compose selection instead of the reader", controller.selectedText.isNotEmpty())
            assertEquals("alpha", controller.selectedText)
        }
    }

    @Test fun publicSelectableTextOverloadsKeepSourceOrderAfterConditionalPrefixReturns() {
        val controller = SmoothSelectionController()
        var showPrefix by mutableStateOf(true)
        val builder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NATIVE_TEXT
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
                Column {
                    if (showPrefix) SmoothSelectableText("prefix alpha")
                    SmoothSelectableText(AnnotatedString("suffix beta"))
                }
            }
        }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("custom paragraph", selectable = true, scrollable = false,
                    selectionController = controller,
                    builderRegistry = MarkdownBuilderRegistry().register(Paragraph::class, builder))
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals("prefix alpha\nsuffix beta", controller.selectedText) }
        compose.runOnIdle {
            controller.clear()
            showPrefix = false
        }
        compose.waitForIdle()
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals("suffix beta", controller.selectedText) }
        compose.runOnIdle {
            controller.clear()
            showPrefix = true
        }
        compose.waitForIdle()
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertEquals("prefix alpha\nsuffix beta", controller.selectedText) }

        // Both public overloads must join the reader gesture path, including the reinserted slot.
        compose.runOnIdle { controller.clear() }
        val prefix = compose.onNodeWithText("prefix alpha", useUnmergedTree = true)
        val prefixGlyph = prefix.glyphCenterLocal()
        prefix.performTouchInput { longClick(prefixGlyph) }
        compose.runOnIdle { assertEquals("prefix", controller.selectedText) }
        compose.runOnIdle { controller.clear() }
        val suffix = compose.onNodeWithText("suffix beta", useUnmergedTree = true)
        val suffixGlyph = suffix.glyphCenterLocal()
        suffix.performTouchInput { longClick(suffixGlyph) }
        compose.runOnIdle { assertEquals("suffix", controller.selectedText) }
    }

    @Test fun ordinaryTapClearsSelectionWithoutRequiringExplicitControllerClear() {
        val controller = SmoothSelectionController()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("alpha beta\n\ngamma delta", selectable = true, scrollable = false,
                    selectionController = controller)
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle { assertTrue(controller.selectedText.isNotEmpty()) }
        compose.onNodeWithText("gamma delta", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals("", controller.selectedText) }
    }

    @Test fun selectionHandlesRemainWithinReaderViewportWhileScrolling() {
        val controller = SmoothSelectionController()
        val document = (0..25).joinToString("\n\n") { "Paragraph $it text." }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(200.dp)) {
                    SmoothMarkdown(document, modifier = Modifier.testTag("reader-viewport"),
                        selectable = true, selectionController = controller)
                }
            }
        }
        compose.runOnIdle { assertTrue(controller.selectAllDocument()) }
        compose.waitUntil(timeoutMillis = 10_000) { controller.fullDocumentSelectionEstablished }
        compose.onAllNodesWithTag("reader-selection-start-handle", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("reader-selection-end-handle", useUnmergedTree = true).assertCountEquals(0)
        assertHandleInsideViewport("reader-selection-start-handle")
        repeat(8) {
            compose.onNodeWithTag("reader-viewport").performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("reader-selection-start-handle", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("reader-selection-end-handle", useUnmergedTree = true).assertCountEquals(1)
        assertHandleInsideViewport("reader-selection-end-handle")
        compose.runOnIdle { assertTrue(controller.selectedText.contains("Paragraph 0 text.")) }
    }

    private fun assertHandleInsideViewport(tag: String) {
        val viewport = compose.onNodeWithTag("reader-viewport").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag escaped the reader viewport: $handle outside $viewport",
            handle.left >= viewport.left && handle.right <= viewport.right &&
                handle.top >= viewport.top && handle.bottom <= viewport.bottom)
    }
}
