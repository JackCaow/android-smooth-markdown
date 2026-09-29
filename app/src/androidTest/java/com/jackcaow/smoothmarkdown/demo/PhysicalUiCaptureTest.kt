package com.jackcaow.smoothmarkdown.demo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full-device PNGs for physical-device visual review; run with the Desktop run_capture.sh. */
private object PhysicalUiCapture {
    private val runId: String by lazy {
        InstrumentationRegistry.getArguments().getString("captureRunId")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
            ?: error("Pass -e captureRunId=<safe unique id> to the instrumentation runner")
    }

    fun save(name: String) {
        require(name.matches(Regex("[a-z0-9-]+"))) { "Invalid screenshot name: $name" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val file = File(root, "ui-review/$runId/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "UI_CAPTURE ${file.absolutePath}\n")
        })
    }

    fun settle() {
        // Images and animated dialogs can paint after Compose has no pending frame.
        SystemClock.sleep(350)
    }

    /** Stop only once the tagged scroll container's semantic offset stops changing. */
    fun scrollToEdge(node: SemanticsNodeInteraction, waitForIdle: () -> Unit, bottom: Boolean) {
        fun value(): Float = node.fetchSemanticsNode().config[
            SemanticsProperties.VerticalScrollAxisRange
        ].value()
        repeat(80) {
            val before = value()
            node.performTouchInput { if (bottom) swipeUp() else swipeDown() }
            waitForIdle()
            val after = value()
            if (kotlin.math.abs(after - before) < 0.001f) return
        }
        error("Scroll did not reach ${if (bottom) "bottom" else "top"} within 80 swipes")
    }
}

@RunWith(AndroidJUnit4::class)
class MainPhysicalUiCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        PhysicalUiCapture.settle()
        PhysicalUiCapture.save(name)
    }

    private fun openPage(id: String) {
        rule.onNodeWithTag("open-navigation").performClick()
        rule.onNodeWithTag("nav-$id").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun scrollToEdge(tag: String, bottom: Boolean = true) =
        PhysicalUiCapture.scrollToEdge(rule.onNodeWithTag(tag), rule::waitForIdle, bottom)

    @Test fun homeExamplesAndChrome() {
        capture("home-basic-formatting")
        rule.onNodeWithTag("open-navigation").performClick()
        capture("home-navigation")
        rule.onNodeWithTag("nav-basic-formatting").performClick()

        val ids = listOf("basic-formatting", "headers", "lists", "code-blocks",
            "quotes-rules", "links-images", "enhanced-ui", "theme-showcase",
            "details-summary", "complex-example")
        ids.forEach { id ->
            if (id != "basic-formatting") openPage(id)
            scrollToEdge("reader-scroll", bottom = false)
            capture("example-$id-top")
            scrollToEdge("reader-scroll")
            capture("example-$id-bottom")
        }
        openPage("basic-formatting")
        rule.onNodeWithTag("open-source").performClick()
        rule.onNodeWithTag("markdown-source").assertExists()
        capture("home-source-dialog")
    }

    @Test fun themeAndLanguageChoices() {
        for (index in 0..5) {
            rule.onNodeWithTag("open-theme").performClick()
            rule.onNodeWithTag("theme-$index").performClick()
            capture("theme-$index")
        }
        for (code in listOf("zh", "en", "ja", "es", "fr", "ko")) {
            rule.onNodeWithTag("open-navigation").performClick()
            rule.onNodeWithTag("language-$code").performScrollTo().performClick()
            capture("language-$code")
        }
    }

    @Test fun specialPagesAndStates() {
        for (id in listOf("math", "footnote", "plugin", "editor", "html", "stream")) {
            openPage(id)
            when (id) {
                "math", "footnote" -> scrollToEdge("reader-scroll", bottom = false)
                "plugin" -> scrollToEdge("plugin-reader-scroll", bottom = false)
                "html" -> scrollToEdge("html-markdown", bottom = false)
                "editor" -> scrollToEdge("editor-formatted-scroll", bottom = false)
            }
            if (id == "editor") capture("editor-formatted") else capture("page-$id")
            when (id) {
                "math", "footnote" -> {
                    scrollToEdge("reader-scroll")
                    capture("page-$id-bottom")
                }
                "plugin" -> {
                    scrollToEdge("plugin-reader-scroll")
                    capture("page-plugin-bottom")
                    scrollToEdge("plugin-reader-scroll", bottom = false)
                    rule.onNodeWithTag("plugin-source-toggle").performClick()
                    capture("plugin-source-open")
                }
                "html" -> {
                    scrollToEdge("html-markdown")
                    capture("page-html-bottom")
                    scrollToEdge("html-markdown", bottom = false)
                    rule.onNodeWithTag("html-enabled").performClick()
                    capture("html-disabled")
                    rule.onNodeWithTag("html-enabled").performClick()
                    rule.onNodeWithTag("html-stream-toggle").performClick()
                    rule.onNodeWithTag("html-progress").assertExists()
                    capture("html-streaming")
                    rule.onNodeWithTag("html-stream-toggle").performClick()
                    capture("html-stopped")
                }
                "stream" -> {
                    rule.onNodeWithTag("stream-empty").assertExists()
                    rule.onNodeWithTag("stream-start").performClick()
                    capture("stream-in-progress")
                    rule.waitUntil(15_000) {
                        rule.onAllNodesWithTag("stream-complete-bar").fetchSemanticsNodes().isNotEmpty()
                    }
                    capture("stream-complete")
                    scrollToEdge("stream-markdown")
                    capture("stream-complete-bottom")
                    rule.onNodeWithTag("stream-reset").performClick()
                    capture("stream-reset")
                }
                "editor" -> {
                    scrollToEdge("editor-formatted-scroll")
                    capture("editor-formatted-bottom")
                    scrollToEdge("editor-formatted-scroll", bottom = false)
                    rule.onNodeWithTag("editor-find").performClick()
                    capture("editor-search")
                    rule.onNodeWithTag("editor-find").performClick()
                    rule.onNodeWithTag("editor-mode-source").performClick()
                    rule.onNodeWithTag("editor-source-input").assertExists()
                    capture("editor-source")
                    rule.onNodeWithTag("editor-source-input").performTouchInput { swipeUp() }
                    capture("editor-source-after-scroll")
                    rule.onNodeWithTag("editor-mode-preview").performClick()
                    scrollToEdge("editor-preview-scroll", bottom = false)
                    capture("editor-preview")
                    scrollToEdge("editor-preview-scroll")
                    capture("editor-preview-bottom")
                    rule.onNodeWithTag("editor-mode-split").performClick()
                    scrollToEdge("editor-preview-scroll", bottom = false)
                    capture("editor-split")
                    scrollToEdge("editor-preview-scroll")
                    capture("editor-split-preview-bottom")
                }
            }
            rule.onNodeWithTag("demo-back").performClick()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class MermaidPhysicalUiCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<MermaidDemoActivity>()

    @Test fun allFortyDiagramsAndDarkMode() {
        fun scroll(bottom: Boolean) = PhysicalUiCapture.scrollToEdge(
            rule.onNodeWithTag("mermaid-content-scroll"), rule::waitForIdle, bottom)
        val expected = loadMermaidGallery(rule.activity.assets)
        assertEquals(40, expected.size)
        rule.waitForIdle()
        PhysicalUiCapture.save("mermaid-navigation-closed")
        rule.onNodeWithTag("mermaid-open-navigation").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("mermaid-navigation-open")
        rule.onNodeWithTag("mermaid-nav-1").performClick()
        for (index in 1..40) {
            rule.onNodeWithText("$index / 40").assertExists()
            rule.onNodeWithText(expected[index - 1].title).assertExists()
            rule.waitForIdle()
            PhysicalUiCapture.settle()
            PhysicalUiCapture.save("mermaid-${index.toString().padStart(2, '0')}")
            scroll(bottom = true)
            PhysicalUiCapture.save("mermaid-${index.toString().padStart(2, '0')}-source")
            if (index < 40) {
                rule.onNodeWithTag("mermaid-next").performClick()
                scroll(bottom = false)
            }
        }
        rule.onNodeWithTag("mermaid-theme").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("mermaid-40-dark")
    }
}

@RunWith(AndroidJUnit4::class)
class AIChatPhysicalUiCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<AIChatActivity>()

    @Test fun localMockPromptAndSettings() {
        rule.waitForIdle()
        PhysicalUiCapture.save("ai-chat-empty")
        rule.onNodeWithTag("ai-settings").performClick()
        rule.onNodeWithTag("ai-real-api").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("ai-chat-settings")
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithTag("ai-prompts-menu").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("ai-chat-prompts")
        rule.onNodeWithTag("ai-prompt-thinking").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("ai-source-2").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("正在输入...").fetchSemanticsNodes().isEmpty()
        }
        rule.waitForIdle()
        PhysicalUiCapture.save("ai-chat-mock-reply")
        rule.onNodeWithTag("ai-more-menu").performClick()
        rule.onNodeWithTag("ai-theme").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("ai-chat-dark")
    }
}

