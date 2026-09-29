package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BlockquoteStyleRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customBorderCoversEntireTallQuote() {
        val style = MarkdownStyleSheet(
            blockSpacing = 0.dp,
            blockquoteDecoration = MarkdownBlockquoteDecoration(
                backgroundColor = Color.Cyan,
                borderColor = Color.Red,
                borderWidth = 6.dp,
            ),
            blockquotePadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        )
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    "> First line\n>\n> Second line\n>\n> Third line",
                    styleSheet = style,
                )
            }
        }

        val quote = compose.onNodeWithTag("markdown-blockquote")
        val bounds = quote.getUnclippedBoundsInRoot()
        assertTrue("quote must be taller than the old 44 dp fixed border", bounds.bottom - bounds.top > 44.dp)
        val image = quote.captureToImage()
        val pixels = image.toPixelMap()
        assertEquals(Color.Red, pixels[1, 1])
        assertEquals(Color.Red, pixels[1, image.height - 2])
        assertEquals(Color.Cyan, pixels[image.width - 2, image.height - 2])
    }
}
