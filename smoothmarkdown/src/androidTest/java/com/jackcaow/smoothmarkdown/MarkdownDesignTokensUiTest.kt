package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MarkdownDesignTokensUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun enhancedQuoteHonorsExplicitFillAndUpdatesAfterThemeChanges() {
        val style = mutableStateOf(MarkdownStyleSheet(quoteBackground = Color.Green))
        compose.setContent { MaterialTheme { SmoothMarkdown("> Theme-controlled quote", styleSheet = style.value, useEnhancedComponents = true) } }
        fun containsFill(color: Color): Boolean {
            val pixels = compose.onNodeWithTag("markdown-blockquote").captureToImage().toPixelMap()
            var count = 0
            for (x in 0 until pixels.width step 4) for (y in 0 until pixels.height step 4) if (pixels[x,y] == color) count++
            return count > 20
        }
        assertTrue("Enhanced quote ignored explicit fill", containsFill(Color.Green))
        compose.runOnIdle { style.value = style.value.copy(quoteBackground = Color.Yellow) }
        assertTrue("Theme update was not rendered", containsFill(Color.Yellow))
    }
    @Test fun codeControlsUseHostLabelsAndNestedTokens() {
        val sheet = MarkdownStyleSheet(designTokens = MarkdownDesignTokens(code = MarkdownCodeTokens(copyLabel = "复制代码", copiedLabel = "已复制", syntaxColors = MarkdownSyntaxColors.light())))
        compose.setContent { MaterialTheme { SmoothMarkdown("```kotlin\nval x = 42\n```", styleSheet = sheet, useEnhancedComponents = true) } }
        compose.onNodeWithText("复制代码").assertExists()
    }
}
