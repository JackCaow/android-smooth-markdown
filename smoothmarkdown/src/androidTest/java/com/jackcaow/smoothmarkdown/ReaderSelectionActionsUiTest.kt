package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ReaderSelectionActionsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controllerSelectsWordAndParagraphAtWindowPositionAndClearsOutside() {
        val controller = SmoothSelectionController()
        val targets = mutableMapOf<Any, MarkdownSelectionTarget>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    markdown = "alpha beta gamma\n\n---\n\ndelta epsilon zeta",
                    selectable = true,
                    scrollable = false,
                    selectionController = controller,
                    onTextPositioned = { targets[it.key] = it },
                )
            }
        }
        compose.waitForIdle()
        val first = targets.values.first { it.text.text == "alpha beta gamma" }
        val beta = pointForOffset(first, 7)
        compose.runOnIdle { controller.selectWordAt(beta) }
        compose.runOnIdle { assertEquals("beta", controller.selectedText) }
        compose.runOnIdle { controller.selectParagraphAt(beta) }
        compose.runOnIdle { assertEquals("alpha beta gamma", controller.selectedText) }
        val second = targets.values.first { it.text.text == "delta epsilon zeta" }
        compose.runOnIdle { controller.selectWordAt(pointForOffset(second, 7)) }
        compose.runOnIdle { assertEquals("epsilon", controller.selectedText) }
        compose.runOnIdle { controller.selectParagraphAt(pointForOffset(second, 7)) }
        compose.runOnIdle { assertEquals("delta epsilon zeta", controller.selectedText) }
        compose.runOnIdle { controller.selectWordAt(Offset(-100f, -100f)) }
        compose.runOnIdle { assertEquals("", controller.selectedText) }
    }

    @Test fun nativeMenuCanReplaceCopyWithHostActionReceivingVisibleText() {
        val controller = SmoothSelectionController()
        val targets = mutableMapOf<Any, MarkdownSelectionTarget>()
        val provider = RecordingTextContextMenuProvider()
        var customCopied: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalReaderSelectionMenuObserver provides provider::record) {
                MaterialTheme {
                    SmoothMarkdown(
                        markdown = "alpha beta gamma",
                        selectable = true,
                        scrollable = false,
                        selectionController = controller,
                        onTextPositioned = { targets[it.key] = it },
                        showDefaultCopyAction = false,
                        selectionMenuActions = listOf(SmoothSelectionMenuAction("copy-visible", "Copy visible") {
                            customCopied = it
                        }),
                    )
                }
            }
        }
        compose.waitForIdle()
        val first = targets.values.first { it.text.text == "alpha beta gamma" }
        compose.runOnIdle { controller.selectWordAt(pointForOffset(first, 7)) }
        compose.waitUntil(timeoutMillis = 5_000) { provider.data != null }
        val components = provider.data!!.actions
        assertFalse("default Copy should be hidden", provider.data!!.defaultCopyVisible)
        val custom = components.firstOrNull { it.label == "Copy visible" }
        assertNotNull("custom native menu action missing", custom)
        compose.runOnIdle { provider.data!!.invokeAction(custom!!.key) }
        assertEquals("beta", customCopied)
    }

    @Test fun controllerUsesUpdatedTextWhenMarkdownChangesWithoutChangingLayout() {
        val controller = SmoothSelectionController()
        val targets = mutableMapOf<Any, MarkdownSelectionTarget>()
        var markdown by mutableStateOf("alpha beta")
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    markdown = markdown,
                    selectable = true,
                    scrollable = false,
                    selectionController = controller,
                    onTextPositioned = { targets[it.key] = it },
                )
            }
        }
        compose.waitForIdle()
        val original = targets.values.first { it.text.text == "alpha beta" }
        compose.runOnIdle { controller.selectWordAt(pointForOffset(original, 7)) }
        compose.runOnIdle { assertEquals("beta", controller.selectedText) }
        compose.runOnIdle { markdown = "omega zeta" }
        compose.waitForIdle()
        val updated = targets.values.first { it.text.text == "omega zeta" }
        compose.runOnIdle { controller.selectWordAt(pointForOffset(updated, 7)) }
        compose.runOnIdle { assertEquals("zeta", controller.selectedText) }
    }

    @Test fun customMenuActionFiltersRuleAnchorFromSelectedText() {
        val controller = SmoothSelectionController()
        val provider = RecordingTextContextMenuProvider()
        var customCopied: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalReaderSelectionMenuObserver provides provider::record) {
                MaterialTheme {
                    SmoothMarkdown(
                        markdown = "Before.\n\n---\n\nAfter.",
                        selectable = true,
                        scrollable = false,
                        selectionController = controller,
                        selectionMenuActions = listOf(SmoothSelectionMenuAction("copy-visible", "Copy visible") {
                            customCopied = it
                        }),
                    )
                }
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitUntil(timeoutMillis = 5_000) { provider.data != null }
        val custom = provider.data!!.actions.first { it.label == "Copy visible" }
        compose.runOnIdle { provider.data!!.invokeAction(custom.key) }
        assertEquals("Before.\nAfter.", customCopied)
    }

    private fun pointForOffset(target: MarkdownSelectionTarget, desired: Int): Offset {
        val y = target.boundsInWindow.top + 12f
        for (x in target.boundsInWindow.left.toInt() until target.boundsInWindow.right.toInt()) {
            val point = Offset(x.toFloat(), y)
            if (target.offsetAtWindowPosition?.invoke(point) == desired) return point
        }
        throw AssertionError("no point maps to rendered offset $desired")
    }

    private class RecordingTextContextMenuProvider {
        @Volatile var data: ReaderSelectionMenuSnapshot? = null
        fun record(snapshot: ReaderSelectionMenuSnapshot?) { data = snapshot }
    }
}
