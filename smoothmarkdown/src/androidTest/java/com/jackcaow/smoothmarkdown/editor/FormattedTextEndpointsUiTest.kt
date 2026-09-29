package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedTextEndpointsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun caretEndpointsAcrossParagraphAndHeadingDeleteOnlySelectedCharactersWithOneUndo() {
        val original = "BeforeX\n\n# YAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("X\n\n# Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()

        compose.runOnIdle {
            assertEquals("BeforeAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }

    @Test fun caretEndpointsReplaceRangeWithoutChangingOutsideBlocks() {
        val original = "BeforeX\n\nYAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-replacement").performScrollTo().performTextInput("# Inserted")
        compose.onNodeWithTag("formatted-text-replace").performClick()

        compose.runOnIdle {
            assertEquals("Before\n\n# Inserted\n\nAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }
}
