package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AiChatPluginRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thinkingStartsCollapsedAndCanExpand() {
        val plugins = ParserPluginRegistry().also { it.register(ThinkingPlugin()) }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown("<thinking>\nA private draft\n</thinking>", plugins = plugins)
            }
        }
        compose.onNodeWithText("Thinking").assertExists()
        compose.onNodeWithText("A private draft").assertDoesNotExist()
        compose.onNodeWithText("Thinking").performClick()
        compose.onNodeWithText("A private draft").assertExists()
    }

    @Test fun artifactAndToolRenderSafeText() {
        val plugins = ParserPluginRegistry().also { it.registerAll(listOf(ArtifactPlugin(), ToolCallPlugin())) }
        compose.setContent {
            MaterialTheme {
                SmoothMarkdown(
                    "<artifact id='doc' type='html' title='Generated page'>\n<script>no execution</script>\n</artifact>\n\n" +
                        "<tool_use>\n<tool_name>search</tool_name>\n<input>\nquery: example\n</input>\n</tool_use>",
                    plugins = plugins,
                )
            }
        }
        compose.onNodeWithText("Generated page").assertExists()
        compose.onNodeWithText("<script>no execution</script>").assertExists()
        compose.onNodeWithText("Tool: search").assertExists()
        compose.onNodeWithText("query: example").assertExists()
    }
}
