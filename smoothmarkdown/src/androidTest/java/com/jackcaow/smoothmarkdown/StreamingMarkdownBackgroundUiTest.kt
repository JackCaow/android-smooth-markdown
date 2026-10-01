package com.jackcaow.smoothmarkdown

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class StreamingMarkdownBackgroundUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeWorkIsOffMainAndCompletionFollowsFinalCompositionOnlyOnce() {
        val source = "Hello **world** 中文🙂"
        val flow = flowOf("Hello ", "**world** 中文🙂")
        val html = mutableStateOf(false)
        val completions = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        var composedSource: String? = null
        var parserThread: Long? = null
        val registry = MarkdownBuilderRegistry().register(Paragraph::class, object : MarkdownNodeBuilder {
            override fun canBuild(node: Node) = node is Paragraph
            @Composable override fun Render(node: Node, context: MarkdownBuilderContext) {
                val document = LocalStreamingMarkdownDocument.current
                context.renderInlineChildren(node)
                SideEffect {
                    composedSource = document?.source
                    if (document?.parserThreadId != null) parserThread = document.parserThreadId
                }
            }
        })
        val bridge = Class.forName("com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge")
        val count = bridge.getDeclaredField("successfulParses").apply { isAccessible = true }.get(null) as AtomicLong
        val before = count.get()
        compose.setContent { MaterialTheme {
            StreamMarkdown(flow, throttleMillis = 1_000, builderRegistry = registry, enableHtml = html.value,
                onError = { errors += it },
                onComplete = {
                    assertSame(Looper.getMainLooper(), Looper.myLooper())
                    assertEquals("Final document must already have participated in UI composition", source, composedSource)
                    completions += it
                })
        } }
        compose.waitUntil(5_000) { completions.size == 1 || errors.isNotEmpty() }
        compose.runOnIdle { assertTrue("Callback error: ${errors.joinToString { it.stackTraceToString() }}", errors.isEmpty()) }
        compose.onNodeWithText("Hello world 中文🙂", substring = true).assertExists()
        compose.runOnIdle {
            assertTrue("The real packaged JNI parser must run", count.get() > before)
            assertNotNull(parserThread)
            assertNotEquals("Native parse and wire decode belong to the worker", Thread.currentThread().id, parserThread)
            assertEquals(listOf(source), completions)
            html.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle { html.value = false }
        compose.waitUntil(5_000) { composedSource == source }
        compose.runOnIdle { assertEquals("Configuration changes must not repeat completion", listOf(source), completions) }
    }

    @Test fun customParserCallbacksAndCompletionStayOnMainThread() {
        var invocations = 0
        val plugins = ParserPluginRegistry().apply { register(object : InlineParserPlugin {
            override val id = "main-only"
            override val name = "Main only"
            override val triggerCharacter = '@'
            override fun canParse(text: String, index: Int) = text[index] == '@'
            override fun parse(text: String, startIndex: Int): InlineParseResult {
                assertSame("Opaque plugin callbacks retain their UI-thread contract", Looper.getMainLooper(), Looper.myLooper())
                invocations++
                return InlineParseResult(PluginInlineNode(), text.length - startIndex)
            }
            override fun render(node: PluginInlineNode) = InlinePluginPresentation("UI-only")
        }) }
        val source = flowOf("@custom")
        val completions = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            StreamMarkdown(source, plugins = plugins, throttleMillis = 0, onComplete = {
                assertSame(Looper.getMainLooper(), Looper.myLooper())
                assertTrue(invocations > 0)
                completions += it
            })
        } }
        compose.waitUntil(5_000) { completions.size == 1 }
        compose.onNodeWithText("UI-only").assertExists()
        compose.runOnIdle { assertEquals(listOf("@custom"), completions) }
    }

    @Test fun replacingAnUnfinishedFlowCannotCompleteOrRestoreItsOldDocument() {
        val unfinished: Flow<String> = flow { emit("Old source"); awaitCancellation() }
        val current = mutableStateOf(unfinished)
        val completions = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            StreamMarkdown(current.value, throttleMillis = 0, onComplete = { completions += it })
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Old source")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { current.value = flowOf("New final") }
        compose.waitUntil(5_000) { completions.size == 1 }
        compose.onNodeWithText("New final").assertExists()
        compose.onNodeWithText("Old source").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("New final"), completions) }
    }
}
