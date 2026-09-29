package com.jackcaow.smoothmarkdown.demo

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class DeepSeekChatClientTest {
    @Test fun streamsReasoningAndAnswerWithProviderSpecificRequest() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var authorization: String? = null
        var path: String? = null
        var body: JSONObject? = null
        server.createContext("/chat/completions") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            path = exchange.requestURI.path
            body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = """data: {"choices":[{"delta":{"reasoning_content":"思考"}}]}

data: {"choices":[{"delta":{"content":"答案"}}]}

data: [DONE]

"""
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val chunks = mutableListOf<String>()
            DeepSeekChatClient("http://127.0.0.1:${server.address.port}/chat/completions")
                .stream("你好", "test-deepseek-key", "deepseek-flash", true) { chunks += it }
            assertEquals("/chat/completions", path)
            assertEquals("Bearer test-deepseek-key", authorization)
            assertEquals("deepseek-flash", body?.getString("model"))
            assertTrue(body?.getBoolean("stream") == true)
            assertEquals("enabled", body?.getJSONObject("thinking")?.getString("type"))
            assertEquals("你好", body?.getJSONArray("messages")?.getJSONObject(1)?.getString("content"))
            assertEquals("<thinking>\n思考\n</thinking>\n\n答案", chunks.joinToString(""))
            assertFalse(body.toString().contains("enable_thinking"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun rejectsQwenModelBeforeOpeningConnection() = runBlocking {
        val error = runCatching {
            DeepSeekChatClient("http://127.0.0.1:1/chat/completions")
                .stream("hi", "test-deepseek-key", "qwen-max", false) { }
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("Unsupported DeepSeek model", error?.message)
    }

    @Test fun reportsHttpStatusWithoutExposingResponseOrCredential() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val bytes = "secret response body".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val error = runCatching {
                DeepSeekChatClient("http://127.0.0.1:${server.address.port}/chat/completions")
                    .stream("hi", "test-deepseek-key", "deepseek-flash", false) { }
            }.exceptionOrNull()
            assertTrue(error is DeepSeekHttpException)
            assertEquals(401, (error as DeepSeekHttpException).statusCode)
            assertFalse(error.message.orEmpty().contains("test-deepseek-key"))
            assertFalse(error.message.orEmpty().contains("secret response body"))
        } finally {
            server.stop(0)
        }
    }
}
