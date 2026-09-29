package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderNonTextSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectAllIncludesTextOnBothSidesOfRuleAndTableCells() {
        val controller = SmoothSelectionController()
        val source = """
            Before the rule.

            ---

            | Left | Right |
            | --- | --- |
            | One | Two |

            After the table.
        """.trimIndent()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(source, selectable = true, selectionController = controller)
            }
        }
        compose.runOnIdle { controller.selectAll() }
        compose.waitForIdle()
        compose.runOnIdle {
            val selected = controller.selectedText
            assertTrue("Missing text before rule: $selected", selected.contains("Before the rule."))
            assertTrue("Missing table cell: $selected", selected.contains("One"))
            assertTrue("Missing text after table: $selected", selected.contains("After the table."))
        }
    }
}
