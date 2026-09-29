package com.jackcaow.smoothmarkdown.demo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AIChatProviderTest {
    @Test fun deepSeekIsTheDefaultWithAConfiguredModel() {
        assertEquals(AIProvider.DEEPSEEK, defaultAIProvider)
        assertEquals("DeepSeek", defaultAIProvider.displayName)
        assertTrue("deepseek-flash" in DeepSeekChatClient.MODELS)
    }

    @Test fun flutterWelcomeAndGenericReplyNameTheSelectedProvider() {
        val asset = listOf(
            File("src/main/assets/examples/ai-chat/ai-chat.json"),
            File("app/src/main/assets/examples/ai-chat/ai-chat.json"),
        ).first { it.isFile }
        val fixture = JSONObject(asset.readText())
        for (key in listOf("welcome", "genericResponseTemplate")) {
            val source = fixture.getString(key)
            val deepSeek = aiChatProviderCopy(source, AIProvider.DEEPSEEK)
            assertTrue(deepSeek.contains("DeepSeek"))
            assertFalse(deepSeek.contains("Qwen"))
            val qwen = aiChatProviderCopy(source, AIProvider.QWEN)
            assertTrue(qwen.contains("Qwen"))
            assertFalse(qwen.contains("DeepSeek"))
        }
    }
}
