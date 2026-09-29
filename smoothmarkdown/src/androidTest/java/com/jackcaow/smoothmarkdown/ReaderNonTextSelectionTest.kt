package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ReaderNonTextSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unselectableReaderKeepsCodeToolbarAvailableWithoutAttachingController() {
        val controller = SmoothSelectionController()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("Before.\n\n```kotlin\nfirst()\n```\n\nAfter.",
                    selectable = false, selectionController = controller,
                    useEnhancedComponents = true)
            }
        }
        compose.onNodeWithText("Copy").assertHasClickAction()
        compose.runOnIdle {
            controller.selectAll()
            assertTrue(controller.selectedText.isEmpty())
        }
    }

    @Test fun builtInCodeSelectionKeepsSourceLinesAndExcludesToolbarWithEitherButtonSetting() {
        val controller = SmoothSelectionController()
        var options by mutableStateOf(CodeBlockOptions())
        val source = "Before code.\n\n```kotlin\nfirst()\nsecond()\n```\n\nAfter code."
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(source, selectable = true, selectionController = controller,
                    codeBlockOptions = options, useEnhancedComponents = true)
            }
        }
        for (showButton in listOf(true, false)) {
            compose.runOnIdle {
                options = CodeBlockOptions(showCopyButton = showButton, showLanguageTag = true)
            }
            compose.waitForIdle()
            if (showButton) compose.onNodeWithText("Copy").assertHasClickAction()
            else compose.onNodeWithText("Copy").assertDoesNotExist()
            compose.runOnIdle {
                controller.selectAll()
                val selected = controller.selectedText
                assertTrue("preceding prose absent: $selected", selected.contains("Before code."))
                assertTrue("first source line absent: $selected", selected.contains("first()\nsecond()"))
                assertTrue("following prose absent: $selected", selected.contains("After code."))
                assertFalse("language label was selected: $selected", selected.contains("KOTLIN"))
                assertFalse("button label was selected: $selected", selected.contains("Copy"))
            }
        }
    }

    @Test fun dragAcrossBuiltInCodeAndFollowingProseCopiesOnlyVisibleSourceOrder() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val source = "Before code.\n\n```kotlin\nfirst()\nsecond()\n```\n\nAfter code."
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(source, selectable = true, selectionController = controller)
                }
            }
        }
        val start = compose.onNodeWithText("first()", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText("After code.", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(start)
            advanceEventTime(750)
            repeat(16) { step -> moveTo(start + (end - start) * ((step + 1) / 16f)) }
            up()
        }
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("drag missed first code line: $selected", selected.contains("first()"))
            assertTrue("drag missed second code line: $selected", selected.contains("second()"))
            assertTrue("drag missed following prose: $selected", selected.contains("After code"))
            assertFalse("language label was selected: $selected", selected.contains("KOTLIN"))
            assertFalse("button label was selected: $selected", selected.contains("Copy"))
        }
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
        val first = copied.indexOf("first()")
        val second = copied.indexOf("second()")
        val after = copied.indexOf("After code")
        assertTrue("Copy changed visible source order: $copied", first >= 0 && first < second && second < after)
        assertFalse("Copy included language label: $copied", copied.contains("KOTLIN"))
        assertFalse("Copy included button label: $copied", copied.contains("Copy"))
    }

    @Test fun selectAllIncludesTextOnBothSidesOfRuleAndTableCells() {
        val controller = SmoothSelectionController()
        val source = """
            Before the rule.

            ---

            | Left | Right |
            | --- | --- |
            | One | Two |

            After the table.
        """.trimIndent()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(source, selectable = true, selectionController = controller)
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitForIdle()
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("Missing text before rule: $selected", selected.contains("Before the rule."))
            assertTrue("Missing table cell: $selected", selected.contains("One"))
            assertTrue("Missing text after table: $selected", selected.contains("After the table."))
        }
    }

    @Test fun selectAllSpansMathVisualAndDelimitedTextWithoutCopyingAnchor() {
        val controller = SmoothSelectionController()
        val plugins = ParserPluginRegistry().apply { registerBlock(DelimitedBlockPlugin("note")) }
        val source = "Before math.\n\n\u0024\u0024\nx + y\n\u0024\u0024\n\n" +
            ":::note\nVisible plugin text.\n:::\n\nAfter plugin."
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(source, selectable = true, selectionController = controller,
                    plugins = plugins)
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitForIdle()
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("Missing preceding prose: $selected", selected.contains("Before math."))
            assertTrue("Missing native plugin text: $selected", selected.contains("Visible plugin text."))
            assertTrue("Missing following prose: $selected", selected.contains("After plugin."))
            assertFalse("Visual anchor leaked into copy: $selected", selected.contains("smd"))
        }
    }

    @Test fun visualBuilderAnchorPreservesTouchCallback() {
        var taps = 0
        val builder = object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Image
            override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NON_TEXT
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
                Box(Modifier.size(80.dp).testTag("custom-visual").clickable { taps++ })
            }
        }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("Before.\n\n![visual](https://example.com/v.png)\n\nAfter.",
                    selectable = true,
                    builderRegistry = MarkdownBuilderRegistry().register(Image::class, builder))
            }
        }
        compose.onNodeWithTag("custom-visual").performTouchInput { click() }
        compose.runOnIdle { assertTrue("Visual tap was intercepted by selection anchor", taps == 1) }
    }

    @Test fun longPressDragFromTableCellIntoFollowingParagraphSelectsVisibleText() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val source = """
            Before the table.

            | Left | Right |
            | --- | --- |
            | One | Two |

            After the table.
        """.trimIndent()
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                MaterialTheme {
                    SmoothMarkdown(source, selectable = true, scrollable = false,
                        selectionController = controller)
                }
            }
        }
        val start = compose.onNodeWithText("One", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText("After the table.", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(start)
            advanceEventTime(750)
            repeat(16) { step -> moveTo(start + (end - start) * ((step + 1) / 16f)) }
            up()
        }
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("long-press drag missed first table cell: $selected", selected.contains("One"))
            assertTrue("long-press drag missed next table cell: $selected", selected.contains("Two"))
            assertTrue("long-press drag missed following paragraph: $selected", selected.contains("After the table"))
        }
        // Long-press selection opens Compose's toolbar/handle popups, each with
        // its own semantics root. Send the shortcut to the content root.
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue("copy missed first table cell: $copied", copied.contains("One"))
        assertTrue("copy missed next table cell: $copied", copied.contains("Two"))
        assertTrue("copy missed following paragraph: $copied", copied.contains("After the table"))
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
}
