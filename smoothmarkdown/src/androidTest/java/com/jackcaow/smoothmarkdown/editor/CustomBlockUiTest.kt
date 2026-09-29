package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CustomBlockUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitlyMatchedRawBlockDelegatesViewAndEdit() {
        val original = "Before\n\n<div>Old</div>\n\nAfter"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(
                    controller,
                    Modifier.fillMaxSize(),
                    customBlockMatcher = { it.kind == MarkdownBlockKind.RAW && it.source.startsWith("<div>") },
                    customBlockBuilder = { block ->
                        TextButton(onClick = block.edit, modifier = Modifier.testTag("host-custom-preview")) {
                            Text("Host ${block.blockType}: ${block.markdown}")
                        }
                    },
                    customBlockEditorBuilder = { block ->
                        TextButton(onClick = { block.replaceMarkdown("## Edited") }, modifier = Modifier.testTag("host-custom-save")) {
                            Text("Save custom")
                        }
                    },
                )
            }
        }
        compose.onNodeWithTag("host-custom-preview").assertExists().performClick()
        compose.onNodeWithTag("host-custom-save").assertExists().performClick()
        compose.onNodeWithTag("host-custom-save").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("Before\n\n## Edited\n\nAfter", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }
}
