package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailsSummaryInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plainTextExpandsWhileSafeLinkCallsBackAndUnsafeLinkDoesNot() {
        val links = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    "<details>\n<summary>Plain [Safe](https://example.com/safe) " +
                        "[Unsafe](javascript:alert(1))</summary>\nHidden body\n</details>",
                    onLinkClick = { links += it },
                )
            }
        }
        val summary = compose.onNodeWithText("Plain Safe Unsafe", useUnmergedTree = true)
        compose.onNodeWithText("Hidden body").assertDoesNotExist()

        summary.tapWord("Plain")
        compose.onNodeWithText("Hidden body").assertExists()
        summary.tapWord("Plain")
        compose.onNodeWithText("Hidden body").assertDoesNotExist()

        summary.tapWord("Safe")
        compose.runOnIdle { assertEquals(listOf("https://example.com/safe"), links) }
        compose.onNodeWithText("Hidden body").assertDoesNotExist()

        summary.tapWord("Unsafe")
        compose.runOnIdle { assertEquals(1, links.size) }
        compose.onNodeWithText("Hidden body").assertExists()
    }

    @Test fun inlineImageCallsBackWithoutExpandingSummary() {
        val legacy = mutableListOf<String>()
        val metadata = mutableListOf<Triple<String, String?, String?>>()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    "<details>\n<summary>Plain ![Icon](icon.png \"Title\") end</summary>\nHidden body\n</details>",
                    onImageClick = { legacy += it },
                    onImageClickWithMetadata = { source, alt, title -> metadata += Triple(source, alt, title) },
                )
            }
        }
        compose.onNodeWithText("Plain", substring = true, useUnmergedTree = true)
            .tapInlineImage()
        compose.runOnIdle {
            assertEquals(listOf("icon.png"), legacy)
            assertEquals(listOf(Triple("icon.png", "Icon", "Title")), metadata)
        }
        compose.onNodeWithText("Hidden body").assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.tapWord(word: String) {
        val layout = textLayout()
        val start = layout.layoutInput.text.text.indexOf(word)
        check(start >= 0)
        val center = layout.getBoundingBox(start + word.length / 2).center
        performTouchInput { click(center) }
    }

    private fun SemanticsNodeInteraction.tapInlineImage() {
        val rect = textLayout().placeholderRects.singleOrNull()
        check(rect != null) { "Expected one inline image placeholder" }
        performTouchInput { click(rect.center) }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        check(action?.invoke(results) == true && results.size == 1)
        return results.single()
    }
}
