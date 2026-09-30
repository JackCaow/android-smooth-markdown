package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorMode
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EnhancedComponentsModeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun codeToolbarAppearsOnlyWhenEnhancedModeIsEnabled() {
        var enhanced by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("# Heading\n\n```kotlin\nval x = 1\n```",
                    useEnhancedComponents = enhanced)
            }
        }
        compose.onNodeWithText("val x = 1", substring = true).assertExists()
        compose.onNodeWithText("Copy").assertDoesNotExist()

        compose.runOnIdle { enhanced = true }
        compose.onNodeWithText("Copy").assertHasClickAction()
    }

    @Test fun editorPreviewDefaultsToEnhancedAndCanUseStandardMode() {
        val controller = MarkdownEditorController("```kotlin\nval x = 1\n```").also {
            it.mode = MarkdownEditorMode.PREVIEW
        }
        var enhanced by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, useEnhancedComponents = enhanced)
            }
        }
        compose.onNodeWithText("Copy").assertHasClickAction()
        compose.runOnIdle { enhanced = false }
        compose.onNodeWithText("Copy").assertDoesNotExist()
    }

    @Test fun externalLinkKeepsItsLabelAndTapInBothModes() {
        var enhanced by mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("[site](https://example.com)", useEnhancedComponents = enhanced,
                    onLinkClick = { opened += it })
            }
        }
        compose.onNodeWithText("site").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("https://example.com"), opened)
            enhanced = true
        }
        compose.onNodeWithText("site").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("https://example.com", "https://example.com"), opened) }
    }
}