@RunWith(AndroidJUnit4::class)
class ChatListPhysicalUiCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ChatListActivity>()

    @Test fun welcomeReplyCacheAndDarkMode() {
        rule.waitForIdle()
        PhysicalUiCapture.save("chat-list-welcome")
        rule.onNodeWithTag("chat-input").performTextInput("Show me a Markdown table")
        rule.onNodeWithTag("chat-send").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("chat-assistant-2").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("Online").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        PhysicalUiCapture.save("chat-list-reply")
        rule.onNodeWithTag("chat-cache").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("chat-list-cache")
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithTag("chat-theme").performClick()
        rule.waitForIdle()
        PhysicalUiCapture.save("chat-list-dark")
    }
}

@RunWith(AndroidJUnit4::class)
class ConversationPhysicalUiCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ConversationListActivity>()

    @Test fun listDetailAndLongPressMenu() {
        val samples = loadConversationSamples(rule.activity)
        assertEquals(12, samples.size)
        rule.waitForIdle()
        PhysicalUiCapture.save("conversation-list")
        PhysicalUiCapture.scrollToEdge(rule.onNodeWithTag("conversation-list"), rule::waitForIdle, true)
        PhysicalUiCapture.save("conversation-list-bottom")
        for (sample in samples) {
            rule.onNodeWithTag("conversation-list")
                .performScrollToNode(hasTestTag("conversation-row-${sample.id}"))
            rule.onNodeWithTag("conversation-row-${sample.id}").performClick()
            rule.onNodeWithText(sample.name).assertExists()
            PhysicalUiCapture.scrollToEdge(rule.onNodeWithTag("conversation-detail"),
                rule::waitForIdle, false)
            rule.waitForIdle()
            PhysicalUiCapture.save("conversation-detail-${sample.id.padStart(2, '0')}")
            if (sample.id == "1") {
                rule.onNodeWithTag("conversation-message-menu-1-0").performClick()
                rule.waitForIdle()
                PhysicalUiCapture.save("conversation-message-menu")
                rule.onNodeWithText("关闭").performClick()
            }
            PhysicalUiCapture.scrollToEdge(rule.onNodeWithTag("conversation-detail"),
                rule::waitForIdle, true)
            PhysicalUiCapture.save("conversation-detail-${sample.id.padStart(2, '0')}-bottom")
            rule.onNodeWithTag("conversation-back").performClick()
        }
    }
}
