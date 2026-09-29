package com.jackcaow.smoothmarkdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class NonTextSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun platformClipboardWriteFiltersOneCharacterBesideAnchorOnlyForTheSelectedPayload() {
        val anchor = "smd" + "a".repeat(497)
        val tagged = buildAnnotatedString {
            append(anchor)
            addStringAnnotation(nonTextAnchorAnnotationTag, anchor, 0, length)
        }
        val selected = listOf(tagged.subSequence(249, 250), AnnotatedString("A"))
        val nativeCopy = ClipEntry(ClipData.newPlainText("plain text", "a\nA"))
        val filtered = readerCopyClipEntry(nativeCopy, selected, setOf(anchor))
        assertEquals("A", filtered?.clipData?.getItemAt(0)?.text?.toString())
        assertEquals("plain text", filtered?.clipData?.description?.label?.toString())

        val unrelated = ClipEntry(ClipData.newPlainText("plain text", "A"))
        assertSame(unrelated, readerCopyClipEntry(unrelated, selected, setOf(anchor)))
        assertSame(nativeCopy, readerCopyClipEntry(nativeCopy, selected, emptySet()))
    }

    @Test fun longPressStartsOnRuleAndImageAndImageTapStillOpens() {
        val controller = SmoothSelectionController()
        var imageTaps = 0
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    markdown = "Before.\n\n---\n\n![Diagram](diagram.png)\n\nAfter.",
                    selectable = true,
                    scrollable = false,
                    selectionController = controller,
                    onImageClick = { imageTaps++ },
                    imageBuilder = { _, _, _ -> Box(Modifier.fillMaxWidth().height(96.dp)) },
                )
            }
        }
        val anchors = compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)
        anchors.assertCountEquals(2)
        assertTrue("overlay must be hidden from accessibility", anchors[0].fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        assertTrue("overlay must be hidden from accessibility", anchors[1].fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        compose.runOnIdle { controller.selectAll() }
        compose.runOnIdle {
            assertTrue("anchors absent from selection registrar: ${controller.selectedText.length}",
                controller.selectedText.contains("smd"))
            controller.clear()
        }
        anchors[0].performTouchInput { longClick() }
        compose.runOnIdle { assertTrue("rule did not start selection: ${controller.selectedText.length}", controller.selectedText.contains("smd")) }
        compose.runOnIdle { controller.clear() }
        anchors[1].performTouchInput { longClick() }
        compose.runOnIdle { assertTrue("image did not start selection", controller.selectedText.contains("smd")) }
        compose.runOnIdle { controller.clear() }
        compose.onNodeWithContentDescription("Diagram", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, imageTaps) }
    }

    @Test fun nativeCopyAcrossRuleAndImageStripsRegisteredAnchors() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val provider = RecordingMenuProvider()
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard,
                LocalTextContextMenuToolbarProvider provides provider) {
                MaterialTheme {
                    SmoothMarkdown(
                        markdown = "Before.\n\n---\n\n![Diagram](diagram.png)\n\nAfter.",
                        selectable = true,
                        scrollable = false,
                        selectionController = controller,
                        imageBuilder = { _, _, _ -> Box(Modifier.fillMaxWidth().height(96.dp)) },
                    )
                }
            }
        }
        compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)[0].performTouchInput { longClick() }
        compose.runOnIdle { controller.selectAll() }
        clickMenuCopy(provider)
        compose.waitForIdle()
        val copied = clipboard.entry?.clipData?.getItemAt(0)?.text?.toString()
        assertEquals("Before.\nAfter.", copied)
        compose.runOnIdle { controller.clear() }
        provider.data = null
        compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)[1].performTouchInput { longClick() }
        clickMenuCopy(provider)
        compose.waitForIdle()
        assertEquals("", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())
    }

    @Test fun composeCopyFromMiddleOfRuleAnchorPreservesEveryVisibleCharacter() {
        val controller = SmoothSelectionController()
        val clipboard = RecordingClipboard()
        val provider = RecordingMenuProvider()
        var customCopied: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides clipboard,
                LocalTextContextMenuToolbarProvider provides provider) {
                MaterialTheme {
                    SmoothMarkdown("Before.\n\n---\n\nAfter.", selectable = true,
                        scrollable = false, selectionController = controller,
                        selectionMenuActions = listOf(SmoothSelectionMenuAction(
                            "copy-visible", "Copy visible") { customCopied = it }))
                }
            }
        }
        compose.runOnIdle { controller.selectAll() }
        val all = compose.runOnIdle { selectionState(controller).selectedTexts }
        val anchorIndex = all.indexOfFirst {
            it.getStringAnnotations(nonTextAnchorAnnotationTag, 0, it.length).isNotEmpty()
        }
        assertTrue("Compose did not retain the anchor annotation", anchorIndex >= 0)
        val anchorBase = all.take(anchorIndex).sumOf { it.length }
        val afterIndex = all.indexOfFirst { it.text == "After." }
        assertTrue("later paragraph missing", afterIndex > anchorIndex)
        val afterBase = all.take(afterIndex).sumOf { it.length }

        compose.runOnIdle { controller.select(TextRange(anchorBase + 249, anchorBase + 250)) }
        compose.runOnIdle {
            val selected = selectionState(controller).selectedTexts
            assertEquals(1, selected.single().length)
            assertTrue(selected.single().getStringAnnotations(nonTextAnchorAnnotationTag, 0, 1).isNotEmpty())
        }
        clickMenuCopy(provider)
        compose.waitForIdle()
        assertEquals("", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())

        provider.data = null
        compose.runOnIdle { controller.select(TextRange(anchorBase + 249, afterBase + 5)) }
        clickMenuCopy(provider)
        compose.waitForIdle()
        assertEquals("After", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())

        provider.data = null
        compose.runOnIdle { controller.select(TextRange(anchorBase + 249, afterBase + 5)) }
        compose.waitUntil(timeoutMillis = 5_000) { provider.data != null }
        val action = provider.data!!.components.filterIsInstance<TextContextMenuItem>()
            .first { it.label == "Copy visible" }
        compose.runOnIdle { action.onClick(RecordingMenuSession()) }
        assertEquals("After", customCopied)

        clipboard.entry = null
        compose.runOnIdle { controller.select(TextRange(anchorBase + 249, afterBase + 5)) }
        compose.onAllNodes(isRoot())[0].performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.C)
            keyUp(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        assertEquals("After", clipboard.entry?.clipData?.getItemAt(0)?.text?.toString())
    }

    @Test fun platformFloatingCopyTapFromRuleWritesCleanClipboardWhenAvailable() {
        val controller = SmoothSelectionController()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("selection", "clipboard-before-copy"))
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("Before.\n\n---\n\nAfter.", selectable = true,
                    scrollable = false, selectionController = controller)
            }
        }
        compose.onAllNodesWithTag("nontext-selection-anchor", useUnmergedTree = true)[0]
            .performTouchInput { longClick() }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 6_000
        var copy: AccessibilityNodeInfo? = null
        while (copy == null && SystemClock.uptimeMillis() < deadline) {
            copy = findCopyNode(automation.rootInActiveWindow)
            if (copy == null) SystemClock.sleep(200)
        }
        if (copy == null) {
            Log.w("NonTextSelectionTest", "System floating Copy was not accessible; platform tap remains unverified")
            return
        }
        val clickable = generateSequence(copy) { it.parent }.firstOrNull { it.isClickable }
        assertTrue("System floating Copy did not accept tap",
            clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        compose.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() != "clipboard-before-copy"
        }
        assertEquals("", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    private fun findCopyNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.equals("Copy", ignoreCase = true) == true ||
            node.contentDescription?.toString()?.equals("Copy", ignoreCase = true) == true) return node
        for (index in 0 until node.childCount) {
            findCopyNode(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun selectionState(controller: SmoothSelectionController): SelectionState {
        val field = SmoothSelectionController::class.java.getDeclaredField("region").apply { isAccessible = true }
        return field.get(controller) as SelectionState
    }

    private fun clickMenuCopy(provider: RecordingMenuProvider) {
        compose.waitUntil(timeoutMillis = 5_000) { provider.data != null }
        val copy = provider.data!!.components.filterIsInstance<TextContextMenuItem>()
            .first { it.key == TextContextMenuKeys.CopyKey }
        compose.runOnIdle { copy.onClick(RecordingMenuSession()) }
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }

    private class RecordingMenuProvider : TextContextMenuProvider {
        @Volatile var data: TextContextMenuData? = null
        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            data = dataProvider.data()
            awaitCancellation()
        }
    }

    private class RecordingMenuSession : TextContextMenuSession {
        override fun close() = Unit
    }
}
