package com.jackcaow.smoothmarkdown.demo

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeepSeekChatClientTest {
    @Test fun streamsReasoningAndAnswerWithProviderSpecificRequest() = runBlocking {
        var authorization: String? = null
        var path: String? = null
        var body: JSONObject? = null
        val server = LocalHttpFixture { request ->
            authorization = request.headers["authorization"]
            path = request.path
            body = JSONObject(request.body)
            val response = """data: {"choices":[{"delta":{"reasoning_content":"思考"}}]}

data: {"choices":[{"delta":{"content":"答案"}}]}

data: [DONE]

"""
            LocalHttpResponse(200, response, "text/event-stream")
        }
        try {
            val chunks = mutableListOf<String>()
            DeepSeekChatClient("http://127.0.0.1:${server.port}/chat/completions")
                .stream("你好", "test-deepseek-key", "deepseek-flash", true) { chunks += it }
            server.awaitResponse()
            assertEquals("/chat/completions", path)
            assertEquals("Bearer test-deepseek-key", authorization)
            assertEquals("deepseek-flash", body?.getString("model"))
            assertTrue(body?.getBoolean("stream") == true)
            assertEquals("enabled", body?.getJSONObject("thinking")?.getString("type"))
            assertEquals("你好", body?.getJSONArray("messages")?.getJSONObject(1)?.getString("content"))
            assertEquals("<thinking>\n思考\n</thinking>\n\n答案", chunks.joinToString(""))
            assertFalse(body.toString().contains("enable_thinking"))
        } finally {
            server.close()
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
        val server = LocalHttpFixture { request ->
            assertEquals("/chat/completions", request.path)
            LocalHttpResponse(401, "secret response body")
        }
        try {
            val error = runCatching {
                DeepSeekChatClient("http://127.0.0.1:${server.port}/chat/completions")
                    .stream("hi", "test-deepseek-key", "deepseek-flash", false) { }
            }.exceptionOrNull()
            server.awaitResponse()
            assertTrue(error is DeepSeekHttpException)
            assertEquals(401, (error as DeepSeekHttpException).statusCode)
            assertFalse(error.message.orEmpty().contains("test-deepseek-key"))
            assertFalse(error.message.orEmpty().contains("secret response body"))
        } finally {
            server.close()
        }
    }
}

/** A one-request loopback HTTP fixture using only java.base (available to Kotlin 1.9 tests). */
private class LocalHttpFixture(handler: (LocalHttpRequest) -> LocalHttpResponse) : AutoCloseable {
    private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "deepseek-http-fixture").apply { isDaemon = true }
    }
    @Volatile private var client: Socket? = null
    val port: Int get() = listener.localPort
    private val response = executor.submit<Unit> {
        listener.accept().use { socket ->
            client = socket
            socket.soTimeout = 5_000
            val input = socket.getInputStream()
            val requestLine = readHttpLine(input).split(' ')
            require(requestLine.size == 3 && requestLine[0] == "POST") { "Expected HTTP POST" }
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readHttpLine(input)
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                require(separator > 0) { "Malformed HTTP header" }
                headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
            }
            val length = headers.getValue("content-length").toInt()
            val body = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(body, offset, length - offset)
                require(count > 0) { "Truncated HTTP request" }
                offset += count
            }
            val result = handler(LocalHttpRequest(requestLine[1], headers, String(body, StandardCharsets.UTF_8)))
            val bytes = result.body.toByteArray(StandardCharsets.UTF_8)
            val statusText = if (result.status == 200) "OK" else "Unauthorized"
            socket.getOutputStream().apply {
                write(("HTTP/1.1 ${result.status} $statusText\r\n" +
                    "Content-Type: ${result.contentType}\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray(StandardCharsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
        Unit
    }

    /** Propagate server-side assertion/failure and establish visibility before checking requests. */
    fun awaitResponse() { response.get(5, TimeUnit.SECONDS) }

    override fun close() {
        listener.close()
        client?.close()
        executor.shutdownNow()
    }
}

private data class LocalHttpRequest(val path: String, val headers: Map<String, String>, val body: String)
private data class LocalHttpResponse(val status: Int, val body: String, val contentType: String = "text/plain")

private fun readHttpLine(input: InputStream): String {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = input.read()
        require(value >= 0) { "Unexpected EOF reading HTTP headers" }
        if (value == '\n'.code) break
        if (value != '\r'.code) bytes.write(value)
        require(bytes.size() < 65_536) { "HTTP header line too large" }
    }
    return bytes.toString(StandardCharsets.US_ASCII.name())
}
