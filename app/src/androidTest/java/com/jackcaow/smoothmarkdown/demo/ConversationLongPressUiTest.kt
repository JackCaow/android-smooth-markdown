package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationLongPressUiTest {
    @get:Rule val rule = createAndroidComposeRule<ConversationListActivity>()

    @Test fun longPressShowsFlutterCopyAndSelectMenu() {
        rule.onNodeWithTag("conversation-row-1").performClick()
        holdUntilMenuAppears("@alice")
        rule.onNodeWithTag("conversation-longpress-copy").assertExists()
        rule.onNodeWithTag("conversation-longpress-select").performClick()
        rule.onNodeWithTag("conversation-longpress-select").assertDoesNotExist()
        rule.onNodeWithTag("conversation-bubble-1-0").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "已选择：@alice SmoothMarkdown 缓存策略更新了吗？性能提升了 32倍 🚀"))
    }

    private fun holdUntilMenuAppears(text: String) {
        val paragraph = rule.onNodeWithText(text, substring = true)
        paragraph.performScrollTo()
        paragraph.performTouchInput { down(center) }
        try {
            // The Demo timer uses coroutine time; a batched longClick only advances
            // input timestamps. Hold the actual pointer until its menu is displayed.
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("conversation-longpress-select").fetchSemanticsNodes().size == 1
            }
        } finally {
            paragraph.performTouchInput { up() }
        }
    }

    @Test fun selectsPressedParagraphInsteadOfEntireMessage() {
        rule.onNodeWithTag("conversation-row-3").performClick()
        holdUntilMenuAppears("不过我建议")
        rule.onNodeWithTag("conversation-longpress-select").performClick()
        rule.onNodeWithTag("conversation-bubble-3-1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "已选择：不过我建议再看一下 长期维护成本。"))
    }
}
