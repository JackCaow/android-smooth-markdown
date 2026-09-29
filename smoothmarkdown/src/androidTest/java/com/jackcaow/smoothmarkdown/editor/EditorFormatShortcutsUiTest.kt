package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EditorFormatShortcutsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ctrlBUsesSourceSelectionAndOneUndoStep() {
        val controller = MarkdownEditorController("alpha beta")
        val commands = mutableListOf<MarkdownEditorCommand>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(), onCommand = { commands += it })
            }
        }

        compose.onNodeWithTag("editor-source-input").performClick()
        compose.runOnIdle { controller.setSelection(0, 5) }
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.B)
            keyUp(Key.B)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("**alpha** beta", controller.text)
            assertEquals(TextRange(2, 7), controller.selection)
            assertEquals(listOf(MarkdownEditorCommand.BOLD), commands)
        }

        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Z)
            keyUp(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("alpha beta", controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun ctrlIAndCtrlKUseFormattedSelectionWithoutExtraHistory() {
        val controller = MarkdownEditorController("alpha beta")
        controller.mode = MarkdownEditorMode.FORMATTED
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithText("alpha beta").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.I)
            keyUp(Key.I)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle { assertEquals("*alpha* beta", controller.text) }

        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(6, 10))
        compose.onNodeWithText("alpha beta").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.K)
            keyUp(Key.K)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("*alpha* [beta](https://example.com)", controller.text)
            check(controller.undo())
            assertEquals("*alpha* beta", controller.text)
            check(controller.undo())
            assertEquals("alpha beta", controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun strikeToolbarAndCtrlShiftXUseFormattedSelectionAndOneUndoEach() {
        val controller = MarkdownEditorController("alpha beta")
        controller.mode = MarkdownEditorMode.FORMATTED
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithText("Strike").performClick()
        compose.runOnIdle { assertEquals("~~alpha~~ beta", controller.text) }

        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(6, 10))
        compose.onNodeWithText("alpha beta").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.X)
            keyUp(Key.X)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("~~alpha~~ ~~beta~~", controller.text)
            check(controller.undo())
            assertEquals("~~alpha~~ beta", controller.text)
            check(controller.undo())
            assertEquals("alpha beta", controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun ctrlShiftSUsesSourceSelectionAndKeepsOneUndoStep() {
        val controller = MarkdownEditorController("alpha beta")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("editor-source-input").performClick()
        compose.runOnIdle { controller.setSelection(0, 5) }
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.S)
            keyUp(Key.S)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("~~alpha~~ beta", controller.text)
            check(controller.undo())
            assertEquals("alpha beta", controller.text)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun ctrlShiftSRespectsHostShortcutAndCommandCapabilities() {
        val controller = MarkdownEditorController("alpha")
        var hostSawStrike = false
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(),
                    capabilities = MarkdownEditorCapabilities(setOf(MarkdownEditorCommand.STRIKETHROUGH)),
                    onShortcut = { event, _ ->
                        (event.key == Key.S && event.isCtrlPressed && event.isShiftPressed).also {
                            if (it) hostSawStrike = true
                        }
                    })
            }
        }
        compose.onNodeWithTag("editor-source-input").performClick()
        compose.runOnIdle { controller.setSelection(0, 5) }
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.S)
            keyUp(Key.S)
            keyDown(Key.X)
            keyUp(Key.X)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("alpha", controller.text)
            check(hostSawStrike)
            assertFalse(controller.canUndo)
        }
    }

    @Test fun capabilitiesAndHostHookSuppressBuiltInFormatting() {
        val controller = MarkdownEditorController("alpha")
        val commands = mutableListOf<MarkdownEditorCommand>()
        var hostSawItalic = false
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(),
                    capabilities = MarkdownEditorCapabilities(setOf(MarkdownEditorCommand.BOLD)),
                    onShortcut = { event, _ ->
                        (event.key == Key.I && event.isCtrlPressed).also { if (it) hostSawItalic = true }
                    },
                    onCommand = { commands += it })
            }
        }

        compose.onNodeWithTag("editor-source-input").performClick()
        compose.runOnIdle { controller.setSelection(0, 5) }
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.B)
            keyUp(Key.B)
            keyDown(Key.I)
            keyUp(Key.I)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("alpha", controller.text)
            assertEquals(emptyList<MarkdownEditorCommand>(), commands)
            check(hostSawItalic)
        }
    }

    @Test fun hostShortcutStillRunsWhenBuiltInsDisabled() {
        val controller = MarkdownEditorController("alpha")
        var hostSawKey = false
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(), enableKeyboardShortcuts = false,
                    onShortcut = { event, _ ->
                        (event.key == Key.B && event.isCtrlPressed).also { if (it) hostSawKey = true }
                    })
            }
        }
        compose.onNodeWithTag("editor-source-input").performClick()
        compose.runOnIdle { controller.setSelection(0, 5) }
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.B)
            keyUp(Key.B)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals("alpha", controller.text)
            check(hostSawKey)
        }
    }
}
