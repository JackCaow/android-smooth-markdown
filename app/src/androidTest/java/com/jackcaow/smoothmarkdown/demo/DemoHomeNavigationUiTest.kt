package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        rule.onNodeWithTag("current-title").assertTextEquals("Headers")
        rule.onNodeWithTag("open-source").performClick()
        rule.onNodeWithTag("markdown-source").assertExists()
    }
}
