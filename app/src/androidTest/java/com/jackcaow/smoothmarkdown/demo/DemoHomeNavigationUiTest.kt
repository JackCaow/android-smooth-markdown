package com.jackcaow.smoothmarkdown.demo

import android.graphics.Bitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoHomeNavigationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun drawerUsesFlutterExampleTitlesAndStillNavigatesToSource() {
        rule.onNodeWithTag("open-navigation").performClick()
        rule.onNodeWithTag("nav-basic-formatting").assertTextEquals("Basic Formatting")
        rule.onNodeWithText("示例").assertDoesNotExist()

        rule.onNodeWithTag("nav-headers").performClick()
        rule.onNodeWithTag("current-title").assertDoesNotExist()
        rule.onNodeWithTag("current-theme").assertDoesNotExist()
        rule.onNodeWithText("Header 1").assertExists()
        rule.onNodeWithTag("open-theme").assertExists()
        rule.onNodeWithTag("open-source").performClick()
        rule.onNodeWithTag("markdown-source").assertExists()
    }

    @Test fun flutterExamplePagesKeepNavigationWithoutSecondaryTitleBar() {
        val pages = listOf(
            "basic-formatting" to "Basic Text Formatting",
            "headers" to "Header 1",
            "lists" to "Unordered Lists",
            "code-blocks" to "Code Example",
            "quotes-rules" to "Blockquotes",
            "links-images" to "Links",
            "enhanced-ui" to "增强 UI 组件展示",
            "theme-showcase" to "主题展示",
            "details-summary" to "Details & Summary 折叠块",
            "complex-example" to "🚀 完整 Markdown 功能展示",
        )
        pages.forEach { (id, heading) ->
            rule.onNodeWithTag("open-navigation").performClick()
            rule.onNodeWithTag("nav-$id").performScrollTo().performClick()
            rule.onNodeWithText(heading).assertExists()
            rule.onNodeWithTag("current-title").assertDoesNotExist()
            rule.onNodeWithTag("current-theme").assertDoesNotExist()
            rule.onNodeWithTag("open-theme").assertExists()
            rule.waitForIdle()
            val screenshot = requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            )
            val output = File(requireNotNull(rule.activity.getExternalFilesDir(null)), "visual-audit/$id.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
    }
}
