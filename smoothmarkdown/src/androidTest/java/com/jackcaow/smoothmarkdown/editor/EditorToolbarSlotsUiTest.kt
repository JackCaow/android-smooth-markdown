package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorToolbarSlotsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostSlotsWrapDefaultControlsAndKeepTheirActions() {
        val controller = MarkdownEditorController("Body")
        var leadingClicks = 0
        var trailingClicks = 0
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(
                    controller,
                    toolbarLeading = listOf({
                        TextButton(onClick = { leadingClicks++ }, modifier = Modifier.testTag("host-leading")) { Text("Lead") }
                    }),
                    toolbarTrailing = listOf({
                        TextButton(onClick = { trailingClicks++ }, modifier = Modifier.testTag("host-trailing")) { Text("Trail") }
                    }),
                    toolbarBuilder = { defaultToolbar ->
                        Column {
                            Text("Host wrapper", modifier = Modifier.testTag("host-toolbar-wrapper"))
                            defaultToolbar()
                        }
                    },
                )
            }
        }

        compose.onNodeWithTag("host-toolbar-wrapper").assertExists()
        compose.onNodeWithTag("host-leading").performClick()
        compose.onNodeWithTag("host-trailing").performClick()
        compose.onNodeWithText("Preview").performClick()
        compose.runOnIdle {
            assertEquals(1, leadingClicks)
            assertEquals(1, trailingClicks)
            assertEquals(MarkdownEditorMode.PREVIEW, controller.mode)
        }
    }

    @Test fun builderCanReplaceToolbarWhileEditorBodyRemains() {
        val controller = MarkdownEditorController("Body")
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, toolbarBuilder = { _ ->
                    Text("Host only", modifier = Modifier.testTag("host-only-toolbar"))
                })
            }
        }

        compose.onNodeWithTag("host-only-toolbar").assertExists()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("editor-source-input").assertExists()
    }

    @Test fun hiddenToolbarLeavesSourceVisibleAndDoesNotComposeHostSlots() {
        val controller = MarkdownEditorController("Body")
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, showToolbar = false,
                    toolbarLeading = listOf({ Text("Leading", modifier = Modifier.testTag("hidden-leading")) }),
                    toolbarTrailing = listOf({ Text("Trailing", modifier = Modifier.testTag("hidden-trailing")) }))
            }
        }

        compose.onNodeWithTag("hidden-leading").assertDoesNotExist()
        compose.onNodeWithTag("hidden-trailing").assertDoesNotExist()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("editor-source-input").assertExists()
        compose.runOnIdle {
            controller.setSelection(0, 4)
            controller.applyCommand(MarkdownEditorCommand.BOLD)
        }
        compose.runOnIdle { assertEquals("**Body**", controller.text) }
    }

    @Test fun focusModeHidesHostToolbarButRetainsExitControl() {
        val controller = MarkdownEditorController("Body")
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, toolbarBuilder = { defaultToolbar ->
                    Column {
                        Text("Wrapped", modifier = Modifier.testTag("host-wrapper"))
                        defaultToolbar()
                    }
                })
            }
        }

        compose.onNodeWithTag("host-wrapper").assertExists()
        compose.onNodeWithTag("editor-focus-mode").performClick()
        compose.onNodeWithTag("host-wrapper").assertDoesNotExist()
        compose.onNodeWithTag("editor-exit-focus").performClick()
        compose.onNodeWithTag("host-wrapper").assertExists()
    }
}
