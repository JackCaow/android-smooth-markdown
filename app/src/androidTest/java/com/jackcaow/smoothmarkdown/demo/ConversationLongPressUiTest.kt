package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationLongPressUiTest {
    @get:Rule val rule = createAndroidComposeRule<ConversationListActivity>()

    @Test fun longPressShowsFlutterCopyAndSelectMenu() {
        rule.onNodeWithTag("conversation-row-1").performClick()
        rule.onNodeWithText("@alice", substring = true).performTouchInput { longClick() }
        rule.onNodeWithTag("conversation-longpress-copy").assertExists()
        rule.onNodeWithTag("conversation-longpress-select").performClick()
        rule.onNodeWithTag("conversation-longpress-select").assertDoesNotExist()
        rule.onNodeWithTag("conversation-bubble-1-0").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "已选择：@alice SmoothMarkdown 缓存策略更新了吗？性能提升了 32倍 🚀"))
    }

    @Test fun selectsPressedParagraphInsteadOfEntireMessage() {
        rule.onNodeWithTag("conversation-row-3").performClick()
        rule.onNodeWithText("不过我建议", substring = true).performTouchInput { longClick() }
        rule.onNodeWithTag("conversation-longpress-select").performClick()
        rule.onNodeWithTag("conversation-bubble-3-1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "已选择：不过我建议再看一下 长期维护成本。"))
    }
}
