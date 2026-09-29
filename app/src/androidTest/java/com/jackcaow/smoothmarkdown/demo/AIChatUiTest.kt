package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a physical Android device to check the compact AI Chat entry path. */
@RunWith(AndroidJUnit4::class)
class AIChatUiTest {
    @get:Rule val rule = createAndroidComposeRule<AIChatActivity>()

    @Test fun deepSeekOpensAnEmptyChatAndPromptsStayInTheMenu() {
        rule.onNodeWithTag("ai-status").assertTextContains("DeepSeek Flash · 模拟")
        rule.onNodeWithText("开始对话").assertExists()
        rule.onNodeWithText("🤖 AI Chat Demo").assertDoesNotExist()

        rule.onNodeWithTag("ai-prompts-menu").performClick()
        rule.onNodeWithTag("ai-prompt-thinking").performClick()
        rule.onNodeWithTag("ai-user-1").assertExists()

        rule.onNodeWithTag("ai-more-menu").performClick()
        rule.onNodeWithTag("ai-new-chat").performClick()
        rule.onNodeWithText("开始对话").assertExists()
        rule.onNodeWithTag("ai-user-1").assertDoesNotExist()

        rule.onNodeWithTag("ai-settings").performClick()
        rule.onNodeWithTag("ai-provider-deepseek").assertTextContains("✓ DeepSeek")
    }
}
