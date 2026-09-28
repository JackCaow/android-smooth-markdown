package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedInlineEditorUiTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun formattedBoldAndLinkButtonsRoundTripToSourceAndUndo() {
        val controller = MarkdownEditorController("alpha beta")
        compose.setContent {
            MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
        }

        compose.onNodeWithText("Formatted").performClick()
        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithText("B").performClick()
        compose.runOnIdle { assertEquals("**alpha** beta", controller.text) }

        compose.onNodeWithText("alpha beta").performTextInputSelection(TextRange(6, 10))
        compose.runOnIdle {
            assertEquals("**alpha** beta", controller.text)
            assertEquals(TextRange(6, 10), controller.formattedSelection)
        }
        compose.onNodeWithText("Link").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("**alpha** [beta](https://example.com)", controller.text) }

        compose.onNodeWithText("Source").performClick()
        compose.onNodeWithText("**alpha** [beta](https://example.com)").assertExists()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.onNodeWithText("**alpha** beta").assertExists()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.onNodeWithText("alpha beta").assertExists()
        compose.runOnIdle { assertEquals("alpha beta", controller.text) }
    }
}
