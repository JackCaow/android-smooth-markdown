package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Text
import org.junit.Rule
import org.junit.Test

class MarkdownRegistryUpdatesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlinePluginMutationReparsesExistingReaderWithoutReplacingRegistry() {
        val registry = ParserPluginRegistry()
        compose.setContent { MaterialTheme { SmoothMarkdown(":smile:", plugins = registry) } }
        compose.onNodeWithText(":smile:").assertExists()
        compose.runOnIdle { registry.register(EmojiPlugin()) }
        compose.onNodeWithText("😄").assertExists()
        compose.runOnIdle { registry.unregisterInline("emoji") }
        compose.onNodeWithText(":smile:").assertExists()
    }

    @Test fun groupedOptionsRespectExplicitCodeControlsAndRichCopyEvent() {
        val copied = mutableListOf<Pair<String, String?>>()
        compose.setContent { MaterialTheme { SmoothMarkdown(
            "```kotlin\nval answer = 42\n```",
            renderOptions = MarkdownRenderOptions(useEnhancedComponents = false, codeBlocks = CodeBlockOptions()),
            events = MarkdownEvents(onCodeCopied = { code, language -> copied += code to language }),
        ) } }
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle {
            assertEquals(listOf("val answer = 42\n" to "kotlin"), copied)
        }
    }

    @Test fun blockAndInlineImageEachLoadOnceWhenCacheIsDisabled() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val bytes = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
        val resources = MarkdownResourceOptions(cachePolicy = MarkdownResourceCachePolicy.NO_STORE,
            loader = MarkdownResourceLoader { calls.incrementAndGet(); bytes })
        compose.setContent { MaterialTheme { SmoothMarkdown(
            "![block](https://example.com/image.png)\n\nbefore ![inline](https://example.com/image.png) after",
            resourceOptions = resources, scrollable = false,
        ) } }
        compose.waitUntil(5_000) { calls.get() >= 2 }
        compose.waitForIdle()
        assertEquals("Intrinsic sizing must share the rendered image request", 2, calls.get())
    }

    @Test fun builderMutationRerendersExistingReaderWithoutReplacingRegistry() {
        val registry = MarkdownBuilderRegistry()
        compose.setContent { MaterialTheme { SmoothMarkdown("original", builderRegistry = registry) } }
        compose.onNodeWithText("original").assertExists()
        compose.runOnIdle {
            registry.register(Text::class, object : MarkdownNodeBuilder {
                override fun canBuild(node: Node) = node is Text
                @Composable override fun Render(node: Node, context: MarkdownBuilderContext) = Unit
                override fun renderInline(node: Node) = MarkdownInlinePresentation.Text("updated")
            })
        }
        compose.onNodeWithText("updated").assertExists()
        compose.runOnIdle { registry.clear() }
        compose.onNodeWithText("original").assertExists()
    }
}
