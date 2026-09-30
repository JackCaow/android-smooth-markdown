package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FormattedTextPositionTrackerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun measuredFieldsMapWindowPointsAfterScrollAndRejectStaleSource() {
        val source = "Alpha\n\n# אבגדה"
        val registry = FormattedTextPositionRegistry()
        lateinit var first: FormattedTextFieldTracker
        lateinit var second: FormattedTextFieldTracker
        compose.setContent {
            Column(Modifier.height(90.dp).verticalScroll(rememberScrollState())) {
                first = trackedField(registry, "block-0", source, "Alpha", Modifier.testTag("first"))
                Spacer(Modifier.height(120.dp))
                second = trackedField(registry, "block-1", source, "אבגדה", Modifier.testTag("second"), rtl = true)
            }
        }
        compose.runOnIdle {
            val firstBounds = requireNotNull(first.target.boundsInWindow())
            assertEquals("block-0", registry.positionAt(firstBounds.center, source)?.blockId)
            assertNull(registry.positionAt(firstBounds.center, "old source"))
        }
        compose.onNodeWithTag("second").performScrollTo()
        compose.runOnIdle {
            val bounds = requireNotNull(second.target.boundsInWindow())
            assertEquals("block-1", registry.positionAt(bounds.center, source)?.blockId)
            val left = registry.positionAt(Offset(bounds.left + 2f, bounds.center.y), source)?.offset
            val right = registry.positionAt(Offset(bounds.right - 2f, bounds.center.y), source)?.offset
            assertTrue("RTL visual left should resolve after visual right", left != null && right != null && left > right)
        }
    }

    @Composable
    private fun trackedField(
        registry: FormattedTextPositionRegistry,
        id: String,
        source: String,
        visible: String,
        modifier: Modifier,
        rtl: Boolean = false,
    ): FormattedTextFieldTracker {
        val tracker = rememberFormattedTextFieldTracker(registry, id, source, visible)
        BasicTextField(visible, {}, modifier.fillMaxWidth().then(tracker.modifier),
            textStyle = TextStyle(textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr),
            onTextLayout = tracker::onTextLayout)
        return tracker
    }
}
