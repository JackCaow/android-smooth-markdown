package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
