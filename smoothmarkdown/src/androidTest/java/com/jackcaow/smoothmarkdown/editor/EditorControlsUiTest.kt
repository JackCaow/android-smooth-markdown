package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slashSuggestionIsVisibleAndOneUndoStep() {
        val controller = MarkdownEditorController("/he")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-slash-suggestions").assertExists()
        compose.onNodeWithTag("editor-slash-suggestion-1").performClick()
        compose.runOnIdle {
            assertEquals("## ", controller.text)
            check(controller.undo())
            assertEquals("/he", controller.text)
        }
    }

    @Test fun configuredToolbarOrderAndCapabilitiesReachCommand() {
        val controller = MarkdownEditorController("Plain")
        val capabilities = MarkdownEditorCapabilities(setOf(MarkdownEditorCommand.BOLD))
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(),
                    capabilities = capabilities,
                    toolbarCommands = listOf(MarkdownEditorCommand.BLOCKQUOTE, MarkdownEditorCommand.BOLD))
            }
        }
        compose.onNodeWithText("Blockquote").assertExists().performClick()
        compose.onNodeWithText("B").assertDoesNotExist()
        compose.runOnIdle { assertEquals("> Plain", controller.text) }
    }

    @Test fun customSlashCommandAppearsAndEditsSource() {
        val controller = MarkdownEditorController("/ca")
        controller.setSelection(3)
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(),
                    customSlashCommands = listOf(MarkdownEditorSlashCommand("Callout", "note", markdown = "> Hello")))
            }
        }
        compose.onNodeWithText("Callout").performClick()
        compose.waitUntil(5_000) { controller.text == "> Hello" }
        compose.runOnIdle {
            assertEquals("> Hello", controller.text)
            check(controller.undo())
            assertEquals("/ca", controller.text)
        }
    }

    @Test fun findNavigatesSourceAndFocusModeCanBeExited() {
        val controller = MarkdownEditorController("Alpha beta alpha")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-focus-mode").performClick()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("editor-exit-focus").performClick()
        compose.onNodeWithText("Undo").assertExists()

        compose.onNodeWithTag("editor-find").performClick()
        compose.onNodeWithTag("editor-search-query").performTextInput("alpha")
        compose.onNodeWithTag("editor-search-count").assertTextEquals("1/2")
        compose.onNodeWithTag("editor-search-next").performClick()
        compose.onNodeWithTag("editor-search-count").assertTextEquals("2/2")
        compose.runOnIdle {
            assertEquals(MarkdownEditorMode.SOURCE, controller.mode)
            assertEquals("Alpha beta alpha", controller.text)
            check(!controller.canUndo)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun ctrlFAndCtrlShiftEnterWorkFromFocusedEditor() {
        val controller = MarkdownEditorController("Alpha beta alpha")
        controller.mode = MarkdownEditorMode.SOURCE
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-source-input").performClick()
        compose.onNodeWithTag("editor-source-input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.F)
            keyUp(Key.F)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("editor-search-query").assertExists()
        compose.onNodeWithTag("editor-search-query").performTextInput("alpha")
        compose.onNodeWithTag("editor-search-count").assertTextEquals("1/2")

        compose.onNodeWithTag("editor-search-query").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.Enter)
            keyUp(Key.Enter)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("editor-exit-focus").assertExists()
        compose.onNodeWithTag("editor-find").assertDoesNotExist()
        compose.onNodeWithTag("editor-search-query").assertExists()

        compose.onNodeWithTag("editor-search-query").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.Enter)
            keyUp(Key.Enter)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("editor-focus-mode").assertExists()
    }

    @Test fun formattedFindSearchesVisibleTextAndNavigatesWithoutLeavingFormattedMode() {
        val source = "# **Alpha** [link](https://alpha.example)\n\n- alpha"
        val controller = MarkdownEditorController(source).apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("editor-find").performClick()
        compose.onNodeWithTag("editor-search-query").performTextInput("alpha")
        compose.onNodeWithTag("editor-search-count").assertTextEquals("1/2")
        compose.onNodeWithTag("editor-search-next").performClick()
        compose.onNodeWithTag("editor-search-count").assertTextEquals("2/2")
        compose.runOnIdle {
            assertEquals(MarkdownEditorMode.FORMATTED, controller.mode)
            assertEquals("alpha", controller.selectedText)
            check(!controller.canUndo)
        }
        compose.onNodeWithTag("editor-search-close").performClick()
        compose.onNodeWithTag("editor-search-query").assertDoesNotExist()
    }
}
