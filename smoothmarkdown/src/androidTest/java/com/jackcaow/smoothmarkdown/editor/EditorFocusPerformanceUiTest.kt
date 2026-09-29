package com.jackcaow.smoothmarkdown.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorFocusPerformanceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceFocusTransitionsExcludeSearchAndFormattedFields() {
        val controller = MarkdownEditorController("Note")
        val transitions = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme { SmoothMarkdownEditor(controller, onFocusChanged = { transitions += it }) }
        }

        compose.onNodeWithTag("editor-source-input").performClick()
        compose.waitUntil(5_000) { transitions == listOf(true) }
        compose.onNodeWithTag("editor-find").performClick()
        compose.waitUntil(5_000) { transitions == listOf(true, false) }
        compose.onNodeWithText("Formatted").performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), transitions) }
    }

    @Test fun snapshotsContainFinalSourceAndFindState() {
        val controller = MarkdownEditorController("One")
        val snapshots = mutableListOf<MarkdownEditorPerformanceSnapshot>()
        compose.setContent {
            MaterialTheme { SmoothMarkdownEditor(controller, onPerformanceSnapshot = { snapshots += it }) }
        }
        compose.waitUntil(5_000) { snapshots.isNotEmpty() }
        compose.runOnIdle {
            snapshots.clear()
            controller.text = "One Two"
            controller.text = "One Two Two"
        }
        compose.waitUntil(5_000) { snapshots.lastOrNull()?.sourceLength == "One Two Two".length }
        compose.runOnIdle { assertEquals(1, snapshots.size) }

        compose.onNodeWithTag("editor-find").performClick()
        compose.onNodeWithTag("editor-search-query").performTextInput("Two")
        compose.waitUntil(5_000) { snapshots.lastOrNull()?.searchMatchCount == 2 }
        compose.runOnIdle { assertTrue(snapshots.last().timestampMillis > 0) }
    }
}
