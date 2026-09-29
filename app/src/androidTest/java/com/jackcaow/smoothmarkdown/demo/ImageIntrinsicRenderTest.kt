package com.jackcaow.smoothmarkdown.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImageIntrinsicRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineSvgUsesAssetIntrinsicSizeInsteadOfFixedPlaceholder() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                SmoothMarkdown("before ![Mark](smooth-markdown-mark.svg) after")
            }
        }
        compose.waitUntil(10_000) {
            val paragraph = compose.onNodeWithText("before", substring = true, useUnmergedTree = true)
            val results = mutableListOf<TextLayoutResult>()
            val action = paragraph.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
            action?.invoke(results) == true &&
                results.single().placeholderRects.firstOrNull()?.width?.let { it >= 79f * density } == true
        }
        compose.runOnIdle {
            val paragraph = compose.onNodeWithText("before", substring = true, useUnmergedTree = true)
            val results = mutableListOf<TextLayoutResult>()
            check(paragraph.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
            val rect = results.single().placeholderRects.single()!!
            assertTrue(rect.width >= 79f * density)
            assertTrue(rect.height >= 79f * density)
        }
    }

    @Test fun blockSvgKeepsIntrinsicWidthInsteadOfFillingParagraph() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            MaterialTheme { SmoothMarkdown("![Mark](smooth-markdown-mark.svg)") }
        }
        compose.waitUntil(10_000) {
            val width = compose.onNodeWithContentDescription("Mark", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width
            width >= 79f * density
        }
        compose.runOnIdle {
            val width = compose.onNodeWithContentDescription("Mark", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width
            assertTrue(width in 79f * density..90f * density)
        }
    }
}
