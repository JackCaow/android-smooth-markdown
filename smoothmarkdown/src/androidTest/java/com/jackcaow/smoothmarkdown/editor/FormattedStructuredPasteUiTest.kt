package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormattedStructuredPasteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formattedFieldConvertsMultiBlockMarkdownPasteIntoSiblingBlocks() {
        val controller = MarkdownEditorController("before target after").apply { mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-block-drag-block-0")
            .performTextReplacement("before # Heading\n\n- item after")
        compose.runOnIdle {
            assertEquals("before \n\n# Heading\n\n- item\n\n after", controller.text)
            check(controller.undo())
            assertEquals("before target after", controller.text)
            check(!controller.canUndo)
        }
    }
}
