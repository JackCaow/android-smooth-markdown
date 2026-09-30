package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImageBuilderRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customImageContentCoversBlockInlineAndHtmlWithOriginalMetadata() {
        val built = mutableListOf<Triple<String, String?, String?>>()
        val clicked = mutableListOf<Triple<String, String?, String?>>()
        val markdown = """
            ![Block](https://example.com/block.png "B title")

            before ![Inline](inline.png "I title") <img src='html.png' alt='Html' title='H title' width='40'> ![Unsafe](file:///etc/passwd) <img src='javascript:alert(1)' alt='Bad'>
        """.trimIndent()
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(markdown, enableHtml = true,
                    onImageClickWithMetadata = { url, alt, title -> clicked += Triple(url, alt, title) },
                    imageBuilder = { url, alt, title ->
                        SideEffect { built += Triple(url, alt, title) }
                        Text("custom:$alt")
                    })
            }
        }
        compose.runOnIdle {
            assertTrue(built.contains(Triple("https://example.com/block.png", "Block", "B title")))
            assertTrue(built.contains(Triple("inline.png", "Inline", "I title")))
            assertTrue(built.contains(Triple("html.png", "Html", "H title")))
            assertFalse(built.any { it.second == "Unsafe" || it.second == "Bad" })
        }
        compose.onNodeWithContentDescription("Block").performClick()
        val paragraph = compose.onNodeWithText("before", substring = true, useUnmergedTree = true)
        paragraph.tapInlineImage(0)
        paragraph.tapInlineImage(1)
        compose.runOnIdle {
            assertEquals(listOf(
                Triple("https://example.com/block.png", "Block", "B title"),
                Triple("inline.png", "Inline", "I title"),
                Triple("html.png", "Html", "H title"),
            ), clicked)
        }
    }

    @Test fun streamMarkdownForwardsCustomImageBuilder() {
        val built = mutableListOf<Triple<String, String?, String?>>()
        compose.setContent {
            MaterialTheme {
                StreamMarkdown(flowOf("![Stream](stream.png \"title\")"), throttleMillis = 0,
                    imageBuilder = { url, alt, title ->
                        SideEffect { built += Triple(url, alt, title) }
                        Text("custom:$alt")
                    })
            }
        }
        compose.waitUntil(5_000) { built.contains(Triple("stream.png", "Stream", "title")) }
        compose.onNodeWithContentDescription("Stream").assertExists()
    }

    @Test fun finiteStreamPublishesFinalTextBeforeCompletingOnce() {
        val completions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                StreamMarkdown(flowOf("Hello ", "**world**"), throttleMillis = 1_000,
                    onComplete = { completions += it })
            }
        }
        compose.waitUntil(5_000) { completions.size == 1 }
        compose.onNodeWithText("Hello world", substring = true).assertExists()
        compose.runOnIdle { assertEquals(listOf("Hello **world**"), completions) }
    }

    private fun SemanticsNodeInteraction.tapInlineImage(index: Int) {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        check(action?.invoke(results) == true && results.size == 1)
        val rect = results.single().placeholderRects[index] ?: error("Missing inline image $index")
        performTouchInput { click(rect.center) }
    }
}
