package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jackcaow.smoothmarkdown.MarkdownBuilderContext
import com.jackcaow.smoothmarkdown.MarkdownBuilderRegistry
import com.jackcaow.smoothmarkdown.MarkdownInlinePresentation
import com.jackcaow.smoothmarkdown.MarkdownNodeBuilder
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text as MarkdownText
import org.junit.Rule
import org.junit.Test

class EditorPreviewBuilderRegistryUiTest {
    @get:Rule val compose = createComposeRule()

    private val source = "# Native heading\n\nNative body"

    private class HostHeadingBuilder(private val accepts: Boolean) : MarkdownNodeBuilder {
        override fun canBuild(node: Node) = accepts && node is Heading

        @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
            Text("Host heading", modifier = Modifier.testTag("host-heading"))
        }
    }

    private class HostInlineBuilder(private val accepts: Boolean) : MarkdownNodeBuilder {
        override fun canBuild(node: Node) = accepts && node is MarkdownText

        @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit

        override fun renderInline(node: Node): MarkdownInlinePresentation? =
            if (canBuild(node)) MarkdownInlinePresentation.Text("Host inline") else null
    }

    @Test fun hostBlockAndInlineOverridesAppearInPreviewAndSplit() {
        val controller = MarkdownEditorController(source).also { it.mode = MarkdownEditorMode.PREVIEW }
        val builders = MarkdownBuilderRegistry()
            .register(Heading::class, HostHeadingBuilder(accepts = true))
            .register(MarkdownText::class, HostInlineBuilder(accepts = true))
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(), builderRegistry = builders)
            }
        }

        assertHostOverrides()
        compose.runOnIdle { controller.mode = MarkdownEditorMode.SPLIT }
        assertHostOverrides()
    }

    @Test fun rejectedBuildersKeepBuiltinPreviewAndSplitRendering() {
        val controller = MarkdownEditorController(source).also { it.mode = MarkdownEditorMode.PREVIEW }
        val builders = MarkdownBuilderRegistry()
            .register(Heading::class, HostHeadingBuilder(accepts = false))
            .register(MarkdownText::class, HostInlineBuilder(accepts = false))
        compose.setContent {
            MaterialTheme {
                SmoothMarkdownEditor(controller, Modifier.fillMaxSize(), builderRegistry = builders)
            }
        }

        assertBuiltinRendering()
        compose.runOnIdle { controller.mode = MarkdownEditorMode.SPLIT }
        assertBuiltinRendering()
    }

    private fun assertHostOverrides() {
        compose.onNodeWithTag("host-heading").assertExists()
        compose.onNodeWithText("Host inline").assertExists()
        compose.onNodeWithText("Native body").assertDoesNotExist()
    }

    private fun assertBuiltinRendering() {
        compose.onNodeWithTag("host-heading").assertDoesNotExist()
        compose.onNodeWithText("Host inline").assertDoesNotExist()
        compose.onNodeWithText("Native heading").assertExists()
        compose.onNodeWithText("Native body").assertExists()
    }
}
