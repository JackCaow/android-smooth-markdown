package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedListUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editsTaskTextAndCheckboxThenUndoesBoth() {
        val original = "- [ ] first\n- [x] second"
        val controller = MarkdownEditorController(original)
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("Formatted").performClick()
        compose.onNodeWithText("first").performTextReplacement("updated")
        compose.runOnIdle { assertEquals("- [ ] updated\n- [x] second", controller.text) }
        compose.onNodeWithTag("formatted-task-block-0-0").performClick()
        compose.runOnIdle { assertEquals("- [x] updated\n- [x] second", controller.text) }

        compose.onNodeWithText("Source").performClick()
        compose.onNodeWithText("- [x] updated\n- [x] second").assertExists()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("- [ ] updated\n- [x] second", controller.text) }
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, controller.text) }
    }

    @Test fun editsNestedItemAndItsContinuationInFormattedMode() {
        val original = "- parent\n  continuation\n  - child\n    detail\n- sibling"
        val controller = MarkdownEditorController(original)
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("Formatted").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-0-0").performTextReplacement("renamed")
        compose.onNodeWithTag("formatted-list-continuation-block-0-0-0-1").performTextReplacement("more detail")
        val expected = "- parent\n  continuation\n  - renamed\n    more detail\n- sibling"
        compose.runOnIdle {
            assertEquals(expected, controller.text)
            assertEquals(true, controller.undo())
            assertEquals(true, controller.undo())
            assertEquals(original, controller.text)
            assertEquals(true, controller.redo())
            assertEquals(true, controller.redo())
            assertEquals(expected, controller.text)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun enterAndTabChangeFormattedListStructureAtCaret() {
        val controller = MarkdownEditorController("- parent\n- childnext")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithText("Formatted").performClick()

        compose.onNodeWithTag("formatted-list-item-block-0-1").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-1").performTextInputSelection(TextRange(5))
        compose.onNodeWithTag("formatted-list-item-block-0-1").performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.runOnIdle { assertEquals("- parent\n- child\n- next", controller.text) }

        compose.onNodeWithTag("formatted-list-item-block-0-1").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-1").performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }
        compose.runOnIdle { assertEquals("- parent\n  - child\n- next", controller.text) }

        compose.onNodeWithTag("formatted-list-item-block-0-0-0").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-0-0").performKeyInput {
            keyDown(Key.ShiftLeft)
            keyDown(Key.Tab)
            keyUp(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        compose.runOnIdle { assertEquals("- parent\n- child\n- next", controller.text) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun returnOnEmptyRootItemCreatesFocusedEditableParagraph() {
        val controller = MarkdownEditorController("- parent\n- ")
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }
        compose.onNodeWithText("Formatted").performClick()

        compose.onNodeWithTag("formatted-list-item-block-0-1").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-1").performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.runOnIdle { assertEquals("- parent\n\n", controller.text) }

        compose.onNodeWithTag("formatted-empty-paragraph").performTextInput("body")
        compose.runOnIdle { assertEquals("- parent\n\nbody", controller.text) }
    }
}
