package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorHostActionsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imageImportExportButtonsReachHostAndKeepUndoAtomic() {
        val controller = MarkdownEditorController("Intro")
        val events = mutableListOf<MarkdownEditorImagePickStatus>()
        var exported = ""
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                    onPickImage = { MarkdownEditorImageSelection("assets/logo.png", "Badge", "Picked") },
                    onImagePickEvent = { events += it.status },
                    onImportMarkdown = { "# Imported" },
                    onExportMarkdown = { exported = it },
                )
            }
        }

        compose.onNodeWithTag("editor-pick-image").performScrollTo().performClick()
        compose.waitUntil(5_000) { events.lastOrNull() == MarkdownEditorImagePickStatus.INSERTED }
        compose.runOnIdle {
            assertEquals("Intro\n\n![Badge](assets/logo.png \"Picked\")", controller.text)
            assertEquals(listOf(MarkdownEditorImagePickStatus.PICKING, MarkdownEditorImagePickStatus.INSERTED), events)
            check(controller.undo())
            assertEquals("Intro", controller.text)
        }

        compose.onNodeWithTag("editor-import-markdown").performScrollTo().performClick()
        compose.waitUntil(5_000) { controller.text.contains("# Imported") }
        compose.runOnIdle {
            assertEquals("Intro\n\n# Imported", controller.text)
            check(controller.undo())
            assertEquals("Intro", controller.text)
        }

        compose.onNodeWithTag("editor-export-markdown").performScrollTo().performClick()
        compose.waitUntil(5_000) { exported.isNotEmpty() }
        compose.runOnIdle {
            assertEquals("Intro", exported)
            assertEquals("Intro", controller.text)
        }
    }
}
