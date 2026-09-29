package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a physical Android device to check the compact AI Chat entry path. */
@RunWith(AndroidJUnit4::class)
class AIChatUiTest {
    @get:Rule val rule = createAndroidComposeRule<AIChatActivity>()

    @Test fun deepSeekOpensAnEmptyChatAndPromptsStayInTheMenu() {
        rule.onNodeWithTag("ai-status").assertTextContains("DeepSeek Flash", substring = true)
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

    @Test fun localDevelopmentKeyStreamsRealDeepSeekReply() {
        assumeTrue("Requires the ignored local.properties development Key",
            BuildConfig.DEEPSEEK_API_KEY.isNotBlank())

        rule.onNodeWithTag("ai-status").assertTextContains("DeepSeek Flash", substring = true)
        rule.onNodeWithTag("ai-input").performTextInput("Reply with exactly OK.")
        rule.onNodeWithTag("ai-send").performClick()
        rule.waitUntil(120_000) {
            try {
                rule.onNodeWithTag("ai-status").assertTextContains("DeepSeek Flash", substring = true)
                rule.onNodeWithTag("ai-assistant-2").assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        rule.onNodeWithTag("ai-source-2").performClick()
        rule.onNodeWithTag("ai-source-content").assertTextContains("OK", substring = true)
        rule.onNodeWithText("⚠️", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
