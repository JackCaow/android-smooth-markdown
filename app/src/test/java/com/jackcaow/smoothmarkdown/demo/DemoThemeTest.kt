package com.jackcaow.smoothmarkdown.demo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoThemeTest {
    @Test fun eachMarkdownPresetGivesTheWholeDemoItsFlutterBrightness() {
        (0..5).forEach { index ->
            val dark = index % 2 == 1
            assertEquals("preset $index", dark, demoThemeIsDark(index))
            val chrome = demoColorScheme(index)
            if (dark) {
                assertTrue("preset $index should use light text", chrome.onBackground.luminance() > chrome.background.luminance())
                assertEquals(Color(0xFF0D1117), chrome.background)
                assertEquals(Color(0xFF161B22), chrome.surface)
            } else {
                assertFalse("preset $index should use dark text", chrome.onBackground.luminance() > chrome.background.luminance())
                assertEquals(Color.White, chrome.background)
                assertEquals(Color.White, chrome.surface)
            }
        }
    }
}
